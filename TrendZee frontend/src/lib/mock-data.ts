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
    description:
      "The heart of TrendXee. Baggy denims, boxy tees, layered hoodies and the underdog fits creators are actually wearing this week.",
    signalCount: 8421,
    trendScore: 97,
    colorPalette: ["#f97316", "#dc2626", "#fde047", "#0f172a"],
    heroImage: "https://images.unsplash.com/photo-1523398002811-999ca8dec234?w=1400&q=80",
    vibeTags: ["baggy", "layered", "graphic", "underdog"],
  },
  {
    id: "sneakers",
    name: "SNEAKERS",
    description:
      "Sneaker culture, grail drops, chunky silhouettes and the resell-tier kicks lighting up sneaker-tok.",
    signalCount: 4980,
    trendScore: 91,
    colorPalette: ["#fbbf24", "#ef4444", "#111827", "#f5f5f4"],
    heroImage: "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=900&q=80",
    vibeTags: ["chunky", "retro", "grail", "colorway"],
  },
  {
    id: "sportswear",
    name: "SPORTSWEAR",
    description:
      "Gym fits, activewear, compression tees, and performance gear that transitions from the workout to the streets.",
    signalCount: 4200,
    trendScore: 88,
    colorPalette: ["#10b981", "#3b82f6", "#1e293b", "#f8fafc"],
    heroImage: "https://images.unsplash.com/photo-1517836357463-d25dfeac3438?w=900&q=80",
    vibeTags: ["activewear", "gym", "compression", "athletic"],
  },
  {
    id: "animewear",
    name: "ANIMEWEAR",
    description:
      "Anime-themed clothing, otaku merch, manga-panel tees and the anime-core graphics flooding fit-check reels.",
    signalCount: 3560,
    trendScore: 86,
    colorPalette: ["#ec4899", "#8b5cf6", "#22d3ee", "#111827"],
    heroImage: "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=900&q=80",
    vibeTags: ["anime-core", "manga", "graphic-tee", "otaku"],
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
