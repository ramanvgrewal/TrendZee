import { useState } from "react";
import { ChevronDown, ExternalLink, MapPin, Sparkles, Radio, Zap, Bookmark } from "lucide-react";
import type { ProductMatch, Trend } from "@/lib/mock-data";
import { useEffect } from "react";
import { archiveTrend, unarchiveTrend, getArchiveStatus } from "@/lib/archiveApi";
type Source = "underdog" | "amazon" | "flipkart";
const sourceLabels: Record<Source, string> = {
  underdog: "The Underdog",
  amazon: "Amazon",
  flipkart: "Flipkart",
};

const trackClick = (trendId: string, source: Source, url: string) => {
  const baseUrl = import.meta.env?.VITE_API_BASE_URL || (typeof process !== 'undefined' && process.env.VITE_API_BASE_URL) || (import.meta.env?.DEV ? "http://localhost:8080" : "");
  const token = localStorage.getItem("token");
  
  const headers: HeadersInit = {
    'Content-Type': 'application/json'
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  // navigator.sendBeacon is better for tracking links, but doesn't easily support custom headers like Authorization
  // fetch with keepalive ensures the request is not cancelled when navigating away
  fetch(`${baseUrl}/api/analytics/click`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ trendId, source, url }),
    keepalive: true,
  }).catch(err => console.error("Failed to track click", err));
};

function UnderdogHero({ product, trendId, fill }: { product: ProductMatch; trendId: string; fill?: boolean }) {
  return (
    <a
      href={product.shopUrl}
      target="_blank"
      rel="noopener noreferrer"
      onClick={() => trackClick(trendId, 'underdog', product.shopUrl)}
      className="group flex h-full flex-col overflow-hidden rounded-3xl border border-[oklch(0.72_0.09_55/40%)] bg-[oklch(0.72_0.09_55/6%)] shadow-[0_25px_60px_-30px_oklch(0.72_0.09_55/60%)] transition-all hover:-translate-y-1"
    >
      <div className={`relative w-full overflow-hidden ${fill ? "min-h-0 flex-1" : "aspect-[4/5]"}`}>
        <img
          src={product.imageUrl}
          alt={product.title}
          loading="lazy"
          className="absolute inset-0 h-full w-full object-cover transition-transform duration-700 group-hover:scale-105"
        />
        <div className="absolute left-4 top-4 rounded-full bg-[oklch(0.72_0.09_55)] px-3 py-1.5 font-mono text-[10px] font-bold uppercase tracking-[0.24em] text-background">
          The Underdog
        </div>
      </div>
      <div className={`flex flex-col gap-1 p-6 ${fill ? "" : "flex-1"}`}>
        <div className="font-mono text-[10px] uppercase tracking-[0.22em] text-foreground/40">{product.brandName}</div>
        <div className="line-clamp-2 font-display text-xl font-semibold text-foreground">{product.title}</div>
        <div className="mt-3 flex items-center justify-between">
          <span className="font-display text-3xl font-bold text-foreground">
            {product.currency}
            {product.price?.toLocaleString() ?? "N/A"}
          </span>
          <ExternalLink className="h-4 w-4 text-foreground/40 transition-transform group-hover:translate-x-0.5" />
        </div>
      </div>
    </a>
  );
}

function CompactProduct({ source, product, trendId }: { source: Source; product: ProductMatch; trendId: string }) {
  return (
    <a
      href={product.shopUrl}
      target="_blank"
      rel="noopener noreferrer"
      onClick={() => trackClick(trendId, source, product.shopUrl)}
      className="group flex flex-col overflow-hidden rounded-2xl border border-foreground/10 bg-foreground/[0.02] transition-all hover:-translate-y-0.5 hover:border-foreground/25"
    >
      <div className="relative aspect-square w-full overflow-hidden">
        <img
          src={product.imageUrl}
          alt={product.title}
          loading="lazy"
          className="absolute inset-0 h-full w-full object-cover transition-transform duration-700 group-hover:scale-105"
        />
        <div className="absolute left-2 top-2 rounded-full bg-background/75 px-2 py-0.5 font-mono text-[9px] font-bold uppercase tracking-[0.2em] text-foreground/80 backdrop-blur">
          {sourceLabels[source]}
        </div>
      </div>
      <div className="flex flex-1 flex-col gap-0.5 p-3">
        <div className="font-mono text-[9px] uppercase tracking-[0.2em] text-foreground/40">{product.brandName}</div>
        <div className="line-clamp-2 text-xs font-medium text-foreground/85">{product.title}</div>
        <div className="mt-1.5 flex items-center justify-between">
          <span className="font-display text-base font-bold text-foreground">
            {product.currency}
            {product.price?.toLocaleString() ?? "N/A"}
          </span>
          <ExternalLink className="h-3 w-3 text-foreground/40" />
        </div>
      </div>
    </a>
  );
}

function MainstreamPicks({ products, trendId }: { products: { source: Source; product?: ProductMatch }[]; trendId: string }) {
  return (
    <div>
      <div className="mb-2 font-mono text-[10px] uppercase tracking-[0.28em] text-foreground/40">
        Mainstream picks
      </div>
      <div className="grid grid-cols-2 gap-3">
        {products.map(
          ({ source, product }) =>
            product && <CompactProduct key={source} source={source} product={product} trendId={trendId} />,
        )}
      </div>
    </div>
  );
}

export function TrendCard({ trend, isArchivedContext = false, onUnarchive }: { trend: Trend, isArchivedContext?: boolean, onUnarchive?: () => void }) {
  const [summaryOpen, setSummaryOpen] = useState(false);
  const [isArchived, setIsArchived] = useState(isArchivedContext);
  const [isHovered, setIsHovered] = useState(false);

  useEffect(() => {
    const checkStatus = async () => {
      const token = localStorage.getItem("token");
      if (!token) return;
      try {
        const status = await getArchiveStatus(trend.id);
        setIsArchived(status);
      } catch (e) {
        console.error(e);
      }
    };
    if (!isArchivedContext) {
      checkStatus();
    }
  }, [trend.id, isArchivedContext]);

  const handleArchiveClick = async () => {
    const token = localStorage.getItem("token");
    if (!token) {
      alert("Please login to archive trends.");
      return;
    }
    
    try {
      if (isArchived) {
        if (window.confirm("Are you sure you want to unarchive this trend? If this trend is no longer in the main feed, it will be gone permanently.")) {
          await unarchiveTrend(trend.id);
          setIsArchived(false);
          if (onUnarchive) onUnarchive();
        }
      } else {
        await archiveTrend(trend.id);
        setIsArchived(true);
      }
    } catch (e) {
      console.error(e);
      alert("Failed to update archive status.");
    }
  };
  const mainstream: { source: Source; product?: ProductMatch }[] = [
    { source: "amazon", product: trend.products?.amazon },
    { source: "flipkart", product: trend.products?.flipkart },
  ];

  return (
    <article className="rounded-3xl border border-foreground/10 bg-foreground/[0.02] p-6 md:p-10">
      <div className="flex flex-col gap-8 md:grid md:grid-cols-[1.15fr_1fr] md:gap-10">
        {/* Left column: header, why trending, mobile products, AI summary, desktop mainstream picks */}
        <div className="flex flex-col gap-6">
          {/* Header */}
          <div>
            <div className="flex items-center gap-2">
              <span className="relative flex h-2 w-2">
                {trend.active && (
                  <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-[oklch(0.72_0.09_55)] opacity-70" />
                )}
                <span className={`relative inline-flex h-2 w-2 rounded-full ${trend.active ? "bg-[oklch(0.72_0.09_55)]" : "bg-foreground/20"}`} />
              </span>
              <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.28em] text-foreground/50">
                {trend.active ? "active drop" : "archived"}
              </span>
            </div>

            <div className="mt-3 flex items-start justify-between gap-4">
              <h2 className="font-display text-3xl font-bold leading-[1.05] tracking-tight text-foreground md:text-5xl">
                {trend.name}
              </h2>
              <button
                onClick={handleArchiveClick}
                onMouseEnter={() => setIsHovered(true)}
                onMouseLeave={() => setIsHovered(false)}
                className="mt-1 flex-shrink-0 text-foreground/40 hover:text-[oklch(0.72_0.09_55)] transition-colors"
                title={isArchived ? "Unarchive" : "Archive"}
              >
                <Bookmark 
                  className="h-6 w-6 transition-all" 
                  fill={isArchived || isHovered ? "currentColor" : "none"}
                />
              </button>
            </div>

            <div className="mt-5 flex flex-wrap items-center gap-4">
              <div className="flex items-baseline gap-2">
                <span className="font-display text-5xl font-bold italic leading-none text-[oklch(0.72_0.09_55)]">
                  {trend.trendScore}
                </span>
                <span className="font-mono text-[10px] uppercase tracking-[0.22em] text-foreground/40">
                  trend score
                </span>
              </div>
              <div className="h-8 w-px bg-foreground/10" />
              <div className="flex flex-wrap items-center gap-2 font-mono text-[10px] uppercase tracking-[0.22em] text-foreground/50">
                <span className="inline-flex items-center gap-1"><Radio className="h-3 w-3" />{trend.totalSignals} signals</span>
                {trend.signalProducts.length > 0 && (
                  <>
                    <span className="text-foreground/15">·</span>
                    <span className="inline-flex items-center gap-1"><Zap className="h-3 w-3" />{trend.signalProducts.length} shoppable</span>
                  </>
                )}
                {trend.indiaRelevant && (
                  <>
                    <span className="text-foreground/15">·</span>
                    <span className="inline-flex items-center gap-1 text-[oklch(0.72_0.09_55)]">
                      <MapPin className="h-3 w-3" /> India
                    </span>
                  </>
                )}
              </div>
            </div>

            <div className="mt-5 flex flex-wrap gap-1.5">
              {trend.vibeTags.map((t) => (
                <span
                  key={t}
                  className="rounded-full border border-foreground/15 bg-background/40 px-3 py-1 font-mono text-[10px] uppercase tracking-[0.18em] text-foreground/60"
                >
                  #{t}
                </span>
              ))}
            </div>
          </div>

          {/* Why it's trending */}
          <div>
            <div className="mb-2 font-mono text-[10px] uppercase tracking-[0.28em] text-foreground/50">
              Why it's trending
            </div>
            <ul className="flex flex-col gap-2">
              {trend.whyTrending.map((point, i) => (
                <li key={i} className="flex items-start gap-2 text-sm text-foreground/75">
                  <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-[oklch(0.72_0.09_55)]" />
                  <span>{point}</span>
                </li>
              ))}
            </ul>
          </div>

          {/* Mobile: underdog then mainstream picks */}
          {trend.products?.underdog && (
            <div className="md:hidden">
              <UnderdogHero product={trend.products.underdog} trendId={trend.id} />
            </div>
          )}
          <div className="md:hidden">
            <MainstreamPicks products={mainstream} trendId={trend.id} />
          </div>

          {/* AI Summary toggle */}
          <div className="rounded-2xl border border-foreground/10 bg-background/30">
            <button
              type="button"
              onClick={() => setSummaryOpen((v) => !v)}
              aria-expanded={summaryOpen}
              className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-foreground/[0.03]"
            >
              <span className="flex items-center gap-1.5 font-mono text-[10px] uppercase tracking-[0.28em] text-foreground/50">
                <Sparkles className="h-3 w-3" /> AI Summary
              </span>
              <ChevronDown
                className={`h-4 w-4 text-foreground/40 transition-transform ${summaryOpen ? "rotate-180" : ""}`}
              />
            </button>
            {summaryOpen && (
              <div className="border-t border-foreground/10 px-4 py-4">
                <p className="text-[15px] leading-relaxed text-foreground/75">{trend.aiSummary}</p>
              </div>
            )}
          </div>

          {/* Mainstream picks — desktop only */}
          <div className="hidden md:block">
            <MainstreamPicks products={mainstream} trendId={trend.id} />
          </div>
        </div>

        {/* Desktop: underdog right column fills full card height */}
        {trend.products?.underdog && (
          <div className="hidden h-full min-h-0 md:block">
            <UnderdogHero product={trend.products.underdog} trendId={trend.id} fill />
          </div>
        )}
      </div>
    </article>
  );
}
