package com.trendzy.ingestion.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class GenericParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern WIX_IMAGE_PATTERN = Pattern.compile("wix:image://v1/([^/~]+)");

    private static final Set<String> NAV_WORDS = Set.of(
            "home", "cart", "login", "log in", "sign in", "signup", "sign up",
            "my account", "wishlist", "wish list", "search", "shop", "products",
            "all products", "collections", "contact", "about", "faq", "help"
    );

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

            // Strategy 2: Wix-specific extraction
            products = extractViaWix(page, baseUrl);
            if (!products.isEmpty()) {
                log.debug("[GENERIC] Wix extracted {} products from {}", products.size(), baseUrl);
                return products;
            }

            // Strategy 3: Broad DOM card selectors
            products = extractViaDomCards(page, baseUrl);
            if (!products.isEmpty()) {
                log.debug("[GENERIC] DOM cards extracted {} products from {}", products.size(), baseUrl);
                return products;
            }

            // Strategy 4: Product link grid detection
            products = extractViaProductLinks(page, baseUrl);
            if (!products.isEmpty()) {
                log.debug("[GENERIC] Product links extracted {} products from {}", products.size(), baseUrl);
                return products;
            }

            // Strategy 5: The Anchor-Price Heuristic (Custom SPAs like Zestwear)
            products = extractViaAllAnchorsWithPrice(page, baseUrl);
            if (!products.isEmpty()) {
                log.debug("[GENERIC] Anchor-Price heuristic extracted {} products from {}", products.size(), baseUrl);
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

        if (node.has("@graph") && node.get("@graph").isArray()) {
            for (JsonNode item : node.get("@graph")) {
                extractProductsFromJsonLd(item, baseUrl, products);
            }
            return;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                extractProductsFromJsonLd(item, baseUrl, products);
            }
            return;
        }

        String type = node.path("@type").asText("");
        if ("ItemList".equalsIgnoreCase(type) && node.has("itemListElement")) {
            for (JsonNode item : node.get("itemListElement")) {
                extractProductsFromJsonLd(item, baseUrl, products);
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
        String currency = "Rs.";
        JsonNode offers = node.path("offers");
        if (offers.isMissingNode() && node.has("offer")) offers = node.get("offer");

        if (!offers.isMissingNode()) {
            JsonNode offer = offers.isArray() && !offers.isEmpty() ? offers.get(0) : offers;

            if (offer.has("priceCurrency")) {
                String ccy = offer.path("priceCurrency").asText("").toUpperCase();
                if ("USD".equals(ccy)) currency = "$";
                else if ("EUR".equals(ccy)) currency = "€";
                else if ("GBP".equals(ccy)) currency = "£";
                else if ("INR".equals(ccy)) currency = "Rs.";
            }

            price = parsePrice(offer.path("price").asText(null));
            if (price == null) price = parsePrice(offer.path("lowPrice").asText(null));
            originalPrice = parsePrice(offer.path("highPrice").asText(null));
            if (originalPrice == null) originalPrice = price;
        }

        products.add(RawProduct.builder()
                .productName(title)
                .mainPrice(price)
                .originalPrice(originalPrice)
                .currency(currency)
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

    // ── Wix-Specific Extraction ───────────────────────────────────────

    private List<RawProduct> extractViaWix(Page page, String baseUrl) {
        List<RawProduct> products = new ArrayList<>();

        String[] containerSelectors = {
                "[data-hook='product-list-grid-item']",
                "li[data-hook='product-list-grid-item']",
                "[data-hook='product-item-root']"
        };

        List<ElementHandle> cards = new ArrayList<>();
        for (String sel : containerSelectors) {
            try {
                List<ElementHandle> found = page.querySelectorAll(sel);
                if (found.size() >= 2) { cards = found; break; }
            } catch (Exception ignored) {}
        }
        if (cards.isEmpty()) return products;

        for (ElementHandle element : cards) {
            try {
                String title = extractFirstText(element,
                        "[data-hook='product-item-name']", "h3", "[class*='productName']");
                if (title == null || title.isBlank()) continue;

                Double price = extractFirstPrice(element,
                        "[data-hook='product-item-price-to-pay']",
                        "[data-hook='product-item-price']",
                        "span[class*='price']");

                String currency = "Rs.";
                if (price != null) {
                    currency = detectCurrency(element,
                            "[data-hook='product-item-price-to-pay']",
                            "[data-hook='product-item-price']",
                            "span[class*='price']");
                }

                Double originalPrice = extractFirstPrice(element,
                        "[data-hook='product-item-price-before-discount']", "del", "s");
                if (originalPrice == null) originalPrice = price;

                String imageUrl = extractWixImage(element, baseUrl);

                String url = extractFirstHref(element, baseUrl,
                        "a[data-hook='product-item-container']",
                        "a[data-hook='product-item-images']",
                        "a[href*='/product-page/']",
                        "a");

                products.add(RawProduct.builder()
                        .productName(title)
                        .mainPrice(price)
                        .originalPrice(originalPrice)
                        .currency(currency)
                        .productUrl(url)
                        .imageUrl(imageUrl)
                        .build());
            } catch (Exception ignored) {}
        }
        return products;
    }

    private String extractFirstText(ElementHandle parent, String... selectors) {
        for (String sel : selectors) {
            try {
                ElementHandle el = parent.querySelector(sel);
                if (el != null) {
                    String text = el.innerText().trim();
                    if (!text.isBlank()) return text;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Double extractFirstPrice(ElementHandle parent, String... selectors) {
        for (String sel : selectors) {
            try {
                ElementHandle el = parent.querySelector(sel);
                if (el != null) {
                    String text = el.innerText().replaceAll("[^0-9.]", "");
                    if (!text.isBlank()) return Double.parseDouble(text);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String detectCurrency(ElementHandle parent, String... selectors) {
        for (String sel : selectors) {
            try {
                ElementHandle el = parent.querySelector(sel);
                if (el != null) {
                    String text = el.innerText();
                    if (text.contains("$")) return "$";
                    if (text.contains("€")) return "€";
                    if (text.contains("£")) return "£";
                    if (text.contains("₹") || text.toLowerCase().contains("rs")) return "Rs.";
                }
            } catch (Exception ignored) {}
        }
        return "Rs.";
    }

    private String extractFirstHref(ElementHandle parent, String baseUrl, String... selectors) {
        for (String sel : selectors) {
            try {
                ElementHandle el = parent.querySelector(sel);
                if (el != null) {
                    String href = el.getAttribute("href");
                    if (href != null && !href.isBlank()) {
                        return resolveUrl(href, baseUrl);
                    }
                }
            } catch (Exception ignored) {}
        }
        return baseUrl;
    }

    private String extractWixImage(ElementHandle element, String baseUrl) {
        String url = extractImageFromElement(element, baseUrl);
        if (url != null) return url;

        try {
            ElementHandle imgEl = element.querySelector("[data-hook='product-item-images'] img");
            if (imgEl == null) imgEl = element.querySelector("img[data-hook]");
            if (imgEl == null) imgEl = element.querySelector("img");
            if (imgEl != null) {
                String src = imgEl.getAttribute("src");
                if (src != null) return resolveImageUrl(src, baseUrl);
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ── DOM Card Extraction (broadened selectors) ──────────────────────

    private List<RawProduct> extractViaDomCards(Page page, String baseUrl) {
        List<RawProduct> products = new ArrayList<>();

        String[] cardSelectors = {
                ".product-card", ".product-item", ".product", ".product-tile",
                ".grid-product", "li.grid__item", ".collection-item",
                "[class*='product-card']", "[class*='product_card']", "[class*='ProductCard']",
                "[class*='product']",
                "[itemtype*='Product']", "[data-product]", "[data-product-id]",
                ".woocommerce-LoopProduct-link", "li.product", ".wc-block-grid__product",
                ".product-grid-item", ".grid-view-item",
                "[class*='ProductItem']", "[class*='product-item']",
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

                Double price = null;
                String currency = "Rs.";
                for (String sel : List.of(".price", ".amount", "span[class*='price']",
                        "[class*='Price']", "[class*='price']", ".money",
                        "[data-hook='product-item-price-to-pay']",
                        "[data-hook='product-item-price']",
                        "[class*='salePrice']")) {
                    ElementHandle priceEl = element.querySelector(sel);
                    if (priceEl != null) {
                        String priceText = priceEl.innerText();
                        if (priceText.contains("$")) currency = "$";
                        else if (priceText.contains("€")) currency = "€";
                        else if (priceText.contains("£")) currency = "£";
                        else if (priceText.contains("₹") || priceText.toLowerCase().contains("rs")) currency = "Rs.";

                        String priceStr = priceText.replaceAll("[^0-9.]", "");
                        if (!priceStr.isBlank()) { price = Double.parseDouble(priceStr); break; }
                    }
                }

                Double originalPrice = null;
                ElementHandle oldPriceEl = element.querySelector(
                        "del, s, strike, .old-price, .original-price, [class*='compare'], [class*='was'], [class*='mrp']");
                if (oldPriceEl != null) {
                    String oldPriceStr = oldPriceEl.innerText().replaceAll("[^0-9.]", "");
                    if (!oldPriceStr.isBlank()) originalPrice = Double.parseDouble(oldPriceStr);
                }
                if (originalPrice == null) originalPrice = price;

                String url = baseUrl;
                ElementHandle linkEl = element.querySelector("a[href*='/product'], a[href*='/shop'], a");
                if (linkEl != null) {
                    String href = linkEl.getAttribute("href");
                    if (href != null) url = resolveUrl(href, baseUrl);
                }

                String imageUrl = extractImageFromElement(element, baseUrl);

                products.add(RawProduct.builder()
                        .productName(title)
                        .mainPrice(price)
                        .originalPrice(originalPrice)
                        .currency(currency)
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
                    "a[href*='/product'], a[href*='/products/'], a[href*='/shop/'], a[href*='/p/'], a[href*='/item/'], a[href*='/product-page/']");

            for (var linkEl : productLinks) {
                try {
                    String href = linkEl.getAttribute("href");
                    if (href == null) continue;

                    String title = "";
                    ElementHandle img = linkEl.querySelector("img");
                    if (img != null) {
                        String alt = img.getAttribute("alt");
                        if (alt != null && !alt.isBlank()) title = alt.trim();
                    }
                    if (title.isBlank()) {
                        title = linkEl.innerText().trim();
                    }
                    if (title.isBlank() || title.length() > 200) continue;

                    String titleLower = title.toLowerCase().trim();
                    if (NAV_WORDS.contains(titleLower)) continue;
                    if (titleLower.matches("\\d+")) continue;

                    String url = resolveUrl(href, baseUrl);

                    String imageUrl = extractImageFromElement(linkEl, baseUrl);

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

    // ── Anchor-Price Heuristic (For Custom SPAs like Zestwear) ──────────

    private List<RawProduct> extractViaAllAnchorsWithPrice(Page page, String baseUrl) {
        List<RawProduct> products = new ArrayList<>();
        try {
            var allAnchors = page.querySelectorAll("a");
            for (var linkEl : allAnchors) {
                try {
                    String text = linkEl.innerText();
                    if (text == null || text.isBlank()) continue;

                    // Check if it has a currency symbol
                    String textLower = text.toLowerCase();
                    if (!textLower.contains("₹") && !textLower.contains("rs") && !textLower.contains("$") && !textLower.contains("€") && !textLower.contains("£")) {
                        continue;
                    }

                    String href = linkEl.getAttribute("href");
                    if (href == null || href.isBlank() || href.startsWith("javascript") || href.startsWith("#") || href.startsWith("tel:") || href.startsWith("mailto:")) {
                        continue;
                    }

                    String title = "";
                    ElementHandle img = linkEl.querySelector("img");
                    if (img != null) {
                        String alt = img.getAttribute("alt");
                        if (alt != null && !alt.isBlank()) title = alt.trim();
                    }
                    
                    // Try to find a title-like element inside the anchor
                    if (title.isBlank()) {
                        for (String sel : List.of("h2", "h3", "h4", "p.title", "p[class*='title']", "p[class*='name']", "span[class*='title']")) {
                            ElementHandle el = linkEl.querySelector(sel);
                            if (el != null) {
                                String t = el.innerText();
                                if (t != null && !t.isBlank()) {
                                    title = t.trim();
                                    break;
                                }
                            }
                        }
                    }

                    if (title.isBlank()) {
                        // Fallback: take the first line of text that isn't a price
                        String[] lines = text.split("\n");
                        for (String line : lines) {
                            String l = line.trim();
                            if (!l.isBlank() && !l.toLowerCase().contains("₹") && !l.toLowerCase().contains("rs") && !l.matches(".*\\d.*")) {
                                title = l;
                                break;
                            }
                        }
                    }

                    if (title.isBlank() || title.length() > 200) continue;

                    String titleLowerStr = title.toLowerCase().trim();
                    if (NAV_WORDS.contains(titleLowerStr)) continue;
                    if (titleLowerStr.matches("\\d+")) continue;

                    Double price = parsePrice(text);
                    if (price == null) continue; // Must have a parseable price

                    String currency = "Rs.";
                    if (text.contains("$")) currency = "$";
                    else if (text.contains("€")) currency = "€";
                    else if (text.contains("£")) currency = "£";

                    String url = resolveUrl(href, baseUrl);
                    String imageUrl = extractImageFromElement(linkEl, baseUrl);

                    products.add(RawProduct.builder()
                            .productName(title)
                            .mainPrice(price)
                            .currency(currency)
                            .productUrl(url)
                            .imageUrl(imageUrl)
                            .build());
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return products;
    }

    // ── Utilities ─────────────────────────────────────────────────────

    private String extractImageFromElement(ElementHandle element, String baseUrl) {
        try {
            ElementHandle imgEl = element.querySelector("img");
            if (imgEl != null) {
                String[] attrs = {"src", "data-src", "data-lazy-src", "data-original", "data-pin-media"};
                for (String attr : attrs) {
                    String val = imgEl.getAttribute(attr);
                    if (val != null && !val.isBlank() && !val.startsWith("data:image")) {
                        return resolveImageUrl(val, baseUrl);
                    }
                }

                String srcset = imgEl.getAttribute("srcset");
                String fromSrcset = extractFirstUrlFromSrcset(srcset);
                if (fromSrcset != null) return resolveImageUrl(fromSrcset, baseUrl);

                String dataSrcset = imgEl.getAttribute("data-srcset");
                String fromDataSrcset = extractFirstUrlFromSrcset(dataSrcset);
                if (fromDataSrcset != null) return resolveImageUrl(fromDataSrcset, baseUrl);
            }

            List<ElementHandle> sources = element.querySelectorAll("picture source");
            for (ElementHandle source : sources) {
                String srcset = source.getAttribute("srcset");
                String fromSrcset = extractFirstUrlFromSrcset(srcset);
                if (fromSrcset != null) return resolveImageUrl(fromSrcset, baseUrl);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private String extractFirstUrlFromSrcset(String srcset) {
        if (srcset == null || srcset.isBlank()) return null;
        String first = srcset.split(",")[0].trim().split("\\s+")[0].trim();
        if (first.isBlank() || first.startsWith("data:image")) return null;
        return first;
    }

    private String resolveImageUrl(String url, String baseUrl) {
        if (url == null || url.isBlank()) return null;

        Matcher wixMatcher = WIX_IMAGE_PATTERN.matcher(url);
        if (wixMatcher.find()) {
            return "https://static.wixstatic.com/media/" + wixMatcher.group(1);
        }

        if (url.startsWith("data:image")) return null;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return baseUrl.replaceAll("/+$", "") + url;
        return url;
    }

    private String resolveUrl(String href, String baseUrl) {
        if (href == null || href.isBlank()) return baseUrl;
        if (href.startsWith("http")) return href;
        if (href.startsWith("//")) return "https:" + href;
        return baseUrl.replaceAll("/+$", "") + (href.startsWith("/") ? href : "/" + href);
    }


}