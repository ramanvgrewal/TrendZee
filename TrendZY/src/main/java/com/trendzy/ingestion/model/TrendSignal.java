package com.trendzy.ingestion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "v2_signals")
@CompoundIndexes({
        @CompoundIndex(name = "idx_platform_processed", def = "{'platform': 1, 'processedByAi': 1}"),
        @CompoundIndex(name = "idx_platform_collectedAt", def = "{'platform': 1, 'collectedAt': -1}"),
        @CompoundIndex(name = "idx_category_collectedAt", def = "{'category': 1, 'collectedAt': -1}")
})
public class TrendSignal {

    @Id
    private String id;
    private Platform platform;

    /** Category this signal was scraped under (e.g. STREETWEAR, SNEAKERS). */
    private String category;

    @Indexed(unique = true)
    private String sourceUrl;
    @Builder.Default
    private String currency = "Rs.";
    private Double price;
    private String rawText;

    @Builder.Default
    private List<String> hashtags = new ArrayList<>();

    private long engagementScore;
    private Long commentCount;

    private String authorUsername;
    private String mediaUrl;

    @CreatedDate
    private Instant collectedAt;

    @Builder.Default
    private boolean processedByAi = false;

    /** LLM-extracted product search terms specific to THIS signal. */
    @Builder.Default
    private List<String> extractedKeywords = new ArrayList<>();

    /** All products scraped from the creator's website — AI worker selects the best one. */
    @Builder.Default
    private List<ScrapedProduct> scrapedProducts = new ArrayList<>();

    private Trend.ProductDetail underdogProduct;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScrapedProduct {
        private String productName;
        private Double mainPrice;
        private Double originalPrice;
        @Builder.Default
        private String currency = "Rs.";
        private String productUrl;
        private String imageUrl;
    }
}
