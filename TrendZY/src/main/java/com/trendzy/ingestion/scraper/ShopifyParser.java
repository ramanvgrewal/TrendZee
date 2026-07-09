package com.trendzy.ingestion.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
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
        List<RawProduct> products = extractViaJsonApi(page, storeRoot);
        if (!products.isEmpty()) return products;

        try {
            page.navigate(resolvedUrl);
            page.waitForTimeout(2000);
            products = extractViaDomCards(page, storeRoot);
            if (!products.isEmpty()) return products;
        } catch (Exception ignored) {}

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
                try { mainPrice = Double.parseDouble(priceStr); } catch (Exception ignored) {}
            }

            // MRP / Original price: variants[0].compare_at_price
            JsonNode compareNode = firstVariant.path("compare_at_price");
            if (!compareNode.isMissingNode() && !compareNode.isNull()) {
                String compareStr = compareNode.asText(null);
                if (compareStr != null && !compareStr.isBlank()) {
                    try { originalPrice = Double.parseDouble(compareStr); } catch (Exception ignored) {}
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
                    if (priceText.contains("$")) currency = "$";
                    else if (priceText.contains("€")) currency = "€";
                    else if (priceText.contains("£")) currency = "£";
                    else if (priceText.contains("₹") || priceText.toLowerCase().contains("rs")) currency = "Rs.";
                    try { price = Double.parseDouble(priceText.replaceAll("[^0-9.]", "")); } catch (Exception ignored) {}
                }

                // 👉 ORIGINAL PRICE (MRP)
                Double originalPrice = null;
                ElementHandle oldPriceEl = card.querySelector("del, s, .price--compare, .compare-at-price");
                if (oldPriceEl != null) {
                    try { originalPrice = Double.parseDouble(oldPriceEl.innerText().replaceAll("[^0-9.]", "")); } catch (Exception ignored) {}
                }
                if (originalPrice == null) originalPrice = price;

                // URL
                String url = null;
                ElementHandle a = card.querySelector("a[href*='/products/']");
                if (a != null) {
                    url = a.getAttribute("href");
                    if (url != null && !url.startsWith("http")) url = storeRoot.replaceAll("/+$", "") + url;
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