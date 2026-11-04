import time
from collections import defaultdict

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.auth import Principal, build_principal, get_principal
from app.core.config import get_settings
from app.core.database import get_db
from app.core.errors import api_error
from app.core.security import (
    InvalidTokenError,
    create_token_pair,
    decode_token,
    verify_password,
)
from app.core.timeutils import utcnow
from app.models.identity import Account, BindingToken
from app.schemas.auth import (
    BindRequest,
    LoginRequest,
    LoginResponse,
    Me,
    RefreshRequest,
    TokenPair,
)

router = APIRouter(tags=["auth"])

# 进程内登录失败计数；生产多副本部署时应换 Redis 等共享存储。
_login_failures: dict[str, list[float]] = defaultdict(list)


def _me(principal: Principal) -> Me:
    return Me(
        account_id=principal.account_id,
        display_name=principal.display_name,
        roles=list(principal.roles),
        org_id=principal.org_id,
        org_name=principal.org_name,
    )


def _token_pair_for(db: Session, account: Account) -> dict:
    principal = build_principal(db, account)
    return create_token_pair(
        account.account_id, account.org_id, list(principal.roles)
    )


def _check_login_rate_limit(username: str) -> None:
    settings = get_settings()
    cutoff = time.time() - settings.login_window_seconds
    recent = [ts for ts in _login_failures[username] if ts >= cutoff]
    _login_failures[username] = recent
    if len(recent) >= settings.login_max_failures:
        raise api_error(429, "RATE_LIMITED", "尝试次数过多，请稍后再试")


def _record_login_failure(username: str) -> None:
    _login_failures[username].append(time.time())


@router.post("/auth/bind", response_model=TokenPair)
def bind(payload: BindRequest, db: Session = Depends(get_db)):
    token = db.get(BindingToken, payload.binding_token)
    if token is None:
        raise api_error(401, "UNAUTHORIZED", "绑定码无效")
    if token.used_at is not None or token.expires_at < utcnow():
        raise api_error(409, "CONFLICT", "绑定码已使用或已过期")

    account = db.get(Account, token.account_id)
    if account is None or account.status != "active" or account.org_id != token.org_id:
        raise api_error(401, "UNAUTHORIZED", "绑定码无效")

    token.used_at = utcnow()
    db.commit()
    return _token_pair_for(db, account)


@router.post("/auth/login", response_model=LoginResponse)
def login(payload: LoginRequest, db: Session = Depends(get_db)):
    _check_login_rate_limit(payload.username)

    account = db.scalar(select(Account).where(Account.username == payload.username))
    if (
        account is None
        or account.status != "active"
        or not verify_password(payload.password, account.password_hash)
    ):
        _record_login_failure(payload.username)
        raise api_error(401, "UNAUTHORIZED", "用户名或密码错误")

    # TODO: 待实现 TOTP 二次验证；当前契约字段仅接收，不校验。
    _login_failures.pop(payload.username, None)
    principal = build_principal(db, account)
    pair = _token_pair_for(db, account)
    return LoginResponse(
        access_token=pair["accessToken"],
        refresh_token=pair["refreshToken"],
        expires_in=pair["expiresIn"],
        profile=_me(principal),
    )


@router.get("/me", response_model=Me)
def me(principal: Principal = Depends(get_principal)):
    return _me(principal)


@router.post("/auth/refresh", response_model=TokenPair)
def refresh(payload: RefreshRequest, db: Session = Depends(get_db)):
    try:
        token_payload = decode_token(payload.refresh_token, expected_type="refresh")
    except InvalidTokenError:
        raise api_error(401, "UNAUTHORIZED", "未认证或令牌失效")

    account = db.get(Account, token_payload["sub"])
    if (
        account is None
        or account.status != "active"
        or token_payload.get("org_id") != account.org_id
    ):
        raise api_error(401, "UNAUTHORIZED", "未认证或令牌失效")

    return _token_pair_for(db, account)
