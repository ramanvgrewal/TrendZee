package com.trendzy.ingestion.scraper.instagram;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.trendzy.ingestion.model.Platform;
import com.trendzy.ingestion.model.TrendSignal;
import com.trendzy.ingestion.repository.TrendSignalRepository;
import com.trendzy.ingestion.scraper.util.RandomDelayUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes Instagram Explore / hashtag pages for trend signals.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InstagramExploreClient {

    private final InstagramSessionManager sessionManager;
    private final TrendSignalRepository trendSignalRepository;

    // ── Config ──────────────────────────────────────────────────────────────
    private static final String SEARCH_URL_TMPL   = "https://www.instagram.com/explore/tags/%s/";
    private static final String USER_AGENT        =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int DOM_SETTLE_TIMEOUT   = 15_000;
    private static final int POST_PAGE_TIMEOUT    = 8_000;
    private static final int SCROLL_COUNT_PER_TAG = 4;
    private static final int POSTS_PER_TAG        = 15;
    private static final int MIN_POSTS_TOTAL      = 50;

    // ── Category → Tag Mapping (keys match Category enum) ────────────────────
    private static final Map<String, List<String>> SECTION_TAGS = Map.of(
            "STREETWEAR", List.of(
                    "streetwearindia",    // Your clean local streetwear anchor
                    "homegrownstreetwear",          // The global tag: massive volume, very high-quality aesthetic fits
                    "vintagefootball",    // Brings in super clean retro jersey and tee styling
                    "oversizedteesindia", // Keeps the focus on the actual product fit
                    "hgstreet",
                    "jerseycultureindia"    // Highly curated homegrown Indian streetwear community
            ),

            "SPORTSWEAR", List.of(
                    "pumpcoverindia",        // Massive Gen Z gym trend. Pulls in the oversized hoodies/tees worn specifically for the gym.
                    "loosefitjoggersindia",       // Captures the lifestyle crossover where sportswear is styled for everyday street use.
                    "compressionwearindia",  // High-signal for the tight-fitting, athletic aesthetic dominating modern gym culture.
                    "homegrowngymwear",      // Filters out drop-shippers and targets high-quality, aesthetic Indian fitness brands.
                    "gymwearindia"      // Forces the scraper to pull product-focused lifestyle shoots rather than random workout videos.
            ),

            "ANIMEWEAR", List.of("animestreetwearindia", "animeclothingindia", "animemerchindia",
                    "animedropindia", "otakufashionindia", "animeapparelindia",
                    "homegrownanime", "animestreetstyleindia"),

            "SNEAKERS", List.of(
                    "shoesindia",          // The cleanest, highest-quality community tag for on-feet styling and authentic sneakerheads.
                    "sneakerdropindia",      // Pure commerce/hype signal. Pulls in actual new releases, brand announcements, and stock drops.
                    "sneakerindia",    // Points the scraper directly at homegrown D2C brands actively selling their own silhouettes.
                    "shoeindia",  // High-res, professional product shots from premium curators and resellers.
                    "sneakercultureindia"    // Captures the broader streetwear lifestyle and how sneakers are being paired with fits.
            ),

            "SHIRTS", List.of(
                    "oldmoneyshirts",     // Hits the exact core aesthetic you are targeting.
                    "linenshirtsindia",   // Extremely high-signal for classic, smart-casual summer fits.
                    "oxfordshirtsindia",  // The absolute staple for classic menswear and clean data.
                    "poloshirtsindia",    // Specifically pulls in the collared knitwear/polo vibe you mentioned.
                    "cubancollarindia"    // A refined, classic resort-wear style that completely avoids the basic tee look.
            ),

            "BOTTOMS", List.of(
                    "baggyjeansindia",        // The absolute core product pushing streetwear commerce right now. Every brand is selling these.
                    "joggersindia",             // Massive summer/monsoon trend. Highly specific to streetwear labels dropping new collections.
                    "cargopantsindia",        // The staple utility piece; pulls in heavy D2C brand catalogs and drops.
                    "widelegpantsindia",      // Captures the relaxed, flowy trousers and cords that brands are pivoting to outside of denim.
                    "denimindia"    // The perfect anchor tag to ensure you are getting streetwear brands, avoiding generic mall-brand jeans.
            ),

            "ACCESSORIES", List.of(
                    "pendantsindia",        // High trend signal: Pulls in the exact Gen Z / streetwear aesthetic (chunky rings, futuristic shades)
                    "capsindia",           // Extremely specific to streetwear headwear; guarantees the right vibe
                    "jwelleryindia",           // Covers chains, pendants, and rings with much cleaner data than individual tags
                    "vintagewatchesindia",
                     "watchesindia",             // Brings in that premium, retro-styling element that pairs perfectly with streetwear
                    "braceletsindia"  // Your core anchor to keep the scraper grounded in the niche
            ),

            "WATCHES", List.of(
                    // Your originals
                    "vintagewatchesindia", "digitalwatchesindia",

                    // Additions (HMT is huge in the Indian vintage/streetwear scene right now)
                    "watchesindia", "hmtwatches", "hmtwatchesindia", "budgetwatchesindia"
            ),

            "FRAGRANCES", List.of(
                    "desifragranceaddicts",   // The biggest and most passionate Indian fragrance community; guarantees real user shots.
                    "fragcommindia",          // The standard "Fragrance Community" tag; pulls high-quality, curated collector content.
                    "scentofthedayindia",     // Essential for visual scrapers; people use this when pairing fragrances with their daily streetwear/fits.
                    "nichefragrancesindia",   // Filters out the cheap clone spam and brings in premium, high-aesthetic photography.
                    "perfumecollectionindia"  // High-signal tag that usually features clean, organized aesthetic shelf displays.
            )
    );

    // ── URL Patterns ─────────────────────────────────────────────────────────
    private static final Pattern POST_OR_REEL_PATTERN =
            Pattern.compile("https://www\\.instagram\\.com/(p|reel)/[A-Za-z0-9_-]+/?");

    // ── Caption Extraction Patterns ──────────────────────────────────────────
    /**
     * Applied to a ~5000-char window starting at the {@code edge_media_to_caption}
     * marker.  Matches the {@code "text":"..."} node within the caption edges array.
     * A 5000-char window comfortably covers Instagram's 2200-char caption limit
     * plus JSON structural overhead.
     */
    private static final Pattern CAPTION_TEXT_IN_WINDOW =
            Pattern.compile("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)+)\"");

    /**
     * Applied to a ~600-char window starting at {@code "caption":\{} for the
     * newer GraphQL API response schema: {@code "caption": {"text": "..." }}.
     */
    private static final Pattern CAPTION_OBJECT_PATTERN = Pattern.compile(
            "\"caption\"\\s*:\\s*\\{[^{}]{0,300}?\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)+)\"");

    // ── Like & Comment Count Patterns ────────────────────────────────────────
    /**
     * Fallback direct-field pattern for like count — scanned across the full HTML
     * and the maximum value is used, to handle repeated occurrences.
     */
    private static final Pattern LIKE_COUNT_DIRECT =
            Pattern.compile("\"like_count\"\\s*:\\s*(\\d+)");

    /**
     * Fallback direct-field pattern for comment count.
     */
    private static final Pattern COMMENT_COUNT_DIRECT =
            Pattern.compile("\"comment_count\"\\s*:\\s*(\\d+)");

    // ── Author Extraction Patterns ────────────────────────────────────────────
    /** JSON-LD Schema.org: {@code "author": \{"alternateName": "@username"\}} */
    private static final Pattern JSON_LD_AUTHOR_PATTERN = Pattern.compile(
            "\"author\"\\s*:\\s*\\{[^}]*\"alternateName\"\\s*:\\s*\"@([A-Za-z0-9._]+)\"");

    /** Embedded JSON owner block: {@code "owner": \{"username": "..."\}} */
    private static final Pattern OWNER_USERNAME_PATTERN = Pattern.compile(
            "\"owner\"\\s*:\\s*\\{[^}]*\"username\"\\s*:\\s*\"([A-Za-z0-9._]+)\"");

    private static final Pattern OG_IMAGE_PATTERN = Pattern.compile(
            "<meta[^>]+property\\s*=\\s*\"og:image\"[^>]+content\\s*=\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#([A-Za-z0-9_]+)");

    private static final Set<String> BLOCKED_RESOURCE_TYPES =
            Set.of("image", "media", "font", "stylesheet");

    private static final List<String> RESERVED_PATHS =
            List.of("explore", "p", "reel", "stories", "accounts", "tags", "direct");



    // ════════════════════════════════════════════════════════════════════════
    //  V2 — fetchExploreSignals
    // ════════════════════════════════════════════════════════════════════════

    public List<TrendSignal> fetchExploreSignals(Playwright playwright, String section) {
        log.info("[EXPLORE-V2] ════════ Starting signal extraction — section={} ════════", section);
        List<TrendSignal> signals = new ArrayList<>();

        if (!sessionManager.ensureSession(playwright)) {
            log.warn("[EXPLORE-V2] No valid Instagram session"); return signals;
        }

        Set<String> postUrls = null;
        try (Browser browser = playwright.chromium()
                .launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            BrowserContext ctx = createContext(browser);
            postUrls = new LinkedHashSet<>();
            Page tagPage = ctx.newPage();

            // ── Shuffle tags so each run starts from different hashtags ──
            List<String> tags = new ArrayList<>(SECTION_TAGS.getOrDefault(
                    section == null ? "" : section.toUpperCase(), SECTION_TAGS.get("STREETWEAR")));
            Collections.shuffle(tags);
            log.info("[EXPLORE-V2] Shuffled tag order: {}", tags);

            for (String tag : tags) {
                if (postUrls.size() >= MIN_POSTS_TOTAL) break;
                collectFromTagUrl(tagPage, String.format(SEARCH_URL_TMPL, tag), postUrls);
                RandomDelayUtil.delay();
            }
            tagPage.close();
            log.info("[EXPLORE-V2] Phase 1 done — {} post URLs collected", postUrls.size());

            if (postUrls.isEmpty()) {
                log.warn("[EXPLORE-V2] No post URLs found — aborting");
                ctx.close();
                return signals;
            }

            // ── Pre-filter: skip already-seen post URLs ──
            Set<String> freshUrls = new LinkedHashSet<>();
            for (String url : postUrls) {
                if (!trendSignalRepository.existsBySourceUrl(url)) {
                    freshUrls.add(url);
                }
            }
            log.info("[EXPLORE-V2] Dedup: {} total → {} fresh (skipped {} already-seen)",
                    postUrls.size(), freshUrls.size(), postUrls.size() - freshUrls.size());

            if (freshUrls.isEmpty()) {
                log.warn("[EXPLORE-V2] All posts already seen — try different hashtags or wait for new content");
                ctx.close();
                return signals;
            }

            Page signalPage = ctx.newPage();
            installResourceBlocker(signalPage);

            int processed = 0, total = freshUrls.size();
            for (String postUrl : freshUrls) {
                processed++;
                log.info("[EXPLORE-V2] Extracting signal {}/{}: {}", processed, total, postUrl);
                try {
                    TrendSignal signal = extractSignalFromPost(signalPage, postUrl);
                    if (signal != null) {
                        signals.add(signal);
                        log.info("[EXPLORE-V2] ✅ @{} | likes={} | comments={} | tags={} | caption={}",
                                signal.getAuthorUsername(), signal.getEngagementScore(),
                                signal.getCommentCount(), signal.getHashtags().size(),
                                truncate(signal.getRawText(), 80));
                    } else {
                        log.debug("[EXPLORE-V2] ⚠ No signal extracted from: {}", postUrl);
                    }
                } catch (IllegalStateException e) {
                    if ("HTTP_429".equals(e.getMessage())) {
                        log.error("[EXPLORE-V2] 🚨 RATE LIMIT HIT (429). Aborting batch to protect session.");
                        break;
                    }
                    log.warn("[EXPLORE-V2] Error on {}: {}", postUrl, e.getMessage());
                } catch (TimeoutError te) {
                    log.warn("[EXPLORE-V2] Timeout — skipping: {}", postUrl);
                } catch (Exception e) {
                    log.warn("[EXPLORE-V2] Error on {}: {}", postUrl, e.getMessage());
                }
                
                // Increase delays significantly to avoid 429 blocks
                RandomDelayUtil.delay(3000, 6000, "post-to-post delay");
                if (processed % 5 == 0 && processed < total) {
                    RandomDelayUtil.delay(10_000, 15_000, "v2-rate-limit-pause");
                }
            }
            signalPage.close();
            ctx.close();
        } catch (Exception e) {
            log.error("[EXPLORE-V2] Fatal: {}", e.getMessage(), e);
        }

        assert postUrls != null;
        log.info("[EXPLORE-V2] ════════ Done: {} signals from {} posts ════════",
                signals.size(), postUrls.size());
        return signals;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SIGNAL EXTRACTION — INDIVIDUAL POST PAGE
    // ════════════════════════════════════════════════════════════════════════

    private TrendSignal extractSignalFromPost(Page page, String postUrl) {
        // ── Navigate ──────────────────────────────────────────────────────────
        Response response = null;
        try {
            response = page.navigate(postUrl, new Page.NavigateOptions().setTimeout(POST_PAGE_TIMEOUT));
        } catch (TimeoutError te) {
            log.warn("[EXPLORE-V2] Navigation timeout for: {}", postUrl);
        }
        
        if (response != null && response.status() == 429) {
            throw new IllegalStateException("HTTP_429");
        }
        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(POST_PAGE_TIMEOUT));
        } catch (TimeoutError ignored) {}

        if (isLoginPage(page.url())) {
            log.warn("[EXPLORE-V2] Session expired — login redirect detected");
            sessionManager.invalidateSession();
            return null;
        }

        RandomDelayUtil.delay(500, 1500, "post-settle");

        // ── Raw HTML ──────────────────────────────────────────────────────────
        String html;
        try {
            html = page.content();
        } catch (Exception e) {
            log.warn("[EXPLORE-V2] Failed to get page content for {}: {}", postUrl, e.getMessage());
            return null;
        }

        if (html == null || html.length() < 200) {
            if (html != null && (html.contains("HTTP ERROR 429") || html.contains("429 Too Many Requests") || html.contains("rate limit"))) {
                throw new IllegalStateException("HTTP_429");
            }
            log.warn("[EXPLORE-V2] Page suspiciously small ({} chars) for {}",
                    html != null ? html.length() : 0, postUrl);
            return null;
        }

        // ── Extract Fields ────────────────────────────────────────────────────
        String caption      = extractCaption(html);
        long   likeCount    = extractLikeCount(html);
        long   commentCount = extractCommentCount(html);
        String author       = extractAuthorUsername(html, page);
        String mediaUrl     = extractMediaUrl(html);

        // ── Poison Guard ──────────────────────────────────────────────────────
        if ("dekhoooooooo".equalsIgnoreCase(author)) {
            log.warn("[EXPLORE-V2] ☠ Poison guard — discarding post from bot @dekhoooooooo: {}", postUrl);
            return null;
        }

        // ── Discard Gate — with 500-char diagnostic snippet ───────────────────
        // If we hit this after the extraction rewrite, the snippet exposes whether
        // we are seeing a CAPTCHA wall, login modal, or a genuinely empty page.
        if ((caption == null || caption.isBlank()) && likeCount == 0 && commentCount == 0) {
            String snippet = html.substring(0, Math.min(html.length(), 500))
                    .replaceAll("\\s+", " ");
            log.warn("[EXPLORE-V2] ⚠ DISCARD — no caption/engagement for {}. " +
                    "Page snippet (500 chars): [{}]", postUrl, snippet);
            return null;
        }

        return TrendSignal.builder()
                .platform(Platform.INSTAGRAM)
                .sourceUrl(postUrl)
                .rawText(caption != null ? caption : "")
                .hashtags(extractHashtags(caption))
                .engagementScore(likeCount)
                .commentCount(commentCount > 0 ? commentCount : null)
                .authorUsername(author != null ? author : "unknown")
                .mediaUrl(mediaUrl)
                .collectedAt(Instant.now())
                .processedByAi(false)
                .build();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CAPTION EXTRACTION — 4-STRATEGY WINDOW-BASED
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Extracts the post caption via a 4-strategy waterfall that targets the raw
     * JSON blobs embedded in Instagram's {@code <script>} tags rather than the
     * obfuscated visual DOM.
     *
     * <h3>Strategy summary</h3>
     * <ol>
     *   <li><b>edge_media_to_caption window</b> — canonical GraphQL caption path.
     *       Scans ALL occurrences and returns the longest valid match to guard
     *       against duplicate blobs with truncated content.</li>
     *   <li><b>caption.text object window</b> — newer GraphQL API schema.</li>
     *   <li><b>og:description</b> — attempts to strip the engagement prefix
     *       ({@code "N likes, M comments - @user on Date: "}) before returning
     *       the raw caption portion.</li>
     *   <li><b>accessibility_caption</b> — Instagram's alt-text field used for
     *       AI-generated image descriptions; often contains product context.</li>
     * </ol>
     */
    private String extractCaption(String html) {

        // ── S1: edge_media_to_caption window ──────────────────────────────────
        // JSON path: edge_media_to_caption → edges[0] → node → text
        // We scan all occurrences and keep the longest valid caption to handle
        // pages where the same blob appears more than once in different contexts.
        {
            String best = null;
            int searchFrom = 0;
            while (true) {
                int markerIdx = html.indexOf("edge_media_to_caption", searchFrom);
                if (markerIdx == -1) break;

                String window = html.substring(markerIdx, Math.min(markerIdx + 5000, html.length()));
                Matcher m = CAPTION_TEXT_IN_WINDOW.matcher(window);
                if (m.find()) {
                    String candidate = unescapeJsonString(m.group(1));
                    if (isValidCaption(candidate) && (best == null || candidate.length() > best.length())) {
                        best = candidate.trim();
                    }
                }
                searchFrom = markerIdx + 21; // len("edge_media_to_caption")
                if (searchFrom > 5_000_000) break; // safety on pathologically large pages
            }
            if (best != null) {
                log.debug("[EXPLORE-V2] Caption via edge_media_to_caption ({} chars)", best.length());
                return best;
            }
        }

        // ── S2: "caption":{"text":"..."} — newer GraphQL schema ───────────────
        {
            int searchFrom = 0;
            while (true) {
                int markerIdx = html.indexOf("\"caption\":{", searchFrom);
                if (markerIdx == -1) markerIdx = html.indexOf("\"caption\": {", searchFrom);
                if (markerIdx == -1) break;

                String window = html.substring(markerIdx, Math.min(markerIdx + 600, html.length()));
                Matcher m = CAPTION_OBJECT_PATTERN.matcher(window);
                if (m.find()) {
                    String candidate = unescapeJsonString(m.group(1));
                    if (isValidCaption(candidate)) {
                        log.debug("[EXPLORE-V2] Caption via caption.text ({} chars)", candidate.length());
                        return candidate.trim();
                    }
                }
                searchFrom = markerIdx + 11;
                if (searchFrom > 5_000_000) break;
            }
        }

        // ── S3: og:description ────────────────────────────────────────────────
        // Instagram format: "N likes, M comments - @user on Date: "caption""
        // We first try to extract the portion inside the trailing quotes.
        {
            String ogDesc = extractOgContent(html, "og:description");
            if (ogDesc != null) {
                ogDesc = unescapeHtmlEntities(ogDesc);
                // Try to strip the metadata prefix and extract the quoted caption
                Matcher quotedM = Pattern.compile("\"(.{10,})\"\\s*$").matcher(ogDesc);
                if (quotedM.find() && isValidCaption(quotedM.group(1))) {
                    log.debug("[EXPLORE-V2] Caption via og:description[quoted] ({} chars)",
                            quotedM.group(1).length());
                    return quotedM.group(1).trim();
                }
                // Fall back to the full description string
                if (isValidCaption(ogDesc) && ogDesc.length() > 20) {
                    log.debug("[EXPLORE-V2] Caption via og:description[full] ({} chars)", ogDesc.length());
                    return ogDesc.trim();
                }
            }
        }

        // ── S4: accessibility_caption JSON field ─────────────────────────────
        {
            Matcher m = Pattern.compile(
                    "\"accessibility_caption\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.){20,})\"").matcher(html);
            if (m.find()) {
                String candidate = unescapeJsonString(m.group(1));
                if (isValidCaption(candidate)) {
                    log.debug("[EXPLORE-V2] Caption via accessibility_caption ({} chars)", candidate.length());
                    return candidate.trim();
                }
            }
        }

        log.debug("[EXPLORE-V2] No caption found in page HTML");
        return null;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  LIKE COUNT — WINDOW-BASED WITH DIRECT-SCAN FALLBACK
    // ════════════════════════════════════════════════════════════════════════

    /**
     * <ol>
     *   <li><b>Window strategy:</b> locates the {@code edge_media_preview_like}
     *       marker, takes a 200-char window, and extracts the first
     *       {@code "count":N} value.</li>
     *   <li><b>Direct scan fallback:</b> scans all {@code "like_count":N}
     *       occurrences and returns the maximum value.</li>
     * </ol>
     */
    private long extractLikeCount(String html) {
        // S1: edge_media_preview_like window
        int idx = html.indexOf("edge_media_preview_like");
        if (idx != -1) {
            String window = html.substring(idx, Math.min(idx + 200, html.length()));
            Matcher m = Pattern.compile("\"count\"\\s*:\\s*(\\d+)").matcher(window);
            if (m.find()) {
                try {
                    long count = Long.parseLong(m.group(1));
                    log.debug("[EXPLORE-V2] Likes via edge_media_preview_like: {}", count);
                    return count;
                } catch (NumberFormatException ignored) {}
            }
        }

        // S2: direct-field scan — take max across all occurrences
        long max = 0;
        Matcher m = LIKE_COUNT_DIRECT.matcher(html);
        while (m.find()) {
            try {
                long n = Long.parseLong(m.group(1));
                if (n > max) max = n;
            } catch (NumberFormatException ignored) {}
        }

        if (max > 0) log.debug("[EXPLORE-V2] Likes via like_count direct: {}", max);
        else         log.debug("[EXPLORE-V2] Could not extract like count");
        return max;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  COMMENT COUNT — WINDOW-BASED WITH DIRECT-SCAN FALLBACK
    // ════════════════════════════════════════════════════════════════════════

    /**
     * <ol>
     *   <li><b>Window strategy:</b> locates the {@code edge_media_to_comment}
     *       marker, takes a 200-char window, and extracts the first
     *       {@code "count":N} value.</li>
     *   <li><b>Direct scan fallback:</b> scans all {@code "comment_count":N}
     *       occurrences and returns the maximum value.</li>
     * </ol>
     */
    private long extractCommentCount(String html) {
        // S1: edge_media_to_comment window
        int idx = html.indexOf("edge_media_to_comment");
        if (idx != -1) {
            String window = html.substring(idx, Math.min(idx + 200, html.length()));
            Matcher m = Pattern.compile("\"count\"\\s*:\\s*(\\d+)").matcher(window);
            if (m.find()) {
                try {
                    long count = Long.parseLong(m.group(1));
                    log.debug("[EXPLORE-V2] Comments via edge_media_to_comment: {}", count);
                    return count;
                } catch (NumberFormatException ignored) {}
            }
        }

        // S2: direct-field scan — take max
        long max = 0;
        Matcher m = COMMENT_COUNT_DIRECT.matcher(html);
        while (m.find()) {
            try {
                long n = Long.parseLong(m.group(1));
                if (n > max) max = n;
            } catch (NumberFormatException ignored) {}
        }

        if (max > 0) log.debug("[EXPLORE-V2] Comments via comment_count direct: {}", max);
        return max;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  AUTHOR EXTRACTION — 7-STRATEGY WATERFALL WITH POISON GUARD
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Extracts the post author's username using 7 ordered strategies.
     *
     * <p><strong>Key fix:</strong> Earlier versions used {@code [^}]*} in regex patterns
     * to match the owner/author JSON block. This breaks on nested objects (e.g.,
     * {@code "owner":{"id":"123","edge_followed_by":{"count":100},"username":"target"}})
     * because {@code [^}]*} stops at the first closing brace. The fix uses
     * <strong>windowed extraction</strong>: find the JSON marker, take a bounded
     * substring, and search for the username within that window.</p>
     */
    private String extractAuthorUsername(String html, Page page) {

        // S1: Window-based "owner" JSON extraction (handles nested objects)
        //     Find "owner" marker, take a 500-char window, search for "username":"..." within it
        {
            int idx = html.indexOf("\"owner\"");
            if (idx == -1) idx = html.indexOf("\"owner\" ");
            if (idx != -1) {
                String window = html.substring(idx, Math.min(idx + 500, html.length()));
                Matcher m = Pattern.compile("\"username\"\\s*:\\s*\"([A-Za-z0-9._]{2,30})\"").matcher(window);
                if (m.find()) {
                    String u = m.group(1);
                    if (isValidAuthor(u)) {
                        log.debug("[EXPLORE-V2] Author via owner-window: @{}", u);
                        return u;
                    }
                }
            }
        }

        // S2: Window-based "user" JSON extraction (alternate API schema)
        {
            int idx = html.indexOf("\"user\":{");
            if (idx == -1) idx = html.indexOf("\"user\" :{");
            if (idx != -1) {
                String window = html.substring(idx, Math.min(idx + 500, html.length()));
                Matcher m = Pattern.compile("\"username\"\\s*:\\s*\"([A-Za-z0-9._]{2,30})\"").matcher(window);
                if (m.find()) {
                    String u = m.group(1);
                    if (isValidAuthor(u)) {
                        log.debug("[EXPLORE-V2] Author via user-window: @{}", u);
                        return u;
                    }
                }
            }
        }

        // S3: <title> tag — "Display Name (@username) • Instagram photos and videos"
        {
            Matcher m = Pattern.compile("<title>[^<]*\\(@([A-Za-z0-9._]{2,30})\\)[^<]*</title>").matcher(html);
            if (m.find()) {
                String u = m.group(1);
                if (isValidAuthor(u)) {
                    log.debug("[EXPLORE-V2] Author via <title>: @{}", u);
                    return u;
                }
            }
        }

        // S4: og:description — "N Likes, M Comments - @username on Instagram: ..."
        {
            Matcher m = Pattern.compile(
                    "og:description\"\\s+content\\s*=\\s*\"[^\"]*?@([A-Za-z0-9._]{2,30})\\s+on Instagram",
                    Pattern.CASE_INSENSITIVE).matcher(html);
            if (m.find()) {
                String u = m.group(1);
                if (isValidAuthor(u)) {
                    log.debug("[EXPLORE-V2] Author via og:description: @{}", u);
                    return u;
                }
            }
        }

        // S5: JSON-LD Schema.org — "author":{"alternateName":"@username"}
        {
            Matcher m = JSON_LD_AUTHOR_PATTERN.matcher(html);
            if (m.find()) {
                String u = m.group(1);
                if (isValidAuthor(u)) {
                    log.debug("[EXPLORE-V2] Author via JSON-LD: @{}", u);
                    return u;
                }
            }
        }

        // S6: DOM — article header anchor links (may fail behind login wall / resource blocking)
        try {
            for (ElementHandle link : page.querySelectorAll("article header a")) {
                String href = link.getAttribute("href");
                if (href != null && href.startsWith("/") && href.length() > 2) {
                    String u = href.replace("/", "").trim();
                    if (isValidAuthor(u)) {
                        log.debug("[EXPLORE-V2] Author via DOM header: @{}", u);
                        return u;
                    }
                }
            }
        } catch (Exception ignored) {}

        // S7: Full-page "username" scan — pick the FIRST valid, non-reserved username
        //     after excluding Instagram's own infrastructure usernames
        {
            Matcher m = Pattern.compile("\"username\"\\s*:\\s*\"([A-Za-z0-9._]{3,30})\"").matcher(html);
            while (m.find()) {
                String u = m.group(1);
                if (isValidAuthor(u)) {
                    log.debug("[EXPLORE-V2] Author via full-page username scan: @{}", u);
                    return u;
                }
            }
        }

        // All strategies failed — log diagnostic info
        log.warn("[EXPLORE-V2] ⚠ All 7 author extraction strategies failed. " +
                "owner-marker-present={}, title-snippet=[{}]",
                html.contains("\"owner\""),
                extractTitleSnippet(html));
        return null;
    }

    /**
     * Validates that a username candidate is a real post author (not the bot,
     * not a reserved Instagram path, not an infrastructure username).
     */
    private boolean isValidAuthor(String username) {
        if (username == null || username.length() < 2) return false;
        if ("dekhoooooooo".equalsIgnoreCase(username)) return false;
        if (RESERVED_PATHS.contains(username.toLowerCase())) return false;
        // Reject common Instagram infrastructure usernames
        if (username.equalsIgnoreCase("instagram")) return false;
        if (username.equalsIgnoreCase("meta")) return false;
        return true;
    }

    /** Extracts a <title> snippet for diagnostic logging. */
    private String extractTitleSnippet(String html) {
        int start = html.indexOf("<title>");
        if (start == -1) return "<no-title>";
        int end = html.indexOf("</title>", start);
        if (end == -1) end = Math.min(start + 100, html.length());
        return html.substring(start + 7, Math.min(end, start + 100));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  MEDIA URL EXTRACTION
    // ════════════════════════════════════════════════════════════════════════

    private String extractMediaUrl(String html) {
        // og:image
        Matcher m = OG_IMAGE_PATTERN.matcher(html);
        if (m.find()) {
            String url = unescapeHtmlEntities(m.group(1));
            if (url != null && url.startsWith("http")) {
                log.debug("[EXPLORE-V2] Media URL via og:image");
                return url;
            }
        }

        // display_url JSON field
        Matcher displayUrl = Pattern.compile(
                "\"display_url\"\\s*:\\s*\"(https?://[^\"]+)\"").matcher(html);
        if (displayUrl.find()) {
            log.debug("[EXPLORE-V2] Media URL via display_url JSON");
            return displayUrl.group(1).replace("\\/", "/");
        }

        log.debug("[EXPLORE-V2] Could not extract media URL");
        return null;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HASHTAG EXTRACTION
    // ════════════════════════════════════════════════════════════════════════

    private List<String> extractHashtags(String caption) {
        List<String> hashtags = new ArrayList<>();
        if (caption == null || caption.isBlank()) return hashtags;
        Matcher m = HASHTAG_PATTERN.matcher(caption);
        Set<String> seen = new LinkedHashSet<>();
        while (m.find()) {
            String tag = m.group(1).toLowerCase();
            if (tag.length() >= 2 && tag.length() <= 50 && seen.add(tag)) {
                hashtags.add(tag);
            }
        }
        log.debug("[EXPLORE-V2] Extracted {} unique hashtags", hashtags.size());
        return hashtags;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  TAG PAGE URL COLLECTION (V1 + V2 SHARED)
    // ════════════════════════════════════════════════════════════════════════

    private void collectFromTagUrl(Page page, String url, Set<String> accumulator) {
        try {
            log.info("[EXPLORE] Navigating → {}", url);
            page.navigate(url);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(DOM_SETTLE_TIMEOUT));

            if (isLoginPage(page.url())) {
                log.warn("[EXPLORE] Redirected to login — session expired");
                sessionManager.invalidateSession();
                return;
            }

            RandomDelayUtil.longDelay();

            for (int i = 0; i < SCROLL_COUNT_PER_TAG; i++) {
                page.evaluate("window.scrollBy(0, window.innerHeight * 2)");
                RandomDelayUtil.delay();
                log.debug("[EXPLORE] Scroll {}/{} on {}", i + 1, SCROLL_COUNT_PER_TAG, url);
            }

            List<ElementHandle> anchors = page.querySelectorAll("a[href*='/p/'], a[href*='/reel/']");
            log.debug("[EXPLORE] Found {} anchor elements on {}", anchors.size(), url);

            int collected = 0;
            for (ElementHandle a : anchors) {
                if (collected >= POSTS_PER_TAG) break;
                try {
                    String href = a.getAttribute("href");
                    if (href == null || href.isBlank()) continue;
                    String full = href.startsWith("http")
                            ? href : "https://www.instagram.com" + href;
                    if (!full.endsWith("/")) full += "/";
                    String clean = full.split("\\?")[0];
                    if (POST_OR_REEL_PATTERN.matcher(clean).matches() && accumulator.add(clean)) {
                        collected++;
                    }
                } catch (Exception e) {
                    log.trace("[EXPLORE] Anchor error: {}", e.getMessage());
                }
            }
            log.info("[EXPLORE] +{} unique posts from {}. Total: {}", collected, url, accumulator.size());

        } catch (Exception e) {
            log.warn("[EXPLORE] Failed to collect from {}: {}", url, e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  UTILITY HELPERS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Robustly extracts a named Open Graph meta tag {@code content} value,
     * regardless of attribute declaration order inside the {@code <meta>} tag.
     */
    private static String extractOgContent(String html, String property) {
        int idx = html.indexOf(property + "\"");
        if (idx == -1) idx = html.indexOf(property + "'");
        if (idx == -1) return null;

        int tagStart = html.lastIndexOf("<meta", idx);
        if (tagStart == -1) return null;
        int tagEnd = html.indexOf(">", idx);
        if (tagEnd == -1) return null;

        String tag = html.substring(tagStart, tagEnd + 1);
        Matcher m = Pattern.compile("content\\s*=\\s*[\"']([^\"']+)[\"']").matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Returns {@code true} iff the candidate string is a plausible Instagram
     * caption (non-null, ≥5 chars, not an Instagram boilerplate phrase).
     */
    private static boolean isValidCaption(String s) {
        if (s == null || s.length() < 5) return false;
        String lower = s.toLowerCase();
        if (lower.startsWith("follow") || lower.startsWith("log in") || lower.startsWith("sign up")) return false;
        if (lower.contains("© instagram") || lower.contains("instagram, inc")) return false;
        return true;
    }

    private void installResourceBlocker(Page page) {
        page.route("**/*", route -> {
            if (BLOCKED_RESOURCE_TYPES.contains(route.request().resourceType())) {
                route.abort();
            } else {
                route.resume();
            }
        });
        log.debug("[EXPLORE-V2] Resource blocker installed — blocking: {}", BLOCKED_RESOURCE_TYPES);
    }

    private BrowserContext createContext(Browser browser) {
        return browser.newContext(new Browser.NewContextOptions()
                .setStorageStatePath(sessionManager.getSessionPath())
                .setViewportSize(1280, 900)
                .setUserAgent(USER_AGENT));
    }

    private boolean isLoginPage(String url) {
        return url != null
                && (url.contains("/accounts/login") || url.contains("/accounts/emailsignup"));
    }

    private static String unescapeJsonString(String s) {
        if (s == null) return null;
        return s.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
                .replace("\\\"", "\"").replace("\\/", "/").replace("\\\\", "\\");
    }

    private static String unescapeHtmlEntities(String s) {
        if (s == null) return null;
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&#x27;", "'").replace("&apos;", "'");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "<null>";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}