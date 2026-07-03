import { createFileRoute } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { ArrowUpRight, Radio, Sparkles } from "lucide-react";
import { AestheticCard } from "@/components/AestheticCard";
import { Link } from "@tanstack/react-router";
import { SiteHeader } from "@/components/SiteHeader";
import { aesthetics } from "@/lib/mock-data";

export const Route = createFileRoute("/")({
  component: Index,
});

function Index() {
  const totalSignals = aesthetics.reduce((s, a) => s + a.signalCount, 0);
  const streetwear = aesthetics.find((a) => a.id === "streetwear")!;
  const rest = aesthetics.filter((a) => a.id !== "streetwear");

  return (
    <div className="relative min-h-screen">
      <SiteHeader />

      {/* Hero */}
      <section className="relative mx-auto max-w-7xl px-6 pb-16 pt-20">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.7 }}
          className="flex flex-col items-start gap-6"
        >
          <div className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1.5 backdrop-blur-xl">
            <span className="relative flex h-2 w-2">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-400" />
            </span>
            <span className="text-xs font-medium tracking-wide text-white/80">
              live · {totalSignals.toLocaleString()} signals cooking this week
            </span>
          </div>

          <h1 className="max-w-4xl font-display text-6xl font-semibold leading-[1.02] tracking-tighter text-white md:text-8xl">
            fits before they <span className="text-gradient">go viral</span>.
          </h1>

          <p className="max-w-2xl text-lg text-white/60">
            TrendZY is the AI-powered feed reading the internet&apos;s style pulse —
            we cluster Reels, TikToks and creator drops into aesthetic movements
            and hand you the underdog fit before the algorithm does.
          </p>

          <div className="mt-2 flex flex-wrap items-center gap-3">
            <button className="group flex items-center gap-2 rounded-full aesthetic-gradient px-5 py-3 text-sm font-semibold text-black transition-transform hover:scale-[1.02]">
              Explore aesthetics
              <ArrowUpRight className="h-4 w-4 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
            </button>
            <button className="rounded-full border border-white/15 bg-white/5 px-5 py-3 text-sm font-semibold text-white transition-colors hover:bg-white/10">
              How the engine works
            </button>
          </div>
        </motion.div>

        {/* Stat row */}
        <div className="mt-20 grid grid-cols-2 gap-px overflow-hidden rounded-2xl border border-white/10 bg-white/5 md:grid-cols-4">
          {[
            { label: "Signals / week", value: `${(totalSignals / 1000).toFixed(1)}k` },
            { label: "Trend score refresh", value: "hourly" },
            { label: "Marketplaces mapped", value: "3" },
            { label: "Products per trend", value: "3" },
          ].map((s) => (
            <div key={s.label} className="bg-obsidian/80 p-6">
              <div className="font-display text-3xl font-semibold text-white">{s.value}</div>
              <div className="mt-1 text-xs uppercase tracking-widest text-white/40">{s.label}</div>
            </div>
          ))}
        </div>
      </section>

      {/* Aesthetics grid */}
      <section className="relative mx-auto max-w-7xl px-6 pb-32">
        <div className="mb-10 flex items-end justify-between">
          <div>
            <div className="flex items-center gap-2 text-xs uppercase tracking-widest text-white/40">
              <Sparkles className="h-3.5 w-3.5" />
              Categories
            </div>
            <h2 className="mt-2 font-display text-4xl font-semibold tracking-tight text-white md:text-5xl">
              Pick your lane
            </h2>
            <p className="mt-2 max-w-xl text-sm text-white/50">
              We keep it clean — no mixing. Streetwear is the main event, the rest is on rotation.
            </p>
          </div>
          <div className="hidden items-center gap-2 text-xs text-white/50 md:flex">
            <Radio className="h-3.5 w-3.5 animate-pulse text-emerald-400" />
            <span className="font-mono">refreshed 4 min ago</span>
          </div>
        </div>

        {/* Featured: Streetwear */}
        <Link
          to="/aesthetic/$id"
          params={{ id: streetwear.id }}
          className="group relative mb-6 block overflow-hidden rounded-3xl glass"
        >
          <div className="absolute inset-0 aesthetic-gradient opacity-40 transition-opacity duration-700 group-hover:opacity-70"
               style={{
                 ["--aesthetic-1" as string]: streetwear.colorPalette[0],
                 ["--aesthetic-2" as string]: streetwear.colorPalette[1],
                 ["--aesthetic-3" as string]: streetwear.colorPalette[2],
                 ["--aesthetic-4" as string]: streetwear.colorPalette[3],
               } as React.CSSProperties}
          />
          <div className="absolute left-0 right-0 top-0 z-10 flex h-1">
            {streetwear.colorPalette.map((c) => (
              <div key={c} style={{ background: c }} className="flex-1" />
            ))}
          </div>
          <div className="relative grid grid-cols-1 md:grid-cols-2">
            <div className="relative aspect-[4/3] md:aspect-auto md:min-h-[420px] overflow-hidden">
              <img
                src={streetwear.heroImage}
                alt={streetwear.name}
                className="h-full w-full object-contain transition-transform duration-[1400ms] group-hover:scale-105"
              />
              <div className="absolute inset-0 bg-gradient-to-t md:bg-gradient-to-r from-black/80 via-black/20 to-transparent" />
            </div>
            <div className="relative flex flex-col justify-between p-8 md:p-10">
              <div>
                <div className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/5 px-3 py-1 text-[10px] uppercase tracking-widest text-white/70">
                  <span className="h-1.5 w-1.5 rounded-full bg-orange-400 shadow-[0_0_10px_#fb923c]" />
                  main event · what we do best
                </div>
                <h3 className="mt-4 font-display text-5xl font-semibold tracking-tight text-white md:text-6xl">
                  {streetwear.name}
                </h3>
                <p className="mt-3 max-w-md text-white/70">{streetwear.description}</p>
                <div className="mt-5 flex flex-wrap gap-1.5">
                  {streetwear.vibeTags.map((t) => (
                    <span key={t} className="rounded-full border border-white/15 bg-white/5 px-2.5 py-1 text-xs text-white/80">
                      #{t}
                    </span>
                  ))}
                </div>
              </div>
              <div className="mt-8 flex items-center justify-between text-xs text-white/50">
                <span className="font-mono">{streetwear.signalCount.toLocaleString()} signals</span>
                <span className="inline-flex items-center gap-1.5 text-white/85">
                  Enter lane <ArrowUpRight className="h-4 w-4 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
                </span>
              </div>
            </div>
          </div>
        </Link>

        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {rest.map((a, i) => (
            <AestheticCard key={a.id} aesthetic={a} index={i} />
          ))}
        </div>
      </section>

      <footer className="border-t border-white/5">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-8 text-xs text-white/40">
          <span>© 2026 TrendZY · AI trend intelligence</span>
          <span className="font-mono">v0.9.4-beta</span>
        </div>
      </footer>
    </div>
  );
}
