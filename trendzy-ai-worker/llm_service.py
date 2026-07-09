import json
import logging
import os
import re
from typing import Any

from groq import Groq

from env_loader import load_project_env

load_project_env()

LOGGER = logging.getLogger(__name__)


class LlmServiceError(Exception):
    pass


FASHION_SIGNAL_SYSTEM_PROMPT = """
You are the core intelligence engine for TrendZY. Your job is to analyze social media
signals (Instagram posts) and extract structured fashion/clothing trends that will be
inserted directly into the MongoDB `trends` collection.

Reason carefully before answering:
- Infer the most specific fashion micro-trend actually supported by the evidence.
- If evidence is weak, still provide the closest high-confidence inference; never null.
- Never output markdown, commentary, code fences, or explanatory text.

Return ONE strict JSON object with EXACTLY this top-level structure:
{
  "name": "string (2-4 words, punchy, e.g. 'Oversized Anime Graphics')",
  "subcategory": "string (e.g. Y2K, Cyberpunk, Gorpcore, Old Money)",
  "vibeTags": ["tag1", "tag2", "tag3"],
  "aiSummary": "string (2-3 sentences explaining the trend)",
  "whyTrending": ["Reason 1", "Reason 2"],
  "indiaRelevant": true,
  "indiaRelevanceNote": "string (why this works or does not work in India)",
  "enrichmentQuery": "string (2-4 words, Amazon/Flipkart-ready, e.g. 'oversized naruto tshirt')",
  "aiBrandNames": ["brand1", "brand2"],
  "estimatedPrice": 1299.0,
  "extractedKeywords": ["string"]
}

Hard requirements:
- name: 2-4 words, punchy, non-empty.
- subcategory: single short label (Y2K, Cyberpunk, Gorpcore, Old Money, etc.).
- vibeTags: 3-6 short lowercase descriptive tags.
- enrichmentQuery: 2-4 words, all lowercase, that will be pasted directly into Amazon India search.
- estimatedPrice: expected INR price for a representative product (number, not string).
- extractedKeywords: 2-5 lowercase product-search phrases derived from THIS signal.

CRITICAL — indiaRelevant CLASSIFICATION RULE:
Set indiaRelevant = true if ANY of:
  1. raw_text mentions Indian brands, cities, festivals, or cultural cues.
  2. hashtags include India-specific tags (#streetwearindia, #indianfashion, etc.).
  3. authorUsername strongly implies an Indian market or creator context.
indiaRelevanceNote must always be filled with a 1-2 sentence rationale.

Do NOT include tier, trendScore, totalSignals, supportingSignals, enrichmentStatus, firstDetectedAt, lastUpdatedAt, or active — the backend computes and writes those.
""".strip()


FASHION_SIGNAL_REPAIR_PROMPT = """
Your previous response was invalid or incomplete.
Return strict JSON only, using the exact schema already requested.

Repair rules:
- Do not omit required objects or keys.
- name must be 2-4 words and non-empty.
- enrichmentQuery must be 2-4 lowercase words, Amazon/Flipkart-ready.
- estimatedPrice must be a number (INR).
- indiaRelevant must be a boolean; indiaRelevanceNote must be a non-empty string.
""".strip()


def _clean_llm_json(content: str) -> str:
    stripped = (content or "").strip()
    if stripped.startswith("```"):
        lines = stripped.splitlines()
        if len(lines) >= 3:
            stripped = "\n".join(lines[1:-1]).strip()
    return stripped or "{}"


def _safe_string(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        normalized = " ".join(value.strip().split())
        if normalized.lower() in {"none", "null", "n/a", "unknown"}:
            return ""
        return normalized
    return " ".join(str(value).strip().split())


def _safe_string_list(value: Any, *, lowercase: bool = False) -> list[str]:
    if value is None:
        return []
    if isinstance(value, str):
        candidates = [value]
    elif isinstance(value, list):
        candidates = value
    else:
        return []

    normalized_values: list[str] = []
    seen: set[str] = set()
    for item in candidates:
        normalized = _safe_string(item)
        if not normalized:
            continue
        if lowercase:
            normalized = normalized.lower()
        key = normalized.lower()
        if key in seen:
            continue
        seen.add(key)
        normalized_values.append(normalized)
    return normalized_values


def _safe_float(value: Any) -> float:
    try:
        if isinstance(value, bool):
            return 0.0
        if isinstance(value, (int, float)):
            return float(value)
        if isinstance(value, str):
            cleaned = re.sub(r"[^\d.]", "", value)
            return float(cleaned) if cleaned else 0.0
    except (TypeError, ValueError):
        return 0.0
    return 0.0


def _default_extraction() -> dict[str, Any]:
    return {
        "name": "",
        "subcategory": "",
        "vibeTags": [],
        "aiSummary": "",
        "whyTrending": [],
        "indiaRelevant": False,
        "indiaRelevanceNote": "",
        "enrichmentQuery": "",
        "aiBrandNames": [],
        "estimatedPrice": 0.0,
        "extractedKeywords": [],
    }


def parse_llm_json_response(payload: str) -> dict[str, Any]:
    normalized = _default_extraction()
    try:
        parsed = json.loads(_clean_llm_json(payload))
        if not isinstance(parsed, dict):
            parsed = {}
    except (json.JSONDecodeError, TypeError):
        parsed = {}

    # Handle case where LLM still nests under "trend" despite instructions
    data = parsed.get("trend", parsed) if isinstance(parsed.get("trend"), dict) else parsed

    normalized["name"] = _safe_string(data.get("name"))
    normalized["subcategory"] = _safe_string(data.get("subcategory"))
    normalized["vibeTags"] = _safe_string_list(data.get("vibeTags"), lowercase=True)
    normalized["aiSummary"] = _safe_string(data.get("aiSummary"))
    normalized["whyTrending"] = _safe_string_list(data.get("whyTrending"))
    normalized["indiaRelevant"] = bool(data.get("indiaRelevant", False))
    normalized["indiaRelevanceNote"] = _safe_string(data.get("indiaRelevanceNote"))
    normalized["enrichmentQuery"] = _safe_string(data.get("enrichmentQuery")).lower()
    normalized["aiBrandNames"] = _safe_string_list(data.get("aiBrandNames"))
    normalized["estimatedPrice"] = _safe_float(data.get("estimatedPrice"))
    normalized["extractedKeywords"] = _safe_string_list(data.get("extractedKeywords"), lowercase=True)[:5]
    
    return normalized


def _request_structured_completion(client: Groq, system_prompt: str, user_prompt: str) -> Any:
    return client.chat.completions.create(
        model=os.getenv("GROQ_MODEL", "llama-3.3-70b-versatile"),
        temperature=0.1,
        response_format={"type": "json_object"},
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
    )


def _build_user_prompt(raw_text: str, hashtags: list[str], platform: str, author_username: str, category: str = "") -> str:
    return (
        "Analyze this fashion signal and return only valid JSON.\n\n"
        f"category: {category or ''}\n"
        f"raw_text: {raw_text or ''}\n"
        f"hashtags: {hashtags or []}\n"
        f"platform: {platform or ''}\n"
        f"authorUsername: {author_username or ''}"
    )


def extract_trend_data(
    raw_text: str,
    hashtags: list[str],
    platform: str = "",
    author_username: str = "",
    category: str = "",
) -> dict[str, Any]:
    api_key = os.getenv("GROQ_API_KEY")
    if not api_key:
        raise LlmServiceError("GROQ_API_KEY is not configured.")

    client = Groq(api_key=api_key)
    user_prompt = _build_user_prompt(raw_text, hashtags, platform, author_username, category)
    LOGGER.info("Sending signal content to Groq for structured fashion extraction")

    try:
        response = _request_structured_completion(client, FASHION_SIGNAL_SYSTEM_PROMPT, user_prompt)
        content = response.choices[0].message.content or "{}"
        extraction = parse_llm_json_response(content)

        if extraction["name"]:
            return extraction

        LOGGER.warning("LLM response missing required names, retrying with repair prompt")
        repair_prompt = (
            f"{FASHION_SIGNAL_REPAIR_PROMPT}\n\n"
            f"Original input:\n{user_prompt}\n\n"
            f"Previous invalid JSON:\n{_clean_llm_json(content)}"
        )
        retry_response = _request_structured_completion(client, FASHION_SIGNAL_SYSTEM_PROMPT, repair_prompt)
        retry_content = retry_response.choices[0].message.content or "{}"
        repaired_extraction = parse_llm_json_response(retry_content)

        if repaired_extraction["name"]:
            return repaired_extraction

        raise LlmServiceError("LLM response missing required trend name after retry.")
    except LlmServiceError:
        raise
    except Exception as exc:
        LOGGER.exception("Failed to extract structured fashion intelligence")
        raise LlmServiceError("Unable to extract structured fashion intelligence.") from exc


# ── Product Selection (AI-powered underdog ranking) ────────────────────────

PRODUCT_SELECTION_SYSTEM_PROMPT = """
You are the product selection engine for TrendZY. You receive:
- A detected fashion trend (name, subcategory, vibeTags, aiSummary)
- The original Instagram signal (caption + hashtags)
- The target product category (e.g. STREETWEAR, SNEAKERS, SHIRTS)
- A numbered list of scraped products from a creator's website

Your job: select the SINGLE best-matching product for this trend.

Steps:
1. CATEGORY GATE — Eliminate products that clearly don't belong to the target
   category. E.g. if category is SNEAKERS, a "Graphic Tee" is out.
   Be reasonably flexible: "Oversized Shirt" fits STREETWEAR and SHIRTS.
2. TREND RELEVANCE — Among remaining products, score how well each matches
   the detected fashion trend name, subcategory, vibe tags, and the original
   Instagram signal's aesthetic.
3. IMAGE PRIORITY — Prefer products that have an image URL (non-null).
4. SELECTION — Pick the product with the highest relevance.
   If NO product meaningfully matches the category AND trend, set
   selectedIndex to -1.

Return ONE strict JSON object with EXACTLY this structure:
{
  "selectedIndex": <int, 0-based index into the products array, or -1 if none match>,
  "reasoning": "<1-2 sentences explaining why this product was chosen>",
  "confidenceScore": <float 0.0-1.0, how confident you are in this selection>
}

Rules:
- selectedIndex MUST be a valid index or -1. Never null.
- Do NOT return markdown, code fences, or commentary.
- Prefer mid-range priced products over extremely cheap or extremely expensive ones.
- CRITICAL: DO NOT select any product if its price is 'N/A' or missing. Products without a price are usually scraped logos, banners, or navigational elements. If the only relevant products have price=N/A, you MUST return -1.
- If multiple products are equally relevant, prefer the one with better data
  completeness (has image, has price, has descriptive name).
""".strip()


def _build_product_selection_prompt(
    products: list[dict],
    trend_data: dict,
    category: str,
    raw_text: str,
    hashtags: list[str],
) -> str:
    product_lines: list[str] = []
    for i, p in enumerate(products):
        name = p.get("productName") or "Untitled"
        price = p.get("mainPrice")
        price_str = f"₹{price:.0f}" if price else "N/A"
        has_image = "yes" if p.get("imageUrl") else "no"
        product_lines.append(f"  [{i}] {name} | price={price_str} | hasImage={has_image}")

    products_block = "\n".join(product_lines)

    return (
        "Select the best underdog product. Return only valid JSON.\n\n"
        f"TARGET CATEGORY: {category or 'UNKNOWN'}\n\n"
        f"DETECTED TREND:\n"
        f"  name: {trend_data.get('name', '')}\n"
        f"  subcategory: {trend_data.get('subcategory', '')}\n"
        f"  vibeTags: {trend_data.get('vibeTags', [])}\n"
        f"  aiSummary: {trend_data.get('aiSummary', '')}\n\n"
        f"INSTAGRAM SIGNAL:\n"
        f"  caption: {raw_text or ''}\n"
        f"  hashtags: {hashtags or []}\n\n"
        f"SCRAPED PRODUCTS ({len(products)} total):\n{products_block}"
    )


def select_underdog_product(
    products: list[dict],
    trend_data: dict,
    category: str = "",
    raw_text: str = "",
    hashtags: list[str] | None = None,
) -> dict[str, Any] | None:
    """Use AI to select the best underdog product from a list of scraped products.

    Returns the selected product dict (from the input list) or None if no match.
    """
    if not products:
        return None

    api_key = os.getenv("GROQ_API_KEY")
    if not api_key:
        LOGGER.warning("GROQ_API_KEY not configured, skipping product selection")
        return None

    # Cap at 25 products to keep token usage reasonable
    capped_products = products[:25]

    client = Groq(api_key=api_key)
    user_prompt = _build_product_selection_prompt(
        capped_products, trend_data, category, raw_text, hashtags or [],
    )
    LOGGER.info(
        "Sending %d products to Groq for AI underdog selection (category=%s, trend=%s)",
        len(capped_products), category, trend_data.get("name", ""),
    )

    try:
        response = _request_structured_completion(
            client, PRODUCT_SELECTION_SYSTEM_PROMPT, user_prompt,
        )
        content = response.choices[0].message.content or "{}"
        parsed = json.loads(_clean_llm_json(content))

        if not isinstance(parsed, dict):
            LOGGER.warning("Product selection LLM returned non-dict: %s", content)
            return None

        selected_index = parsed.get("selectedIndex", -1)
        reasoning = parsed.get("reasoning", "")
        confidence = parsed.get("confidenceScore", 0.0)

        if not isinstance(selected_index, int) or selected_index < 0:
            LOGGER.info(
                "AI declined product selection (index=%s, reason=%s)",
                selected_index, reasoning,
            )
            return None

        if selected_index >= len(capped_products):
            LOGGER.warning(
                "AI returned out-of-range index %d for %d products",
                selected_index, len(capped_products),
            )
            return None

        selected = capped_products[selected_index]
        LOGGER.info(
            "AI selected product [%d] '%s' (confidence=%.2f, reason=%s)",
            selected_index,
            selected.get("productName", ""),
            confidence,
            reasoning,
        )
        return selected

    except (json.JSONDecodeError, TypeError) as exc:
        LOGGER.warning("Failed to parse product selection response: %s", exc)
        return None
    except Exception as exc:
        LOGGER.exception("Product selection LLM call failed")
        return None