"""Persistent per-recording features used by CIST prefetch and final analysis."""

import hashlib
import json
import sqlite3
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator


CACHE_SCHEMA_VERSION = "cist-clip-features-v1"


def feature_key(
    *,
    recording_id: str,
    question_code: str,
    variant_id: str,
    question_set_version: str,
    raw_transcript: str,
    audio_sha256: str | None,
    ast_model_version: str,
    kcelectra_model_version: str,
) -> str:
    """Exclude expiring signed URLs while invalidating changed inputs/models."""
    identity = {
        "schema": CACHE_SCHEMA_VERSION,
        "recording_id": recording_id,
        "question_code": question_code,
        "variant_id": variant_id,
        "question_set_version": question_set_version,
        "raw_transcript": raw_transcript,
        "audio_sha256": audio_sha256,
        "ast_model_version": ast_model_version,
        "kcelectra_model_version": kcelectra_model_version,
    }
    encoded = json.dumps(identity, sort_keys=True, ensure_ascii=False).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


class SQLiteClipFeatureRepository:
    def __init__(self, database_path: Path) -> None:
        self._path = database_path.resolve()
        self._path.parent.mkdir(parents=True, exist_ok=True)
        with self._connection() as connection:
            connection.execute(
                """CREATE TABLE IF NOT EXISTS clip_feature_cache (
                    cache_key TEXT PRIMARY KEY,
                    recording_id TEXT NOT NULL,
                    features_json TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )"""
            )

    def get(self, cache_key: str) -> dict[str, Any] | None:
        with self._connection() as connection:
            row = connection.execute(
                "SELECT features_json FROM clip_feature_cache WHERE cache_key = ?",
                (cache_key,),
            ).fetchone()
        return json.loads(row[0]) if row is not None else None

    def put(self, cache_key: str, recording_id: str, features: dict[str, Any]) -> None:
        with self._connection() as connection:
            connection.execute(
                """INSERT OR REPLACE INTO clip_feature_cache
                   (cache_key, recording_id, features_json) VALUES (?, ?, ?)""",
                (cache_key, recording_id, json.dumps(features, allow_nan=False)),
            )

    @contextmanager
    def _connection(self) -> Iterator[sqlite3.Connection]:
        connection = sqlite3.connect(self._path, timeout=30)
        try:
            with connection:
                yield connection
        finally:
            connection.close()
