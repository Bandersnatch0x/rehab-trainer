import hashlib
import hmac
import os
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any

import jwt

from app.core.config import get_settings

PBKDF2_ALGORITHM = "pbkdf2_sha256"
PBKDF2_ITERATIONS = 210_000


def hash_password(password: str) -> str:
    salt = os.urandom(16)
    digest = hashlib.pbkdf2_hmac(
        "sha256", password.encode("utf-8"), salt, PBKDF2_ITERATIONS
    )
    return (
        f"{PBKDF2_ALGORITHM}${PBKDF2_ITERATIONS}$"
        f"{salt.hex()}${digest.hex()}"
    )


def verify_password(password: str, stored: str | None) -> bool:
    if not stored:
        return False
    try:
        algorithm, iterations, salt_hex, digest_hex = stored.split("$")
        if algorithm != PBKDF2_ALGORITHM:
            return False
        digest = hashlib.pbkdf2_hmac(
            "sha256",
            password.encode("utf-8"),
            bytes.fromhex(salt_hex),
            int(iterations),
        )
    except (ValueError, TypeError):
        return False
    return hmac.compare_digest(digest.hex(), digest_hex)


class InvalidTokenError(Exception):
    pass


def create_token(
    *,
    account_id: str,
    org_id: str,
    roles: list[str],
    token_type: str,
    ttl_seconds: int,
) -> str:
    settings = get_settings()
    now = datetime.now(timezone.utc)
    payload: dict[str, Any] = {
        "sub": account_id,
        "org_id": org_id,
        "roles": roles,
        "typ": token_type,
        "iat": now,
        "exp": now + timedelta(seconds=ttl_seconds),
        "jti": uuid.uuid4().hex,
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)


def decode_token(token: str, expected_type: str) -> dict[str, Any]:
    settings = get_settings()
    try:
        payload = jwt.decode(
            token,
            settings.jwt_secret,
            algorithms=[settings.jwt_algorithm],
        )
    except jwt.PyJWTError as exc:
        raise InvalidTokenError(str(exc)) from exc
    if payload.get("typ") != expected_type:
        raise InvalidTokenError("token type mismatch")
    if not payload.get("sub") or not payload.get("org_id"):
        raise InvalidTokenError("token payload incomplete")
    return payload


def create_token_pair(account_id: str, org_id: str, roles: list[str]) -> dict[str, Any]:
    settings = get_settings()
    return {
        "accessToken": create_token(
            account_id=account_id,
            org_id=org_id,
            roles=roles,
            token_type="access",
            ttl_seconds=settings.access_token_ttl_seconds,
        ),
        "refreshToken": create_token(
            account_id=account_id,
            org_id=org_id,
            roles=roles,
            token_type="refresh",
            ttl_seconds=settings.refresh_token_ttl_seconds,
        ),
        "expiresIn": settings.access_token_ttl_seconds,
    }
