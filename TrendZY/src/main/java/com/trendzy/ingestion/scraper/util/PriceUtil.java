package com.trendzy.ingestion.scraper.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PriceUtil {

    private static final Pattern PRICE_PATTERN = Pattern.compile("([0-9]{1,3}(?:[, ][0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)");

    public static Double parsePrice(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            Matcher m = PRICE_PATTERN.matcher(text);
            if (m.find()) {
                String match = m.group(1);
                match = match.replaceAll("[, ]", "");
                return Double.parseDouble(match);
            }
            
            // Fallback: just extract the first contiguous sequence of digits/dots
            Matcher m2 = Pattern.compile("([0-9][0-9.]*)").matcher(text.replaceAll(",", ""));
            if (m2.find()) {
                return Double.parseDouble(m2.group(1));
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static String detectCurrency(String text) {
        if (text == null || text.isBlank()) return "Rs.";
        if (text.contains("$") || text.toLowerCase().contains("usd")) return "$";
        if (text.contains("€") || text.toLowerCase().contains("eur")) return "€";
        if (text.contains("£") || text.toLowerCase().contains("gbp")) return "£";
        if (text.contains("₹") || text.toLowerCase().contains("rs") || text.toLowerCase().contains("inr")) return "Rs.";
        return "Rs.";
    }
}
