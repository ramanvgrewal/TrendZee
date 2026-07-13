import { createFileRoute } from "@tanstack/react-router";
import { motion, AnimatePresence } from "framer-motion";
import { ArrowUpRight, X } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "@tanstack/react-router";
import { SiteHeader } from "@/components/SiteHeader";
import { aesthetics } from "@/lib/mock-data";

export const Route = createFileRoute("/")({
  loader: async () => {
    let baseUrl = import.meta.env?.VITE_API_BASE_URL ?? (typeof process !== 'undefined' ? process.env.VITE_API_BASE_URL : undefined) ?? "http://127.0.0.1:8080";
    if (typeof window === 'undefined' && !baseUrl.startsWith('http')) {
      baseUrl = "https://api.trendxee.com";
    }
    const rotationMap: Record<string, { brand: string; title: string; image: string }[]> = {};
    try {
      await Promise.all(
        aesthetics.map(async (a) => {
          let queryCategory = a.id;
          if (a.id === "upper") queryCategory = "shirts";
          else if (a.id === "gym") queryCategory = "sportswear";
          const res = await fetch(`${baseUrl}/api/v2/trends?category=${queryCategory}&size=15`);
          if (res.ok) {
            const data = await res.json();
            const trends = data.content || [];
            rotationMap[a.id] = trends
              .filter((t: any) => t.signalProducts?.underdog?.imageUrl)
              .slice(0, 3)
              .map((t: any) => ({
                brand: t.signalProducts.underdog.brandName || "",
                title: t.signalProducts.underdog.title || "",
                image: t.signalProducts.underdog.imageUrl,
              }));
          } else {
            rotationMap[a.id] = [];
          }
        }),
      );
    } catch (e) {
      console.error("Failed to fetch rotations", e);
    }
    return { rotationMap };
  },
  component: Index,
});

function Index() {
  const { rotationMap } = Route.useLoaderData();

  const laneEmoji: Record<string, string> = {
    streetwear: "👕",
    upper: "🎽",
    sneakers: "👟",
    bottoms: "👖",
    accessories: "⛓️",
    gym: "💪",
    fragrances: "🧴",
  };

  const streetwear = aesthetics.find((a) => a.id === "streetwear")!;
  const rest = aesthetics.filter((a) => a.id !== "streetwear");

  return (
    <div className="relative min-h-screen">
      <SiteHeader />

      {/* Hero */}
      <section className="relative mx-auto max-w-7xl px-6 pb-24 pt-14 md:pt-20">
        <div className="grid grid-cols-1 items-center gap-14 lg:grid-cols-[1.05fr_0.95fr]">
          {/* Left column */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6 }}
            className="flex flex-col items-start gap-7"
          >
            <LiveBadge signals={streetwear.signalCount} />

            <h1 className="font-display text-[64px] font-bold leading-[0.95] tracking-tight text-foreground md:text-[96px]">
              Fits before they
              <br />
              <em className="font-serif italic font-bold uppercase text-[oklch(0.55_0.09_50)]">
                Go Viral.
              </em>
            </h1>

            <p className="max-w-md text-base leading-relaxed text-foreground/60">
              TrendXee clusters millions of creator signals into aesthetic
              movements and finds the underdog fit before the algorithm does.
            </p>

            <div className="mt-1 flex flex-wrap items-center gap-3">
              <Link
                to="/aesthetic/$id"
                params={{ id: "streetwear" }}
                className="group inline-flex items-center gap-2 rounded-full bg-foreground px-6 py-3.5 text-[11px] font-bold uppercase tracking-[0.22em] text-background shadow-[0_10px_30px_-10px_oklch(0.22_0.03_35/60%)] transition-transform hover:-translate-y-0.5"
              >
                Explore Lanes
                <ArrowUpRight className="h-4 w-4 transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
              </Link>
              <a 
                href="#about"
                className="rounded-full border border-foreground/20 bg-background px-6 py-3.5 text-[11px] font-bold uppercase tracking-[0.22em] text-foreground transition-colors hover:border-foreground/40"
              >
                How It Works
              </a>
            </div>
          </motion.div>

          {/* Polaroid */}
          <HeroPolaroid a={streetwear} />
        </div>
      </section>

      {/* Categories */}
      <section id="archive" className="relative mx-auto max-w-7xl px-6 pb-32">
        <div className="mb-10 flex items-end justify-between border-t border-foreground/10 pt-10">
          <div>
            <div className="font-mono text-[10px] uppercase tracking-[0.3em] text-foreground/40">
              The Collection
            </div>
            <h2 className="mt-2 font-display text-4xl font-bold tracking-tight text-foreground md:text-6xl">
              Curated <em className="italic font-semibold text-[oklch(0.55_0.09_50)]">lanes</em>.
            </h2>
          </div>
          <Link
            to="/aesthetic/$id"
            params={{ id: "streetwear" }}
            className="hidden items-center gap-2 rounded-full border border-foreground/20 bg-background px-5 py-2.5 text-[11px] font-bold uppercase tracking-[0.22em] text-foreground transition-colors hover:border-foreground/40 md:inline-flex"
          >
            View all lanes
            <ArrowUpRight className="h-3.5 w-3.5" />
          </Link>
        </div>

        {/* Horizontal numbered lane rail */}
        <div className="-mx-6 overflow-x-auto px-6 pb-4 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          <div className="flex gap-4">
            {[streetwear, ...rest].map((a, i) => (
              <LaneCard
                key={a.id}
                a={a}
                rotationImages={
                  rotationMap?.[a.id]?.length ? rotationMap[a.id] : a.underdogRotation || []
                }
                emoji={laneEmoji[a.id] ?? "✦"}
                index={i}
                number={String(i + 1).padStart(2, "0")}
              />
            ))}
          </div>
        </div>
      </section>

      {/* About Section */}
      <section id="about" className="relative mx-auto max-w-4xl px-6 pb-32 pt-16 text-center">
        <h2 className="font-display text-4xl font-bold tracking-tight text-foreground md:text-5xl mb-6">
          About Trend<em className="italic font-semibold text-[oklch(0.55_0.09_50)]">xee</em>
        </h2>
        <p className="mb-12 text-xl font-medium text-foreground/80">
          Stop searching. Start discovering.
        </p>

        <div className="space-y-6 text-base leading-relaxed text-foreground/70 text-left mx-auto max-w-3xl">
          <p>
            Ever see a fire fit or a trending aesthetic on Instagram and wonder, "Where did they even get that?" Trendxee is your answer. We bridge the gap between what Gen Z is actually hyping up online and the hidden-gem brands creating those viral pieces.
          </p>
          <p>
            If you want to know what is actually popping off without digging through endless hashtags, you are in the right place.
          </p>
        </div>

        <div className="mt-16 text-left mx-auto max-w-3xl">
          <h3 className="font-display text-2xl font-bold text-foreground mb-8">What You Can Expect From Us</h3>
          <ul className="space-y-8">
            <li className="flex gap-5">
              <span className="flex-shrink-0 mt-1 h-7 w-7 rounded-full bg-[oklch(0.55_0.09_50/15%)] flex items-center justify-center text-[oklch(0.55_0.09_50)] font-bold text-sm">1</span>
              <div>
                <strong className="block text-foreground mb-1.5 text-lg">No More Gatekeeping</strong>
                <span className="text-foreground/70 leading-relaxed block">We track the exact trends and streetwear signals blowing up right now, so you always know what is genuinely popular on the internet.</span>
              </div>
            </li>
            <li className="flex gap-5">
              <span className="flex-shrink-0 mt-1 h-7 w-7 rounded-full bg-[oklch(0.55_0.09_50/15%)] flex items-center justify-center text-[oklch(0.55_0.09_50)] font-bold text-sm">2</span>
              <div>
                <strong className="block text-foreground mb-1.5 text-lg">Spotlight on Underdog Brands</strong>
                <span className="text-foreground/70 leading-relaxed block">Skip the mainstream corporate giants. We uncover those cool, premium, and authentic indie brands that dominate Instagram but are impossible to find on generic e-commerce sites.</span>
              </div>
            </li>
            <li className="flex gap-5">
              <span className="flex-shrink-0 mt-1 h-7 w-7 rounded-full bg-[oklch(0.55_0.09_50/15%)] flex items-center justify-center text-[oklch(0.55_0.09_50)] font-bold text-sm">3</span>
              <div>
                <strong className="block text-foreground mb-1.5 text-lg">100% Genuine, Zero Middlemen</strong>
                <span className="text-foreground/70 leading-relaxed block">We do not sit in the middle of your purchase. When you find something you like, we connect you straight to the brand’s original Shopify or official website. You get the authentic product, and the original creators get your full support.</span>
              </div>
            </li>
          </ul>
        </div>

        <div className="mt-20 text-left mx-auto max-w-3xl">
          <h3 className="font-display text-2xl font-bold text-foreground mb-8">How to Use Trendxee</h3>
          <ul className="space-y-6">
            <li className="relative pl-7 before:absolute before:left-0 before:top-2.5 before:h-2 before:w-2 before:rounded-full before:bg-[oklch(0.55_0.09_50)]">
              <strong className="text-foreground text-[17px]">Catch the Wave:</strong> <span className="text-foreground/70 ml-1">Scroll through our platform to discover exactly what Gen Z is currently hyping up across social media.</span>
            </li>
            <li className="relative pl-7 before:absolute before:left-0 before:top-2.5 before:h-2 before:w-2 before:rounded-full before:bg-[oklch(0.55_0.09_50)]">
              <strong className="text-foreground text-[17px]">Hit the Collection Lanes:</strong> <span className="text-foreground/70 ml-1">Find those premium, aesthetic-matching brands waiting for you right in our collection lanes.</span>
            </li>
            <li className="relative pl-7 before:absolute before:left-0 before:top-2.5 before:h-2 before:w-2 before:rounded-full before:bg-[oklch(0.55_0.09_50)]">
              <strong className="text-foreground text-[17px]">Just Here for the Vibes?:</strong> <span className="text-foreground/70 ml-1">Even if you don't want to buy anything today, no stress. Just jump into the streetwear section and check the trends. The internet moves fast—old trends disappear and new ones drop every single day. Stay plugged in so you never miss a beat.</span>
            </li>
            <li className="relative pl-7 before:absolute before:left-0 before:top-2.5 before:h-2 before:w-2 before:rounded-full before:bg-[oklch(0.55_0.09_50)]">
              <strong className="text-foreground text-[17px]">Cop it Directly:</strong> <span className="text-foreground/70 ml-1">When you are ready to buy, click straight through to the brand’s original store to get your gear with zero e-commerce middlemen in the way.</span>
            </li>
          </ul>
        </div>
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

function LiveBadge({ signals }: { signals: number }) {
  return (
    <div className="inline-flex items-start gap-3">
      <span className="mt-[2px] inline-flex items-center gap-1.5 rounded-full bg-background px-2.5 py-1 ring-1 ring-foreground/15 text-[11px] font-semibold uppercase tracking-[0.2em] text-foreground/70">
        <span className="relative flex h-1.5 w-1.5">
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-[oklch(0.72_0.09_55)] opacity-75" />
          <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-[oklch(0.72_0.09_55)]" />
        </span>
        Live
      </span>
      <div className="flex flex-col items-start gap-0.5">
        <span className="text-[15px] font-bold tracking-wide text-foreground/90">Pipeline active.</span>
        <span className="text-[13px] text-foreground/50 normal-case tracking-normal">
          Fresh drops coming everyday.
        </span>
      </div>
    </div>
  );
}

function HeroPolaroid({ a }: { a: (typeof aesthetics)[number] }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 24, rotate: -1 }}
      animate={{ opacity: 1, y: 0, rotate: 2.5 }}
      transition={{ duration: 0.7, ease: [0.2, 0.7, 0.2, 1] }}
      className="relative mx-auto w-full max-w-md"
    >
      {/* Tape */}
      <span className="absolute -top-4 left-1/2 z-10 h-8 w-24 -translate-x-1/2 rotate-[-4deg] rounded-sm bg-[oklch(0.72_0.09_55/85%)] shadow-md" />

      <Link
        to="/aesthetic/$id"
        params={{ id: a.id }}
        className="group block rounded-[6px] bg-[oklch(0.97_0.008_75)] p-3 shadow-[0_30px_60px_-25px_oklch(0.22_0.03_35/45%)] transition-transform duration-500 hover:-translate-y-1 hover:rotate-0"
      >
        <div className="relative overflow-hidden">
          <img
            src={a.heroImage}
            alt={a.name}
            className="aspect-[4/5] w-full object-cover transition-transform duration-700 group-hover:scale-[1.03]"
          />
        </div>
        <div className="flex items-end justify-between px-2 pt-4 pb-2">
          <div>
            <div className="border-b-2 border-black/80 pb-1 font-display text-sm font-bold uppercase tracking-[0.2em] text-black">
              {a.name}
            </div>
            <div className="mt-1.5 font-mono text-[10px] uppercase tracking-[0.2em] text-black/50">
              {a.signalCount.toLocaleString()} signals
            </div>
          </div>
          <div className="flex items-end gap-1.5 font-mono text-[10px] uppercase tracking-[0.2em] text-black/60">
            Score
            <span className="font-display text-2xl italic font-bold text-[oklch(0.55_0.09_50)]">
              {a.trendScore}
            </span>
          </div>
        </div>
      </Link>

      <span className="pointer-events-none absolute -bottom-2 right-4 flex h-8 w-8 items-center justify-center rounded-full bg-black text-white">
        <ArrowUpRight className="h-4 w-4" />
      </span>
    </motion.div>
  );
}

function LaneCard({
  a,
  rotationImages,
  emoji,
  index,
  number,
}: {
  a: (typeof aesthetics)[number];
  rotationImages: { brand: string; title: string; image: string }[];
  emoji: string;
  index: number;
  number: string;
}) {
  const rotation = rotationImages;
  const [idx, setIdx] = useState(0);
  useEffect(() => {
    if (rotation.length < 2) return;
    const t = setInterval(() => setIdx((i) => (i + 1) % rotation.length), 2800 + index * 300);
    return () => clearInterval(t);
  }, [rotation.length, index]);

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay: index * 0.05 }}
      className="w-[260px] shrink-0 sm:w-[280px]"
    >
      <Link
        to="/aesthetic/$id"
        params={{ id: a.id }}
        className="group relative block overflow-hidden rounded-2xl border border-foreground/10 bg-foreground/[0.02] transition-all hover:-translate-y-1 hover:shadow-[0_20px_40px_-20px_oklch(0.30_0.04_40/40%)]"
      >
        <div className="relative aspect-[3/4] overflow-hidden bg-foreground/5">
          {rotation.length > 0 ? (
            rotation.map((r, i) => (
              <img
                key={r.image}
                src={r.image}
                alt={r.title}
                loading="lazy"
                className={`absolute inset-0 h-full w-full object-cover transition-opacity duration-700 ${
                  i === idx ? "opacity-100" : "opacity-0"
                }`}
              />
            ))
          ) : (
            <img
              src={a.heroImage}
              alt={a.name}
              className="h-full w-full object-cover transition-transform duration-700 group-hover:scale-105"
              loading="lazy"
            />
          )}
          <div className="absolute inset-0 bg-gradient-to-t from-[oklch(0.15_0.02_35/95%)] via-[oklch(0.15_0.02_35/30%)] to-transparent" />

          {/* number */}
          <div className="absolute left-4 top-3 font-mono text-[10px] font-semibold uppercase tracking-[0.2em] text-[oklch(0.96_0.015_75/80%)]">
            {number}
          </div>

          {/* emoji chip */}
          <div className="absolute right-3 top-3">
            <span
              className="inline-flex h-8 w-8 items-center justify-center rounded-full text-sm shadow-sm"
              style={{ background: "oklch(0.955 0.012 75 / 95%)" }}
            >
              {emoji}
            </span>
          </div>

          {/* bottom info */}
          <div className="absolute inset-x-0 bottom-0 p-4">
            <h3 className="font-display text-2xl font-bold capitalize text-[oklch(0.97_0.015_75)]">
              {a.name.toLowerCase()}
            </h3>
            <div className="mt-1 flex items-center justify-between font-mono text-[10px] uppercase tracking-[0.18em] text-[oklch(0.97_0.015_75/70%)]">
              <span>{a.signalCount.toLocaleString()} signals</span>
              <span className="text-[oklch(0.78_0.10_55)]">score {a.trendScore}</span>
            </div>
          </div>
        </div>
      </Link>
    </motion.div>
  );
}


