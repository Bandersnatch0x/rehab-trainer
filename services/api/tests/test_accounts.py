from sqlalchemy import select

from app.models.audit import AuditEvent
from tests.helpers import API, auth_headers, login

ORG_A_USERNAMES = {
    "admin-a",
    "therapist-a",
    "patient-a",
    "caregiver-a",
    "patient-unconfirmed",
    "patient-hy5",
}
ORG_B_USERNAMES = {"therapist-b", "patient-b"}


def _contains_secret_key(value) -> bool:
    if isinstance(value, dict):
        if any(key in ("password", "passwordHash") for key in value):
            return True
        return any(_contains_secret_key(item) for item in value.values())
    if isinstance(value, list):
        return any(_contains_secret_key(item) for item in value)
    return False


def _find_username(response_body, username):
    return next(
        (item for item in response_body["items"] if item["username"] == username),
        None,
    )


def test_care_admin_lists_org_accounts(client, seed):
    token = login(client, "admin-a")
    response = client.get(f"{API}/accounts?limit=100", headers=auth_headers(token))
    assert response.status_code == 200, response.text
    body = response.json()

    usernames = {item["username"] for item in body["items"]}
    assert usernames <= ORG_A_USERNAMES, usernames & ORG_B_USERNAMES
    assert "admin-a" in usernames
    assert "therapist-a" in usernames
    assert not _contains_secret_key(body)


def test_therapist_can_list_org_accounts(client, seed):
    # 治疗师要看得到同事名册才能把同事拉进来；患者与照护者不属于管理端角色。
    token = login(client, "therapist-a")
    response = client.get(f"{API}/accounts?limit=100", headers=auth_headers(token))

    assert response.status_code == 200, response.text
    usernames = {item["username"] for item in response.json()["items"]}
    assert "therapist-a" in usernames


def test_patient_and_caregiver_cannot_list_accounts(client, seed, db_session):
    actors = {
        "patient-a": "patient-a-account",
        "caregiver-a": "caregiver-a",
    }
    for username, actor in actors.items():
        token = login(client, username)
        response = client.get(f"{API}/accounts", headers=auth_headers(token))
        assert response.status_code == 403, response.text
        assert response.json()["code"] == "FORBIDDEN"

        denied = db_session.scalars(
            select(AuditEvent).where(
                AuditEvent.action == "authz.role_denied",
                AuditEvent.outcome == "denied",
                AuditEvent.actor_account_id == actor,
            )
        ).all()
        assert denied, f"{username} 拒绝访问应写审计"


def _create(client, token, username, role):
    return client.post(
        f"{API}/accounts",
        headers=auth_headers(token),
        json={
            "displayName": "新同事",
            "username": username,
            "password": "zqgn-vptl-461",
            "role": role,
        },
    )


def test_therapist_can_create_therapist(client, seed):
    token = login(client, "therapist-a")
    response = _create(client, token, "new-therapist-1", "rehab_therapist")

    assert response.status_code == 201, response.text
    assert response.json()["roles"] == ["rehab_therapist"]


def test_therapist_cannot_create_care_admin(client, seed, db_session):
    # 授予 care_admin 等于拿到分级确认与开方权，治疗师不能自造管理员。
    token = login(client, "therapist-a")
    response = _create(client, token, "new-admin-1", "care_admin")

    assert response.status_code == 403, response.text
    assert response.json()["code"] == "FORBIDDEN"
    denied = db_session.scalars(
        select(AuditEvent).where(
            AuditEvent.action == "authz.role_denied",
            AuditEvent.actor_account_id == "therapist-a",
        )
    ).all()
    assert denied, "越权授予应写审计"


def test_therapist_cannot_touch_admin_account(client, seed):
    # 目标账号是管理员时，治疗师连改名都不行。
    token = login(client, "therapist-a")
    response = client.patch(
        f"{API}/accounts/admin-a",
        headers=auth_headers(token),
        json={"displayName": "被治疗师改名"},
    )

    assert response.status_code == 403, response.text
    assert response.json()["code"] == "FORBIDDEN"


def test_therapist_cannot_promote_anyone_to_admin(client, seed):
    token = login(client, "therapist-a")
    created = _create(client, token, "promote-me", "rehab_therapist")
    assert created.status_code == 201, created.text

    response = client.patch(
        f"{API}/accounts/{created.json()['accountId']}",
        headers=auth_headers(token),
        json={"role": "care_admin"},
    )

    assert response.status_code == 403, response.text


def test_therapist_can_disable_another_therapist(client, seed):
    token = login(client, "therapist-a")
    created = _create(client, token, "disable-me", "rehab_therapist")
    account_id = created.json()["accountId"]

    response = client.patch(
        f"{API}/accounts/{account_id}",
        headers=auth_headers(token),
        json={"status": "disabled"},
    )

    assert response.status_code == 200, response.text
    assert response.json()["status"] == "disabled"


def test_created_account_can_login_and_response_hides_password(client, seed, db_session):
    admin = login(client, "admin-a")
    response = client.post(
        f"{API}/accounts",
        json={
            "displayName": "新治疗师",
            "username": "new-therapist",
            "password": "vurun-bekam-817",
            "role": "rehab_therapist",
        },
        headers=auth_headers(admin),
    )
    assert response.status_code == 201, response.text
    body = response.json()
    assert body["accountId"]
    assert body["displayName"] == "新治疗师"
    assert body["username"] == "new-therapist"
    assert body["status"] == "active"
    assert body["roles"] == ["rehab_therapist"]
    assert body["createdAt"]
    assert "password" not in body
    assert "passwordHash" not in body

    logged_in = client.post(
        f"{API}/auth/login",
        json={"username": "new-therapist", "password": "vurun-bekam-817"},
    )
    assert logged_in.status_code == 200, logged_in.text
    assert logged_in.json()["profile"]["roles"] == ["rehab_therapist"]

    created = db_session.scalars(
        select(AuditEvent).where(
            AuditEvent.action == "account.created",
            AuditEvent.resource_id == body["accountId"],
        )
    ).all()
    assert created


def test_patch_cross_org_returns_403_and_audits(client, seed, db_session):
    admin = login(client, "admin-a")
    response = client.patch(
        f"{API}/accounts/therapist-b",
        json={"displayName": "x"},
        headers=auth_headers(admin),
    )
    assert response.status_code == 403, response.text
    assert response.json()["code"] == "FORBIDDEN"

    denied = db_session.scalars(
        select(AuditEvent).where(
            AuditEvent.action == "authz.cross_org_access",
            AuditEvent.outcome == "denied",
            AuditEvent.actor_account_id == "admin-a",
            AuditEvent.resource_id == "therapist-b",
        )
    ).all()
    assert denied


def test_disabled_account_cannot_use_old_token_or_login(client, seed):
    admin = login(client, "admin-a")
    created = client.post(
        f"{API}/accounts",
        json={
            "displayName": "待禁用",
            "username": "disable-me",
            "password": "smoke-pass-123",
            "role": "rehab_therapist",
        },
        headers=auth_headers(admin),
    )
    assert created.status_code == 201, created.text
    account_id = created.json()["accountId"]

    victim = login(client, "disable-me", password="smoke-pass-123")

    disabled = client.patch(
        f"{API}/accounts/{account_id}",
        json={"status": "disabled"},
        headers=auth_headers(admin),
    )
    assert disabled.status_code == 200, disabled.text
    assert disabled.json()["status"] == "disabled"

    assert client.get(f"{API}/me", headers=auth_headers(victim)).status_code == 401
    assert (
        client.post(
            f"{API}/auth/login",
            json={"username": "disable-me", "password": "smoke-pass-123"},
        ).status_code
        == 401
    )


def test_cannot_disable_self(client, seed):
    admin = login(client, "admin-a")
    second = client.post(
        f"{API}/accounts",
        json={
            "displayName": "管理员甲二",
            "username": "admin-a2",
            "password": "smoke-pass-123",
            "role": "care_admin",
        },
        headers=auth_headers(admin),
    )
    assert second.status_code == 201, second.text

    response = client.patch(
        f"{API}/accounts/admin-a",
        json={"status": "disabled"},
        headers=auth_headers(admin),
    )
    assert response.status_code == 409, response.text
    assert response.json()["code"] == "CONFLICT"
    assert response.json()["detail"]["reason"] == "self_disable"


def test_cannot_remove_last_care_admin(client, seed):
    admin = login(client, "admin-a")
    response = client.patch(
        f"{API}/accounts/admin-a",
        json={"role": "rehab_therapist"},
        headers=auth_headers(admin),
    )
    assert response.status_code == 409, response.text
    assert response.json()["code"] == "CONFLICT"
    assert response.json()["detail"]["reason"] == "last_care_admin"

    listing = client.get(f"{API}/accounts?limit=100", headers=auth_headers(admin))
    assert listing.status_code == 200
    assert _find_username(listing.json(), "admin-a")["roles"] == ["care_admin"]


def test_cannot_downgrade_self_when_another_admin_exists(client, seed):
    admin = login(client, "admin-a")
    second = client.post(
        f"{API}/accounts",
        json={
            "displayName": "管理员甲二",
            "username": "admin-a2",
            "password": "smoke-pass-123",
            "role": "care_admin",
        },
        headers=auth_headers(admin),
    )
    assert second.status_code == 201, second.text

    response = client.patch(
        f"{API}/accounts/admin-a",
        json={"role": "rehab_therapist"},
        headers=auth_headers(admin),
    )
    assert response.status_code == 409, response.text
    assert response.json()["detail"]["reason"] == "self_role_change"


def test_role_change_takes_effect_immediately(client, seed):
    admin = login(client, "admin-a")
    created = client.post(
        f"{API}/accounts",
        json={
            "displayName": "待升权",
            "username": "promote-me",
            "password": "smoke-pass-123",
            "role": "rehab_therapist",
        },
        headers=auth_headers(admin),
    )
    assert created.status_code == 201, created.text
    account_id = created.json()["accountId"]
    old_token = login(client, "promote-me", password="smoke-pass-123")

    promoted = client.patch(
        f"{API}/accounts/{account_id}",
        json={"role": "care_admin"},
        headers=auth_headers(admin),
    )
    assert promoted.status_code == 200, promoted.text
    assert promoted.json()["roles"] == ["care_admin"]

    me = client.get(f"{API}/me", headers=auth_headers(old_token))
    assert me.status_code == 200, me.text
    assert me.json()["roles"] == ["care_admin"]


def test_username_conflict_returns_409_and_short_password_422(client, seed):
    admin = login(client, "admin-a")
    headers = auth_headers(admin)
    body = {
        "displayName": "重复名",
        "username": "dup-name",
        "password": "smoke-pass-123",
        "role": "rehab_therapist",
    }
    first = client.post(f"{API}/accounts", json=body, headers=headers)
    assert first.status_code == 201, first.text

    second = client.post(f"{API}/accounts", json=body, headers=headers)
    assert second.status_code == 409, second.text
    assert second.json()["code"] == "CONFLICT"

    short = client.post(
        f"{API}/accounts",
        json={**body, "username": "too-short-pw", "password": "short"},
        headers=headers,
    )
    assert short.status_code == 422, short.text


def test_demoting_another_admin_is_allowed(client, seed):
    admin = login(client, "admin-a")
    second = client.post(
        f"{API}/accounts",
        json={
            "displayName": "管理员甲二",
            "username": "admin-a2",
            "password": "smoke-pass-123",
            "role": "care_admin",
        },
        headers=auth_headers(admin),
    )
    assert second.status_code == 201, second.text
    account_id = second.json()["accountId"]

    response = client.patch(
        f"{API}/accounts/{account_id}",
        json={"role": "rehab_therapist"},
        headers=auth_headers(admin),
    )
    assert response.status_code == 200, response.text
    assert response.json()["roles"] == ["rehab_therapist"]
