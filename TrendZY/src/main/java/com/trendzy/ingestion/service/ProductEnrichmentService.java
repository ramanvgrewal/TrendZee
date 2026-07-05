package com.trendzy.ingestion.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import com.trendzy.ingestion.model.Trend;
import com.trendzy.ingestion.model.TrendSignal;
import com.trendzy.ingestion.repository.TrendRepository;
import com.trendzy.ingestion.repository.TrendSignalRepository;
import com.trendzy.ingestion.scraper.WebsiteClient;
import com.trendzy.ingestion.scraper.RawProduct;
import com.trendzy.ingestion.scraper.InstagramBioExtractor;
import com.trendzy.ingestion.scraper.instagram.InstagramSessionManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductEnrichmentService {

    private final TrendRepository trendRepository;
    private final TrendSignalRepository signalRepository;
    private final WebsiteClient websiteClient;
    private final InstagramSessionManager sessionManager;
    private final InstagramBioExtractor instagramBioExtractor;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Pattern URL_IN_CAPTION =
            Pattern.compile("https?://[\\w.\\-/?=&%#+~:@]+", Pattern.CASE_INSENSITIVE);

    private static final Set<String> BLOCKED_URL_HOSTS = Set.of(
            "instagram.com", "www.instagram.com", "facebook.com", "fb.com",
            "twitter.com", "x.com", "youtube.com", "youtu.be", "tiktok.com",
            "threads.net", "pinterest.com", "snapchat.com", "t.me",
            "linktr.ee", "beacons.ai", "bio.link", "bio.site", "solo.to",
            "campsite.bio", "hoo.be", "linkpop.com",
            "amazon.com", "amazon.in", "flipkart.com", "myntra.com",
            "ajio.com", "meesho.com", "snapdeal.com"
    );

    /** Maps each category to product-type keywords for filtering and matching. */
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = Map.of(
            "SNEAKERS",    List.of("shoe", "sneaker", "kicks", "trainer", "footwear", "jordan", "yeezy", "dunk", "air max", "sports shoe"),
            "STREETWEAR",  List.of("tee", "t-shirt", "hoodie", "jacket", "pants", "shirt", "co-ord", "cargo", "oversized"),
            "SPORTSWEAR",  List.of("gym", "activewear", "sports", "compression", "jersey", "tracksuit", "shorts", "athletic"),
            "WATCHES",     List.of("watch", "timepiece", "chronograph", "wristwatch"),
            "ACCESSORIES", List.of("bag", "wallet", "belt", "chain", "ring", "bracelet", "cap", "sunglasses")
    );

    /** Maps each category to a short suffix appended to marketplace search queries. */
    private static final Map<String, String> CATEGORY_SEARCH_SUFFIX = Map.of(
            "SNEAKERS",    "shoes",
            "STREETWEAR",  "clothing",
            "SPORTSWEAR",  "activewear",
            "WATCHES",     "watch",
            "ACCESSORIES", "accessories"
    );

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "60000")
    public void runEnrichmentBatch() {
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("[ENRICH] 🚨 Previous batch still running — skipping tick.");
            return;
        }
        try {
            processEnrichmentBatch();
        } finally {
            isRunning.set(false);
        }
    }

    private void processEnrichmentBatch() {
        List<Trend> pendingTrends = trendRepository.findPendingEnrichment();
        if (pendingTrends.isEmpty()) return;

        log.info("[ENRICH] Starting batch for {} trends...", pendingTrends.size());

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium()
                    .launch(new BrowserType.LaunchOptions().setHeadless(true));
            sessionManager.ensureSession(playwright);

            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                    .setUserAgent(USER_AGENT)
                    .setViewportSize(1280, 900);

            if (sessionManager.sessionExists()) {
                contextOptions.setStorageStatePath(sessionManager.getSessionPath());
            }

            try (BrowserContext context = browser.newContext(contextOptions)) {
                context.setDefaultTimeout(8_000);
                context.setDefaultNavigationTimeout(20_000);

                for (Trend trend : pendingTrends) {
                    try {
                        enrichSingleTrend(trend, context, playwright);
                    } catch (Exception e) {
                        log.error("[ENRICH] Failed for trend {}: {}",
                                trend.getTrendName(), e.getMessage());
                        trend.setEnrichmentStatus("FAILED");
                        trendRepository.save(trend);
                    }
                }
            }
            browser.close();
        } catch (Exception e) {
            log.error("[ENRICH] Fatal engine error: {}", e.getMessage(), e);
        }
    }

    // ── Per-signal enrichment (single signal per trend) ───────────────────
    private void enrichSingleTrend(Trend trend, BrowserContext context, Playwright playwright) {
        log.info("[ENRICH] Hunting products for trend '{}'", trend.getTrendName());
        trend.setEnrichmentStatus("PROCESSING");
        trendRepository.save(trend);

        List<String> signalIds = trend.getSupportingSignalIds();
        if (signalIds == null || signalIds.isEmpty()) {
            trend.setEnrichmentStatus("FAILED");
            trend.setLastUpdatedAt(LocalDateTime.now());
            trendRepository.save(trend);
            log.warn("[ENRICH] No supporting signals for '{}'", trend.getTrendName());
            return;
        }

        // Pick the first signal only
        String signalId = signalIds.get(0);
        var signalOpt = signalRepository.findById(signalId);
        if (signalOpt.isEmpty()) {
            trend.setEnrichmentStatus("FAILED");
            trend.setLastUpdatedAt(LocalDateTime.now());
            trendRepository.save(trend);
            log.warn("[ENRICH] Signal {} not found for '{}'", signalId, trend.getTrendName());
            return;
        }
        TrendSignal signal = signalOpt.get();

        // ── 1. Query keywords (LLM → fallback trend name) ──
        String query = pickQuery(signal, trend);
        String marketplaceQuery = pickMarketplaceQuery(signal, trend);
        log.info("[ENRICH] signal={} query='{}' marketplaceQuery='{}'", signalId, query, marketplaceQuery);

        // ── 2. Marketplace scrapes (category-aware query) ──
        Trend.ProductDetail amazon = null;
        Trend.ProductDetail flipkart = null;
        try { amazon   = scrapeAmazon(marketplaceQuery, context); } catch (Exception e) { log.warn("[AMAZON] {}", e.getMessage()); }
        try { flipkart = scrapeFlipkart(marketplaceQuery, context); } catch (Exception e) { log.warn("[FLIPKART] {}", e.getMessage()); }

        // ── 3. Underdog: caption URL first, then IG bio (category-filtered) ──
        Set<String> usedUnderdogHosts = new HashSet<>();
        Trend.ProductDetail underdog = resolveUnderdog(signal, context, playwright, usedUnderdogHosts, query, trend.getCategory());

        // Set the single SignalProducts object
        trend.setSignalProducts(Trend.SignalProducts.builder()
                .signalId(signalId)
                .authorUsername(signal.getAuthorUsername())
                .queryUsed(query)
                .underdog(underdog)
                .amazon(amazon)
                .flipkart(flipkart)
                .build());

        if (underdog != null && underdog.getCurrency() != null) {
            trend.setCurrency(underdog.getCurrency());
        }

        // ── Status reflects actual coverage (max 3 pieces) ──
        int totalPieces = 0;
        if (amazon != null)   totalPieces++;
        if (flipkart != null) totalPieces++;
        if (underdog != null) totalPieces++;

        String status;
        if (totalPieces == 0)       status = "FAILED";
        else if (totalPieces >= 3)  status = "COMPLETED";
        else                        status = "PARTIAL";

        trend.setEnrichmentStatus(status);
        trend.setLastUpdatedAt(LocalDateTime.now());
        trendRepository.save(trend);

        log.info("[ENRICH] ✅ '{}' → {} | pieces={}/3",
                trend.getTrendName(), status, totalPieces);
    }

    // ── Query selection ────────────────────────────────────────────────
    private String pickQuery(TrendSignal signal, Trend trend) {
        // Prefer LLM keywords when present.
        if (signal.getExtractedKeywords() != null && !signal.getExtractedKeywords().isEmpty()) {
            // First keyword is usually the most specific product phrase.
            String primary = signal.getExtractedKeywords().get(0);
            if (primary != null && !primary.isBlank()) return primary.trim();
        }
        // Fallback: trend enrichmentQuery → trendName
        if (trend.getEnrichmentQuery() != null && !trend.getEnrichmentQuery().isBlank())
            return trend.getEnrichmentQuery();
        return trend.getTrendName() == null ? "" : trend.getTrendName();
    }

    /**
     * Builds a marketplace-specific search query by appending a category-relevant
     * suffix (e.g. "shoes" for SNEAKERS) so Amazon/Flipkart return relevant products.
     */
    private String pickMarketplaceQuery(TrendSignal signal, Trend trend) {
        String base = pickQuery(signal, trend);
        String category = trend.getCategory();
        String suffix = category != null
                ? CATEGORY_SEARCH_SUFFIX.getOrDefault(category.toUpperCase(), "")
                : "";

        if (!suffix.isEmpty() && !base.toLowerCase().contains(suffix)) {
            return base + " " + suffix;
        }
        return base;
    }

    // ── Underdog resolution (caption URL → IG bio) ────────────────────
    private Trend.ProductDetail resolveUnderdog(TrendSignal signal, BrowserContext context,
                                                Playwright playwright, Set<String> usedHosts,
                                                String query, String category) {
        // 1) Caption URL first
        String captionUrl = firstUsableUrl(signal.getRawText());
        if (captionUrl != null) {
            Trend.ProductDetail p = scrapeUnderdogFromUrl(captionUrl, signal, playwright, usedHosts, query, category);
            if (p != null) return p;
        }

        // 2) Instagram bio
        String platform = signal.getPlatform() != null ? signal.getPlatform().name() : "";
        if ("INSTAGRAM".equalsIgnoreCase(platform) && signal.getAuthorUsername() != null) {
            try {
                String bioLink = instagramBioExtractor.extractBioLink(signal.getAuthorUsername(), context);
                if (bioLink != null && !bioLink.isBlank()) {
                    return scrapeUnderdogFromUrl(bioLink, signal, playwright, usedHosts, query, category);
                }
            } catch (Exception e) {
                log.warn("[UNDERDOG] IG bio failed for @{}: {}", signal.getAuthorUsername(), e.getMessage());
            }
        }
        return null;
    }

    private String firstUsableUrl(String rawText) {
        if (rawText == null || rawText.isBlank()) return null;
        Matcher m = URL_IN_CAPTION.matcher(rawText);
        while (m.find()) {
            String url = m.group();
            String host = safeHost(url);
            if (host != null && !BLOCKED_URL_HOSTS.contains(host)) return url;
        }
        return null;
    }

    private String safeHost(String url) {
        try {
            String h = URI.create(url).getHost();
            return h == null ? null : h.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    private Trend.ProductDetail scrapeUnderdogFromUrl(String url, TrendSignal signal,
                                                      Playwright playwright, Set<String> usedHosts,
                                                      String query, String category) {
        String host = safeHost(url);
        if (host == null || usedHosts.contains(host)) return null;  // dedupe

        try {
            List<RawProduct> products = websiteClient.extractProducts(url, playwright, signal.getAuthorUsername());
            if (products == null || products.isEmpty()) return null;

            RawProduct p = findBestMatch(products, query, category);
            usedHosts.add(host);

            Integer selling = p.getMainPrice() != null ? Integer.valueOf(p.getMainPrice().intValue()) : null;
            Integer mrp     = p.getOriginalPrice() != null ? Integer.valueOf(p.getOriginalPrice().intValue()) : selling;

            // Fall back to the signal's original post media URL when the scraper
            // could not fetch a product image (e.g. gated/loading-screen sites).
            String imageUrl = p.getImageUrl();
            if (imageUrl == null || imageUrl.isBlank()) {
                imageUrl = signal.getMediaUrl();
                if (imageUrl != null && !imageUrl.isBlank()) {
                    log.info("[UNDERDOG] Image scrape failed for '{}' — falling back to signal mediaUrl", url);
                }
            }

            return Trend.ProductDetail.builder()
                    .brandName(signal.getAuthorUsername())
                    .title(p.getProductName())
                    .price(selling)
                    .originalPrice(mrp)
                    .currency(p.getCurrency() != null ? p.getCurrency() : "Rs.")
                    .shopUrl(p.getProductUrl())
                    .imageUrl(imageUrl)
                    .codAvailable(true)
                    .build();
        } catch (Exception e) {
            log.warn("[UNDERDOG] scrape failed for {}: {}", url, e.getMessage());
            return null;
        }
    }

    // ── Amazon India ───────────────────────────────────────────────────
    private Trend.ProductDetail scrapeAmazon(String query, BrowserContext context) {
        if (query == null || query.isBlank()) return null;
        try (Page page = context.newPage()) {
            page.navigate("https://www.amazon.in/s?k=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
            page.waitForLoadState();
            page.waitForTimeout(2000);

            String pageTitle = page.title();
            if (pageTitle != null && pageTitle.toLowerCase().contains("captcha")) {
                log.warn("[AMAZON] CAPTCHA for '{}'", query);
                return null;
            }

            Locator firstTile = null;
            String[] tileSelectors = {
                    "div[data-component-type='s-search-result']",
                    "div.s-result-item[data-asin]:not([data-asin=''])",
                    "div.sg-col-inner .s-result-item",
                    "[data-cel-widget^='search_result_']"
            };
            for (String sel : tileSelectors) {
                Locator loc = page.locator(sel).first();
                try { if (loc.count() > 0) { firstTile = loc; break; } } catch (Exception ignored) {}
            }
            if (firstTile == null) return null;

            String title = extractText(firstTile, "h2 a span", "h2 span", "h2", "span.a-text-normal");
            if (title == null || title.isBlank()) return null;

            Integer sellingPrice = parsePriceClean(extractText(firstTile,
                    "span.a-price:not([data-a-strike]) span.a-offscreen",
                    "span.a-price-whole",
                    "span.a-price span.a-offscreen",
                    "span.a-color-price"));

            Integer originalPrice = parsePriceClean(extractText(firstTile,
                    "span.a-price[data-a-strike] span.a-offscreen",
                    "span.a-text-price span.a-offscreen",
                    "span.a-text-price",
                    "span.priceBlockStrikePriceString"));
            if (originalPrice == null) originalPrice = sellingPrice;

            String link = null;
            for (String sel : new String[]{"h2 a.a-link-normal", "a.a-link-normal.s-no-outline",
                    "a.a-link-normal[href*='/dp/']", ".s-product-image-container a"}) {
                try {
                    Locator loc = firstTile.locator(sel).first();
                    if (loc.count() > 0) {
                        String href = loc.getAttribute("href");
                        if (href != null && !href.isBlank()) {
                            link = href.startsWith("http") ? href : "https://www.amazon.in" + href;
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }

            String imgUrl = null;
            try {
                imgUrl = firstTile.locator("img.s-image").getAttribute("src");
                if (imgUrl == null) imgUrl = firstTile.locator("img").first().getAttribute("src");
            } catch (Exception ignored) {}

            return Trend.ProductDetail.builder()
                    .title(title).price(sellingPrice).originalPrice(originalPrice)
                    .shopUrl(link).imageUrl(imgUrl).brandName("Amazon").codAvailable(true).build();
        } catch (Exception e) {
            log.warn("[AMAZON] '{}' failed: {}", query, e.getMessage());
            return null;
        }
    }

    // ── Flipkart (short timeouts) ─────────────────────────────────────
    private Trend.ProductDetail scrapeFlipkart(String query, BrowserContext context) {
        if (query == null || query.isBlank()) return null;
        try (Page page = context.newPage()) {
            page.setDefaultTimeout(6_000);
            page.setDefaultNavigationTimeout(20_000);

            page.navigate(
                    "https://www.flipkart.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8),
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

            try {
                page.locator("button._2KpZ6l._2doB4z, button[class*='_2KpZ6l']").first()
                        .click(new Locator.ClickOptions().setTimeout(1500));
            } catch (Exception ignored) {}

            String anyAnchor = "a[href*='/p/'], a.CGtC98, a._1fQZEK, a.s1Q9rs, a.IRpwTa";
            try {
                page.locator(anyAnchor).first().waitFor(
                        new Locator.WaitForOptions().setTimeout(4000).setState(WaitForSelectorState.ATTACHED));
            } catch (TimeoutError e) {
                log.warn("[FLIPKART] no anchors for '{}'", query);
                return null;
            }

            Locator firstTile = null;
            for (String sel : new String[]{"a.CGtC98", "a._1fQZEK", "a[href*='/p/']", "div._1AtVbE a", "div._4ddWXP a"}) {
                Locator loc = page.locator(sel).first();
                if (loc.count() > 0) { firstTile = loc; break; }
            }
            if (firstTile == null) return null;

            Locator card;
            try {
                Locator candidate = firstTile.locator("xpath=ancestor::div[@data-id]").first();
                card = (candidate.count() > 0) ? candidate : firstTile;
            } catch (Exception e) { card = firstTile; }

            String href = safeAttr(firstTile, "href");
            String link = href == null ? null : (href.startsWith("http") ? href : "https://www.flipkart.com" + href);

            String imgUrl = null;
            try {
                Locator img = card.locator("img").first();
                if (img.count() > 0) {
                    imgUrl = safeAttr(img, "src");
                    if (imgUrl == null || imgUrl.isBlank()) imgUrl = safeAttr(img, "srcset");
                }
            } catch (Exception ignored) {}

            String title = extractText(card, ".KzDlHZ", "div.KzDlHZ", ".IRpw9B", ".syl9yP",
                    "div._4rR01T", "div.s1Q9rs", "a.IRpwTa");
            if (title == null || title.isBlank()) {
                try {
                    Locator img = card.locator("img").first();
                    if (img.count() > 0) title = safeAttr(img, "alt");
                } catch (Exception ignored) {}
            }

            Integer sellingPrice = parsePriceClean(extractText(card,
                    "div.Nx9bqj", ".Nx9bqj", "div._30jeq3", "._30jeq3", "[class*='Nx9bqj']"));
            Integer originalPrice = parsePriceClean(extractText(card,
                    "div.yRaY8j", ".yRaY8j", "div._3I9_wc", "._3I9_wc", "[class*='yRaY8j']"));
            if (originalPrice == null) originalPrice = sellingPrice;

            if ((title == null || title.isBlank()) && sellingPrice == null && link == null) return null;

            return Trend.ProductDetail.builder()
                    .title(title).price(sellingPrice).originalPrice(originalPrice)
                    .shopUrl(link).imageUrl(imgUrl).brandName("Flipkart").codAvailable(true).build();
        } catch (Exception e) {
            log.warn("[FLIPKART] '{}' failed: {}", query, e.getMessage());
            return null;
        }
    }

    // ── Category-aware product matching ───────────────────────────────

    /**
     * Scores each product against the query and category keywords,
     * returning the best match. Falls back to the first product if
     * nothing matches.
     */
    private RawProduct findBestMatch(List<RawProduct> products, String query, String category) {
        if (products.size() == 1) return products.get(0);

        List<String> categoryTerms = category != null
                ? CATEGORY_KEYWORDS.getOrDefault(category.toUpperCase(), List.of())
                : List.of();

        String[] queryTokens = query != null ? query.toLowerCase().split("\\s+") : new String[0];

        RawProduct best = null;
        int bestScore = -1;

        for (RawProduct product : products) {
            String title = product.getProductName() != null ? product.getProductName().toLowerCase() : "";
            int score = 0;

            // Score: category keyword matches (high weight)
            for (String term : categoryTerms) {
                if (title.contains(term)) score += 3;
            }

            // Score: query token matches
            for (String token : queryTokens) {
                if (token.length() > 2 && title.contains(token)) score += 1;
            }

            if (score > bestScore) {
                bestScore = score;
                best = product;
            }
        }

        if (best != null && bestScore > 0) {
            log.debug("[UNDERDOG] Best match: '{}' (score={})", best.getProductName(), bestScore);
            return best;
        }

        // No keyword match found — fall back to first product
        return products.get(0);
    }

    // ── Utilities ─────────────────────────────────────────────────────
    private String extractText(Locator parent, String... selectors) {
        for (String selector : selectors) {
            try {
                Locator loc = parent.locator(selector).first();
                if (loc.count() > 0) {
                    String text = loc.innerText(new Locator.InnerTextOptions().setTimeout(1500));
                    if (text != null && !text.isBlank()) return text.trim();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String safeAttr(Locator loc, String attr) {
        try { return loc.getAttribute(attr, new Locator.GetAttributeOptions().setTimeout(1500)); }
        catch (Exception e) { return null; }
    }

    private Integer parsePriceClean(String priceStr) {
        if (priceStr == null || priceStr.isBlank()) return null;
        try {
            String clean = priceStr.replaceAll("[^0-9]", "");
            if (clean.isEmpty()) return null;
            return Integer.parseInt(clean);
        } catch (NumberFormatException e) { return null; }
    }
}
