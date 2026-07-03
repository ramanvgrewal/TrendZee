import { Link } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { TrendingUp, Radio } from "lucide-react";
import type { Aesthetic } from "@/lib/mock-data";
import { paletteVars } from "@/lib/mock-data";

export function AestheticCard({ aesthetic, index = 0 }: { aesthetic: Aesthetic; index?: number }) {
  const style = paletteVars(aesthetic.colorPalette);

  return (
    <motion.div
      initial={{ opacity: 0, y: 24 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay: index * 0.06, ease: [0.2, 0.7, 0.2, 1] }}
      style={style}
      className="group relative"
    >
      <Link
        to="/aesthetic/$id"
        params={{ id: aesthetic.id }}
        className="relative block overflow-hidden rounded-3xl glass hover:ring-aesthetic transition-all duration-500"
      >
        {/* Ambient gradient wash */}
        <div className="absolute inset-0 aesthetic-gradient opacity-30 group-hover:opacity-60 transition-opacity duration-700" />

        {/* Palette strip */}
        <div className="absolute top-0 left-0 right-0 flex h-1 z-10">
          {aesthetic.colorPalette.map((c) => (
            <div key={c} style={{ background: c }} className="flex-1" />
          ))}
        </div>

        <div className="relative aspect-[4/5] w-full overflow-hidden">
          <img
            src={aesthetic.heroImage}
            alt={aesthetic.name}
            className="h-full w-full object-contain transition-transform duration-[1400ms] group-hover:scale-110"
            loading="lazy"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-black/30 to-transparent" />

          {/* Palette swatches floating */}
          <div className="absolute top-4 right-4 flex gap-1.5">
            {aesthetic.colorPalette.map((c) => (
              <div
                key={c}
                style={{ background: c, boxShadow: `0 0 20px ${c}80` }}
                className="h-2.5 w-2.5 rounded-full ring-1 ring-white/30"
              />
            ))}
          </div>

          {/* Score badge */}
          <div className="absolute top-4 left-4 flex items-center gap-1.5 rounded-full glass-strong px-3 py-1.5">
            <TrendingUp className="h-3.5 w-3.5" style={{ color: aesthetic.colorPalette[0] }} />
            <span className="text-xs font-semibold tracking-tight">{aesthetic.trendScore}</span>
          </div>

          {/* Content overlay */}
          <div className="absolute bottom-0 left-0 right-0 p-5">
            <h3 className="font-display text-2xl font-semibold leading-tight text-white">
              {aesthetic.name}
            </h3>
            <p className="mt-2 line-clamp-2 text-sm leading-snug text-white/70">
              {aesthetic.description}
            </p>

            <div className="mt-4 flex items-center justify-between">
              <div className="flex items-center gap-1.5 text-xs text-white/60">
                <Radio className="h-3.5 w-3.5 animate-pulse" style={{ color: aesthetic.colorPalette[2] }} />
                <span className="font-mono">{aesthetic.signalCount.toLocaleString()} signals</span>
              </div>
              <div className="flex gap-1">
                {aesthetic.vibeTags.slice(0, 2).map((t) => (
                  <span
                    key={t}
                    className="rounded-full border border-white/15 bg-white/5 px-2 py-0.5 text-[10px] uppercase tracking-wider text-white/70"
                  >
                    {t}
                  </span>
                ))}
              </div>
            </div>
          </div>
        </div>
      </Link>
    </motion.div>
  );
}