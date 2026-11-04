import base64
import binascii

from app.core.errors import api_error


def decode_cursor(cursor: str | None) -> int:
    """游标编码为 offset 的 base64；不透明字符串，客户端不应解析。"""
    if not cursor:
        return 0
    try:
        raw = base64.urlsafe_b64decode(cursor.encode("ascii")).decode("ascii")
        offset = int(raw)
    except (binascii.Error, UnicodeDecodeError, ValueError):
        raise api_error(422, "VALIDATION_FAILED", "cursor 无效")
    if offset < 0:
        raise api_error(422, "VALIDATION_FAILED", "cursor 无效")
    return offset


def encode_cursor(offset: int) -> str:
    return base64.urlsafe_b64encode(str(offset).encode("ascii")).decode("ascii")
