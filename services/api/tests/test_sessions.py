from sqlalchemy import func, select

from app.models.record import SessionRecord
from tests.helpers import API, auth_headers, login


def _record_payload(seed, client_record_id="client-record-1"):
    return {
        "clientRecordId": client_record_id,
        "patientId": seed["patient_a"],
        "prescriptionId": seed["prescription_a"],
        "recordedAt": "2026-11-04T08:30:00Z",
        "checkedIn": True,
        "completionCount": 12,
        "movementErrorCount": 2,
        "interruptionCount": 1,
        "validFrameRatio": 0.92,
        "isDoubtful": False,
        "modelVersion": "pose-full-1.2.2",
        "modelSha256": "a" * 64,
        "metricAlgorithmVersion": "metrics-0.1.0",
        "thresholdConfigVersion": "thresholds-2026.11",
        "metrics": {"cadenceSpm": 96.5},
    }


def test_duplicate_client_record_id_is_idempotent(client, seed, db_session):
    token = login(client, "patient-a")
    headers = auth_headers(token)
    payload = {"records": [_record_payload(seed)]}

    first = client.post(f"{API}/sessions", json=payload, headers=headers)
    assert first.status_code == 200, first.text
    first_result = first.json()["results"][0]
    assert first_result["accepted"] is True
    assert first_result["recordId"]

    second = client.post(f"{API}/sessions", json=payload, headers=headers)
    assert second.status_code == 200, second.text
    second_result = second.json()["results"][0]
    assert second_result["accepted"] is True
    assert second_result["recordId"] == first_result["recordId"]

    count = db_session.scalar(select(func.count()).select_from(SessionRecord))
    assert count == 1


def test_duplicate_record_returns_existing_record(client, seed):
    token = login(client, "patient-a")
    headers = auth_headers(token)
    payload = {"records": [_record_payload(seed, "idem-key-2")]}

    first = client.post(f"{API}/sessions", json=payload, headers=headers).json()
    second = client.post(f"{API}/sessions", json=payload, headers=headers).json()
    assert first["results"][0]["recordId"] == second["results"][0]["recordId"]


def test_duplicate_key_within_one_batch(client, seed, db_session):
    token = login(client, "patient-a")
    duplicate = _record_payload(seed, "idem-key-batch")
    response = client.post(
        f"{API}/sessions",
        json={"records": [duplicate, duplicate]},
        headers=auth_headers(token),
    )
    assert response.status_code == 200, response.text
    results = response.json()["results"]
    assert [item["accepted"] for item in results] == [True, True]
    assert results[0]["recordId"] == results[1]["recordId"]

    count = db_session.scalar(
        select(func.count())
        .select_from(SessionRecord)
        .where(SessionRecord.client_record_id == "idem-key-batch")
    )
    assert count == 1


def test_unlinked_patient_record_is_rejected_per_item(client, seed):
    token = login(client, "patient-a")
    payload = {"records": [_record_payload(seed, "idem-key-3")]}
    payload["records"][0]["patientId"] = seed["patient_unconfirmed"]

    response = client.post(
        f"{API}/sessions", json=payload, headers=auth_headers(token)
    )
    assert response.status_code == 200, response.text
    result = response.json()["results"][0]
    assert result["accepted"] is False
    assert result["error"]["code"] == "FORBIDDEN"


def test_session_query_is_scoped(client, seed):
    token = login(client, "patient-a")
    headers = auth_headers(token)
    client.post(f"{API}/sessions", json={"records": [_record_payload(seed)]}, headers=headers)

    listing = client.get(
        f"{API}/sessions",
        params={"patientId": seed["patient_a"]},
        headers=headers,
    )
    assert listing.status_code == 200
    assert len(listing.json()["items"]) == 1
    assert listing.json()["items"][0]["clientRecordId"] == "client-record-1"
    assert "modelSha256" not in listing.json()["items"][0]

    cross_org = client.get(
        f"{API}/sessions",
        params={"patientId": seed["patient_a"]},
        headers=auth_headers(login(client, "therapist-b")),
    )
    assert cross_org.status_code == 403


def test_doubtful_is_derived_from_valid_frame_ratio(client, seed, db_session):
    token = login(client, "patient-a")
    record = _record_payload(seed, "derived-doubtful-low-ratio")
    record["isDoubtful"] = False
    record["validFrameRatio"] = 0.1

    response = client.post(
        f"{API}/sessions", json={"records": [record]}, headers=auth_headers(token)
    )
    assert response.status_code == 200, response.text
    assert response.json()["results"][0]["accepted"] is True

    stored = db_session.scalar(
        select(SessionRecord).where(
            SessionRecord.client_record_id == "derived-doubtful-low-ratio"
        )
    )
    assert stored is not None
    assert stored.is_doubtful is True

    listing = client.get(
        f"{API}/sessions",
        params={"patientId": seed["patient_a"]},
        headers=auth_headers(token),
    )
    item = next(
        row
        for row in listing.json()["items"]
        if row["clientRecordId"] == "derived-doubtful-low-ratio"
    )
    assert item["isDoubtful"] is True
    assert item["validFrameRatio"] == 0.1
    assert item["thresholdConfigVersion"] == "thresholds-2026.11"


def test_client_reported_doubtful_is_preserved(client, seed, db_session):
    token = login(client, "patient-a")
    record = _record_payload(seed, "client-reported-doubtful")
    record["isDoubtful"] = True
    record["validFrameRatio"] = 0.99

    response = client.post(
        f"{API}/sessions", json={"records": [record]}, headers=auth_headers(token)
    )
    assert response.status_code == 200, response.text

    stored = db_session.scalar(
        select(SessionRecord).where(
            SessionRecord.client_record_id == "client-reported-doubtful"
        )
    )
    assert stored is not None
    assert stored.is_doubtful is True


def test_missing_valid_frame_ratio_fails_closed(client, seed, db_session):
    token = login(client, "patient-a")
    record = _record_payload(seed, "missing-frame-ratio")
    record["isDoubtful"] = False
    del record["validFrameRatio"]

    response = client.post(
        f"{API}/sessions", json={"records": [record]}, headers=auth_headers(token)
    )
    assert response.status_code == 200, response.text

    stored = db_session.scalar(
        select(SessionRecord).where(
            SessionRecord.client_record_id == "missing-frame-ratio"
        )
    )
    assert stored is not None
    assert stored.is_doubtful is True


def test_session_requires_algorithm_versions(client, seed):
    token = login(client, "patient-a")
    payload = {"records": [_record_payload(seed, "idem-key-4")]}
    del payload["records"][0]["modelSha256"]

    response = client.post(
        f"{API}/sessions", json=payload, headers=auth_headers(token)
    )
    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_FAILED"
