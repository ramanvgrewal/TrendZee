import { ExternalLink, MapPin, Radio, Sparkles, Zap } from "lucide-react";
import type { ProductMatch, Trend } from "@/lib/mock-data";

type StoreKey = "underdog" | "amazon" | "flipkart";

function formatUtc(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const pad = (n: number) => n.toString().padStart(2, "0");
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())} ${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())} UTC`;
}

const sourceLabels: Record<StoreKey, string> = {
  underdog: "The Underdog",
  amazon: "Amazon",
  flipkart: "Flipkart",
};

function ProductBlock({
  source,
  product,
  highlight,
}: {
  source: StoreKey;
  product: ProductMatch | null;
  highlight?: boolean;
}) {
  if (!product) {
    return (
      <div className="flex min-h-52 flex-col justify-between rounded-2xl border border-dashed border-white/10 bg-white/[0.02] p-4">
        <div className="text-[10px] font-semibold uppercase tracking-wider text-white/40">
          {sourceLabels[source]}
        </div>
        <div>
          <div className="font-display text-lg text-white/55">Not Available</div>
          <p className="mt-1 text-xs leading-relaxed text-white/35">
            No mapped product for this store yet.
          </p>
        </div>
      </div>
    );
  }

  const content = (
    <>
      <div className="relative aspect-square overflow-hidden">
        {product.imageUrl ? (
          <img
            src={product.imageUrl}
            alt={product.title}
            loading="lazy"
            className="h-full w-full object-cover transition-transform duration-700 group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center bg-white/[0.04] text-xs text-white/35">
            No image
          </div>
        )}
        <div className="absolute left-3 top-3 rounded-full bg-black/60 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-wider text-white backdrop-blur">
          {sourceLabels[source]}
        </div>
      </div>
      <div className="flex flex-1 flex-col gap-1 p-4">
        <div className="text-[11px] uppercase tracking-widest text-white/40">
          {product.brandName}
        </div>
        <div className="line-clamp-2 text-sm text-white/85">{product.title}</div>
        <div className="mt-2 flex items-center justify-between">
          {product.price > 0 ? (
            <span className="font-display text-lg font-semibold text-white">
              {product.currency}
              {product.price.toLocaleString()}
            </span>
          ) : (
            <span className="text-xs text-white/35">Price unavailable</span>
          )}
          {product.shopUrl && (
            <ExternalLink className="h-3.5 w-3.5 text-white/40 transition-transform group-hover:translate-x-0.5" />
          )}
        </div>
      </div>
    </>
  );

  const className = `group flex flex-col overflow-hidden rounded-2xl border transition-colors ${
    highlight
      ? "border-white/20 bg-white/[0.06]"
      : "border-white/10 bg-white/[0.02] hover:bg-white/[0.05]"
  }`;

  if (!product.shopUrl) {
    return <div className={className}>{content}</div>;
  }

  return (
    <a href={product.shopUrl} className={className}>
      {content}
    </a>
  );
}

export function TrendCard({ trend }: { trend: Trend }) {
  const availableProductCount = trend.signalProducts.reduce(
    (count, signalProduct) =>
      count +
      (signalProduct.underdog ? 1 : 0) +
      (signalProduct.amazon ? 1 : 0) +
      (signalProduct.flipkart ? 1 : 0),
    0,
  );
  const tags = [...trend.vibeTags, ...trend.aiBrandNames];

  return (
    <article className="glass rounded-3xl p-6 md:p-8">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="inline-flex h-2 w-2 rounded-full bg-emerald-400 shadow-[0_0_10px_#34d399]" />
            <span className="text-[10px] uppercase tracking-widest text-white/50">
              {trend.tier || "active drop"}
            </span>
          </div>
          <h2 className="mt-1.5 font-display text-2xl font-semibold text-white md:text-3xl">
            {trend.name}
          </h2>
          <div className="mt-2 flex flex-wrap items-center gap-2 text-[11px] uppercase tracking-widest text-white/50">
            <span className="inline-flex items-center gap-1">
              <Radio className="h-3 w-3" />
              {trend.category || "Uncategorized"}
            </span>
            {trend.signalProducts.length > 0 && (
              <>
                <span className="text-white/20">-</span>
                <span className="inline-flex items-center gap-1">
                  <Zap className="h-3 w-3" />
                  {trend.signalProducts.length} shoppable posts
                </span>
              </>
            )}
            {availableProductCount > 0 && (
              <>
                <span className="text-white/20">-</span>
                <span>{availableProductCount} product links</span>
              </>
            )}
            {trend.subcategory && (
              <>
                <span className="text-white/20">-</span>
                <span>{trend.subcategory}</span>
              </>
            )}
            {trend.indiaRelevant && (
              <>
                <span className="text-white/20">-</span>
                <span className="inline-flex items-center gap-1 text-emerald-300">
                  <MapPin className="h-3 w-3" /> India-relevant
                </span>
              </>
            )}
          </div>
        </div>
        <div className="flex flex-col items-end rounded-2xl border border-white/10 bg-white/5 px-4 py-2.5">
          <div className="text-[10px] uppercase tracking-widest text-white/50">Trend Score</div>
          <div className="font-display text-3xl font-semibold leading-none text-gradient">
            {trend.trendScore}
          </div>
        </div>
      </header>

      {tags.length > 0 && (
        <div className="mt-5 flex flex-wrap gap-1.5">
          {tags.map((tag) => (
            <span
              key={tag}
              className="rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-xs text-white/75"
            >
              #{tag}
            </span>
          ))}
        </div>
      )}

      <div className="mt-6">
        <div className="flex items-center gap-1.5 text-[11px] uppercase tracking-widest text-white/45">
          <Sparkles className="h-3 w-3" /> AI Summary
        </div>
        <p className="mt-2 leading-relaxed text-white/80">
          {trend.aiSummary || "No summary available yet."}
        </p>
        {trend.indiaRelevanceNote && (
          <p className="mt-3 text-sm leading-relaxed text-emerald-100/70">
            {trend.indiaRelevanceNote}
          </p>
        )}
      </div>

      {trend.whyTrending.length > 0 && (
        <div className="mt-5">
          <div className="text-[11px] uppercase tracking-widest text-white/45">
            Why it&apos;s trending
          </div>
          <ul className="mt-2 space-y-1.5">
            {trend.whyTrending.map((reason, index) => (
              <li key={`${reason}-${index}`} className="flex gap-2 text-sm text-white/75">
                <span className="mt-2 h-1 w-1 shrink-0 rounded-full bg-white/40" />
                <span>{reason}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="mt-7">
        <div className="mb-3 flex items-center justify-between">
          <div className="text-[11px] uppercase tracking-widest text-white/45">Product Triad</div>
          {trend.estimatedPrice > 0 && (
            <div className="text-[11px] text-white/40">
              est. price{" "}
              <span className="font-mono text-white/70">
                Rs. {trend.estimatedPrice.toLocaleString()}
              </span>
            </div>
          )}
        </div>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <ProductBlock source="underdog" product={trend.products.underdog} highlight />
          <ProductBlock source="amazon" product={trend.products.amazon} />
          <ProductBlock source="flipkart" product={trend.products.flipkart} />
        </div>
      </div>

      <footer className="mt-6 flex items-center justify-between border-t border-white/5 pt-4 text-[11px] text-white/35">
        <span className="font-mono">
          id: {trend.id}
          {trend.signalProducts.length > 0 && (
            <span className="ml-3">- {trend.signalProducts.length} signal products</span>
          )}
        </span>
        <span>updated {formatUtc(trend.lastUpdatedAt)}</span>
      </footer>
    </article>
  );
}
