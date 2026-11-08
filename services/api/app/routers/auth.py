from datetime import timedelta
from uuid import uuid4

from fastapi import APIRouter, Depends
from sqlalchemy import delete, func, or_, select, update
from sqlalchemy.orm import Session

from app.core import totp
from app.core.audit import record_audit
from app.core.auth import Principal, build_principal, get_principal
from app.core.config import get_settings
from app.core.database import get_db
from app.core.errors import api_error
from app.core.security import (
    InvalidTokenError,
    create_token_pair,
    create_totp_enrollment_token,
    decode_token,
    verify_password,
)
from app.core.timeutils import utcnow
from app.models.identity import Account, BindingToken, LoginFailure
from app.schemas.auth import (
    BindRequest,
    LoginRequest,
    LoginResponse,
    Me,
    RefreshRequest,
    TokenPair,
    TotpConfirmRequest,
    TotpDisableRequest,
    TotpEnrollment,
    TotpEnrollRequest,
    TotpStatus,
)

router = APIRouter(tags=["auth"])


def _check_login_rate_limit(db: Session, username: str) -> None:
    settings = get_settings()
    cutoff = utcnow() - timedelta(seconds=settings.login_window_seconds)
    recent = db.scalar(
        select(func.count())
        .select_from(LoginFailure)
        .where(
            LoginFailure.username == username,
            LoginFailure.created_at >= cutoff,
        )
    )
    if (recent or 0) >= settings.login_max_failures:
        raise api_error(429, "RATE_LIMITED", "尝试次数过多，请稍后再试")


def _record_login_failure(db: Session, username: str) -> None:
    """失败记录落库：多副本共享计数，重启不清零。"""
    settings = get_settings()
    cutoff = utcnow() - timedelta(seconds=settings.login_window_seconds)
    db.execute(
        delete(LoginFailure).where(
            LoginFailure.username == username,
            LoginFailure.created_at < cutoff,
        )
    )
    db.add(
        LoginFailure(id=uuid4().hex, username=username, created_at=utcnow())
    )
    db.commit()


def _clear_login_failures(db: Session, username: str) -> None:
    db.execute(delete(LoginFailure).where(LoginFailure.username == username))
    db.commit()


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
    _check_login_rate_limit(db, payload.username)

    account = db.scalar(select(Account).where(Account.username == payload.username))
    if (
        account is None
        or account.status != "active"
        or not verify_password(payload.password, account.password_hash)
    ):
        _record_login_failure(db, payload.username)
        raise api_error(401, "UNAUTHORIZED", "用户名或密码错误")

    # 启用二次验证的账号必须提交有效 TOTP；同一时间步不可重放。
    if account.totp_secret:
        step = totp.verify(
            account.totp_secret,
            payload.totp or "",
            last_step=account.totp_last_step,
        )
        if step is None:
            _record_login_failure(db, payload.username)
            raise api_error(401, "UNAUTHORIZED", "二次验证码错误或已失效")
        # 条件更新保证同一时间步只能成功一次；并发重放在这里被拦下。
        consumed = db.execute(
            update(Account)
            .where(
                Account.account_id == account.account_id,
                or_(
                    Account.totp_last_step.is_(None),
                    Account.totp_last_step < step,
                ),
            )
            .values(totp_last_step=step)
        )
        if consumed.rowcount != 1:
            _record_login_failure(db, payload.username)
            raise api_error(401, "UNAUTHORIZED", "二次验证码已使用，请等待下一个验证码")

    _clear_login_failures(db, payload.username)
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


def _account_for(db: Session, principal: Principal) -> Account:
    account = db.get(Account, principal.account_id)
    if account is None or account.status != "active":
        raise api_error(401, "UNAUTHORIZED", "未认证或令牌失效")
    return account


@router.get("/auth/totp", response_model=TotpStatus)
def get_totp_status(
    principal: Principal = Depends(get_principal),
    db: Session = Depends(get_db),
):
    account = _account_for(db, principal)
    return TotpStatus(totp_enabled=account.totp_secret is not None)


@router.post("/auth/totp/enroll", response_model=TotpEnrollment)
def enroll_totp(
    payload: TotpEnrollRequest,
    principal: Principal = Depends(get_principal),
    db: Session = Depends(get_db),
):
    """生成待确认的密钥；**此时不写库**。

    只凭会话不够：必须先验证当前口令，否则被盗会话可给自己绑一个攻击者控制的
    认证器并把真实用户锁在账号外。密钥同时签进短时凭证，confirm 只认本次结果。
    """
    account = _account_for(db, principal)
    if account.totp_secret:
        raise api_error(409, "CONFLICT", "二次验证已启用，请先停用再更换")
    _check_login_rate_limit(db, account.username)
    if not verify_password(payload.password, account.password_hash):
        _record_login_failure(db, account.username)
        raise api_error(422, "VALIDATION_FAILED", "登录口令不正确")
    secret = totp.generate_secret()
    return TotpEnrollment(
        secret=secret,
        provisioning_uri=totp.provisioning_uri(secret, account.username),
        enrollment_token=create_totp_enrollment_token(
            account_id=account.account_id,
            org_id=account.org_id,
            secret=secret,
        ),
    )


@router.post("/auth/totp/confirm", response_model=TotpStatus)
def confirm_totp(
    payload: TotpConfirmRequest,
    principal: Principal = Depends(get_principal),
    db: Session = Depends(get_db),
):
    account = _account_for(db, principal)
    if account.totp_secret:
        raise api_error(409, "CONFLICT", "二次验证已启用，请先停用再更换")
    try:
        claims = decode_token(payload.enrollment_token, "totp_enroll")
    except InvalidTokenError:
        raise api_error(422, "VALIDATION_FAILED", "启用会话已失效，请重新生成密钥")
    if claims.get("sub") != account.account_id:
        raise api_error(422, "VALIDATION_FAILED", "启用会话与当前账号不匹配")
    secret = claims.get("secret")
    if not isinstance(secret, str):
        raise api_error(422, "VALIDATION_FAILED", "启用会话缺少密钥，请重新生成")
    step = totp.verify(secret, payload.code)
    if step is None:
        raise api_error(422, "VALIDATION_FAILED", "验证码不正确，请确认认证器时间与密钥")

    account.totp_secret = secret
    # 记下已消费的时间步：确认时用过的码不能紧接着再用于登录。
    account.totp_last_step = step
    record_audit(
        db,
        org_id=account.org_id,
        actor_account_id=account.account_id,
        action="account.totp_enabled",
        resource_type="account",
        resource_id=account.account_id,
    )
    db.commit()
    return TotpStatus(totp_enabled=True)


@router.post("/auth/totp/disable", response_model=TotpStatus)
def disable_totp(
    payload: TotpDisableRequest,
    principal: Principal = Depends(get_principal),
    db: Session = Depends(get_db),
):
    """停用需同时提供口令与当前验证码：只凭会话不能关掉二次验证。"""
    account = _account_for(db, principal)
    if not account.totp_secret:
        raise api_error(409, "CONFLICT", "二次验证尚未启用")
    # 停用同样校验口令+验证码，必须套用登录限流，否则 6 位码可被在线穷举。
    _check_login_rate_limit(db, account.username)

    # 两项都算完再判断，避免用响应快慢区分「口令错」还是「码错」。
    # 传 last_step 拒绝已消费的验证码：否则密码 + 单个码泄露即可登录并立即关闭二次验证。
    password_ok = verify_password(payload.password, account.password_hash)
    step = totp.verify(
        account.totp_secret, payload.code, last_step=account.totp_last_step
    )
    code_ok = step is not None
    if not (password_ok and code_ok):
        _record_login_failure(db, account.username)
        record_audit(
            db,
            org_id=account.org_id,
            actor_account_id=account.account_id,
            action="account.totp_disabled",
            outcome="denied",
            resource_type="account",
            resource_id=account.account_id,
        )
        db.commit()
        raise api_error(422, "VALIDATION_FAILED", "口令或验证码不正确")

    account.totp_secret = None
    account.totp_last_step = None
    record_audit(
        db,
        org_id=account.org_id,
        actor_account_id=account.account_id,
        action="account.totp_disabled",
        resource_type="account",
        resource_id=account.account_id,
    )
    db.commit()
    return TotpStatus(totp_enabled=False)


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
