import { Link } from "@tanstack/react-router";
import { Zap, Moon, Sun } from "lucide-react";
import { useEffect, useState } from "react";

export function SiteHeader() {
  const [dark, setDark] = useState(true);
  useEffect(() => {
    const stored = localStorage.getItem("trendxee-theme");
    // Default to dark theme; only go light if the user explicitly chose it.
    const isDark = stored ? stored === "dark" : true;
    setDark(isDark);
    document.documentElement.classList.toggle("dark", isDark);
  }, []);
  const toggle = () => {
    const next = !dark;
    setDark(next);
    document.documentElement.classList.toggle("dark", next);
    localStorage.setItem("trendxee-theme", next ? "dark" : "light");
  };
  return (
    <header className="sticky top-0 z-50 border-b border-foreground/10 bg-background/80 backdrop-blur-xl">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-6 px-6 py-4">
        <Link to="/" className="flex items-center gap-2">
          <span className="flex h-8 w-8 items-center justify-center">
            <Zap className="h-6 w-6 fill-foreground text-foreground" strokeWidth={1.5} />
          </span>
          <div className="font-display text-3xl leading-none tracking-tight text-foreground">
            <span className="font-bold">Trend</span>
            <em className="italic font-semibold text-[oklch(0.55_0.09_50)]">Xee</em>
          </div>
        </Link>

        <nav className="hidden items-center gap-9 text-[11px] font-semibold uppercase tracking-[0.22em] text-foreground/70 md:flex">
          <Link to="/" activeProps={{ className: "text-foreground" }} className="transition-colors hover:text-foreground">
            Lanes
          </Link>
          <a href="#archive" className="transition-colors hover:text-foreground">Archive</a>
          <a href="#engine" className="transition-colors hover:text-foreground">Engine</a>
          <a href="#about" className="transition-colors hover:text-foreground">About</a>
        </nav>

        <div className="flex items-center gap-2">
          <button
            onClick={toggle}
            aria-label="Toggle theme"
            className="flex h-9 w-9 items-center justify-center rounded-full border border-foreground/15 bg-background text-foreground transition-transform hover:-translate-y-0.5 hover:shadow-md"
          >
            {dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          </button>
          <button className="flex items-center gap-2 rounded-full border border-foreground/15 bg-background py-1 pl-1 pr-4 text-[11px] font-semibold uppercase tracking-[0.22em] text-foreground transition-transform hover:-translate-y-0.5 hover:shadow-md">
            <span className="flex h-7 w-7 items-center justify-center rounded-full bg-foreground font-display text-xs italic text-background">T</span>
            Profile
          </button>
        </div>
      </div>
    </header>
  );
}
