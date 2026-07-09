package com.trendzy.ingestion.scraper;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebsiteClient {

    private final ShopifyParser shopifyParser;
    private final GenericParser genericParser;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Safari/537.36";
    private static final int LOAD_TIMEOUT = 30_000;
    private static final int MAX_PRODUCTS = 50;

    private static final List<String> AGGREGATOR_DOMAINS = List.of(
            "linktr.ee", "beacons.ai", "solo.to", "bio.site", "bio.link", "campsite.bio", "hoo.be", "linkpop"
    );
    private static final List<String> MARKETPLACE_DOMAINS = List.of(
            "amazon.", "flipkart.", "myntra.", "ajio.", "meesho.", "snapdeal."
    );
    private static final List<String> SHOPIFY_SIGNALS = List.of(
            "cdn.shopify.com", "myshopify.com", "Shopify.theme", "/cart.js", "window.Shopify"
    );
    private static final List<String> WIX_SIGNALS = List.of(
            "static.wixstatic.com", "wixsite.com", "_wix_browser_sess",
            "wix-viewer-model", "X-Wix-", "wix-thunderbolt"
    );
    private static final List<String> WOOCOMMERCE_SIGNALS = List.of(
            "wp-content", "wc-cart", "woocommerce", "wc-blocks", "wp-json/wc"
    );

    // Common "all products" page paths — tried in order when initial page yields no products
    private static final java.util.Map<String, List<String>> CATEGORY_KEYWORDS = java.util.Map.of(
            "sneakers",    List.of("shoe", "sneaker", "kicks", "trainer", "footwear", "jordan", "yeezy", "dunk", "air max", "sports shoe"),
            "streetwear",  List.of("tee", "t-shirt", "hoodie", "jacket", "pants", "shirt", "co-ord", "cargo", "oversized"),
            "sportswear",  List.of("gym", "activewear", "sports", "compression", "jersey", "tracksuit", "shorts", "athletic"),
            "watches",     List.of("watch", "timepiece", "chronograph", "wristwatch"),
            "accessories", List.of("bag", "wallet", "belt", "chain", "ring", "bracelet", "cap", "sunglasses")
    );

    record NavCandidate(String url, double score) {}

    public List<RawProduct> extractProducts(String rawUrl, Playwright playwright, String brandName, String searchQuery) {
        List<RawProduct> products = new ArrayList<>();

        if (rawUrl == null || rawUrl.isBlank()) return products;

        rawUrl = normalizeInputUrl(rawUrl);
        if (rawUrl == null) {
            log.warn("[WEBSITE] normalizeInputUrl returned null for @{}", brandName);
            return products;
        }
        if (isMarketplace(rawUrl)) {
            log.debug("[WEBSITE] Skipping marketplace URL for @{}: {}", brandName, rawUrl);
            return products;
        }

        log.info("[WEBSITE] Scraping underdog for @{}: {}", brandName, rawUrl);

        BrowserType.LaunchOptions launchOpts = new BrowserType.LaunchOptions().setHeadless(true);

        try (Browser browser = playwright.chromium().launch(launchOpts)) {
            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions().setViewportSize(1280, 900).setUserAgent(USER_AGENT));

            Page page = context.newPage();

            String resolvedUrl = resolveUrl(page, rawUrl, brandName);
            if (resolvedUrl == null) {
                log.warn("[WEBSITE] ✗ resolveUrl returned null for @{} ({})", brandName, rawUrl);
                context.close();
                return products;
            }

            String baseUrl = extractBaseUrl(resolvedUrl);
            String platform = detectPlatform(page, resolvedUrl);
            log.info("[WEBSITE] Platform for @{}: {}", brandName, platform);

            // 1. Try Category Search First
            products = tryCategorySearch(page, baseUrl, brandName, searchQuery, platform);
            products = deduplicate(products);

            // Adaptive stop
            long highConfCount = countHighConfidence(products, searchQuery);
            if (highConfCount >= 10) {
                log.info("[WEBSITE] Search bar yielded {} high-confidence products for @{}. Stopping early.", highConfCount, brandName);
                return getTopCandidates(products, searchQuery);
            }

            // 2. Discover internal links (Crawl Homepage)
            log.info("[WEBSITE] Initiating homepage crawl to discover navigation links for @{}", brandName);
            if (!page.url().equals(resolvedUrl)) {
                attemptNavigation(page, resolvedUrl);
            }
            
            List<NavCandidate> navLinks = discoverAndScoreLinks(page, baseUrl, searchQuery);
            log.info("[WEBSITE] Discovered {} scored navigation links for @{}", navLinks.size(), brandName);

            // 3. Visit top links adaptively
            for (NavCandidate nav : navLinks) {
                log.info("[WEBSITE] Crawling internal link: {} (Score: {})", nav.url(), nav.score());
                if (attemptNavigation(page, nav.url())) {
                    List<RawProduct> newProducts = "SHOPIFY".equals(platform) ? 
                            shopifyParser.extractProducts(page, baseUrl, page.url()) : 
                            genericParser.extractProducts(page, baseUrl);
                    
                    // Attempt to handle basic infinite scroll/pagination
                    newProducts.addAll(handlePagination(page, platform, baseUrl));

                    // Add to our pool
                    products.addAll(newProducts);
                    
                    // Deduplicate
                    products = deduplicate(products);

                    // Evaluate adaptive stop condition
                    highConfCount = countHighConfidence(products, searchQuery);
                    log.info("[WEBSITE] Found {} total products ({} high-confidence) so far.", products.size(), highConfCount);
                    if (highConfCount >= 12) {
                        log.info("[WEBSITE] Reached high-confidence threshold. Stopping page exploration.");
                        break;
                    }
                }
            }

            // Fallback: If we didn't visit any links or still have 0, try homepage parsing just in case
            if (products.isEmpty()) {
                log.info("[WEBSITE] No products found on subpages, falling back to homepage extraction.");
                if (!page.url().equals(resolvedUrl)) attemptNavigation(page, resolvedUrl);
                products = "SHOPIFY".equals(platform) ? 
                        shopifyParser.extractProducts(page, baseUrl, page.url()) : 
                        genericParser.extractProducts(page, baseUrl);
            }

            products = deduplicate(products);
            log.info("[WEBSITE] ✓ {} products found for @{} before final ranking", products.size(), brandName);

            // 4. Heuristic Product Ranking & Truncation
            products = getTopCandidates(products, searchQuery);
            
            log.info("[WEBSITE] Returning top {} ranked products for AI analysis", products.size());

            context.close();

        } catch (Exception e) {
            log.error("[WEBSITE] Fatal error for @{} ({}): {}", brandName, rawUrl, e.getMessage());
        }
        return products;
    }

    // ── Category Search & Navigation ───────────────────────────────────────

    private List<RawProduct> tryCategorySearch(Page page, String baseUrl, String brandName, String category, String platform) {
        if (category == null || category.isBlank()) return Collections.emptyList();
        String catLower = category.toLowerCase().trim();
        List<RawProduct> products = new ArrayList<>();

        // 1. Try DOM Search Bar
        try {
            com.microsoft.playwright.Locator searchInput = page.locator("input[type='search'], input[name='q'], input[name='s'], input[placeholder*='Search' i]").first();
            if (searchInput.isVisible()) {
                log.info("[WEBSITE] Found search bar for @{}, searching for {}", brandName, catLower);
                searchInput.fill(catLower);
                searchInput.press("Enter");
                
                try {
                    page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE, 
                            new Page.WaitForLoadStateOptions().setTimeout(5000));
                } catch (Exception ignored) {}
                page.waitForTimeout(2000); // Wait for SPA animations
                
                products = "SHOPIFY".equals(platform) ? 
                        shopifyParser.extractProducts(page, baseUrl, page.url()) : 
                        genericParser.extractProducts(page, baseUrl);
                
                if (!products.isEmpty()) {
                    log.info("[WEBSITE] ✓ DOM search yielded {} products for @{}", products.size(), brandName);
                    return products;
                }
            }
        } catch (Exception e) {
            log.debug("[WEBSITE] DOM search failed/timed out for @{}: {}", brandName, e.getMessage());
        }

        // 2. Try Category-Specific URL paths
        List<String> searchPaths = List.of(
                "/search?q=" + catLower,
                "/?s=" + catLower,
                "/collections/" + catLower,
                "/category/" + catLower,
                "/products/" + catLower
        );
        
        for (String path : searchPaths) {
            String searchUrl = baseUrl.replaceAll("/+$", "") + path;
            log.debug("[WEBSITE] Trying category URL for @{}: {}", brandName, searchUrl);
            try {
                if (!attemptNavigation(page, searchUrl)) continue;
                
                products = "SHOPIFY".equals(platform) ? 
                        shopifyParser.extractProducts(page, baseUrl, searchUrl) : 
                        genericParser.extractProducts(page, baseUrl);
                
                if (!products.isEmpty()) {
                    log.info("[WEBSITE] ✓ Category URL {} yielded {} products for @{}", path, products.size(), brandName);
                    return products;
                }
            } catch (Exception e) {
                log.debug("[WEBSITE] Category URL {} failed for @{}: {}", path, brandName, e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    // ── Collection page auto-discovery (removed, replaced by discoverAndScoreLinks) ──

    // ── URL resolution ────────────────────────────────────────────────────

    private String resolveUrl(Page page, String rawUrl, String brandName) {
        boolean navigated = attemptNavigation(page, rawUrl);

        // If HTTP failed, try HTTPS upgrade
        if (!navigated && rawUrl.startsWith("http://")) {
            String httpsUrl = rawUrl.replaceFirst("^http://", "https://");
            log.info("[WEBSITE] HTTP failed for @{}, retrying with HTTPS: {}", brandName, httpsUrl);
            navigated = attemptNavigation(page, httpsUrl);
        }

        if (!navigated) {
            log.warn("[WEBSITE] Navigation failed for @{}: {} (timeout/error)", brandName, rawUrl);
            return null;
        }

        String currentUrl = page.url();
        boolean isAggregator = AGGREGATOR_DOMAINS.stream().anyMatch(d -> currentUrl.toLowerCase().contains(d));

        if (!isAggregator) return currentUrl;

        log.info("[WEBSITE] Detected aggregator for @{}: {} — scanning for shop link", brandName, currentUrl);

        List<String> shopSelectors = List.of(
                "a[href*='shop']", "a[href*='store']", "a[href*='.in']", "a[href*='.com']",
                "a:has-text('shop')", "a:has-text('store')", "a:has-text('website')"
        );

        for (String sel : shopSelectors) {
            try {
                List<ElementHandle> anchors = page.querySelectorAll(sel);
                for (ElementHandle a : anchors) {
                    String href = a.getAttribute("href");
                    if (href != null && href.startsWith("http") && !isKnownSocialMedia(href)
                            && AGGREGATOR_DOMAINS.stream().noneMatch(href::contains)) {
                        return href.split("\\?")[0];
                    }
                }
            } catch (Exception ignored) {}
        }

        log.warn("[WEBSITE] Aggregator for @{} had no outbound shop link: {}", brandName, currentUrl);
        return null;
    }

    // ── Navigation with NETWORKIDLE + scroll-to-load ──────────────────────

    private boolean attemptNavigation(Page page, String url) {
        try {
            page.navigate(url, new Page.NavigateOptions().setTimeout(LOAD_TIMEOUT));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            // Try NETWORKIDLE for JS-heavy sites (Wix, React, etc.) — don't fail if it times out
            try {
                page.waitForLoadState(LoadState.NETWORKIDLE,
                        new Page.WaitForLoadStateOptions().setTimeout(5_000));
            } catch (Exception ignored) {}

            // Hard wait for React/Vite SPAs (like Zestwear) to hydrate
            page.waitForTimeout(3000);

            // Scroll page to trigger lazy-load rendering (critical for Wix, React sites)
            for (int i = 0; i < 3; i++) {
                page.evaluate("window.scrollBy(0, window.innerHeight)");
                page.waitForTimeout(800);
            }
            // Scroll back to top so subsequent selectors see the full page
            page.evaluate("window.scrollTo(0, 0)");
            page.waitForTimeout(500);

            return true;
        } catch (Exception e) {
            log.debug("[WEBSITE] attemptNavigation failed for {}: {}", url, e.getMessage());
            return false;
        }
    }

    // ── Platform detection ────────────────────────────────────────────────

    private String detectPlatform(Page page, String resolvedUrl) {
        if (resolvedUrl.contains("myshopify.com")) return "SHOPIFY";
        try {
            String html = page.content();
            for (String signal : SHOPIFY_SIGNALS) {
                if (html.contains(signal)) return "SHOPIFY";
            }
            for (String signal : WIX_SIGNALS) {
                if (html.contains(signal)) return "WIX";
            }
            for (String signal : WOOCOMMERCE_SIGNALS) {
                if (html.contains(signal)) return "WOOCOMMERCE";
            }
        } catch (Exception ignored) {}
        return "GENERIC";
    }

    // ── Utilities ─────────────────────────────────────────────────────────

    private String normalizeInputUrl(String rawUrl) {
        String trimmed = rawUrl.trim();
        if (!trimmed.startsWith("http")) trimmed = "https://" + trimmed;
        return trimmed.split("\\?")[0];
    }

    private boolean isMarketplace(String url) {
        return MARKETPLACE_DOMAINS.stream().anyMatch(url.toLowerCase()::contains);
    }

    private String extractBaseUrl(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            return uri.getScheme() + "://" + uri.getHost();
        } catch (Exception e) {
            return url;
        }
    }

    private boolean isKnownSocialMedia(String url) {
        List<String> social = List.of("instagram.com", "facebook.com", "twitter.com", "tiktok.com", "youtube.com");
        return social.stream().anyMatch(url.toLowerCase()::contains);
    }

    // ── Beast Mode Relevance Filter & Heuristics ──────────────────────────

    private List<NavCandidate> discoverAndScoreLinks(Page page, String baseUrl, String searchQuery) {
        List<ElementHandle> anchors = page.querySelectorAll("a[href]");
        java.util.Map<String, Double> linkScores = new java.util.HashMap<>();
        
        String lowerQuery = searchQuery != null ? searchQuery.toLowerCase() : "";
        List<String> queryTerms = CATEGORY_KEYWORDS.getOrDefault(lowerQuery, List.of(lowerQuery));

        for (ElementHandle a : anchors) {
            String href = a.getAttribute("href");
            if (href == null || href.isBlank() || href.startsWith("javascript") || href.startsWith("#") || href.startsWith("tel:") || href.startsWith("mailto:")) continue;
            
            String url = resolveInternalUrl(href, baseUrl);
            if (!url.startsWith(baseUrl)) continue; // Internal links only
            if (url.contains("/products/") || url.contains("/p/")) continue; // Skip direct product links, we want categories
            
            String text = a.innerText();
            if (text == null) text = "";
            text = text.toLowerCase().trim();
            
            double score = 0.0;
            String lowerUrl = url.toLowerCase();
            
            // Highly relevant URL paths
            if (lowerUrl.contains("/collections/") || lowerUrl.contains("/category/") || lowerUrl.contains("/shop")) {
                score += 5.0;
            }
            if (lowerUrl.contains("/all") || text.contains("shop all") || text.contains("all products")) {
                score += 3.0;
            }
            
            // Query term matching
            boolean match = false;
            for (String term : queryTerms) {
                if (term.length() > 2 && (lowerUrl.contains(term) || text.contains(term))) {
                    score += 10.0;
                    match = true;
                }
            }
            
            // Penalize irrelevant
            if (text.contains("about") || text.contains("contact") || text.contains("policy") || text.contains("terms") || lowerUrl.contains("/pages/")) {
                score -= 10.0;
            }
            
            if (score > 0) {
                linkScores.merge(url, score, Math::max);
            }
        }
        
        return linkScores.entrySet().stream()
                .map(e -> new NavCandidate(e.getKey(), e.getValue()))
                .sorted((n1, n2) -> Double.compare(n2.score(), n1.score()))
                .limit(5)
                .toList();
    }

    private List<RawProduct> handlePagination(Page page, String platform, String baseUrl) {
        List<RawProduct> extra = new ArrayList<>();
        try {
            // Infinite scroll: scroll down a few times
            for (int i = 0; i < 3; i++) {
                page.evaluate("window.scrollBy(0, window.innerHeight)");
                page.waitForTimeout(800);
            }
            // Parse again to see if new elements appeared
            List<RawProduct> newScrollProducts = "SHOPIFY".equals(platform) ? 
                            shopifyParser.extractProducts(page, baseUrl, page.url()) : 
                            genericParser.extractProducts(page, baseUrl);
            extra.addAll(newScrollProducts);
            
            // Next button pagination (if visible)
            // Just click next once if it exists
            com.microsoft.playwright.Locator nextBtn = page.locator("a.next, a[rel='next'], a[aria-label='Next'], .pagination__next, a:has-text('Next')").first();
            if (nextBtn.isVisible()) {
                log.info("[WEBSITE] Found next page pagination, clicking...");
                nextBtn.click();
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                page.waitForTimeout(2000);
                
                for (int i = 0; i < 2; i++) {
                    page.evaluate("window.scrollBy(0, window.innerHeight)");
                    page.waitForTimeout(800);
                }
                
                List<RawProduct> nextProducts = "SHOPIFY".equals(platform) ? 
                            shopifyParser.extractProducts(page, baseUrl, page.url()) : 
                            genericParser.extractProducts(page, baseUrl);
                extra.addAll(nextProducts);
            }
        } catch (Exception e) {
            log.debug("[WEBSITE] Pagination handling failed: {}", e.getMessage());
        }
        return extra;
    }

    private long countHighConfidence(List<RawProduct> products, String query) {
        return products.stream().filter(p -> scoreProduct(p, query) >= 15.0).count();
    }
    
    private double scoreProduct(RawProduct p, String query) {
        double score = 0.0;
        String title = p.getProductName() != null ? p.getProductName().toLowerCase() : "";
        
        // Completeness
        if (p.getImageUrl() != null && !p.getImageUrl().isBlank()) score += 10.0;
        if (p.getMainPrice() != null) score += 5.0;
        if (p.getProductUrl() != null && !p.getProductUrl().isBlank()) score += 2.0;
        
        // Discount / Compare at
        if (p.getMainPrice() != null && p.getOriginalPrice() != null && p.getOriginalPrice() > p.getMainPrice()) {
            score += 5.0;
        }
        
        // Title keyword relevance
        if (query != null && !query.isBlank()) {
            String lowerQuery = query.toLowerCase().trim();
            List<String> terms = new ArrayList<>();
            terms.add(lowerQuery);
            if (CATEGORY_KEYWORDS.containsKey(lowerQuery)) {
                terms.addAll(CATEGORY_KEYWORDS.get(lowerQuery));
            }
            
            for (String term : terms) {
                if (term.length() > 2 && title.contains(term)) {
                    score += 15.0;
                    break;
                }
            }
        }
        
        // Featured/Sale terms in title (basic heuristic)
        if (title.contains("sale") || title.contains("new") || title.contains("best")) {
            score += 3.0;
        }
        
        return score;
    }

    private List<RawProduct> deduplicate(List<RawProduct> products) {
        java.util.Map<String, RawProduct> seen = new java.util.LinkedHashMap<>();
        for (RawProduct p : products) {
            String key = p.getProductUrl() != null ? p.getProductUrl() : p.getProductName();
            if (key != null && !seen.containsKey(key)) {
                seen.put(key, p);
            }
        }
        return new ArrayList<>(seen.values());
    }

    private List<RawProduct> getTopCandidates(List<RawProduct> products, String query) {
        if (products == null || products.isEmpty()) return new ArrayList<>();
        
        for (RawProduct p : products) {
            p.setHeuristicScore(scoreProduct(p, query));
        }
        
        return products.stream()
                .sorted((p1, p2) -> Double.compare(p2.getHeuristicScore(), p1.getHeuristicScore()))
                .limit(15)
                .toList();
    }

    private String resolveInternalUrl(String href, String baseUrl) {
        if (href == null || href.isBlank()) return baseUrl;
        if (href.startsWith("http")) return href;
        if (href.startsWith("//")) return "https:" + href;
        return baseUrl.replaceAll("/+$", "") + (href.startsWith("/") ? href : "/" + href);
    }
}