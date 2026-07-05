/** Matches backend `Category` enum values stored on trend documents in MongoDB. */
export const Category = {
  STREETWEAR: "STREETWEAR",
  SNEAKERS: "SNEAKERS",
  SPORTSWEAR: "SPORTSWEAR",
  ANIMEWEAR: "ANIMEWEAR",
} as const;

export type CategoryEnum = (typeof Category)[keyof typeof Category];

export const categoryOptions = [
  { id: "streetwear", label: "Streetwear", enum: Category.STREETWEAR },
  { id: "sneakers", label: "Sneakers", enum: Category.SNEAKERS },
  { id: "sportswear", label: "Sportswear/Gymwear", enum: Category.SPORTSWEAR },
  { id: "animewear", label: "Animewear", enum: Category.ANIMEWEAR },
] as const;

export type CategoryId = (typeof categoryOptions)[number]["id"];

export function getCategoryById(id: string) {
  return categoryOptions.find((category) => category.id === id);
}

/** Values stored in MongoDB — lowercase route slugs matching the backend enum names. */
export function getCategoryMongoValue(id: string): string | undefined {
  return getCategoryById(id)?.id;
}

/** Values to match against MongoDB `category` (and legacy `aestheticId`) fields. */
export function getCategoryQueryValues(id: string): string[] {
  const category = getCategoryById(id);
  if (!category) return [];

  return [...new Set([category.id, category.enum, category.label, category.label.toLowerCase()])];
}

/** Case-insensitive Mongo filter for a category route slug (e.g. "streetwear"). */
export function buildCategoryFilter(categoryId: string): Record<string, unknown> {
  const values = getCategoryQueryValues(categoryId);
  if (values.length === 0) return {};

  const pattern = `^(${values.map((v) => v.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join("|")})$`;

  return {
    $or: [
      { category: { $regex: pattern, $options: "i" } },
      { aestheticId: { $regex: pattern, $options: "i" } },
    ],
  };
}
