from tests.helpers import API, auth_headers, login


def _codes(response):
    assert response.status_code == 200, response.text
    return {item["code"] for item in response.json()["items"]}


def test_unconfirmed_stage_hides_high_risk_entries(client, seed):
    token = login(client, "patient-unconfirmed")
    codes = _codes(client.get(f"{API}/exercises", headers=auth_headers(token)))

    assert "ex-low" in codes
    assert "ex-medium" in codes
    assert "ex-high" not in codes
    # 未发布条目对患者不可见。
    assert "ex-draft" not in codes


def test_confirmed_stage_can_see_high_risk_entries(client, seed):
    token = login(client, "patient-a")
    codes = _codes(client.get(f"{API}/exercises", headers=auth_headers(token)))
    assert "ex-high" in codes


def test_hy_stage_5_has_no_training_entries(client, seed):
    token = login(client, "patient-hy5")
    response = client.get(f"{API}/exercises", headers=auth_headers(token))
    assert response.status_code == 200
    assert response.json()["items"] == []


def test_high_risk_detail_hidden_for_unconfirmed_patient(client, seed):
    unconfirmed = auth_headers(login(client, "patient-unconfirmed"))
    hidden = client.get(f"{API}/exercises/ex-high", headers=unconfirmed)
    assert hidden.status_code == 404

    confirmed = auth_headers(login(client, "patient-a"))
    visible = client.get(f"{API}/exercises/ex-high", headers=confirmed)
    assert visible.status_code == 200
    assert visible.json()["riskLevel"] == "high"


def test_stage_filter_applies_to_catalog(client, seed):
    token = login(client, "therapist-a")
    response = client.get(
        f"{API}/exercises", params={"stage": 5}, headers=auth_headers(token)
    )
    assert response.status_code == 200
    assert response.json()["items"] == []
