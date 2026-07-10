export interface Aesthetic {
  id: string;
  name: string;
  description: string;
  signalCount: number;
  trendScore: number;
  colorPalette: string[];
  heroImage: string;
  vibeTags: string[];
  underdogRotation?: { brand: string; title: string; image: string }[];
}

export interface ProductDetail {
  brandName: string;
  title: string;
  currency: string;
  price: number;
  originalPrice?: number;
  shopUrl: string;
  imageUrl: string;
  codAvailable?: boolean;
}

export interface SignalProducts {
  signalId: string;
  authorUsername: string;
  queryUsed: string;
  underdog: ProductDetail | null;
  amazon: ProductDetail | null;
  flipkart: ProductDetail | null;
}

export interface Trend {
  id: string;
  trendName: string;
  category: string;
  subcategory: string;
  trendScore: number;
  tier: string;
  vibeTags: string[];
  aiSummary: string;
  whyTrending: string[];
  indiaRelevanceNote: string;
  indiaRelevant: boolean;
  totalSignals: number;
  supportingSignalIds: string[];
  enrichmentStatus: string;
  enrichmentQuery: string;
  aiBrandNames: string[];
  signalProducts: SignalProducts;
  estimatedPrice: number;
  currency: string;
  firstDetectedAt: string;
  lastUpdatedAt: string;
  active: boolean;
}

export interface TrendSlice {
  content: Trend[];
  pageable: any;
  last: boolean;
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
  sort: any;
  first: boolean;
  numberOfElements: number;
  empty: boolean;
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
    underdogRotation: [
      { brand: "Bluorng", title: "Washed Baggy Carpenter", image: "https://images.unsplash.com/photo-1584865288642-42078afe6942?w=800&q=80" },
      { brand: "Almost Gods", title: "Boxy Acid-Wash Tee", image: "https://images.unsplash.com/photo-1503341504253-dff4815485f1?w=800&q=80" },
      { brand: "Jaywalking", title: "Heavyweight Zipper", image: "https://images.unsplash.com/photo-1523398002811-999ca8dec234?w=800&q=80" }
    ],
  },
  {
    id: "sneakers",
    name: "SNEAKERS",
    description: "Grail drops, chunky silhouettes and the resell-tier kicks lighting up sneaker-tok.",
    signalCount: 4980,
    trendScore: 91,
    colorPalette: ["#fbbf24", "#ef4444", "#111827", "#f5f5f4"],
    heroImage: "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=900&q=80",
    vibeTags: ["chunky", "retro", "grail", "colorway"],
    underdogRotation: [
      { brand: "Andro Athletic", title: "Ember Runner Low", image: "https://images.unsplash.com/photo-1520256862855-398228c41684?w=800&q=80" },
      { brand: "Voyage & Co.", title: "Bronzed Trail 92", image: "https://images.unsplash.com/photo-1600185365483-26d7a4cc7519?w=800&q=80" },
      { brand: "Grail Studio", title: "Cocoa Chunk Mid", image: "https://images.unsplash.com/photo-1595950653106-6c9ebd614d3a?w=800&q=80" },
    ],
  },
  {
    id: "upper",
    name: "UPPER",
    description: "Boxy tees, oversized button-ups, graphic prints and the layering tops driving every fit-check on the feed.",
    signalCount: 5320,
    trendScore: 93,
    colorPalette: ["#22d3ee", "#0ea5e9", "#f5f5f4", "#0f172a"],
    heroImage: "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=900&q=80",
    vibeTags: ["boxy", "oversized", "graphic", "layered"],
    underdogRotation: [
      { brand: "House of Ash", title: "Boxy Cotton Tee — Camel", image: "https://images.unsplash.com/photo-1618354691373-d851c5c3a990?w=800&q=80" },
      { brand: "Late Bloomer", title: "Oversized Cafe Shirt", image: "https://images.unsplash.com/photo-1622470953794-aa9c70b0fb9d?w=800&q=80" },
      { brand: "Off-Season", title: "Almond Terry Polo", image: "https://images.unsplash.com/photo-1618453292459-53424b66bb6a?w=800&q=80" },
    ],
  },
  {
    id: "bottoms",
    name: "BOTTOMS",
    description: "Baggy jeans, parachute pants, wide-leg joggers and the cargo lowers stacking over every sneaker on the feed.",
    signalCount: 3980,
    trendScore: 88,
    colorPalette: ["#a3a3a3", "#525252", "#f5f5f4", "#0f172a"],
    heroImage: "https://images.unsplash.com/photo-1584865288642-42078afe6942?w=900&q=80",
    vibeTags: ["baggy", "cargo", "parachute", "stacked"],
    underdogRotation: [
      { brand: "Field Notes", title: "Mahogany Cargo — Wide", image: "https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?w=800&q=80" },
      { brand: "Slow Denim Co.", title: "Espresso Baggy Denim", image: "https://images.unsplash.com/photo-1602293589930-45aad59ba3ab?w=800&q=80" },
      { brand: "Postgrad", title: "Caramel Parachute Jogger", image: "https://images.unsplash.com/photo-1594633312681-425c7b97ccd1?w=800&q=80" },
    ],
  },
  {
    id: "accessories",
    name: "ACCESSORIES",
    description: "Chunky silver chains, trucker caps, crossbody bags and the finishing pieces that turn a fit into a moment.",
    signalCount: 2610,
    trendScore: 82,
    colorPalette: ["#e5e7eb", "#9ca3af", "#f59e0b", "#111827"],
    heroImage: "https://images.unsplash.com/photo-1611923134239-b9be5816e23d?w=900&q=80",
    vibeTags: ["chains", "caps", "bags", "layered"],
    underdogRotation: [
      { brand: "Atelier Nine", title: "Bronze Curb Chain", image: "https://images.unsplash.com/photo-1611085583191-a3b181a88401?w=800&q=80" },
      { brand: "Salvage Studio", title: "Waxed Canvas Crossbody", image: "https://images.unsplash.com/photo-1590874103328-eac38a683ce7?w=800&q=80" },
      { brand: "Common Field", title: "Cafe Trucker Cap", image: "https://images.unsplash.com/photo-1521369909029-2afed882baee?w=800&q=80" },
    ],
  },
  {
    id: "gym",
    name: "GYM",
    description: "Oversized stringers, tapered joggers, compression layers and the fit-gear dominating gym-tok and locker room fits.",
    signalCount: 1890,
    trendScore: 78,
    colorPalette: ["#facc15", "#78716c", "#1c1917", "#e7e5e4"],
    heroImage: "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?w=900&q=80",
    vibeTags: ["stringer", "tapered", "compression", "fit-gear"],
    underdogRotation: [
      { brand: "Iron County", title: "Oversized Drop-Arm Stringer — Coal", image: "https://images.unsplash.com/photo-1583454110551-21f2fa2afe61?w=800&q=80" },
      { brand: "Set & Rep", title: "Tapered Training Jogger — Slate", image: "https://images.unsplash.com/photo-1517438476312-10d79c077509?w=800&q=80" },
      { brand: "Raw Motion", title: "Seamless Compression Long Sleeve", image: "https://images.unsplash.com/photo-1571019614242-c5c5dee9f50b?w=800&q=80" },
    ],
  },
  {
    id: "fragrances",
    name: "FRAGRANCES",
    description: "Niche perfumers, gourmand cloud-scents and the bottles TikTok fragrance-tok wont shut up about.",
    signalCount: 2140,
    trendScore: 84,
    colorPalette: ["#f5d0fe", "#c084fc", "#a855f7", "#1e1b4b"],
    heroImage: "https://images.unsplash.com/photo-1541643600914-78b084683601?w=900&q=80",
    vibeTags: ["niche", "gourmand", "oud", "cloud"],
    underdogRotation: [
      { brand: "Maison Vellum", title: "Amber Tobacco Extrait", image: "https://images.unsplash.com/photo-1592945403244-b3fbafd7f539?w=800&q=80" },
      { brand: "Small Room", title: "Cacao & Oud EDP", image: "https://images.unsplash.com/photo-1615634260167-c8cdede054de?w=800&q=80" },
      { brand: "Late Harvest", title: "Caramel Vetiver", image: "https://images.unsplash.com/photo-1594035910387-fea47794261f?w=800&q=80" },
    ],
  },
];



export function paletteVars(palette: string[]): React.CSSProperties {
  const [a, b, c, d] = [...palette, ...palette].slice(0, 4);
  return {
    ["--aesthetic-1" as string]: a,
    ["--aesthetic-2" as string]: b,
    ["--aesthetic-3" as string]: c,
    ["--aesthetic-4" as string]: d,
  };
}