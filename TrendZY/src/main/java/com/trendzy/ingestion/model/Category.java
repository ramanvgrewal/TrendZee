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
    ANIMEWEAR,

    /** Boxy tees, graphic prints, and statement tops. */
    SHIRTS,

    /** Baggy denim, cargo pants, parachute pants. */
    BOTTOMS,

    /** Chains, rings, caps, and the final pieces. */
    ACCESSORIES,

    /** Timepieces and wristwear. */
    WATCHES,

    /** Aesthetic scents, niche houses, perfumes. */
    FRAGRANCES
}
