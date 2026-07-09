import { createFileRoute } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { ArrowUpRight, Maximize, Filter, Link as LinkIcon, MonitorPlay } from "lucide-react";
import { Link } from "@tanstack/react-router";
import { SiteHeader } from "@/components/SiteHeader";
import { aesthetics } from "@/lib/mock-data";

export const Route = createFileRoute("/")({
  component: Index,
});

function Index() {
  const streetwear = aesthetics.find((a) => a.id === "streetwear")!;
  const rest = aesthetics.filter((a) => a.id !== "streetwear");

  return (
    <div className="relative min-h-screen bg-[#050505] text-white overflow-x-hidden font-sans">
      <SiteHeader />

      {/* Hero */}
      <section className="relative mx-auto max-w-[1400px] px-6 pb-2 pt-24 md:pt-28">
        <motion.div
          initial={{ opacity: 1, y: 0 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.7 }}
          className="flex flex-col items-start gap-6 max-w-2xl"
        >
          <div className="inline-flex items-center gap-2 rounded-full border border-cyan-500/30 bg-cyan-950/30 px-3 py-1 shadow-[0_0_15px_rgba(6,182,212,0.3)]">
            <span className="h-2 w-2 rounded-full bg-cyan-400 shadow-[0_0_8px_#22d3ee]" />
            <span className="text-[10px] font-bold uppercase tracking-widest text-cyan-100">
              LIVE FEED
            </span>
          </div>

          <h1 className="font-display text-7xl font-black leading-[0.9] tracking-tighter text-white md:text-[100px]">
            fits before they <br />
            <span className="text-gradient drop-shadow-[0_0_20px_rgba(244,114,182,0.6)] uppercase italic">
              go viral.
            </span>
          </h1>

          <p className="max-w-xl text-lg text-white/60 leading-relaxed font-medium">
            TrendXee is the AI-powered feed reading the internet's style pulse —
            we cluster Reels, TikToks and creator drops into aesthetic movements
            and hand you the underdog fit before the algorithm does.
          </p>

          <div className="mt-2 flex flex-wrap items-center gap-4">
            <Link to="/aesthetic/$id" params={{ id: "streetwear" }} className="group flex items-center gap-2 rounded-full bg-white/10 border border-purple-500/50 px-6 py-3 text-sm font-bold text-white shadow-[0_0_20px_rgba(168,85,247,0.4)] transition-all hover:bg-white/20">
              enter the arcade
              <ArrowUpRight className="h-4 w-4 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
            </Link>
            <button onClick={() => alert("we aint spoil the secret sauce")} className="rounded-full border border-white/20 bg-transparent px-6 py-3 text-sm font-bold text-white transition-colors hover:bg-white/10">
              How the engine works
            </button>
          </div>
          
          <div className="mt-8 text-[10px] font-mono font-bold tracking-widest text-white/30 uppercase">
            TRENDXEE.COM - V0.9.4-BETA
          </div>
        </motion.div>
      </section>

      {/* Arcade Lanes */}
      <section className="relative mx-auto max-w-[1400px] px-6 pb-20 pt-2">
        <div className="flex flex-col items-center text-center mb-10">
          <div className="inline-flex items-center gap-2 rounded-full border border-white/10 px-4 py-1.5 mb-6">
            <span className="text-[10px] font-bold uppercase tracking-widest text-white/50 flex items-center gap-2">
              <span className="text-cyan-400">◆</span> ARCADE - SELECT A LANE
            </span>
          </div>
          
          <h2 className="font-display text-6xl md:text-[80px] font-black italic tracking-tighter text-transparent bg-clip-text bg-gradient-to-b from-white to-white/60 drop-shadow-[0_0_15px_rgba(255,255,255,0.4)] transform -rotate-2">
            PICK YOUR LANE
          </h2>
          
          <p className="mt-4 max-w-sm text-sm text-white/60 font-medium">
            Separate arcades. No mixing. Drop a coin, enter the lane, cop the fit before the algo notices.
          </p>
          
          <div className="mt-6 flex items-center gap-2 text-xs font-mono font-bold text-white/40">
            <span className="text-emerald-400">((•))</span> Live - streetwear-first
          </div>
        </div>

        <div className="flex flex-col lg:flex-row gap-10">
          {/* Polaroid */}
          <Link 
            to="/aesthetic/$id"
            params={{ id: streetwear.id }}
            className="flex-1 lg:max-w-[500px] lg:mr-auto transform rotate-2 hover:rotate-0 transition-all duration-500 block cursor-pointer"
          >
            <div className="bg-[#f8f5ef] p-4 pb-20 rounded-sm shadow-2xl relative">
              {/* Tape marks */}
              <div className="absolute -top-4 left-8 w-16 h-8 bg-cyan-400/50 rotate-[-8deg] z-10 backdrop-blur-sm shadow-sm"></div>
              <div className="absolute -top-3 right-12 w-14 h-8 bg-pink-400/50 rotate-[12deg] z-10 backdrop-blur-sm shadow-sm"></div>
              
              <div className="relative overflow-hidden aspect-[3/4] rounded-sm bg-black">
                <img 
                  src={streetwear.heroImage} 
                  alt="Streetwear" 
                  className="w-full h-full object-cover opacity-90 mix-blend-luminosity hover:mix-blend-normal transition-all duration-700"
                />
                
                {/* Image Overlay Gradient */}
                <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent pointer-events-none" />
                
                <div className="absolute top-4 left-4 inline-flex items-center gap-2 rounded-sm bg-black/80 border border-white/10 px-3 py-1 backdrop-blur-md">
                  <span className="h-2 w-2 rounded-full bg-orange-500 shadow-[0_0_8px_#f97316]" />
                  <span className="text-[10px] font-bold uppercase tracking-widest text-white">MAIN EVENT</span>
                </div>
                
                <div className="absolute bottom-6 left-6 right-6">
                  <h3 className="font-display text-5xl font-black text-white drop-shadow-lg mb-1">
                    STREETWEAR
                  </h3>
                  <div className="text-[10px] font-mono font-bold text-white/80 uppercase">
                    SCORE {streetwear.trendScore} - {streetwear.signalCount.toLocaleString()} SIGNALS
                  </div>
                </div>
              </div>
              
              <div className="absolute bottom-6 left-0 right-0 text-center font-display text-sm font-bold uppercase tracking-widest text-black/80 group-hover:text-black transition-colors">
                WHAT WE DO BEST • ENTER &rarr;
              </div>
            </div>
          </Link>

          {/* Lanes Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-2 gap-6 flex-1 lg:max-w-3xl">
            {rest.map((lane, i) => {
              const borderColor = lane.colorPalette[0] || "#fff";
              const emojis: Record<string, string> = {
                sneakers: "👟",
                shirts: "👕",
                bottoms: "👖",
                gym: "🏋️‍♂️",
                watches: "⌚",
                fragrances: "🧴"
              };
              
              return (
                <Link 
                  key={lane.id}
                  to="/aesthetic/$id"
                  params={{ id: lane.id }}
                  className="relative group cursor-pointer flex flex-col items-center justify-center p-10 rounded-2xl bg-[#0a0a0a] border-2 transition-transform duration-300 hover:scale-[1.02] block"
                  style={{ 
                    borderColor: borderColor,
                    boxShadow: `0 0 30px ${borderColor}40, inset 0 0 20px ${borderColor}10` 
                  }}
                >
                  <div className="absolute top-4 left-4 text-[10px] font-mono font-bold tracking-widest text-white/50">
                    L-0{i+1}
                  </div>
                  <div className="absolute top-4 right-4 text-white/30 group-hover:text-white transition-colors">
                    <ArrowUpRight className="h-4 w-4" />
                  </div>
                  
                  <div className="text-6xl mb-6 filter drop-shadow-[0_0_20px_rgba(255,255,255,0.4)] transition-transform duration-300 group-hover:scale-110">
                    {emojis[lane.id] || "✨"}
                  </div>
                  
                  <h3 className="font-display text-xl font-black uppercase tracking-widest text-white">
                    {lane.name}
                  </h3>
                  <div className="mt-2 text-[10px] font-mono font-bold text-white/50">
                    {lane.signalCount.toLocaleString()} sig
                  </div>
                </Link>
              );
            })}
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="mt-auto border-t border-white/10 py-6 text-center text-xs text-white/40 font-mono">
        &copy; 2026 TrendXee. All rights reserved.
      </footer>
    </div>
  );
}

