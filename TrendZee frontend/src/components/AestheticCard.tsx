import { Link } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { Radio } from "lucide-react";
import type { Aesthetic } from "@/lib/mock-data";

export function AestheticCard({ aesthetic, index = 0 }: { aesthetic: Aesthetic; index?: number }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay: index * 0.05, ease: [0.2, 0.7, 0.2, 1] }}
    >
      <Link
        to="/aesthetic/$id"
        params={{ id: aesthetic.id }}
        className="group relative block overflow-hidden rounded-2xl border border-foreground/5 bg-foreground/[0.02] transition-colors hover:bg-foreground/[0.04]"
      >
        <div className="relative aspect-[4/5] w-full overflow-hidden">
          <img
            src={aesthetic.heroImage}
            alt={aesthetic.name}
            className="h-full w-full object-cover transition-transform duration-700 group-hover:scale-105"
            loading="lazy"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-foreground/80 via-foreground/20 to-transparent" />

          <div className="absolute bottom-0 left-0 right-0 p-5">
            <h3 className="font-display text-xl font-semibold leading-tight text-white">
              {aesthetic.name}
            </h3>
            <p className="mt-2 line-clamp-2 text-sm leading-snug text-white/50">
              {aesthetic.description}
            </p>

            <div className="mt-4 flex items-center gap-1.5 text-xs text-white/40">
              <Radio className="h-3.5 w-3.5" />
              <span className="font-mono">{aesthetic.signalCount.toLocaleString()} signals</span>
            </div>
          </div>
        </div>
      </Link>
    </motion.div>
  );
}
