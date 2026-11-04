"""不透明 keyset 游标。

编码「排序键 + 唯一 tie-breaker」而不是 offset：翻页期间有新增或删除时，
offset 分页会重复或漏行，keyset 不会。游标对客户端不透明，客户端不应解析。
"""

import base64
import binascii
from datetime import datetime
import json

from sqlalchemy import and_, or_

from app.core.errors import api_error


def encode_cursor(**values: str) -> str:
    raw = json.dumps(values, separators=(",", ":"), sort_keys=True)
    return base64.urlsafe_b64encode(raw.encode("utf-8")).decode("ascii").rstrip("=")


def decode_cursor(cursor: str | None, *, keys: tuple[str, ...]) -> dict[str, str] | None:
    """解析游标；键集合必须与调用方声明的一致，否则视为无效。"""
    if not cursor:
        return None
    try:
        padded = cursor + "=" * (-len(cursor) % 4)
        payload = json.loads(base64.urlsafe_b64decode(padded.encode("ascii")))
    except (binascii.Error, UnicodeDecodeError, ValueError):
        raise api_error(422, "VALIDATION_FAILED", "cursor 无效")

    if not isinstance(payload, dict) or set(payload) != set(keys):
        raise api_error(422, "VALIDATION_FAILED", "cursor 无效")
    if not all(isinstance(value, str) and value for value in payload.values()):
        raise api_error(422, "VALIDATION_FAILED", "cursor 无效")
    return payload


def _parse_key(value: str, key_type) -> object:
    try:
        return key_type(value)
    except (TypeError, ValueError):
        raise api_error(422, "VALIDATION_FAILED", "cursor 无效")


def apply_keyset(
    stmt,
    key_col,
    id_col,
    cursor: dict[str, str] | None,
    *,
    descending: bool,
    key_type=str,
):
    """按 (key, id) 做严格的「之后」过滤，与 order_by 的方向一致。

    用 OR 展开而不是行值比较，避免不同数据库对 row value 的支持差异。
    """
    if cursor is None:
        return stmt

    key = _parse_key(cursor["k"], key_type)
    ident = cursor["i"]

    if descending:
        return stmt.where(
            or_(key_col < key, and_(key_col == key, id_col < ident))
        )
    return stmt.where(or_(key_col > key, and_(key_col == key, id_col > ident)))


def cursor_from(*, key: str, ident: str) -> str:
    return encode_cursor(k=key, i=ident)


def datetime_key(value: datetime) -> str:
    return value.isoformat()
