import { Link } from "@tanstack/react-router";
import { Sparkles } from "lucide-react";

export function SiteHeader() {
  return (
    <header className="sticky top-0 z-50 border-b border-white/5 bg-black/40 backdrop-blur-xl">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
        <Link to="/" className="flex items-center gap-2.5">
          <div className="relative">
            <div className="absolute inset-0 rounded-lg aesthetic-gradient blur-md opacity-70" />
            <div className="relative flex h-8 w-8 items-center justify-center rounded-lg aesthetic-gradient">
              <Sparkles className="h-4 w-4 text-black" />
            </div>
          </div>
          <div className="font-display text-xl font-bold tracking-tight text-white">
            trend<span className="text-gradient">zy</span>
          </div>
        </Link>

        <nav className="hidden gap-8 text-sm text-white/70 md:flex">
          <Link to="/" activeProps={{ className: "text-white" }} className="hover:text-white transition-colors">
            Aesthetics
          </Link>
          <Link
            to="/aesthetic/$id"
            params={{ id: "streetwear" }}
            activeProps={{ className: "text-white" }}
            className="hover:text-white transition-colors"
          >
            Trends
          </Link>
        </nav>
      </div>
    </header>
  );
}