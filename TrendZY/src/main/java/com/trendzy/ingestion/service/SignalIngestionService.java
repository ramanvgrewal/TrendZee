package com.trendzy.ingestion.service;

import com.microsoft.playwright.Playwright;
import com.trendzy.ingestion.model.TrendSignal;
import com.trendzy.ingestion.repository.TrendSignalRepository;
import com.trendzy.ingestion.scraper.instagram.InstagramExploreClient;
import com.trendzy.ingestion.kafka.KafkaSignalProducer;
import com.trendzy.ingestion.scraper.WebsiteClient;
import com.trendzy.ingestion.scraper.instagram.InstagramSessionManager;
import com.trendzy.ingestion.scraper.InstagramBioExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class SignalIngestionService {

    private final InstagramExploreClient instagramExploreClient;
    private final TrendSignalRepository trendSignalRepository;
    private final KafkaSignalProducer kafkaSignalProducer;
    
    private final WebsiteClient websiteClient;
    private final InstagramSessionManager sessionManager;
    private final InstagramBioExtractor instagramBioExtractor;

    // The un-hackable lock to protect your RAM
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // Let the controller check if it's currently busy
    public boolean isIngestionRunning() {
        return isRunning.get();
    }

    @Async
    public void runIngestionCycle(String category) {
        // Compare-and-Set: If it's false, set it to true and proceed. If it's already true, return.
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("[INGESTION] 🚨 Cycle already running! Ignoring duplicate background execution.");
            return;
        }

        log.info("[INGESTION] ════════ STARTING INGESTION CYCLE — category={} ════════", category);

        List<TrendSignal> allSignals = new ArrayList<>();

        try (Playwright playwright = Playwright.create()) {
            // Instagram Extraction
            try {
                allSignals.addAll(instagramExploreClient.fetchExploreSignals(playwright, category));
            } catch (Exception e) {
                log.error("[INGESTION] Instagram scraper failed: {}", e.getMessage());
            }

            // Setup browser once
            com.microsoft.playwright.Browser browser = playwright.chromium()
                    .launch(new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(true));
            sessionManager.ensureSession(playwright);

            com.microsoft.playwright.Browser.NewContextOptions contextOptions = new com.microsoft.playwright.Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setViewportSize(1280, 900);

            if (sessionManager.sessionExists()) {
                contextOptions.setStorageStatePath(sessionManager.getSessionPath());
            }

            int newSignals = 0;
            try (com.microsoft.playwright.BrowserContext context = browser.newContext(contextOptions)) {
                context.setDefaultTimeout(8_000);
                context.setDefaultNavigationTimeout(20_000);

                log.info("[INGESTION] Starting product scraping and async dispatch for {} signals...", allSignals.size());

                for (TrendSignal signal : allSignals) {
                    signal.setCategory(category);
                    if (trendSignalRepository.existsBySourceUrl(signal.getSourceUrl())) {
                        continue;
                    }

                    try {
                        String platform = signal.getPlatform() != null ? signal.getPlatform().name() : "";
                        if ("INSTAGRAM".equalsIgnoreCase(platform) && signal.getAuthorUsername() != null) {
                            String bioLink = instagramBioExtractor.extractBioLink(signal.getAuthorUsername(), context);
                            if (bioLink != null && !bioLink.isBlank()) {
                                String searchQuery = extractSpecificSearchQuery(signal);
                                log.debug("[INGESTION] Extracted specific search query '{}' for @{}", searchQuery, signal.getAuthorUsername());

                                java.util.List<com.trendzy.ingestion.scraper.RawProduct> products = 
                                    websiteClient.extractProducts(bioLink, playwright, signal.getAuthorUsername(), searchQuery);

                                if (products != null && !products.isEmpty()) {
                                    // Store ALL scraped products — AI worker will select the best one
                                    List<TrendSignal.ScrapedProduct> scrapedProducts = new java.util.ArrayList<>();
                                    for (com.trendzy.ingestion.scraper.RawProduct p : products) {
                                        scrapedProducts.add(TrendSignal.ScrapedProduct.builder()
                                                .productName(p.getProductName())
                                                .mainPrice(p.getMainPrice())
                                                .originalPrice(p.getOriginalPrice())
                                                .currency(p.getCurrency() != null ? p.getCurrency() : "Rs.")
                                                .productUrl(p.getProductUrl())
                                                .imageUrl(p.getImageUrl())
                                                .build());
                                    }
                                    signal.setScrapedProducts(scrapedProducts);
                                    log.info("[INGESTION] ✓ Scraped {} products from @{}'s store", 
                                            scrapedProducts.size(), signal.getAuthorUsername());
                                } else {
                                    log.warn("[INGESTION] ✗ No products found on bio link for @{}", signal.getAuthorUsername());
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("[INGESTION] Product scraping failed for {}: {}", signal.getSourceUrl(), e.getMessage());
                    }

                    // SAVE AND PUSH TO KAFKA IMMEDIATELY
                    try {
                        TrendSignal saved = trendSignalRepository.save(signal);
                        
                        // GATEKEEPER: Only send to AI (Groq) if we successfully extracted the creator's product
                        if (saved.getScrapedProducts() != null && !saved.getScrapedProducts().isEmpty()) {
                            kafkaSignalProducer.publishSignalEvent(saved.getId(), saved.getPlatform());
                            log.info("[INGESTION] 🟢 Published signal {} to Kafka (scraped products found)", saved.getId());
                        } else {
                            log.info("[INGESTION] 🔴 Skipped Kafka publish for {} (no scraped products)", saved.getId());
                        }
                        
                        newSignals++;
                    } catch (Exception e) {
                        log.error("[INGESTION] Failed to process signal {}: {}", signal.getSourceUrl(), e.getMessage());
                    }
                }
            } finally {
                browser.close();
            }

            log.info("[INGESTION] CYCLE COMPLETE | category={} | Processed: {} | Total Scraped: {}",
                    category, newSignals, allSignals.size());
                    
        } catch (Exception e) {
            log.error("[INGESTION] Fatal Playwright error: {}", e.getMessage(), e);
        } finally {
            // 🔓 RELEASING THE LOCK - guaranteed to happen even if Playwright throws an exception
            isRunning.set(false);
            log.info("[INGESTION] 🔓 Lock released. Ready for next cycle.");
        }
    }

    // Removed enrichUnderdogProducts and processAndSaveSignals as they are now inline in runIngestionCycle

    private String extractSpecificSearchQuery(TrendSignal signal) {
        String baseCategory = signal.getCategory();
        if (baseCategory == null) baseCategory = "";
        
        String text = (signal.getRawText() != null ? signal.getRawText().toLowerCase() : "");
        String tags = String.join(" ", signal.getHashtags()).toLowerCase();
        
        // List of highly specific product keywords
        List<String> specificKeywords = List.of(
            "polo", "t-shirt", "tee", "hoodie", "jacket", "cargo", "co-ord", "oversized", 
            "jeans", "sneaker", "jordan", "yeezy", "dunk", "bag", "watch", "bracelet", "cap",
            "sweater", "sweatshirt", "cardigan", "blazer", "suit", "tracksuit"
        );
        
        for (String kw : specificKeywords) {
            // Match whole word only using regex boundary
            if (text.matches(".*\\b" + kw + "\\b.*") || tags.matches(".*\\b" + kw + "\\b.*")) {
                return kw;
            }
        }
        
        return baseCategory;
    }
}