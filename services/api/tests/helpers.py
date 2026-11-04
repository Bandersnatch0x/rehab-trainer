from fastapi.testclient import TestClient

API = "/v1"
DEFAULT_PASSWORD = "secret123"


def login(
    client: TestClient,
    username: str,
    password: str = DEFAULT_PASSWORD,
) -> str:
    response = client.post(
        f"{API}/auth/login",
        json={"username": username, "password": password},
    )
    assert response.status_code == 200, response.text
    return response.json()["accessToken"]


def auth_headers(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}
