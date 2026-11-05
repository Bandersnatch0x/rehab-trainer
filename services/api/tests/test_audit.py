from tests.helpers import API, auth_headers, login


def test_patient_cannot_read_audit(client, seed):
    token = login(client, "patient-a")
    response = client.get(f"{API}/audit-events", headers=auth_headers(token))
    assert response.status_code == 403


def test_audit_list_is_org_scoped_and_filterable(client, seed):
    admin = login(client, "admin-a")
    created = client.post(
        f"{API}/patients",
        json={"displayName": "审计患者"},
        headers=auth_headers(admin),
    )
    assert created.status_code == 201, created.text

    response = client.get(
        f"{API}/audit-events?action=patient.created",
        headers=auth_headers(admin),
    )
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["items"], "新建患者应写入审计事件"
    assert all(item["action"] == "patient.created" for item in body["items"])
    assert body["items"][0]["actorAccountId"] == "admin-a"
    assert body["items"][0]["resourceId"] == created.json()["id"]

    # 其他机构看不到 org-a 的审计事件。
    other = login(client, "therapist-b")
    other_response = client.get(f"{API}/audit-events", headers=auth_headers(other))
    assert other_response.status_code == 200
    assert other_response.json()["items"] == []