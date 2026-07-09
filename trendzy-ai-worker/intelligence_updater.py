import logging
import time
import math
import re
from datetime import datetime, timezone
from typing import Any

from bson import ObjectId
from pymongo import ReturnDocument

from database import trends, v2_signals
from llm_service import extract_trend_data, select_underdog_product

LOGGER = logging.getLogger(__name__)

def _utc_now() -> datetime:
    return datetime.now(timezone.utc)

def _normalize_name(value: str) -> str:
    return " ".join((value or "").strip().split())

def _to_object_id(signal_id: str) -> ObjectId:
    try:
        return ObjectId(signal_id)
    except Exception as exc:
        raise ValueError(f"Invalid Mongo ObjectId: {signal_id}") from exc

def _calculate_signal_score(signal: dict[str, Any]) -> float:
    engagement = signal.get("engagementScore", 0)
    comments = signal.get("commentCount", 0)
    platform = _safe_string(signal.get("platform")).upper()

    likes = float(engagement) if isinstance(engagement, (int, float)) else 0.0
    comms = float(comments) if isinstance(comments, (int, float)) else 0.0

    if platform == "PINTEREST":
        return (likes * 5.0) + (comms * 20.0)
    return (likes * 1.0) + (comms * 20.0)

def _calculate_macro_score(signal_count: int, latest_signal_score: float) -> float:
    base_score = 50.0
    volume_bonus = min(25.0, signal_count * 1.5)
    engagement_bonus = min(25.0, math.log10(max(1.0, latest_signal_score)) * 5.0)

    total = base_score + volume_bonus + engagement_bonus
    return round(min(100.0, total), 1)

def _safe_string(value: Any) -> str:
    if value is None:
        return ""
    return _normalize_name(str(value))

def _safe_string_list(value: Any, *, lowercase: bool = False) -> list[str]:
    if isinstance(value, str):
        values = [value]
    elif isinstance(value, list):
        values = value
    else:
        return []
    normalized: list[str] = []
    seen: set[str] = set()
    for item in values:
        text = _safe_string(item)
        if not text:
            continue
        if lowercase:
            text = text.lower()
        key = text.lower()
        if key in seen:
            continue
        seen.add(key)
        normalized.append(text)
    return normalized

def _safe_signal_object_ids(values: Any, current_signal_id: ObjectId) -> list[ObjectId]:
    object_ids: list[ObjectId] = []
    seen: set[ObjectId] = set()
    candidates = list(values) if isinstance(values, list) else []
    candidates.append(current_signal_id)
    for value in candidates:
        try:
            object_id = value if isinstance(value, ObjectId) else ObjectId(str(value))
        except Exception:
            continue
        if object_id in seen:
            continue
        seen.add(object_id)
        object_ids.append(object_id)
    return object_ids

def _extract_author_username(signal: dict[str, Any]) -> str:
    direct_username = _safe_string(signal.get("authorUsername"))
    if direct_username:
        return direct_username
    author = signal.get("author")
    if isinstance(author, dict):
        return _safe_string(author.get("username"))
    return ""


def upsert_trend_document(
    trend_payload: dict[str, Any],
    signal_object_id: ObjectId,
    signal_score: float,
    signal: dict[str, Any],
    now: datetime,
) -> dict[str, Any]:
    name = _normalize_name(_safe_string(trend_payload.get("name")))
    category = _safe_string(signal.get("category")).upper()

    # Perform case-insensitive search by name scoped to category
    name_regex = re.compile(f"^{re.escape(name)}$", re.IGNORECASE)
    existing = trends.find_one({"name": name_regex, "category": category}, {"_id": 1, "supportingSignals": 1})
    
    supporting_signals = _safe_signal_object_ids(
        existing.get("supportingSignals", []) if isinstance(existing, dict) else [],
        signal_object_id,
    )

    macro_score = _calculate_macro_score(len(supporting_signals), signal_score)

    raw_india_flag = trend_payload.get("indiaRelevant", False)
    is_india_relevant: bool = bool(raw_india_flag) if not isinstance(raw_india_flag, bool) else raw_india_flag

    enrichment_query = _safe_string(trend_payload.get("enrichmentQuery")).lower()
    if not enrichment_query:
        keywords = _safe_string_list(trend_payload.get("extractedKeywords"), lowercase=True)
        enrichment_query = keywords[0] if keywords else name.lower()

    estimated_price = trend_payload.get("estimatedPrice")
    try:
        estimated_price = float(estimated_price) if estimated_price is not None else 0.0
    except (TypeError, ValueError):
        estimated_price = 0.0

    update_document: dict[str, Any] = {
        "$set": {
            "name": name,
            "category": category,
            "subcategory": _safe_string(trend_payload.get("subcategory")),
            "aiSummary": _safe_string(trend_payload.get("aiSummary")),
            "whyTrending": _safe_string_list(trend_payload.get("whyTrending")),
            "vibeTags": _safe_string_list(trend_payload.get("vibeTags"), lowercase=True),
            "supportingSignals": supporting_signals,
            "totalSignals": len(supporting_signals),
            "indiaRelevant": is_india_relevant,
            "indiaRelevanceNote": _safe_string(trend_payload.get("indiaRelevanceNote")),
            "trendScore": macro_score,
            "enrichmentStatus": "AWAITING_UNDERDOG",
            "enrichmentQuery": enrichment_query,
            "aiBrandNames": _safe_string_list(trend_payload.get("aiBrandNames")),
            "estimatedPrice": estimated_price,
            "active": True,
            "updatedAt": now,
        },
        "$setOnInsert": {
            "createdAt": now,
            "firstDetectedAt": now,
        },
    }

    return trends.find_one_and_update(
        {"name": name_regex, "category": category},
        update_document,
        upsert=True,
        return_document=ReturnDocument.AFTER,
    )


def process_and_upsert(signal_id: str) -> None:
    signal_object_id = _to_object_id(signal_id)
    signal = v2_signals.find_one_and_update(
        {
            "_id": signal_object_id,
            "processedByAi": {"$ne": True},
            "aiProcessing": {"$ne": True},
        },
        {
            "$set": {
                "aiProcessing": True,
                "aiProcessingStartedAt": _utc_now(),
            }
        },
        return_document=ReturnDocument.AFTER,
    )

    if not signal:
        existing_signal = v2_signals.find_one({"_id": signal_object_id}, {"processedByAi": 1, "aiProcessing": 1})
        if not existing_signal:
            LOGGER.warning("Signal not found for _id=%s", signal_id)
            return
        if existing_signal.get("processedByAi") is True:
            LOGGER.info("Skipping already processed signal _id=%s", signal_id)
            return
        LOGGER.info("Skipping signal currently being processed _id=%s", signal_id)
        return

    try:
        raw_text = _safe_string(signal.get("rawText"))
        hashtags = signal.get("hashtags") if isinstance(signal.get("hashtags"), list) else []
        platform = _safe_string(signal.get("platform")).upper()
        author_username = _extract_author_username(signal)
        category = _safe_string(signal.get("category")).upper()

        LOGGER.info("Processing signal _id=%s", signal_id)
        
        extraction = extract_trend_data(
            raw_text=raw_text,
            hashtags=hashtags,
            platform=platform,
            author_username=author_username,
            category=category,
        )

        trend_name = _normalize_name(_safe_string(extraction.get("name")))
        if not trend_name:
            raise ValueError("LLM returned empty trend name.")

        now = _utc_now()
        signal_score = _calculate_signal_score(signal)

        LOGGER.info("Upserting trend: %s", trend_name)
        trend_document = upsert_trend_document(
            trend_payload=extraction,
            signal_object_id=signal_object_id,
            signal_score=signal_score,
            signal=signal,
            now=now,
        )

        v2_signals.update_one(
            {"_id": signal_object_id},
            {
                "$set": {
                    "processedByAi": True,
                    "aiTrendId": trend_document.get("_id"),
                    "aiBrandNames": _safe_string_list(extraction.get("aiBrandNames")),
                    "aiProcessedAt": now,
                    "aiExtraction": extraction,
                    "extractedKeywords": _safe_string_list(
                        extraction.get("extractedKeywords"), lowercase=True
                    )[:5],
                },
                "$unset": {
                    "aiProcessing": "",
                    "aiProcessingStartedAt": "",
                },
            },
        )

        # ── Phase 2: AI-powered underdog product selection ──────────────
        scraped_products = signal.get("scrapedProducts")
        if isinstance(scraped_products, list) and scraped_products:
            author_username = _extract_author_username(signal)
            LOGGER.info(
                "Starting AI product selection for signal %s (%d scraped products)",
                signal_id, len(scraped_products),
            )

            selected = select_underdog_product(
                products=scraped_products,
                trend_data=extraction,
                category=category,
                raw_text=raw_text,
                hashtags=hashtags,
            )

            if selected:
                # Build the ProductDetail-compatible document
                underdog_doc = {
                    "brandName": author_username,
                    "title": selected.get("productName", ""),
                    "price": int(selected["mainPrice"]) if selected.get("mainPrice") else None,
                    "originalPrice": int(selected["originalPrice"]) if selected.get("originalPrice") else None,
                    "currency": selected.get("currency", "Rs."),
                    "shopUrl": selected.get("productUrl"),
                    "imageUrl": selected.get("imageUrl"),
                    "codAvailable": True,
                }

                # Write underdog product back to the signal
                v2_signals.update_one(
                    {"_id": signal_object_id},
                    {"$set": {"underdogProduct": underdog_doc}},
                )

                # Also update the trend's signalProducts.underdog
                trends.update_one(
                    {"_id": trend_document.get("_id")},
                    {"$set": {"signalProducts.underdog": underdog_doc}},
                )

                LOGGER.info(
                    "AI selected underdog '%s' for signal %s → trend %s",
                    underdog_doc["title"], signal_id, trend_name,
                )
            else:
                LOGGER.info(
                    "AI found no suitable underdog product for signal %s (category=%s, trend=%s)",
                    signal_id, category, trend_name,
                )
        else:
            LOGGER.debug("No scraped products on signal %s, skipping product selection", signal_id)

        # Now that Phase 1 and 2 are complete, mark the trend as PENDING so Java can enrich it.
        trends.update_one(
            {"_id": trend_document.get("_id")},
            {"$set": {"enrichmentStatus": "PENDING"}}
        )

        LOGGER.info(
            "Signal %s processed successfully: trend=%s",
            signal_id,
            trend_name,
        )
    except Exception:
        v2_signals.update_one(
            {"_id": signal_object_id},
            {
                "$unset": {
                    "aiProcessing": "",
                    "aiProcessingStartedAt": "",
                }
            },
        )
        LOGGER.exception("Failed while processing signal _id=%s", signal_id)
        raise
    finally:
        time.sleep(2.5)