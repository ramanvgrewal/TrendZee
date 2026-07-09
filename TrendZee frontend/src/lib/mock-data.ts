import { getCategoryQueryValues } from "@/lib/categories";

export interface Aesthetic {
  id: string;
  name: string;
  description: string;
  signalCount: number;
  trendScore: number;
  colorPalette: string[];
  heroImage: string;
  vibeTags: string[];
}

export interface ProductMatch {
  brandName: string;
  title: string;
  price: number;
  currency: string;
  imageUrl: string;
  shopUrl: string;
}

export interface ProductTriad {
  underdog: ProductMatch | null;
  amazon: ProductMatch | null;
  flipkart: ProductMatch | null;
}

export interface SignalProduct extends ProductTriad {
  signalId: string;
  authorUsername: string;
  queryUsed: string;
}

export interface Trend {
  id: string;
  name: string;
  category: string;
  subcategory: string;
  tier: string;
  trendScore: number;
  vibeTags: string[];
  aiBrandNames: string[];
  aiSummary: string;
  whyTrending: string[];
  indiaRelevant: boolean;
  indiaRelevanceNote: string;
  products: ProductTriad;
  signalProducts: SignalProduct[];
  estimatedPrice: number;
  currency: string;
  lastUpdatedAt: string;
}

export const aesthetics: Aesthetic[] = [
  {
    id: "streetwear",
    name: "STREETWEAR",
    description: "The heart of TrendXee. Baggy denims, boxy tees, layered hoodies and the underdog fits creators are actually wearing this week.",
    signalCount: 8421,
    trendScore: 97,
    colorPalette: ["#f97316", "#dc2626", "#fde047", "#0f172a"],
    heroImage: "https://images.unsplash.com/photo-1523398002811-999ca8dec234?w=1400&q=80",
    vibeTags: ["baggy", "layered", "graphic", "underdog"],
  },
  {
    id: "sneakers",
    name: "SNEAKERS",
    description: "Sneaker culture, grail drops, chunky silhouettes and the resell-tier kicks lighting up sneaker-tok.",
    signalCount: 4980,
    trendScore: 91,
    colorPalette: ["#fde047", "#facc15", "#eab308", "#111827"], // yellow glow
    heroImage: "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=900&q=80",
    vibeTags: ["chunky", "retro", "grail", "colorway"],
  },
  {
    id: "shirts",
    name: "UPPER",
    description: "Boxy tees, graphic prints, and statement tops taking over the feed.",
    signalCount: 5328,
    trendScore: 89,
    colorPalette: ["#22d3ee", "#06b6d4", "#0891b2", "#111827"], // cyan glow
    heroImage: "https://images.unsplash.com/photo-1503341504253-dff4815485f1?w=900&q=80",
    vibeTags: ["boxy", "graphic", "layered", "statement"],
  },
  {
    id: "bottoms",
    name: "BOTTOMS",
    description: "Baggy denim, cargo pants, and the silhouettes defining the lower half.",
    signalCount: 3980,
    trendScore: 88,
    colorPalette: ["#3b82f6", "#2563eb", "#1d4ed8", "#111827"], // blue glow
    heroImage: "https://images.unsplash.com/photo-1542272604-780c4050d153?w=900&q=80",
    vibeTags: ["baggy", "cargo", "denim", "parachute"],
  },
  {
    id: "gym",
    name: "GYM",
    description: "Activewear, pump covers, lifting belts, and the athletic fits dominating fitness-tok.",
    signalCount: 2410,
    trendScore: 85,
    colorPalette: ["#f8fafc", "#f1f5f9", "#e2e8f0", "#111827"], // white glow
    heroImage: "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?w=900&q=80",
    vibeTags: ["activewear", "pumpcover", "compression", "lifting"],
  },
  {
    id: "watches",
    name: "WATCHES",
    description: "Timepieces and wristwear catching the algorithm's eye.",
    signalCount: 1890,
    trendScore: 82,
    colorPalette: ["#fde047", "#facc15", "#ca8a04", "#111827"], // yellow glow
    heroImage: "https://images.unsplash.com/photo-1524592094714-0f0654e20314?w=900&q=80",
    vibeTags: ["vintage", "metallic", "digital", "analog"],
  },
  {
    id: "fragrances",
    name: "FRAGRANCES",
    description: "The aesthetic scents, niche houses, and the invisible vibe check.",
    signalCount: 2140,
    trendScore: 84,
    colorPalette: ["#f472b6", "#ec4899", "#db2777", "#111827"], // pink glow
    heroImage: "https://images.unsplash.com/photo-1594035910387-fea47794261f?w=900&q=80",
    vibeTags: ["niche", "designer", "scent", "layering"],
  },
];

export const trends: Trend[] = [];

export function getTrendsForAesthetic(aestheticId: string): Trend[] {
  const categoryValues = new Set(getCategoryQueryValues(aestheticId).map((v) => v.toLowerCase()));
  return trends.filter((t) => categoryValues.has(t.category.toLowerCase()));
}

export function paletteVars(palette: string[]): React.CSSProperties {
  const [a, b, c, d] = [...palette, ...palette].slice(0, 4);
  return {
    ["--aesthetic-1" as string]: a,
    ["--aesthetic-2" as string]: b,
    ["--aesthetic-3" as string]: c,
    ["--aesthetic-4" as string]: d,
  };
}
