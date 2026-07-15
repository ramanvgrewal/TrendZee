package com.trendzy.ingestion.scraper.util;

public class ImageUtil {

    /**
     * Cleans up image URLs to remove low-resolution suffixes.
     * Useful for Shopify and other platforms that append dimensions to image filenames.
     * Example: image_300x300.jpg -> image.jpg
     */
    public static String getHighResUrl(String url) {
        if (url == null || url.isBlank()) return url;
        
        // Remove standard Shopify resolution modifiers: _100x100, _540x, _1024x1024, _small, etc.
        // E.g. https://cdn.shopify.com/s/files/.../img_300x300.jpg?v=123 -> https://.../img.jpg?v=123
        return url.replaceAll("_([0-9]+x[0-9]*|pico|icon|thumb|small|compact|medium|large|grande|original)(?=\\.[a-zA-Z0-9]+(?:\\?.*)?$)", "");
    }
}
