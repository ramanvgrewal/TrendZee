package com.trendzy.ingestion.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class GenericParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<RawProduct> extractProducts(Page page, String baseUrl) {
        List<RawProduct> products = new ArrayList<>();
        try {
            page.waitForLoadState();

            // Strategy 1: JSON-LD structured data (most reliable for modern sites)
            products = extractViaJsonLd(page, baseUrl);
            if (!products.isEmpty()) {
                log.debug("[GENERIC] JSON-LD extracted {} products from {}", products.size(), baseUrl);
                return products;
            }

            // Strategy 2: Broad DOM card selectors
            products = extractViaDomCards(page, baseUrl);
            if (!products.isEmpty()) {
                log.debug("[GENERIC] DOM cards extracted {} products from {}", products.size(), baseUrl);
                return products;
            }

            // Strategy 3: Product link grid detection
            products = extractViaProductLinks(page, baseUrl);
            if (!products.isEmpty()) {
                log.debug("[GENERIC] Product links extracted {} products from {}", products.size(), baseUrl);
            }
        } catch (Exception e) {
            log.error("Error in generic parsing: {}", e.getMessage());
        }
        return products;
    }

    // ── JSON-LD Structured Data Extraction ─────────────────────────────

    private List<RawProduct> extractViaJsonLd(Page page, String baseUrl) {
        List<RawProduct> products = new ArrayList<>();
        try {
            var scripts = page.querySelectorAll("script[type='application/ld+json']");
            for (ElementHandle script : scripts) {
                try {
                    String json = script.innerHTML().trim();
                    if (json.isBlank()) continue;
                    JsonNode root = objectMapper.readTree(json);
                    extractProductsFromJsonLd(root, baseUrl, products);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return products;
    }

    private void extractProductsFromJsonLd(JsonNode node, String baseUrl, List<RawProduct> products) {
        if (node == null) return;

        // Handle @graph arrays (common in Shopify/WooCommerce)
        if (node.has("@graph") && node.get("@graph").isArray()) {
            for (JsonNode item : node.get("@graph")) {
                extractProductsFromJsonLd(item, baseUrl, products);
            }
            return;
        }

        // Handle top-level arrays
        if (node.isArray()) {
            for (JsonNode item : node) {
                extractProductsFromJsonLd(item, baseUrl, products);
            }
            return;
        }

        // Handle ItemList (collection pages)
        String type = node.path("@type").asText("");
        if ("ItemList".equalsIgnoreCase(type) && node.has("itemListElement")) {
            for (JsonNode item : node.get("itemListElement")) {
                extractProductsFromJsonLd(item, baseUrl, products);
                // Also check nested "item" inside ListItem
                if (item.has("item")) {
                    extractProductsFromJsonLd(item.get("item"), baseUrl, products);
                }
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
        if (url == null) url = baseUrl;

        String imageUrl = extractImageUrl(node);

        Double price = null;
        Double originalPrice = null;
        JsonNode offers = node.path("offers");
        if (offers.isMissingNode() && node.has("offer")) offers = node.get("offer");

        if (!offers.isMissingNode()) {
            // Handle single offer or AggregateOffer or first from array
            JsonNode offer = offers.isArray() && !offers.isEmpty() ? offers.get(0) : offers;
            price = parsePrice(offer.path("price").asText(null));
            if (price == null) price = parsePrice(offer.path("lowPrice").asText(null));
            originalPrice = parsePrice(offer.path("highPrice").asText(null));
            if (originalPrice == null) originalPrice = price;
        }

        products.add(RawProduct.builder()
                .productName(title)
                .mainPrice(price)
                .originalPrice(originalPrice)
                .productUrl(url)
                .imageUrl(imageUrl)
                .build());
    }

    private String extractImageUrl(JsonNode node) {
        if (!node.has("image")) return null;
        JsonNode imageNode = node.get("image");
        if (imageNode.isTextual()) return imageNode.asText();
        if (imageNode.isArray() && !imageNode.isEmpty()) {
            JsonNode first = imageNode.get(0);
            return first.isTextual() ? first.asText() : first.path("url").asText(null);
        }
        if (imageNode.has("url")) return imageNode.path("url").asText(null);
        return null;
    }

    private Double parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isBlank()) return null;
        try {
            return Double.parseDouble(priceStr.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── DOM Card Extraction (broadened selectors) ──────────────────────

    private List<RawProduct> extractViaDomCards(Page page, String baseUrl) {
        List<RawProduct> products = new ArrayList<>();

        // Broadened card selectors covering various e-commerce platforms
        String[] cardSelectors = {
                ".product-card", ".product-item", ".product", ".product-tile",
                ".grid-product", "li.grid__item", ".collection-item",
                "[class*='product-card']", "[class*='product_card']", "[class*='ProductCard']",
                "[class*='product']",
                "[itemtype*='Product']", "[data-product]", "[data-product-id]",
                ".card", ".item",
                "article",
        };

        List<ElementHandle> cards = new ArrayList<>();
        for (String sel : cardSelectors) {
            try {
                List<ElementHandle> found = page.querySelectorAll(sel);
                if (found.size() >= 2) { cards = found; break; }
            } catch (Exception ignored) {}
        }
        if (cards.isEmpty()) return products;

        for (ElementHandle element : cards) {
            try {
                // Broadened title selectors
                String title = "";
                for (String sel : List.of("h2", "h3", "h4", ".title", ".product-title",
                        ".product-name", "[class*='title']", "[class*='name']", "a")) {
                    ElementHandle titleEl = element.querySelector(sel);
                    if (titleEl != null && !titleEl.innerText().isBlank()) {
                        title = titleEl.innerText().trim();
                        break;
                    }
                }
                if (title.isBlank()) continue;

                // Selling price — broadened selectors
                Double price = null;
                for (String sel : List.of(".price", ".amount", "span[class*='price']",
                        "[class*='Price']", "[class*='price']", ".money")) {
                    ElementHandle priceEl = element.querySelector(sel);
                    if (priceEl != null) {
                        String priceStr = priceEl.innerText().replaceAll("[^0-9.]", "");
                        if (!priceStr.isBlank()) { price = Double.parseDouble(priceStr); break; }
                    }
                }

                // Original price (MRP)
                Double originalPrice = null;
                ElementHandle oldPriceEl = element.querySelector(
                        "del, s, strike, .old-price, .original-price, [class*='compare'], [class*='was'], [class*='mrp']");
                if (oldPriceEl != null) {
                    String oldPriceStr = oldPriceEl.innerText().replaceAll("[^0-9.]", "");
                    if (!oldPriceStr.isBlank()) originalPrice = Double.parseDouble(oldPriceStr);
                }
                if (originalPrice == null) originalPrice = price;

                // URL — prefer product links
                String url = baseUrl;
                ElementHandle linkEl = element.querySelector("a[href*='/product'], a[href*='/shop'], a");
                if (linkEl != null) {
                    String href = linkEl.getAttribute("href");
                    if (href != null && href.startsWith("http")) url = href;
                    else if (href != null) url = baseUrl.replaceAll("/+$", "") + (href.startsWith("/") ? href : "/" + href);
                }

                // Image
                String imageUrl = extractImageFromElement(element);

                products.add(RawProduct.builder()
                        .productName(title)
                        .mainPrice(price)
                        .originalPrice(originalPrice)
                        .productUrl(url)
                        .imageUrl(imageUrl)
                        .build());
            } catch (Exception ignored) {}
        }
        return products;
    }

    // ── Product Link Grid Detection (last resort) ─────────────────────

    private List<RawProduct> extractViaProductLinks(Page page, String baseUrl) {
        List<RawProduct> products = new ArrayList<>();
        try {
            var productLinks = page.querySelectorAll(
                    "a[href*='/product'], a[href*='/products/'], a[href*='/shop/'], a[href*='/p/'], a[href*='/item/']");

            for (var linkEl : productLinks) {
                try {
                    String href = linkEl.getAttribute("href");
                    if (href == null) continue;

                    String title = "";
                    // Try img alt first, then link inner text
                    ElementHandle img = linkEl.querySelector("img");
                    if (img != null) {
                        String alt = img.getAttribute("alt");
                        if (alt != null && !alt.isBlank()) title = alt.trim();
                    }
                    if (title.isBlank()) {
                        title = linkEl.innerText().trim();
                    }
                    if (title.isBlank() || title.length() > 200) continue;

                    String url = href.startsWith("http") ? href
                            : baseUrl.replaceAll("/+$", "") + (href.startsWith("/") ? href : "/" + href);

                    String imageUrl = null;
                    if (img != null) {
                        imageUrl = img.getAttribute("src");
                        if (imageUrl == null || imageUrl.contains("data:image"))
                            imageUrl = img.getAttribute("data-src");
                        if (imageUrl != null && imageUrl.startsWith("//"))
                            imageUrl = "https:" + imageUrl;
                    }

                    products.add(RawProduct.builder()
                            .productName(title)
                            .productUrl(url)
                            .imageUrl(imageUrl)
                            .build());
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return products;
    }

    // ── Utilities ─────────────────────────────────────────────────────

    private String extractImageFromElement(ElementHandle element) {
        try {
            ElementHandle imgEl = element.querySelector("img");
            if (imgEl == null) return null;
            String src = imgEl.getAttribute("src");
            if (src == null || src.contains("data:image")) src = imgEl.getAttribute("data-src");
            if (src == null) src = imgEl.getAttribute("data-lazy-src");
            if (src != null && src.startsWith("//")) src = "https:" + src;
            return src;
        } catch (Exception e) {
            return null;
        }
    }
}