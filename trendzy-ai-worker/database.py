import logging
import os

from pymongo import MongoClient
from pymongo.uri_parser import parse_uri

from env_loader import load_project_env

load_project_env()

LOGGER = logging.getLogger(__name__)

DEFAULT_MONGO_URI = "mongodb://localhost:27017/trendzy"
MONGO_URI = os.getenv("MONGO_URI", DEFAULT_MONGO_URI)

LOGGER.info("Connecting to MongoDB at %s", MONGO_URI)

mongo_client = MongoClient(MONGO_URI)

parsed_uri = parse_uri(MONGO_URI)
database_name = parsed_uri.get("database") or "trendzy"
db = mongo_client[database_name]

v2_signals = db["v2_signals"]
trends = db["trends"]
aesthetics = db["aesthetics"]
brands = db["brands"]
token_usage = db["token_usage"]
