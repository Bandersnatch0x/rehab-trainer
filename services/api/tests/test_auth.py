from tests.helpers import API, auth_headers, login


def test_login_returns_tokens_and_profile(client, seed):
    response = client.post(
        f"{API}/auth/login",
        json={"username": "therapist-a", "password": "secret123"},
    )
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["accessToken"]
    assert body["refreshToken"]
    assert body["expiresIn"] > 0
    assert body["profile"]["orgId"] == "org-a"
    assert body["profile"]["roles"] == ["rehab_therapist"]


def test_login_rejects_wrong_password(client, seed):
    response = client.post(
        f"{API}/auth/login",
        json={"username": "therapist-a", "password": "wrong-password"},
    )
    assert response.status_code == 401
    assert response.json()["code"] == "UNAUTHORIZED"


def test_me_requires_token_and_returns_identity(client, seed):
    assert client.get(f"{API}/me").status_code == 401

    token = login(client, "caregiver-a")
    response = client.get(f"{API}/me", headers=auth_headers(token))
    assert response.status_code == 200
    body = response.json()
    assert body["accountId"] == "caregiver-a"
    assert body["orgId"] == "org-a"
    assert body["roles"] == ["caregiver"]


def test_binding_token_is_single_use(client, seed):
    first = client.post(
        f"{API}/auth/bind", json={"bindingToken": "bind-once", "deviceId": "tablet-1"}
    )
    assert first.status_code == 200, first.text
    assert first.json()["accessToken"]

    second = client.post(f"{API}/auth/bind", json={"bindingToken": "bind-once"})
    assert second.status_code == 409

    expired = client.post(f"{API}/auth/bind", json={"bindingToken": "bind-expired"})
    assert expired.status_code == 409

    used = client.post(f"{API}/auth/bind", json={"bindingToken": "bind-used"})
    assert used.status_code == 409

    unknown = client.post(f"{API}/auth/bind", json={"bindingToken": "no-such-token"})
    assert unknown.status_code == 401


def test_refresh_token_issues_new_pair(client, seed):
    login_response = client.post(
        f"{API}/auth/login",
        json={"username": "patient-a", "password": "secret123"},
    )
    refresh_token = login_response.json()["refreshToken"]

    response = client.post(
        f"{API}/auth/refresh", json={"refreshToken": refresh_token}
    )
    assert response.status_code == 200, response.text
    assert response.json()["accessToken"]
    assert response.json()["refreshToken"] != refresh_token

    invalid = client.post(
        f"{API}/auth/refresh", json={"refreshToken": "not-a-token"}
    )
    assert invalid.status_code == 401
