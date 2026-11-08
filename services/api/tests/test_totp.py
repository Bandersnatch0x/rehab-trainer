from sqlalchemy import select

from app.core import totp
from app.models.audit import AuditEvent
from app.models.identity import Account
from tests.helpers import API, auth_headers, login


def test_verify_accepts_current_and_adjacent_steps():
    secret = totp.generate_secret()
    code = totp.generate_code(secret)
    step = totp.current_step()
    assert totp.verify(secret, code) == step

    # 时钟偏差 ±1 步仍可接受。
    previous = totp.generate_code(secret, at=(step - 1) * totp.STEP_SECONDS)
    assert totp.verify(secret, previous) == step - 1


def test_verify_rejects_replay_old_steps_and_malformed_codes():
    secret = totp.generate_secret()
    step = totp.current_step()
    code = totp.generate_code(secret)
    assert totp.verify(secret, code, last_step=step) is None
    assert totp.verify(secret, "12345") is None
    assert totp.verify(secret, "abcdef") is None
    assert totp.verify("not-base32!!", code) is None


def test_provisioning_uri_contains_secret_and_issuer():
    secret = totp.generate_secret()
    uri = totp.provisioning_uri(secret, "admin-a")
    assert uri.startswith("otpauth://totp/")
    assert f"secret={secret}" in uri
    assert "issuer=rehab-trainer" in uri
# ---------- 自助启用/停用端点 ----------


def _enroll(client, token):
    response = client.post(f"{API}/auth/totp/enroll", headers=auth_headers(token))
    assert response.status_code == 200, response.text
    return response.json()["secret"]


def _status(client, token) -> bool:
    response = client.get(f"{API}/auth/totp", headers=auth_headers(token))
    assert response.status_code == 200, response.text
    return response.json()["totpEnabled"]


def test_enroll_returns_secret_but_does_not_enable_before_confirm(client, seed):
    token = login(client, "therapist-a")

    secret = _enroll(client, token)
    assert len(secret) >= 16
    # 关键：拿到密钥不等于已启用。确认前放弃，账号不会被锁在没录入的密钥上。
    assert _status(client, token) is False


def test_confirm_enables_totp_and_code_cannot_be_replayed(client, seed):
    token = login(client, "therapist-a")
    secret = _enroll(client, token)
    code = totp.generate_code(secret)

    confirmed = client.post(
        f"{API}/auth/totp/confirm",
        json={"secret": secret, "code": code},
        headers=auth_headers(token),
    )
    assert confirmed.status_code == 200, confirmed.text
    assert confirmed.json()["totpEnabled"] is True
    assert _status(client, token) is True

    missing = client.post(
        f"{API}/auth/login",
        json={"username": "therapist-a", "password": "secret123"},
    )
    assert missing.status_code == 401
    assert "二次验证" in missing.json()["message"]

    # 确认时消费掉的时间步不能紧接着用于登录。
    replay = client.post(
        f"{API}/auth/login",
        json={"username": "therapist-a", "password": "secret123", "totp": code},
    )
    assert replay.status_code == 401


def test_confirm_rejects_wrong_code_and_keeps_totp_off(client, seed):
    token = login(client, "therapist-a")
    secret = _enroll(client, token)

    response = client.post(
        f"{API}/auth/totp/confirm",
        json={"secret": secret, "code": "000000"},
        headers=auth_headers(token),
    )
    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_FAILED"
    assert _status(client, token) is False


def test_enroll_and_confirm_reject_when_already_enabled(client, seed):
    token = login(client, "therapist-a")
    secret = _enroll(client, token)
    client.post(
        f"{API}/auth/totp/confirm",
        json={"secret": secret, "code": totp.generate_code(secret)},
        headers=auth_headers(token),
    )

    enroll_again = client.post(f"{API}/auth/totp/enroll", headers=auth_headers(token))
    assert enroll_again.status_code == 409

    confirm_again = client.post(
        f"{API}/auth/totp/confirm",
        json={"secret": totp.generate_secret(), "code": "123456"},
        headers=auth_headers(token),
    )
    assert confirm_again.status_code == 409


def test_disable_requires_password_and_code(client, seed, db_session):
    token = login(client, "therapist-a")
    secret = totp.generate_secret()
    account = db_session.get(Account, "therapist-a")
    account.totp_secret = secret
    account.totp_last_step = None
    db_session.commit()

    wrong_password = client.post(
        f"{API}/auth/totp/disable",
        json={"password": "wrong-password", "code": totp.generate_code(secret)},
        headers=auth_headers(token),
    )
    assert wrong_password.status_code == 422

    wrong_code = client.post(
        f"{API}/auth/totp/disable",
        json={"password": "secret123", "code": "000000"},
        headers=auth_headers(token),
    )
    assert wrong_code.status_code == 422

    ok = client.post(
        f"{API}/auth/totp/disable",
        json={"password": "secret123", "code": totp.generate_code(secret)},
        headers=auth_headers(token),
    )
    assert ok.status_code == 200, ok.text
    assert ok.json()["totpEnabled"] is False

    # 停用后登录不再要验证码。
    after = client.post(
        f"{API}/auth/login",
        json={"username": "therapist-a", "password": "secret123"},
    )
    assert after.status_code == 200, after.text


def test_disable_rejects_when_not_enabled(client, seed):
    token = login(client, "therapist-a")
    response = client.post(
        f"{API}/auth/totp/disable",
        json={"password": "secret123", "code": "123456"},
        headers=auth_headers(token),
    )
    assert response.status_code == 409


def test_totp_changes_are_audited(client, seed, db_session):
    token = login(client, "therapist-a")
    secret = _enroll(client, token)
    client.post(
        f"{API}/auth/totp/confirm",
        json={"secret": secret, "code": totp.generate_code(secret)},
        headers=auth_headers(token),
    )
    client.post(
        f"{API}/auth/totp/disable",
        json={"password": "secret123", "code": totp.generate_code(secret)},
        headers=auth_headers(token),
    )

    db_session.expire_all()
    actions = db_session.scalars(
        select(AuditEvent.action).where(
            AuditEvent.action.in_(["account.totp_enabled", "account.totp_disabled"])
        )
    ).all()
    assert sorted(actions) == ["account.totp_disabled", "account.totp_enabled"]
