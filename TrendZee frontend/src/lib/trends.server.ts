import type { ProductMatch, SignalProduct, Trend } from "@/lib/mock-data";
import { getCategoryById } from "@/lib/categories";

type StoreKey = "underdog" | "amazon" | "flipkart";
type RawDocument = Record<string, unknown>;

const storeKeys: StoreKey[] = ["underdog", "amazon", "flipkart"];

function stringValue(value: unknown, fallback = ""): string {
  return typeof value === "string" ? value : fallback;
}

function idValue(value: unknown, fallback = ""): string {
  if (typeof value === "string") return value;
  if (value != null && typeof value === "object" && "toString" in value) return value.toString();
  return fallback;
}

function dateValue(value: unknown, fallback = new Date(0).toISOString()): string {
  if (value instanceof Date) return value.toISOString();
  if (typeof value === "string") return value;
  return fallback;
}

function numberValue(value: unknown, fallback = 0): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function booleanValue(value: unknown, fallback = false): boolean {
  return typeof value === "boolean" ? value : fallback;
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === "string")
    : [];
}

function documentArray(value: unknown): RawDocument[] {
  return Array.isArray(value)
    ? value.filter((item): item is RawDocument => item != null && typeof item === "object")
    : [];
}

/** Mongo stores signalProducts as a single object; support array for legacy docs. */
function normalizeSignalProducts(value: unknown): RawDocument[] {
  if (Array.isArray(value)) return documentArray(value);
  if (value != null && typeof value === "object") return [value as RawDocument];
  return [];
}

function mapProduct(value: unknown): ProductMatch | null {
  if (value == null || typeof value !== "object") return null;

  const product = value as RawDocument;
  const title = stringValue(product.title ?? product.name);
  const shopUrl = stringValue(
    product.shopUrl ?? product.url ?? product.productUrl ?? product.link,
  );
  const imageUrl = stringValue(
    product.imageUrl ?? product.image ?? product.thumbnailUrl ?? product.img,
  );

  if (!shopUrl && !title) return null;

  return {
    brandName: stringValue(product.brandName ?? product.brand ?? product.seller, "Unknown brand"),
    title: title || "Untitled product",
    price: numberValue(product.price ?? product.currentPrice),
    currency: stringValue(product.currency, "₹"),
    imageUrl,
    shopUrl,
  };
}

function mapSignalProduct(value: RawDocument, index: number): SignalProduct {
  const triad = {
    underdog: mapProduct(value.underdog),
    amazon: mapProduct(value.amazon),
    flipkart: mapProduct(value.flipkart),
  };

  return {
    signalId: idValue(value.signalId ?? value._id, `signal-${index + 1}`),
    authorUsername: stringValue(value.authorUsername ?? value.author ?? value.username),
    queryUsed: stringValue(value.queryUsed ?? value.query),
    ...triad,
  };
}

function bestAvailableProducts(signalProducts: SignalProduct[]) {
  const result: Record<string, ProductMatch | null> = {
    underdog: null,
    amazon: null,
    flipkart: null,
  };
  for (const store of storeKeys) {
    const sp = signalProducts.find((s) => s[store] != null);
    if (sp) {
      result[store] = sp[store];
    }
  }
  return result;
}

function isCompleteProduct(product: ProductMatch | null): product is ProductMatch {
  return product != null && Boolean(product.shopUrl) && Boolean(product.imageUrl);
}

function hasCompleteTriad(trend: Trend): boolean {
  return storeKeys.every((store) => isCompleteProduct(trend.products[store]));
}

function estimatePrice(signalProducts: SignalProduct[]): number {
  const prices = signalProducts.flatMap((signalProduct) =>
    storeKeys
      .map((store) => signalProduct[store]?.price)
      .filter((price): price is number => typeof price === "number" && price > 0),
  );

  if (prices.length === 0) return 0;
  return Math.round(prices.reduce((sum, price) => sum + price, 0) / prices.length);
}

function mapTrend(document: RawDocument): Trend {
  const signalProducts = normalizeSignalProducts(document.signalProducts).map(mapSignalProduct);
  const products = bestAvailableProducts(signalProducts);

  return {
    id: idValue(document._id),
    name: stringValue(document.trendName ?? document.name, "Untitled trend"),
    category: stringValue(document.category ?? document.aestheticId, "streetwear"),
    subcategory: stringValue(document.subcategory),
    tier: stringValue(document.tier),
    trendScore: numberValue(document.trendScore),
    vibeTags: stringArray(document.vibeTags),
    aiBrandNames: stringArray(document.aiBrandNames),
    aiSummary: stringValue(document.aiSummary),
    indiaRelevanceNote: stringValue(document.indiaRelevanceNote),
    whyTrending: stringArray(document.whyTrending),
    indiaRelevant: booleanValue(document.indiaRelevant),
    products,
    signalProducts,
    estimatedPrice: numberValue(document.estimatedPrice) || estimatePrice(signalProducts),
    lastUpdatedAt: dateValue(document.updatedAt ?? document.lastUpdatedAt ?? document.createdAt),
  };
}

export async function getTrendsByCategory(categoryId = "streetwear", page = 0): Promise<Trend[]> {
  const backendUrl = process.env.BACKEND_URL || "http://127.0.0.1:8080";
  const category = getCategoryById(categoryId);
  const queryCategory = category ? category.enum : categoryId;
  const url = `${backendUrl}/api/v2/trends?category=${queryCategory}&page=${page}`;
  
  try {
    const response = await fetch(url);
    if (!response.ok) {
      console.error(`[TrendZY] Failed to fetch trends from backend: ${response.status} ${response.statusText}`);
      return [];
    }
    
    const data = await response.json();
    const documents = Array.isArray(data) ? data : (data.content || []);
    const trends = documents.map(mapTrend);

    console.info(
      `[TrendZY] fetch(${url}) returned ${documents.length} docs; rendering ${trends.length} trends (including partial ones).`,
    );

    return trends;
  } catch (error) {
    console.error(`[TrendZY] Error fetching trends from backend:`, error);
    return [];
  }
}
