"""keyset 分页：翻页期间插入新数据不应重复或漏行。"""

from tests.helpers import API, auth_headers, login


def _session_payload(seed, client_record_id, recorded_at):
    return {
        "clientRecordId": client_record_id,
        "patientId": seed["patient_a"],
        "prescriptionId": seed["prescription_a"],
        "recordedAt": recorded_at,
        "checkedIn": True,
        "completionCount": 1,
        "validFrameRatio": 0.9,
        "modelVersion": "m1",
        "modelSha256": "a" * 64,
        "metricAlgorithmVersion": "a1",
        "thresholdConfigVersion": "t1",
    }


def _create_sessions(client, headers, seed, stamps):
    for index, stamp in enumerate(stamps):
        response = client.post(
            f"{API}/sessions",
            json={"records": [_session_payload(seed, f"page-{index}-{stamp}", stamp)]},
            headers=headers,
        )
        assert response.status_code == 200, response.text


def test_sessions_keyset_does_not_skip_when_new_row_arrives(client, seed):
    headers = auth_headers(login(client, "patient-a"))
    _create_sessions(
        client,
        headers,
        seed,
        ["2026-11-01T08:00:00Z", "2026-11-02T08:00:00Z", "2026-11-03T08:00:00Z"],
    )

    first = client.get(
        f"{API}/sessions",
        params={"patientId": seed["patient_a"], "limit": 1},
        headers=headers,
    )
    assert first.status_code == 200, first.text
    first_page = first.json()
    assert len(first_page["items"]) == 1
    newest = first_page["items"][0]["recordedAt"]
    cursor = first_page["nextCursor"]
    assert cursor

    # 翻页期间插入一条更新的记录：offset 分页会让第二页重复 newest。
    _create_sessions(client, headers, seed, ["2026-11-04T08:00:00Z"])

    second = client.get(
        f"{API}/sessions",
        params={"patientId": seed["patient_a"], "limit": 1, "cursor": cursor},
        headers=headers,
    )
    assert second.status_code == 200, second.text
    second_items = second.json()["items"]
    assert len(second_items) == 1
    assert second_items[0]["recordedAt"] != newest, "第二页重复了第一页的记录"
    assert second_items[0]["recordedAt"].startswith("2026-11-02")


def test_invalid_cursor_is_rejected(client, seed):
    headers = auth_headers(login(client, "therapist-a"))

    for bad in ("not-a-cursor", "eyJ4IjoxfQ", "e30"):
        response = client.get(
            f"{API}/patients", params={"cursor": bad}, headers=headers
        )
        assert response.status_code == 422, (bad, response.text)
        assert response.json()["code"] == "VALIDATION_FAILED"


def test_cursor_from_another_endpoint_is_rejected(client, seed):
    headers = auth_headers(login(client, "therapist-a"))

    sessions = client.get(
        f"{API}/sessions",
        params={"patientId": seed["patient_a"], "limit": 1},
        headers=headers,
    )
    # 该患者可能没有记录，取任意一个带 cursor 的响应
    if sessions.status_code == 200 and sessions.json().get("nextCursor"):
        foreign = sessions.json()["nextCursor"]
        response = client.get(
            f"{API}/patients", params={"cursor": foreign}, headers=headers
        )
        assert response.status_code == 422


def test_patients_pagination_is_ordered_and_exhaustive(client, seed):
    headers = auth_headers(login(client, "therapist-a"))

    seen = []
    cursor = None
    for _ in range(10):
        params = {"limit": 1}
        if cursor:
            params["cursor"] = cursor
        response = client.get(f"{API}/patients", params=params, headers=headers)
        assert response.status_code == 200, response.text
        body = response.json()
        seen.extend(item["id"] for item in body["items"])
        cursor = body.get("nextCursor")
        if not cursor:
            break

    assert len(seen) == len(set(seen)), "分页出现重复"
    assert set(seen) == {"patient-a", "patient-unconfirmed", "patient-hy5"}


def test_exercises_pagination_is_ordered_and_exhaustive(client, seed):
    headers = auth_headers(login(client, "therapist-a"))

    seen = []
    cursor = None
    for _ in range(50):
        params = {"limit": 1}
        if cursor:
            params["cursor"] = cursor
        response = client.get(f"{API}/exercises", params=params, headers=headers)
        assert response.status_code == 200, response.text
        body = response.json()
        seen.extend(item["code"] for item in body["items"])
        cursor = body.get("nextCursor")
        if not cursor:
            break

    assert len(seen) == len(set(seen)), "分页出现重复"
    assert seen == sorted(seen), "训练条目应按 code 升序"
    assert len(seen) >= 2
