from sqlalchemy import select

from app.models.audit import AuditEvent
from tests.helpers import API, auth_headers, login


def test_cross_org_patient_access_returns_403_and_audits(client, seed, db_session):
    token = login(client, "therapist-b")
    response = client.get(
        f"{API}/patients/{seed['patient_a']}", headers=auth_headers(token)
    )
    assert response.status_code == 403
    assert response.json()["code"] == "FORBIDDEN"

    audit = db_session.scalar(
        select(AuditEvent).where(AuditEvent.action == "authz.cross_org_access")
    )
    assert audit is not None
    assert audit.outcome == "denied"
    assert audit.actor_account_id == "therapist-b"
    assert audit.resource_id == seed["patient_a"]
    assert audit.detail["resourceOrgId"] == "org-a"


def test_patient_list_is_scoped_to_org(client, seed):
    token = login(client, "therapist-b")
    response = client.get(f"{API}/patients", headers=auth_headers(token))
    assert response.status_code == 200
    ids = [item["id"] for item in response.json()["items"]]
    assert ids == ["patient-b"]


def test_non_clinician_only_sees_linked_patients(client, seed):
    token = login(client, "caregiver-a")

    own = client.get(
        f"{API}/patients/{seed['patient_a']}", headers=auth_headers(token)
    )
    assert own.status_code == 200

    other = client.get(
        f"{API}/patients/{seed['patient_unconfirmed']}",
        headers=auth_headers(token),
    )
    assert other.status_code == 403

    listing = client.get(f"{API}/patients", headers=auth_headers(token))
    assert listing.status_code == 200
    assert [item["id"] for item in listing.json()["items"]] == ["patient-a"]


def test_prescription_list_respects_org_and_patient_scope(client, seed):
    cross_org = client.get(
        f"{API}/prescriptions",
        params={"patientId": seed["patient_a"]},
        headers=auth_headers(login(client, "therapist-b")),
    )
    assert cross_org.status_code == 403
    assert cross_org.json()["code"] == "FORBIDDEN"

    unlinked = client.get(
        f"{API}/prescriptions",
        params={"patientId": seed["patient_unconfirmed"]},
        headers=auth_headers(login(client, "caregiver-a")),
    )
    assert unlinked.status_code == 403

    own = client.get(
        f"{API}/prescriptions",
        params={"patientId": seed["patient_a"]},
        headers=auth_headers(login(client, "caregiver-a")),
    )
    assert own.status_code == 200
    assert [item["id"] for item in own.json()["items"]] == [seed["prescription_a"]]


def test_clinician_sees_all_patients_in_org(client, seed):
    token = login(client, "therapist-a")
    response = client.get(f"{API}/patients", headers=auth_headers(token))
    assert response.status_code == 200
    ids = {item["id"] for item in response.json()["items"]}
    assert ids == {"patient-a", "patient-unconfirmed", "patient-hy5"}
