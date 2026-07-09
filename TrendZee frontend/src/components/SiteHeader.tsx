import { Link } from "@tanstack/react-router";
import { TLogo } from "./TLogo";

export function SiteHeader() {
  return (
    <header className="fixed top-0 left-0 right-0 z-50 border-b border-white/5 bg-black/40 backdrop-blur-xl">
      <div className="mx-auto flex max-w-[1400px] items-center justify-between px-6 py-4">
        <div className="flex flex-1 items-center">
          <Link to="/" className="flex items-center gap-2">
            <TLogo className="h-6 w-6" />
            <div className="font-display text-xl font-bold tracking-tight text-white">
              trend<span className="text-gradient">xee</span>
            </div>
          </Link>
        </div>

        <nav className="hidden gap-8 text-sm font-medium text-white/70 md:flex justify-center flex-1">
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

        <div className="flex flex-1 items-center justify-end">
          <button className="flex h-9 w-9 items-center justify-center rounded-full border border-white/20 bg-transparent text-white transition-colors hover:bg-white/10">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-user"><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
          </button>
        </div>
      </div>
    </header>
  );
}