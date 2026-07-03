package com.trendzy.ingestion.scraper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawProduct {
    private String productName;
    private Double mainPrice;
    private Double originalPrice;
    private String productUrl;
    private String imageUrl;
    private boolean validated;
}