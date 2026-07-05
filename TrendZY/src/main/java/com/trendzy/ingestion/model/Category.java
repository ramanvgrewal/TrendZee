package com.trendzy.ingestion.model;

/**
 * The product categories that TrendZY scrapes and classifies signals into.
 *
 * <p>Each category maps to a curated set of Instagram hashtags in
 * {@code InstagramExploreClient.SECTION_TAGS} and is stored on both
 * {@link TrendSignal} and {@link Trend} for downstream filtering.
 */
public enum Category {

    /** Indian streetwear, oversized tees, D2C drops. */
    STREETWEAR,

    /** Sneaker culture, kicks, sole collecting. */
    SNEAKERS,

    /** Gym fits, activewear, compression gear. */
    SPORTSWEAR,

    /** Anime-themed clothing, otaku merch, manga-panel tees. */
    ANIMEWEAR
}
