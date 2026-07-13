package com.trendzy.ingestion.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.trendzy.ingestion.scraper.util.PriceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class ShopifyParser {

    private static final String PRODUCTS_JSON_PATH = "/products.json?limit=50";
    private static final String COLLECTIONS_PATH   = "/collections/all";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<RawProduct> extractProducts(Page page, String storeRoot, String resolvedUrl) {
        List<RawProduct> products = new ArrayList<>();
        try {
            page.navigate(resolvedUrl);
            page.waitForTimeout(2000);
            products = extractViaDomCards(page, storeRoot);
            if (!products.isEmpty()) return products;
        } catch (Exception ignored) {}

        products = extractViaJsonApi(page, storeRoot);
        if (!products.isEmpty()) return products;

        String collectionsUrl = storeRoot.replaceAll("/+$", "") + COLLECTIONS_PATH;
        if (!resolvedUrl.equalsIgnoreCase(collectionsUrl)) {
            try {
                page.navigate(collectionsUrl);
                page.waitForTimeout(2000);
                products = extractViaDomCards(page, storeRoot);
            } catch (Exception ignored) {}
        }
        return products;
    }

    private List<RawProduct> extractViaJsonApi(Page page, String storeRoot) {
        List<RawProduct> results = new ArrayList<>();
        String apiUrl = storeRoot.replaceAll("/+$", "") + PRODUCTS_JSON_PATH;

        try {
            page.navigate(apiUrl);
            page.waitForTimeout(1000);

            String body = page.innerText("body");
            if (body == null || !body.trim().startsWith("{")) return results;

            JsonNode root = objectMapper.readTree(body);
            JsonNode productsNode = root.path("products");

            if (!productsNode.isArray()) return results;

            for (JsonNode product : productsNode) {
                try {
                    RawProduct rp = parseJsonProduct(product, storeRoot);
                    if (rp != null) results.add(rp);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return results;
    }

    /**
     * HTTP-only fast path — fetches /products.json without any browser.
     * Works for all Shopify stores regardless of JS rendering.
     */
    public List<RawProduct> extractViaHttp(String storeUrl) {
        List<RawProduct> results = new ArrayList<>();
        String apiUrl = storeUrl.replaceAll("/+$", "") + PRODUCTS_JSON_PATH;
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(apiUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .timeout(java.time.Duration.ofSeconds(20))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.debug("[SHOPIFY-HTTP] Non-200 status {} for {}", response.statusCode(), apiUrl);
                return results;
            }
            String body = response.body();
            if (body == null || !body.trim().startsWith("{")) return results;
            JsonNode root = objectMapper.readTree(body);
            JsonNode productsNode = root.path("products");
            if (!productsNode.isArray()) return results;
            for (JsonNode product : productsNode) {
                try {
                    RawProduct rp = parseJsonProduct(product, storeUrl.replaceAll("/+$", ""));
                    if (rp != null) results.add(rp);
                } catch (Exception ignored) {}
            }
            log.info("[SHOPIFY-HTTP] ✓ Fast-path extracted {} products from {}", results.size(), apiUrl);
        } catch (Exception e) {
            log.debug("[SHOPIFY-HTTP] Fast-path failed for {}: {}", apiUrl, e.getMessage());
        }
        return results;
    }

    /**
     * HTTP-only HTML fallback — fetches homepage HTML and parses JSON-LD structured data.
     * Used when browser navigation fails entirely.
     */
    public List<RawProduct> extractViaHttpHtml(String storeUrl) {
        List<RawProduct> results = new ArrayList<>();
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(storeUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .timeout(java.time.Duration.ofSeconds(20))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return results;
            String html = response.body();
            if (html == null || html.isBlank()) return results;

            // Extract JSON-LD Product data from HTML
            java.util.regex.Pattern jsonLdPattern = java.util.regex.Pattern.compile(
                    "<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>([\\s\\S]*?)</script>",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = jsonLdPattern.matcher(html);
            while (matcher.find()) {
                try {
                    String json = matcher.group(1).trim();
                    if (json.isBlank()) continue;
                    JsonNode root = objectMapper.readTree(json);
                    extractProductsFromJsonLdNode(root, storeUrl, results);
                } catch (Exception ignored) {}
            }
            if (!results.isEmpty()) {
                log.info("[HTTP-HTML] ✓ Extracted {} products via JSON-LD from {}", results.size(), storeUrl);
            }
        } catch (Exception e) {
            log.debug("[HTTP-HTML] Failed for {}: {}", storeUrl, e.getMessage());
        }
        return results;
    }

    private void extractProductsFromJsonLdNode(JsonNode node, String baseUrl, List<RawProduct> products) {
        if (node == null) return;
        if (node.has("@graph") && node.get("@graph").isArray()) {
            for (JsonNode item : node.get("@graph")) extractProductsFromJsonLdNode(item, baseUrl, products);
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) extractProductsFromJsonLdNode(item, baseUrl, products);
            return;
        }
        String type = node.path("@type").asText("");
        if ("ItemList".equalsIgnoreCase(type) && node.has("itemListElement")) {
            for (JsonNode item : node.get("itemListElement")) {
                extractProductsFromJsonLdNode(item, baseUrl, products);
                if (item.has("item")) extractProductsFromJsonLdNode(item.get("item"), baseUrl, products);
            }
            return;
        }
        if (!"Product".equalsIgnoreCase(type)) return;
        String title = node.path("name").asText("").trim();
        if (title.isBlank()) return;
        String url = node.path("url").asText(null);
        if (url != null && !url.startsWith("http")) {
            url = baseUrl.replaceAll("/+$", "") + (url.startsWith("/") ? url : "/" + url);
        }
        String imageUrl = null;
        if (node.has("image")) {
            JsonNode img = node.get("image");
            if (img.isTextual()) imageUrl = img.asText();
            else if (img.isArray() && !img.isEmpty()) {
                JsonNode first = img.get(0);
                imageUrl = first.isTextual() ? first.asText() : first.path("url").asText(null);
            }
            else if (img.has("url")) imageUrl = img.path("url").asText(null);
        }
        Double price = null;
        Double originalPrice = null;
        JsonNode offers = node.path("offers");
        if (offers.isMissingNode() && node.has("offer")) offers = node.get("offer");
        if (!offers.isMissingNode()) {
            JsonNode offer = offers.isArray() && !offers.isEmpty() ? offers.get(0) : offers;
            String priceStr = offer.path("price").asText(null);
            if (priceStr != null && !priceStr.isBlank()) {
                price = PriceUtil.parsePrice(priceStr);
            }
            if (price == null) {
                String lowStr = offer.path("lowPrice").asText(null);
                if (lowStr != null) price = PriceUtil.parsePrice(lowStr);
            }
            String highStr = offer.path("highPrice").asText(null);
            if (highStr != null) originalPrice = PriceUtil.parsePrice(highStr);
        }
        if (originalPrice == null) originalPrice = price;

        String currency = "Rs.";
        if (offers != null && !offers.isMissingNode()) {
            JsonNode offer = offers.isArray() && !offers.isEmpty() ? offers.get(0) : offers;
            String currStr = offer.path("priceCurrency").asText(null);
            if (currStr != null) {
                currency = PriceUtil.detectCurrency(currStr);
            }
        }

        products.add(RawProduct.builder()
                .productName(title).mainPrice(price).originalPrice(originalPrice)
                .currency(currency).productUrl(url).imageUrl(imageUrl).build());
    }

    private RawProduct parseJsonProduct(JsonNode product, String storeRoot) {
        String title  = product.path("title").asText("").trim();
        String handle = product.path("handle").asText("").trim();
        if (title.isBlank() || handle.isBlank()) return null;

        Double mainPrice = null;
        Double originalPrice = null;
        JsonNode variants = product.path("variants");
        if (variants.isArray() && variants.size() > 0) {
            JsonNode firstVariant = variants.get(0);

            // Selling price: variants[0].price
            String priceStr = firstVariant.path("price").asText(null);
            if (priceStr != null && !priceStr.isBlank()) {
                mainPrice = PriceUtil.parsePrice(priceStr);
            }

            // MRP / Original price: variants[0].compare_at_price
            JsonNode compareNode = firstVariant.path("compare_at_price");
            if (!compareNode.isMissingNode() && !compareNode.isNull()) {
                String compareStr = compareNode.asText(null);
                if (compareStr != null && !compareStr.isBlank()) {
                    originalPrice = PriceUtil.parsePrice(compareStr);
                }
            }

            // If no compare_at_price, default originalPrice to mainPrice
            if (originalPrice == null) {
                originalPrice = mainPrice;
            }
        }

        String imageUrl = null;
        JsonNode images = product.path("images");
        if (images.isArray() && images.size() > 0) {
            imageUrl = images.get(0).path("src").asText(null);
        }

        return RawProduct.builder()
                .productName(title)
                .mainPrice(mainPrice)
                .originalPrice(originalPrice)
                .imageUrl(imageUrl)
                .productUrl(storeRoot.replaceAll("/+$", "") + "/products/" + handle)
                .build();
    }

    private List<RawProduct> extractViaDomCards(Page page, String storeRoot) {
        List<RawProduct> results = new ArrayList<>();
        List<String> cardSelectors = List.of(
                ".product-card", ".product-item", ".grid-product", "li.grid__item",
                ".product-grid-item", ".grid-view-item", "[class*='product-card']",
                "[class*='ProductCard']", "product-card", ".collection-product-card"
        );

        List<ElementHandle> cards = new ArrayList<>();
        for (String sel : cardSelectors) {
            try {
                List<ElementHandle> found = page.querySelectorAll(sel);
                if (found.size() >= 2) { cards = found; break; }
            } catch (Exception ignored) {}
        }

        for (ElementHandle card : cards) {
            try {
                // Title
                String title = null;
                for (String sel : List.of("h3", ".product-card__title", "a[href*='/products/']")) {
                    ElementHandle el = card.querySelector(sel);
                    if (el != null && !el.innerText().isBlank()) { title = el.innerText().trim(); break; }
                }
                if (title == null) continue;

                // 👉 SELLING PRICE
                Double price = null;
                String currency = "Rs.";
                ElementHandle priceEl = card.querySelector(".price, .money, .price__regular");
                if (priceEl != null) {
                    String priceText = priceEl.innerText();
                    currency = PriceUtil.detectCurrency(priceText);
                    price = PriceUtil.parsePrice(priceText);
                }

                // 👉 ORIGINAL PRICE (MRP)
                Double originalPrice = null;
                ElementHandle oldPriceEl = card.querySelector("del, s, .price--compare, .compare-at-price");
                if (oldPriceEl != null) {
                    originalPrice = PriceUtil.parsePrice(oldPriceEl.innerText());
                }
                if (originalPrice == null) originalPrice = price;

                // URL
                String url = null;
                ElementHandle a = card.querySelector("a[href*='/products/']");
                
                // If not found, try a link wrapping the image
                if (a == null) {
                    a = card.querySelector("a:has(img)");
                }
                
                // If still not found, take any link with a valid path
                if (a == null) {
                    List<ElementHandle> anchors = card.querySelectorAll("a[href]");
                    for (ElementHandle anc : anchors) {
                        String href = anc.getAttribute("href");
                        if (href != null && !href.isBlank() && !href.equals("/") && !href.equals("#") && !href.startsWith("javascript")) {
                            a = anc;
                            break;
                        }
                    }
                }
                
                if (a != null) {
                    url = a.getAttribute("href");
                    if (url != null && !url.startsWith("http")) {
                        if (!url.startsWith("/")) url = "/" + url;
                        url = storeRoot.replaceAll("/+$", "") + url;
                    }
                }

                // 👉 IMAGE EXTRACTION — industry-grade: src → data-src → data-lazy-src → srcset
                String imageUrl = null;
                ElementHandle imgEl = card.querySelector("img");
                if (imgEl != null) {
                    imageUrl = imgEl.getAttribute("src");
                    if (imageUrl == null || imageUrl.contains("data:image")) imageUrl = imgEl.getAttribute("data-src");
                    if (imageUrl == null) imageUrl = imgEl.getAttribute("data-lazy-src");
                    if (imageUrl == null) imageUrl = imgEl.getAttribute("data-original");
                    if (imageUrl == null) {
                        String srcset = imgEl.getAttribute("srcset");
                        if (srcset != null && !srcset.isBlank()) {
                            imageUrl = srcset.split(",")[0].trim().split("\\s+")[0];
                        }
                    }
                    if (imageUrl != null && imageUrl.startsWith("//")) imageUrl = "https:" + imageUrl;
                }

                results.add(RawProduct.builder()
                        .productName(title)
                        .mainPrice(price)
                        .originalPrice(originalPrice)
                        .currency(currency)
                        .imageUrl(imageUrl)
                        .productUrl(url)
                        .build());
            } catch (Exception ignored) {}
        }
        return results;
    }
}