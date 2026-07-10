import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { ArrowLeft, Radio } from "lucide-react";
import { motion } from "framer-motion";
import { SiteHeader } from "@/components/SiteHeader";
import { TrendCard } from "@/components/TrendCard";
import type { Aesthetic, Trend, TrendSlice } from "@/lib/mock-data";
import { aesthetics } from "@/lib/mock-data";

export const Route = createFileRoute("/aesthetic/$id")({
  loader: async ({ params }): Promise<{ aesthetic: Aesthetic; trends: Trend[] }> => {
    const aesthetic = aesthetics.find((a) => a.id === params.id);
    if (!aesthetic) throw notFound();
    
    let queryCategory = params.id;
    if (params.id === 'upper') queryCategory = 'shirts';
    else if (params.id === 'gym') queryCategory = 'sportswear';
    const baseUrl = typeof window !== 'undefined' ? '' : 'http://localhost:8080';
    try {
      const res = await fetch(`${baseUrl}/api/v2/trends?category=${queryCategory}&size=20`);
      let trends: Trend[] = [];
      if (res.ok) {
        const data = await res.json() as TrendSlice;
        trends = data.content || [];
      }
      return { aesthetic, trends };
    } catch (e) {
      console.error(e);
      return { aesthetic, trends: [] };
    }
  },
  head: ({ loaderData }) => ({
    meta: loaderData
      ? [
          { title: `${loaderData.aesthetic.name} — TrendXee` },
          { name: "description", content: loaderData.aesthetic.description },
        ]
      : [],
  }),
  component: AestheticDetail,
  notFoundComponent: () => (
    <div className="min-h-screen flex items-center justify-center text-foreground/40">Aesthetic not found</div>
  ),
});

function AestheticDetail() {
  const { aesthetic, trends } = Route.useLoaderData() as {
    aesthetic: Aesthetic;
    trends: Trend[];
  };
  const laneIndex = aesthetics.findIndex((x) => x.id === aesthetic.id);
  const number = String(Math.max(laneIndex, 0) + 1).padStart(2, "0");
  const [firstWord, ...restWords] = aesthetic.name.split(" ");
  const rest = restWords.join(" ");

  return (
    <div className="relative min-h-screen">
      <SiteHeader />

      <section className="relative mx-auto max-w-7xl px-6 pb-16 pt-10 md:pt-14">
        <div className="flex flex-wrap items-center gap-6 md:gap-8">
          <Link
            to="/"
            className="inline-flex shrink-0 items-center gap-1.5 font-mono text-[11px] uppercase tracking-[0.22em] text-foreground/50 transition-colors hover:text-foreground"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> All lanes
          </Link>
          
          <div className="hidden h-3.5 w-px shrink-0 bg-foreground/15 md:block" />

          <div className="hidden flex-wrap items-center gap-4 font-mono text-[10px] uppercase tracking-[0.2em] text-foreground/40 md:flex">
            {aesthetics.map((a) => (
              <Link
                key={a.id}
                to="/aesthetic/$id"
                params={{ id: a.id }}
                className={`transition-colors hover:text-foreground ${
                  a.id === aesthetic.id ? "text-foreground font-semibold" : ""
                }`}
              >
                {a.name}
              </Link>
            ))}
          </div>
        </div>

        <div className="mt-8">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6 }}
            className="flex flex-col items-start gap-6"
          >
            <div className="flex flex-wrap items-center gap-3 font-mono text-[11px] font-semibold uppercase tracking-[0.28em] text-foreground/50">
              <span className="rounded-full bg-foreground px-2.5 py-1 text-background">Lane {number}</span>
              <span className="inline-flex items-center gap-1.5 text-foreground/60">
                <Radio className="h-3 w-3" />
                {aesthetic.signalCount.toLocaleString()} signals
              </span>
              <span className="text-foreground/25">·</span>
              <span className="text-[oklch(0.55_0.09_50)]">Score {aesthetic.trendScore}</span>
            </div>

            <h1 className="font-display text-[64px] font-bold leading-[0.95] tracking-tight text-foreground md:text-[112px]">
              {rest ? (
                <>
                  {firstWord?.toLowerCase()}
                  <br />
                  <em className="font-serif italic font-bold uppercase text-[oklch(0.55_0.09_50)]">
                    {rest}
                  </em>
                </>
              ) : (
                <span className="capitalize">{firstWord?.toLowerCase()}</span>
              )}
            </h1>

            <p className="max-w-2xl text-lg leading-relaxed text-foreground/60">
              {aesthetic.description}
            </p>

            <div className="flex flex-wrap gap-1.5">
              {aesthetic.vibeTags.map((t) => (
                <span
                  key={t}
                  className="rounded-full border border-foreground/15 bg-background px-3 py-1 font-mono text-[10px] uppercase tracking-[0.18em] text-foreground/60"
                >
                  #{t}
                </span>
              ))}
            </div>
          </motion.div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-6 pb-32">
        <div className="mb-10 flex items-end justify-between border-t border-foreground/10 pt-10">
          <div>
            <div className="font-mono text-[10px] uppercase tracking-[0.3em] text-foreground/40">
              The Drops · {trends.length}
            </div>
            <h2 className="mt-2 font-display text-4xl font-bold tracking-tight text-foreground md:text-6xl">
              Trending <em className="italic font-semibold text-[oklch(0.55_0.09_50)]">now</em>.
            </h2>
          </div>
          <span className="hidden font-mono text-[11px] uppercase tracking-[0.22em] text-foreground/40 md:inline">
            {aesthetic.signalCount.toLocaleString()} signals
          </span>
        </div>
        {trends.length === 0 ? (
          <div className="flex flex-col items-center gap-4 rounded-2xl border border-foreground/5 bg-foreground/[0.02] px-6 py-16 text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-full border border-foreground/10 bg-foreground/5">
              <span className="text-xl">🔌</span>
            </div>
            <div>
              <div className="font-display text-xl text-foreground">Waiting for the backend</div>
              <p className="mx-auto mt-2 max-w-md text-sm text-foreground/50">
                Trends for this aesthetic will appear here once the TrendXee engine
                is wired up.
              </p>
            </div>
          </div>
        ) : (
          <div className="space-y-6">
            {trends.map((t) => (
              <TrendCard key={t.id} trend={t} />
            ))}
          </div>
        )}
      </section>

      <footer className="border-t border-foreground/10">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-8 text-xs text-foreground/40">
          <span>© 2026 TrendXee · trendxee.com</span>
          <span className="font-mono">v0.9.4-beta</span>
        </div>
      </footer>
    </div>
  );
}
