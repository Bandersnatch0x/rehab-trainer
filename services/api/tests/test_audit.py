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


def test_audit_pagination_does_not_repeat_or_skip(client, seed):
    admin = login(client, "admin-a")
    headers = auth_headers(admin)
    for name in ("审计甲", "审计乙"):
        created = client.post(
            f"{API}/patients", json={"displayName": name}, headers=headers
        )
        assert created.status_code == 201, created.text

    first = client.get(f"{API}/audit-events?limit=1", headers=headers)
    assert first.status_code == 200
    page1 = first.json()
    assert page1["items"] and page1["nextCursor"]

    # 翻页期间插入新事件：游标应仍指向旧区间，不重复上一页。
    inserted = client.post(
        f"{API}/patients", json={"displayName": "分页插入"}, headers=headers
    )
    assert inserted.status_code == 201, inserted.text

    second = client.get(
        f"{API}/audit-events?limit=1&cursor={page1['nextCursor']}", headers=headers
    )
    assert second.status_code == 200
    page2 = second.json()
    assert page2["items"]
    assert page1["items"][0]["id"] != page2["items"][0]["id"]
    assert page2["items"][0]["createdAt"] <= page1["items"][0]["createdAt"]
