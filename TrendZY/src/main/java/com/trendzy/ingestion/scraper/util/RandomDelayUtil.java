package com.trendzy.ingestion.scraper.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility for injecting randomised delays between scraping actions.
 *
 * <p>All delay ranges are in milliseconds.  Callers pass a logical
 * "action name" so that log output is meaningful without tying delay
 * logic to specific scrapers.
 */
@Slf4j
public final class RandomDelayUtil {

    // ── Default range: 2 000 – 4 000 ms ─────────────────────────
    public static final long DEFAULT_MIN_MS = 2_000L;
    public static final long DEFAULT_MAX_MS = 4_000L;

    // ── Short range for same-page interactions ───────────────────
    public static final long SHORT_MIN_MS = 800L;
    public static final long SHORT_MAX_MS = 1_800L;

    // ── Long range after page navigations ───────────────────────
    public static final long LONG_MIN_MS  = 3_000L;
    public static final long LONG_MAX_MS  = 6_000L;

    private RandomDelayUtil() { /* utility class */ }

    // ─────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────

    /** Default delay (2–4 s). */
    public static void delay() {
        sleep(DEFAULT_MIN_MS, DEFAULT_MAX_MS, "default");
    }

    /** Short delay (0.8–1.8 s), for in-page interactions. */
    public static void shortDelay() {
        sleep(SHORT_MIN_MS, SHORT_MAX_MS, "short");
    }

    /** Long delay (3–6 s), for post-navigation settling. */
    public static void longDelay() {
        sleep(LONG_MIN_MS, LONG_MAX_MS, "long");
    }

    /**
     * Custom range delay.
     *
     * @param minMs minimum milliseconds (inclusive)
     * @param maxMs maximum milliseconds (exclusive)
     * @param label descriptive label for log output
     */
    public static void delay(long minMs, long maxMs, String label) {
        sleep(minMs, maxMs, label);
    }

    // ─────────────────────────────────────────────────────────────
    // INTERNAL
    // ─────────────────────────────────────────────────────────────

    private static void sleep(long minMs, long maxMs, String label) {
        long ms = ThreadLocalRandom.current().nextLong(minMs, maxMs + 1);
        log.debug("[DELAY] {} pause: {}ms", label, ms);
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[DELAY] Delay interrupted ({})", label);
        }
    }
}