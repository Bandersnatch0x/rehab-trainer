from sqlalchemy import func, select

from app.models.prescription import Prescription
from tests.helpers import API, auth_headers, login


def _create_body(seed, patient_id=None, exercise_code="ex-low", variant_code=None):
    return {
        "patientId": patient_id or seed["patient_a"],
        "items": [
            {
                "exerciseCode": exercise_code,
                "variantCode": variant_code,
                "dosage": {"sets": 2, "repetitions": 8},
            }
        ],
        "issuedAt": "2026-11-04T08:00:00Z",
        "validFrom": "2026-11-04T08:00:00Z",
        "validUntil": "2026-12-04T08:00:00Z",
        "mappingTableVersion": "test.1",
    }


def test_create_prescription_rejects_hy_stage_5(client, seed, db_session):
    token = login(client, "therapist-a")
    response = client.post(
        f"{API}/prescriptions",
        json=_create_body(seed, patient_id=seed["patient_hy5"]),
        headers=auth_headers(token),
    )
    assert response.status_code == 422
    assert response.json()["code"] == "STAGE_NOT_ELIGIBLE"

    count = db_session.scalar(
        select(func.count())
        .select_from(Prescription)
        .where(Prescription.patient_id == seed["patient_hy5"])
    )
    assert count == 0


def test_high_risk_rejected_when_stage_unconfirmed(client, seed, db_session):
    token = login(client, "therapist-a")
    response = client.post(
        f"{API}/prescriptions",
        json=_create_body(
            seed,
            patient_id=seed["patient_unconfirmed"],
            exercise_code="ex-high",
        ),
        headers=auth_headers(token),
    )
    assert response.status_code == 422
    assert response.json()["code"] == "HIGH_RISK_NOT_ALLOWED"

    count = db_session.scalar(
        select(func.count())
        .select_from(Prescription)
        .where(Prescription.patient_id == seed["patient_unconfirmed"])
    )
    assert count == 0


def test_create_prescription_requires_published_exercise(client, seed):
    token = login(client, "therapist-a")
    response = client.post(
        f"{API}/prescriptions",
        json=_create_body(seed, exercise_code="ex-draft"),
        headers=auth_headers(token),
    )
    assert response.status_code == 422


def test_create_and_patch_prescription_version(client, seed):
    token = login(client, "therapist-a")
    headers = auth_headers(token)

    created = client.post(
        f"{API}/prescriptions",
        json=_create_body(seed, variant_code="ex-low-v1"),
        headers=headers,
    )
    assert created.status_code == 201, created.text
    body = created.json()
    assert body["version"] == "1.0.0"
    assert body["status"] == "active"
    assert body["items"][0]["exerciseCode"] == "ex-low"

    stale = client.patch(
        f"{API}/prescriptions/{body['id']}",
        json={"note": "首次调整"},
        headers={**headers, "If-Match": "0.0.9"},
    )
    assert stale.status_code == 409
    assert stale.json()["code"] == "VERSION_CONFLICT"

    updated = client.patch(
        f"{API}/prescriptions/{body['id']}",
        json={"note": "首次调整", "validUntil": "2026-12-31T08:00:00Z"},
        headers={**headers, "If-Match": "1.0.0"},
    )
    assert updated.status_code == 200, updated.text
    assert updated.json()["version"] == "1.0.1"

    conflict = client.patch(
        f"{API}/prescriptions/{body['id']}",
        json={"note": "再次调整"},
        headers={**headers, "If-Match": "1.0.0"},
    )
    assert conflict.status_code == 409


def test_prescription_is_therapist_only(client, seed):
    token = login(client, "patient-a")
    response = client.post(
        f"{API}/prescriptions",
        json=_create_body(seed),
        headers=auth_headers(token),
    )
    assert response.status_code == 403
    assert response.json()["code"] == "FORBIDDEN"
