import os
from pathlib import Path

from dotenv import load_dotenv


PROJECT_ROOT = Path(__file__).resolve().parent


def load_project_env() -> str | None:
    candidates = [
        PROJECT_ROOT / ".env",
        PROJECT_ROOT / "venv" / ".env",
    ]

    for candidate in candidates:
        if candidate.exists():
            load_dotenv(candidate, override=False)
            return str(candidate)

    load_dotenv(override=False)
    return None


def get_env_value(name: str, default: str | None = None) -> str | None:
    return os.getenv(name, default)
