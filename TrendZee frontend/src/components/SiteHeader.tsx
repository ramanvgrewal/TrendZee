import { Link } from "@tanstack/react-router";
import { Zap, Moon, Sun, LogOut, Archive } from "lucide-react";
import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";

export function SiteHeader() {
  const [dark, setDark] = useState(true);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  useEffect(() => {
    const stored = localStorage.getItem("trendxee-theme");
    // Default to dark theme; only go light if the user explicitly chose it.
    const isDark = stored ? stored === "dark" : true;
    setDark(isDark);
    document.documentElement.classList.toggle("dark", isDark);
    
    // Check auth
    setIsAuthenticated(!!localStorage.getItem("token"));
  }, []);

  const handleLogout = () => {
    localStorage.removeItem("token");
    setIsAuthenticated(false);
  };

  const { data: user } = useQuery({
    queryKey: ['currentUser', isAuthenticated],
    queryFn: async () => {
      if (!isAuthenticated) return null;
      const baseUrl = import.meta.env?.VITE_API_BASE_URL || (typeof process !== 'undefined' && process.env.VITE_API_BASE_URL) || (import.meta.env?.DEV ? "http://localhost:8080" : "");
      const res = await fetch(`${baseUrl}/api/users/me`);
      if (!res.ok) {
        if (res.status === 401) {
          handleLogout();
        }
        return null;
      }
      return res.json();
    },
    enabled: isAuthenticated,
  });
  
  const toggle = () => {
    const next = !dark;
    setDark(next);
    document.documentElement.classList.toggle("dark", next);
    localStorage.setItem("trendxee-theme", next ? "dark" : "light");
  };

  const handleLogin = () => {
    const baseUrl = import.meta.env?.VITE_API_BASE_URL || (typeof process !== 'undefined' && process.env.VITE_API_BASE_URL) || (import.meta.env?.DEV ? "http://localhost:8080" : "https://api.trendxee.com");
    window.location.href = `${baseUrl}/oauth2/authorization/google`;
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
          {isAuthenticated && (
            <Link to="/archive" activeProps={{ className: "text-foreground" }} className="transition-colors hover:text-foreground">
              Archive
            </Link>
          )}
          <a href="#engine" className="transition-colors hover:text-foreground">Engine</a>
          <a href="#about" className="transition-colors hover:text-foreground">About</a>
        </nav>

        <div className="flex items-center gap-2">
          {isAuthenticated && (
            <Link 
              to="/archive"
              aria-label="Archive"
              className="flex md:hidden h-9 w-9 items-center justify-center rounded-full border border-foreground/15 bg-background text-foreground transition-transform hover:-translate-y-0.5 hover:shadow-md"
            >
              <Archive className="h-4 w-4" />
            </Link>
          )}
          <button
            onClick={toggle}
            aria-label="Toggle theme"
            className="flex h-9 w-9 items-center justify-center rounded-full border border-foreground/15 bg-background text-foreground transition-transform hover:-translate-y-0.5 hover:shadow-md"
          >
            {dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          </button>
          
          {isAuthenticated ? (
            <button 
              onClick={handleLogout}
              title="Click to logout"
              className="group flex items-center gap-2 rounded-full border border-foreground/15 bg-background py-1 pl-1 pr-4 text-[11px] font-semibold uppercase tracking-[0.22em] text-foreground transition-all hover:-translate-y-0.5 hover:border-red-500/30 hover:shadow-md"
            >
              {user?.picture ? (
                <img src={user.picture} alt={user?.name || "User"} className="h-7 w-7 rounded-full object-cover group-hover:hidden" />
              ) : (
                <span className="flex h-7 w-7 items-center justify-center rounded-full bg-foreground font-display text-xs italic text-background group-hover:hidden">
                  {user?.name ? user.name.charAt(0).toUpperCase() : "T"}
                </span>
              )}
              <div className="hidden h-7 w-7 items-center justify-center rounded-full bg-red-500/10 text-red-500 group-hover:flex">
                <LogOut className="h-3.5 w-3.5" />
              </div>
              <span className="group-hover:text-red-500">{user?.name ? user.name.split(' ')[0] : "Profile"}</span>
            </button>
          ) : (
            <button 
              onClick={handleLogin}
              className="flex items-center gap-2 rounded-full bg-foreground py-1.5 px-4 text-[11px] font-semibold uppercase tracking-[0.22em] text-background transition-transform hover:-translate-y-0.5 hover:shadow-md"
            >
              Login
            </button>
          )}
        </div>
      </div>
    </header>
  );
}
