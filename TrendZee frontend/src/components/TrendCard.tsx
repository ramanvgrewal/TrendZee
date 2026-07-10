import { ExternalLink, MapPin, Sparkles, Radio, Zap } from "lucide-react";
import type { ProductDetail, Trend } from "@/lib/mock-data";

function formatUtc(iso: string): string {
  if (!iso) return "Unknown";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const pad = (n: number) => n.toString().padStart(2, "0");
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())} ${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())} UTC`;
}

const sourceLabels: Record<"underdog" | "amazon" | "flipkart", string> = {
  underdog: "The Underdog",
  amazon: "Amazon",
  flipkart: "Flipkart",
};

function ProductBlock({
  source,
  product,
  highlight,
}: {
  source: "underdog" | "amazon" | "flipkart";
  product: ProductDetail | null;
  highlight?: boolean;
}) {
  if (!product) {
    return (
      <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-foreground/10 bg-foreground/[0.01] p-4 text-center text-foreground/30">
        <span className="text-[10px] uppercase tracking-widest">{sourceLabels[source]}</span>
        <span className="mt-2 text-xs">No match found</span>
      </div>
    );
  }

  return (
    <a
      href={product.shopUrl}
      target="_blank"
      rel="noreferrer"
      className={`group flex flex-col overflow-hidden rounded-xl border transition-colors ${
        highlight
          ? "border-foreground/15 bg-foreground/[0.05]"
          : "border-foreground/5 bg-foreground/[0.02] hover:bg-foreground/[0.04]"
      }`}
    >
      <div className="relative aspect-square overflow-hidden">
        <img
          src={product.imageUrl}
          alt={product.title}
          loading="lazy"
          className="h-full w-full object-cover transition-transform duration-700 group-hover:scale-105"
        />
        <div className="absolute left-3 top-3 rounded-full bg-foreground/60 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-wider text-white backdrop-blur">
          {sourceLabels[source]}
        </div>
      </div>
      <div className="flex flex-1 flex-col gap-1 p-4">
        <div className="text-[11px] uppercase tracking-widest text-foreground/30">{product.brandName}</div>
        <div className="line-clamp-2 text-sm text-foreground/80">{product.title}</div>
        <div className="mt-2 flex items-center justify-between">
          <span className="font-display text-lg font-semibold text-foreground">
            {product.currency || "Rs."}
            {product.price?.toLocaleString()}
          </span>
          <ExternalLink className="h-3.5 w-3.5 text-foreground/30 transition-transform group-hover:translate-x-0.5" />
        </div>
      </div>
    </a>
  );
}

export function TrendCard({ trend }: { trend: Trend }) {
  return (
    <article className="rounded-2xl border border-foreground/5 bg-foreground/[0.02] p-6 md:p-8">
      {/* Header */}
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className={`inline-flex h-2 w-2 rounded-full ${trend.active ? "bg-foreground/60" : "bg-foreground/20"}`} />
            <span className="text-[10px] uppercase tracking-widest text-foreground/40">
              {trend.active ? "active drop" : "archived"}
            </span>
          </div>
          <h2 className="mt-1.5 font-display text-2xl font-semibold text-foreground md:text-3xl">{trend.trendName}</h2>
          <div className="mt-2 flex flex-wrap items-center gap-2 text-[11px] uppercase tracking-widest text-foreground/40">
            <span className="inline-flex items-center gap-1"><Radio className="h-3 w-3" />{trend.totalSignals} signals</span>
            {trend.signalProducts?.underdog && (
              <>
                <span className="text-foreground/15">·</span>
                <span className="inline-flex items-center gap-1"><Zap className="h-3 w-3" />Shoppable</span>
              </>
            )}
            <span className="text-foreground/15">·</span>
            <span>{trend.enrichmentStatus?.toLowerCase() || "pending"}</span>
            {trend.indiaRelevant && (
              <>
                <span className="text-foreground/15">·</span>
                <span className="inline-flex items-center gap-1 text-foreground/60">
                  <MapPin className="h-3 w-3" /> India-relevant
                </span>
              </>
            )}
          </div>
        </div>
        <div className="flex flex-col items-end rounded-xl border border-foreground/5 bg-foreground/[0.03] px-4 py-2.5">
          <div className="text-[10px] uppercase tracking-widest text-foreground/40">Trend Score</div>
          <div className="font-display text-3xl font-semibold leading-none text-foreground">{Math.round(trend.trendScore)}</div>
        </div>
      </header>

      {/* Vibe tags */}
      <div className="mt-5 flex flex-wrap gap-1.5">
        {(trend.vibeTags || []).map((t) => (
          <span
            key={t}
            className="rounded-full border border-foreground/5 bg-foreground/[0.03] px-2.5 py-1 text-xs text-foreground/60"
          >
            #{t}
          </span>
        ))}
      </div>

      {/* AI Summary */}
      {trend.aiSummary && (
        <div className="mt-6">
          <div className="flex items-center gap-1.5 text-[11px] uppercase tracking-widest text-foreground/40">
            <Sparkles className="h-3 w-3" /> AI Summary
          </div>
          <p className="mt-2 leading-relaxed text-foreground/70">{trend.aiSummary}</p>
        </div>
      )}

      {/* Why trending */}
      {trend.whyTrending && trend.whyTrending.length > 0 && (
        <div className="mt-5">
          <div className="text-[11px] uppercase tracking-widest text-foreground/40">Why it&apos;s trending</div>
          <ul className="mt-2 space-y-1.5">
            {trend.whyTrending.map((r, i) => (
              <li key={i} className="flex gap-2 text-sm text-foreground/60">
                <span className="mt-2 h-1 w-1 shrink-0 rounded-full bg-foreground/30" />
                <span>{r}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Product triad */}
      <div className="mt-7">
        <div className="mb-3 flex items-center justify-between">
          <div className="text-[11px] uppercase tracking-widest text-foreground/40">Product Triad</div>
          <div className="text-[11px] text-foreground/30">
            est. price <span className="font-mono text-foreground/60">{trend.currency || "Rs."}{trend.estimatedPrice?.toLocaleString()}</span>
          </div>
        </div>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <ProductBlock source="underdog" product={trend.signalProducts?.underdog || null} highlight />
          <ProductBlock source="amazon" product={trend.signalProducts?.amazon || null} />
          <ProductBlock source="flipkart" product={trend.signalProducts?.flipkart || null} />
        </div>
      </div>

      <footer className="mt-6 flex items-center justify-between border-t border-foreground/5 pt-4 text-[11px] text-foreground/25">
        <span className="font-mono">
          id: {trend.id}
          {trend.supportingSignalIds && trend.supportingSignalIds.length > 0 && (
            <span className="ml-3">· {trend.supportingSignalIds.length} supporting signals</span>
          )}
        </span>
        <span>updated {formatUtc(trend.lastUpdatedAt)}</span>
      </footer>
    </article>
  );
}
