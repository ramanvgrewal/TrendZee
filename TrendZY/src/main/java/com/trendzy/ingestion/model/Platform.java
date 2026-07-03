package com.trendzy.ingestion.model;

/**
 * Enumerates the social-media platforms that TrendZY scrapes for trend signals.
 *
 * <p>Instagram is the sole ingestion source. It maps to {@code InstagramExploreClient}
 * and is stored alongside every {@link TrendSignal} for downstream filtering
 * and analytics.
 */
public enum Platform {

    /** Instagram posts, reels, and stories. */
    INSTAGRAM
}
