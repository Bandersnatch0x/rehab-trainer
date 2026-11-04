from datetime import datetime, timezone


def utcnow() -> datetime:
    """返回不带时区信息的 UTC 时间，便于 SQLite 与 PostgreSQL 共用。"""
    return datetime.now(timezone.utc).replace(tzinfo=None)


def to_utc_naive(value: datetime | None) -> datetime | None:
    if value is None:
        return None
    if value.tzinfo is None:
        return value
    return value.astimezone(timezone.utc).replace(tzinfo=None)
