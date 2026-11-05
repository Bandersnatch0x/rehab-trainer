from app.models.alert import AlertEvent
from tests.helpers import API, auth_headers, login


def test_alert_list_scoped_to_patient(client, seed):
    token = login(client, "patient-a")
    response = client.get(f"{API}/alerts", headers=auth_headers(token))
    assert response.status_code == 200
    items = response.json()["items"]
    assert [item["id"] for item in items] == [seed["alert_a"]]
    assert items[0]["deliveries"][0]["channel"] == "in_app_popup"


def test_alert_list_other_org_is_empty(client, seed):
    token = login(client, "therapist-b")
    response = client.get(f"{API}/alerts", headers=auth_headers(token))
    assert response.status_code == 200
    assert response.json()["items"] == []


def test_alert_update_requires_clinical_role(client, seed):
    token = login(client, "patient-a")
    response = client.patch(
        f"{API}/alerts/{seed['alert_a']}",
        json={"status": "acknowledged"},
        headers=auth_headers(token),
    )
    assert response.status_code == 403


def test_alert_update_by_admin(client, seed, db_session):
    token = login(client, "admin-a")
    response = client.patch(
        f"{API}/alerts/{seed['alert_a']}",
        json={"status": "acknowledged", "note": "已电话随访"},
        headers=auth_headers(token),
    )
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["status"] == "acknowledged"
    # 处置人与处置说明随预警返回，随访台账不再只能显示状态。
    assert body["handledBy"] == "admin-a"
    assert body["note"] == "已电话随访"

    db_session.expire_all()
    alert = db_session.get(AlertEvent, seed["alert_a"])
    assert alert.acknowledged_at is not None
    assert alert.handled_by == "admin-a"


def test_alert_cross_org_update_rejected(client, seed):
    token = login(client, "therapist-b")
    response = client.patch(
        f"{API}/alerts/{seed['alert_a']}",
        json={"status": "closed"},
        headers=auth_headers(token),
    )
    assert response.status_code == 403
    assert response.json()["code"] == "FORBIDDEN"
