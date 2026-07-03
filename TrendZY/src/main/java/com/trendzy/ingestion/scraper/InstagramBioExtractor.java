package com.trendzy.ingestion.scraper;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import com.trendzy.ingestion.scraper.instagram.InstagramSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstagramBioExtractor {

    private final InstagramSessionManager sessionManager;

    // ── Pre-compiled Regex Patterns ──
    private static final Pattern EXTERNAL_URL_PATTERN =
            Pattern.compile("\"external_url\"\\s*:\\s*\"(https?[^\"]+)\"");

    private static final Pattern BIO_LINK_PATTERN =
            Pattern.compile("\"bio_links\"\\s*:\\s*\\[\\s*\\{[^}]*\"url\"\\s*:\\s*\"(https?[^\"]+)\"");

    private static final Pattern LINK_HEADER_PATTERN =
            Pattern.compile("\"link_header\"\\s*:\\s*\"(https?[^\"]+)\"");

    // Hosts that are NEVER a creator store — hard block.
    private static final Set<String> BLOCKED_HOSTS = Set.of(
            // Instagram / Meta noise (the actual culprit — Meta sidebar link on logged-in pages)
            "instagram.com", "www.instagram.com", "l.instagram.com",
            "about.instagram.com", "business.instagram.com", "help.instagram.com",
            "about.meta.com", "meta.com", "www.meta.com",
            "meta.ai", "www.meta.ai", "ai.meta.com",           // ← was missing — caused false bio link
            "transparency.meta.com", "transparency.fb.com",
            "threads.com", "www.threads.com", "threads.net", "www.threads.net",
            "l.threads.com", "l.threads.net",
            "facebook.com", "www.facebook.com", "fb.com", "m.facebook.com",
            "l.facebook.com", "about.facebook.com", "business.facebook.com", "help.facebook.com",
            // Other socials — never stores
            "twitter.com", "x.com", "www.twitter.com", "www.x.com", "t.co",
            "youtube.com", "youtu.be", "www.youtube.com", "m.youtube.com",
            "tiktok.com", "www.tiktok.com", "vm.tiktok.com",
            "wa.me", "api.whatsapp.com", "chat.whatsapp.com", "whatsapp.com",
            "t.me", "telegram.me", "telegram.org",
            "snapchat.com", "pinterest.com", "in.pinterest.com", "pin.it",
            "open.spotify.com", "music.apple.com", "spotify.com",
            "discord.gg", "discord.com",
            "reddit.com", "www.reddit.com",
            "linkedin.com", "www.linkedin.com", "lnkd.in",
            "tumblr.com", "www.tumblr.com",
            "medium.com", "substack.com",
            "gmail.com", "mail.google.com",
            // Non-store apps / services
            "strava.com", "www.strava.com", "strava.app.link",
            "mojapp.in", "moj.com",
            "play.google.com", "apps.apple.com"
    );

    // URL query-param markers that identify IG page chrome (footer/nav), not real bio links.
    private static final Set<String> IG_CHROME_MARKERS = Set.of(
            "utm_source=foa_web_footer",
            "utm_source=ig_web_footer",
            "utm_source=ig_embed_footer"
    );

    // Aggregator hosts — follow one hop to find real shop.
    private static final Set<String> AGGREGATOR_HOSTS = Set.of(
            "linktr.ee", "beacons.ai", "solo.to", "bio.site", "bio.link",
            "campsite.bio", "hoo.be", "linkpop.com", "lnk.bio", "linkin.bio",
            "stan.store", "komi.io", "snipfeed.co", "allmylinks.com",
            "withkoji.com", "about.me", "carrd.co", "milkshake.app",
            "flowpage.com", "taplink.cc", "shorby.com", "linkfly.to",
            "linkbio.co", "later.com", "msha.ke"
    );

    public String extractBioLink(String username, BrowserContext context) {
        if (username == null || username.isBlank()) {
            log.warn("[INSTAGRAM] extractBioLink called with blank username");
            return null;
        }

        if (!sessionManager.sessionExists()) {
            log.warn("[INSTAGRAM] No session file — extractor may hit login wall for @{}", username);
        }

        String profileUrl = "https://www.instagram.com/" + username + "/";
        log.info("[INSTAGRAM] Extracting bio link for @{}", username);

        try (Page page = context.newPage()) {
            page.setDefaultTimeout(15000);
            try {
                page.navigate(profileUrl,
                        new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            } catch (Exception nav) {
                log.warn("[INSTAGRAM] navigation error for @{}: {}", username, nav.getMessage());
            }
            // Settle so the JSON blob is injected before we read content()
            try { page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(8000)); } catch (Exception ignored) {}
            try { page.waitForTimeout(1500); } catch (Exception ignored) {}

            String html;
            try { html = page.content(); }
            catch (Exception e) {
                log.warn("[INSTAGRAM] page.content() failed for @{}: {}", username, e.getMessage());
                return null;
            }
            if (html == null || html.length() < 3000) {
                log.warn("[INSTAGRAM] Empty/short HTML for @{} ({} chars) — login wall or IG block",
                        username, html == null ? 0 : html.length());
                return null;
            }
            if (html.contains("\"login_required\"")
                    || html.toLowerCase().contains("log in to instagram")) {
                log.error("[INSTAGRAM] Login wall for @{} — invalidating stale session; restart app to re-login", username);
                sessionManager.invalidateSession();
                return null;
            }

            // JSON regex path (reliable)
            String candidate = firstMatch(html, EXTERNAL_URL_PATTERN);
            String source = "external_url";
            if (candidate == null) { candidate = firstMatch(html, BIO_LINK_PATTERN); source = "bio_links"; }
            if (candidate == null) { candidate = firstMatch(html, LINK_HEADER_PATTERN); source = "link_header"; }

            if (candidate != null) candidate = unwrapInstagramRedirect(candidate);

            // DOM fallback — two-pass approach for better bio link detection
            if (candidate == null) {
                try {
                    // Pass 1: IG-specific bio link selectors (most reliable)
                    // Instagram wraps external bio links through l.instagram.com redirect
                    String[] bioSelectors = {
                            "a[href*='l.instagram.com/']",                          // IG redirect wrapper
                            "a[rel*='nofollow'][rel*='noopener'][href^='http']",    // IG external link rel attrs
                    };
                    for (String sel : bioSelectors) {
                        try {
                            var bioLinks = page.querySelectorAll(sel);
                            for (var linkEl : bioLinks) {
                                String href = linkEl.getAttribute("href");
                                if (href == null || !href.startsWith("http")) continue;
                                String real = unwrapInstagramRedirect(href);
                                if (isBlocked(real) || isIgPageChrome(real) || !isValidBioLink(real)) continue;
                                candidate = real;
                                source = "dom_bio_selector";
                                log.info("[INSTAGRAM] DOM bio selector picked for @{}: {}", username, real);
                                break;
                            }
                        } catch (Exception ignored) {}
                        if (candidate != null) break;
                    }

                    // Pass 2: Scan ALL anchors on the page (not just header/main/section)
                    if (candidate == null) {
                        var links = page.querySelectorAll("a[href^='http']");
                        log.info("[INSTAGRAM] DOM fallback for @{} — scanning {} anchors", username, links.size());
                        for (var linkEl : links) {
                            String href = linkEl.getAttribute("href");
                            if (href == null || !href.startsWith("http")) continue;
                            String real = unwrapInstagramRedirect(href);
                            if (isBlocked(real) || isIgPageChrome(real) || !isValidBioLink(real)) continue;
                            candidate = real;
                            source = "dom_fallback";
                            log.info("[INSTAGRAM] DOM fallback picked for @{}: {}", username, real);
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.warn("[INSTAGRAM] DOM fallback error for @{}: {}", username, e.getMessage());
                }
            }

            if (candidate == null) {
                log.warn("[INSTAGRAM] No bio link for @{} (JSON + DOM exhausted)", username);
                return null;
            }

            // Strip tracking noise (fbclid, utm_*) so downstream gets a clean URL.
            candidate = cleanTrackingParams(candidate);

            log.info("[INSTAGRAM] Candidate for @{} (source={}): {}", username, source, candidate);

            if (isAggregator(candidate)) {
                String resolved = resolveAggregator(candidate, context);
                if (resolved != null) {
                    resolved = cleanTrackingParams(resolved);
                    log.info("[INSTAGRAM] ✓ Aggregator {} → {}", candidate, resolved);
                    return resolved;
                }
                log.warn("[INSTAGRAM] ✗ Aggregator {} produced no shop link for @{}", candidate, username);
                return null;
            }

            if (isValidBioLink(candidate)) {
                log.info("[INSTAGRAM] ✓ Bio link for @{}: {}", username, candidate);
                return candidate;
            }

            log.warn("[INSTAGRAM] ✗ Rejected for @{} (blocked/invalid host): {}", username, candidate);
            return null;

        } catch (Exception e) {
            log.error("[INSTAGRAM] Fatal error extracting bio link for @{}: {}", username, e.getMessage());
            return null;
        }
    }

    private String firstMatch(String html, Pattern p) {
        try {
            Matcher m = p.matcher(html);
            if (m.find()) return unescapeUrl(m.group(1));
        } catch (Exception ignored) {}
        return null;
    }

    private String resolveAggregator(String aggregatorUrl, BrowserContext context) {
        try (Page page = context.newPage()) {
            page.setDefaultTimeout(12000);
            try {
                page.navigate(aggregatorUrl,
                        new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            } catch (Exception ignored) {}
            try { page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(6000)); } catch (Exception ignored) {}
            try { page.waitForTimeout(1500); } catch (Exception ignored) {}

            var anchors = page.querySelectorAll("a[href^='http']");
            log.info("[INSTAGRAM] Aggregator {} — {} anchors", aggregatorUrl, anchors.size());

            // Two-pass: prioritize shop/store links, then fall back to first valid
            String firstValid = null;
            for (var a : anchors) {
                String href = a.getAttribute("href");
                if (href == null) continue;
                if (isAggregator(href) || isBlocked(href)) continue;
                if (!isValidBioLink(href)) continue;

                if (firstValid == null) firstValid = href;

                // Check link text and href for shop keywords
                String text = "";
                try { text = a.innerText().toLowerCase(); } catch (Exception ignored) {}
                if (containsShopKeyword(text) || containsShopKeyword(href.toLowerCase())) {
                    log.info("[INSTAGRAM] \u2713 Aggregator {} \u2192 {} (shop keyword match)", aggregatorUrl, href);
                    return href;
                }
            }

            if (firstValid != null) {
                log.info("[INSTAGRAM] \u2713 Aggregator {} \u2192 {} (fallback)", aggregatorUrl, firstValid);
                return firstValid;
            }

            log.warn("[INSTAGRAM] Aggregator {} — no valid outbound shop link", aggregatorUrl);
        } catch (Exception e) {
            log.warn("[INSTAGRAM] resolveAggregator failed for {}: {}", aggregatorUrl, e.getMessage());
        }
        return null;
    }

    /** Checks whether a text string contains any shop/store-related keywords. */
    private boolean containsShopKeyword(String text) {
        if (text == null || text.isBlank()) return false;
        return text.contains("shop") || text.contains("store") || text.contains("buy")
                || text.contains("order") || text.contains("collection") || text.contains("product")
                || text.contains("merch") || text.contains("catalog") || text.contains("website");
    }

    private String unwrapInstagramRedirect(String url) {
        try {
            String host = host(url);
            if (host != null && (host.equals("l.instagram.com") || host.equals("l.facebook.com"))) {
                URI uri = URI.create(url);
                String query = uri.getRawQuery();
                if (query != null) {
                    for (String part : query.split("&")) {
                        if (part.startsWith("u=")) {
                            return java.net.URLDecoder.decode(part.substring(2),
                                    java.nio.charset.StandardCharsets.UTF_8);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return url;
    }

    private boolean isBlocked(String url) {
        String host = host(url);
        if (host == null) return true;
        if (BLOCKED_HOSTS.contains(host)) return true;
        for (String blocked : BLOCKED_HOSTS) {
            if (host.endsWith("." + blocked)) return true;
        }
        return false;
    }

    /** Detect Instagram page chrome links (footer, nav) via URL query-param markers. */
    private boolean isIgPageChrome(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        for (String marker : IG_CHROME_MARKERS) {
            if (lower.contains(marker)) return true;
        }
        return false;
    }

    private boolean isAggregator(String url) {
        String host = host(url);
        return host != null && AGGREGATOR_HOSTS.contains(host);
    }

    private boolean isValidBioLink(String url) {
        if (url == null || url.isBlank()) return false;
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return false;
        String host = host(url);
        if (host == null || !host.contains(".")) return false;
        return !isBlocked(url);
    }

    private String host(String url) {
        try {
            String h = URI.create(url).getHost();
            return h == null ? null : h.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    private String unescapeUrl(String url) {
        if (url == null) return null;
        return url
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("\\u003d", "=");
    }

    /**
     * Strip tracking query params (fbclid, utm_*) that Instagram appends.
     * Keeps the base URL and any non-tracking params intact.
     */
    private String cleanTrackingParams(String url) {
        if (url == null || !url.contains("?")) return url;
        try {
            int qIdx = url.indexOf('?');
            String base = url.substring(0, qIdx);
            String query = url.substring(qIdx + 1);
            StringBuilder kept = new StringBuilder();
            for (String param : query.split("&")) {
                String lower = param.toLowerCase();
                if (lower.startsWith("fbclid") || lower.startsWith("utm_")
                        || lower.startsWith("igshid") || lower.startsWith("igsh")) {
                    continue; // drop tracking param
                }
                if (!kept.isEmpty()) kept.append('&');
                kept.append(param);
            }
            return kept.isEmpty() ? base : base + "?" + kept;
        } catch (Exception e) {
            // Fallback: just strip everything after ?
            return url.split("\\?")[0];
        }
    }
}
