"""TOTP（RFC 6238）最小实现：仅依赖标准库，供内部账号二次验证使用。

- 30 秒步长、6 位数字、HMAC-SHA1，与常见认证器（Google Authenticator 等）兼容。
- 允许 ±1 个时间步的时钟偏差。
- `last_step` 单调前进：同一码不能重放，旧时间窗的码也不能再用。
"""

import base64
import hashlib
import hmac
import secrets
import struct
import time
from urllib.parse import quote, urlencode

STEP_SECONDS = 30
DIGITS = 6
WINDOW = 1


def generate_secret(byte_length: int = 20) -> str:
    """生成 Base32 密钥（无填充），便于手工录入认证器。"""
    return base64.b32encode(secrets.token_bytes(byte_length)).decode("ascii").rstrip("=")


def _key(secret: str) -> bytes:
    padded = secret + "=" * ((8 - len(secret) % 8) % 8)
    return base64.b32decode(padded, casefold=True)


def _code_at(key: bytes, step: int) -> str:
    digest = hmac.new(key, struct.pack(">Q", step), hashlib.sha1).digest()
    offset = digest[-1] & 0x0F
    binary = struct.unpack(">I", digest[offset : offset + 4])[0] & 0x7FFFFFFF
    return str(binary % (10**DIGITS)).zfill(DIGITS)


def current_step(at: float | None = None) -> int:
    return int((time.time() if at is None else at) // STEP_SECONDS)


def generate_code(secret: str, at: float | None = None) -> str:
    """生成指定时刻的验证码；用于测试与运维排查。"""
    return _code_at(_key(secret), current_step(at))


def verify(
    secret: str,
    code: str,
    *,
    last_step: int | None = None,
    at: float | None = None,
) -> int | None:
    """校验验证码；成功返回时间步，失败返回 None。

    `last_step` 为该账号上次成功使用的时间步，用于拒绝重放与旧码。
    """
    candidate = code.strip()
    if len(candidate) != DIGITS or not candidate.isdigit():
        return None
    try:
        key = _key(secret)
    except (ValueError, TypeError):
        return None

    now = current_step(at)
    for step in range(now - WINDOW, now + WINDOW + 1):
        if last_step is not None and step <= last_step:
            continue
        if hmac.compare_digest(_code_at(key, step), candidate):
            return step
    return None


def provisioning_uri(secret: str, account_name: str, issuer: str = "rehab-trainer") -> str:
    """生成认证器可扫描/手工录入的 otpauth URI。"""
    label = quote(f"{issuer}:{account_name}")
    query = urlencode(
        {
            "secret": secret,
            "issuer": issuer,
            "algorithm": "SHA1",
            "digits": DIGITS,
            "period": STEP_SECONDS,
        }
    )
    return f"otpauth://totp/{label}?{query}"