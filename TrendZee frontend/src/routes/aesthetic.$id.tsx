import { createServerFn } from "@tanstack/react-start";
import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { ArrowLeft, Plug } from "lucide-react";
import { SiteHeader } from "@/components/SiteHeader";
import { TrendCard } from "@/components/TrendCard";
import { categoryOptions, getCategoryById } from "@/lib/categories";
import { getTrendsByCategory } from "@/lib/trends.server";
import type { Aesthetic, Trend } from "@/lib/mock-data";
import { aesthetics, paletteVars } from "@/lib/mock-data";
import { useState, useEffect, useRef } from "react";

const getAestheticPageData = createServerFn({ method: "GET" })
  .validator((categoryId: string) => categoryId)
  .handler(async ({ data }): Promise<{ aesthetic: Aesthetic | null; trends: Trend[] }> => {
    const category = getCategoryById(data);
    if (!category) return { aesthetic: null, trends: [] };

    const aesthetic = aesthetics.find((item) => item.id === category.id) ?? null;
    const trends = await getTrendsByCategory(category.id);
    return { aesthetic, trends };
  });

const getMoreTrends = createServerFn({ method: "GET" })
  .validator((data: { categoryId: string; page: number }) => data)
  .handler(async ({ data }): Promise<Trend[]> => {
    return await getTrendsByCategory(data.categoryId, data.page);
  });

export const Route = createFileRoute("/aesthetic/$id")({
  loader: async ({
    params,
  }: {
    params: { id: string };
  }): Promise<{ aesthetic: Aesthetic; trends: Trend[] }> => {
    const data = await getAestheticPageData({ data: params.id });
    if (!data.aesthetic) throw notFound();
    return { aesthetic: data.aesthetic, trends: data.trends };
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
    <div className="min-h-screen flex items-center justify-center text-white/60">
      Aesthetic not found
    </div>
  ),
});

function AestheticDetail() {
  const { aesthetic, trends: initialTrends } = Route.useLoaderData() as {
    aesthetic: Aesthetic;
    trends: Trend[];
  };
  const style = paletteVars(aesthetic.colorPalette);

  const [trends, setTrends] = useState(initialTrends);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(initialTrends.length === 5);
  const [isLoading, setIsLoading] = useState(false);
  const observerTarget = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setTrends(initialTrends);
    setPage(0);
    setHasMore(initialTrends.length === 5);
  }, [initialTrends, aesthetic.id]);

  const loadMore = async () => {
    if (isLoading || !hasMore) return;
    setIsLoading(true);
    try {
      const nextPage = page + 1;
      const newTrends = await getMoreTrends({ data: { categoryId: aesthetic.id, page: nextPage } });
      if (newTrends.length > 0) {
        setTrends((prev) => [...prev, ...newTrends]);
        setPage(nextPage);
        if (newTrends.length < 5) setHasMore(false);
      } else {
        setHasMore(false);
      }
    } catch (error) {
      console.error(error);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && hasMore && !isLoading) {
          loadMore();
        }
      },
      { threshold: 0.1 }
    );

    if (observerTarget.current) {
      observer.observe(observerTarget.current);
    }

    return () => observer.disconnect();
  }, [hasMore, isLoading, page, aesthetic.id]);

  return (
    <div className="relative min-h-screen" style={style}>
      <SiteHeader />

      <section className="mx-auto max-w-5xl px-6 pb-8 pt-24">
        <Link
          to="/"
          className="inline-flex items-center gap-1.5 text-sm text-white/60 hover:text-white transition-colors"
        >
          <ArrowLeft className="h-4 w-4" /> All aesthetics
        </Link>

        <div className="mt-6 flex flex-wrap gap-2">
          {categoryOptions.map((category) => (
            <Link
              key={category.id}
              to="/aesthetic/$id"
              params={{ id: category.id }}
              className={`rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors ${
                category.id === aesthetic.id
                  ? "border-white/25 bg-white/15 text-white"
                  : "border-white/10 bg-white/5 text-white/55 hover:bg-white/10 hover:text-white"
              }`}
            >
              {category.label}
            </Link>
          ))}
        </div>

        <div className="mt-6 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1.5 backdrop-blur-xl">
          <div className="flex gap-1">
            {aesthetic.colorPalette.map((c) => (
              <div
                key={c}
                style={{ background: c, boxShadow: `0 0 14px ${c}90` }}
                className="h-2 w-2 rounded-full"
              />
            ))}
          </div>
          <span className="text-[11px] uppercase tracking-widest text-white/70">
            gen-z is wearing this rn · score {aesthetic.trendScore}
          </span>
        </div>

        <h1 className="mt-4 font-display text-4xl font-semibold tracking-tight text-white md:text-6xl">
          {aesthetic.name}
          <span className="text-gradient">.</span>
        </h1>
        <p className="mt-3 max-w-2xl text-lg text-white/65">{aesthetic.description}</p>

        <div className="mt-5 flex flex-wrap gap-1.5">
          {aesthetic.vibeTags.map((t, i) => (
            <span
              key={`${t}-${i}`}
              className="rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-xs text-white/75"
            >
              #{t}
            </span>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-5xl px-6 pb-24">
        <div className="mb-4 flex items-baseline justify-between">
          <h2 className="text-[11px] uppercase tracking-widest text-white/45">
            Trending drops · {trends.length}
          </h2>
          <span className="font-mono text-[11px] text-white/35">
            {aesthetic.signalCount.toLocaleString()} signals
          </span>
        </div>
        {trends.length === 0 ? (
          <div className="glass flex flex-col items-center gap-4 rounded-3xl px-6 py-16 text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-full border border-white/10 bg-white/5">
              <Plug className="h-5 w-5 text-white/60" />
            </div>
            <div>
              <div className="font-display text-xl text-white">No trends found</div>
              <p className="mx-auto mt-2 max-w-md text-sm text-white/55">
                Trends will appear here when MongoDB returns documents from the trends collection
                with underdog, Amazon, and Flipkart product links and images.
              </p>
            </div>
            <div className="font-mono text-[11px] uppercase tracking-widest text-white/35">
              trends.find({`{ category: "${getCategoryById(aesthetic.id)?.id ?? aesthetic.id}" }`})
            </div>
          </div>
        ) : (
          <div className="space-y-6">
            {trends.map((t) => (
              <TrendCard key={t.id} trend={t} />
            ))}
            
            {hasMore && (
              <div ref={observerTarget} className="flex justify-center py-8">
                {isLoading ? (
                  <div className="h-6 w-6 animate-spin rounded-full border-2 border-white/20 border-t-white/80" />
                ) : (
                  <div className="h-6 w-6" />
                )}
              </div>
            )}
          </div>
        )}
      </section>
    </div>
  );
}
