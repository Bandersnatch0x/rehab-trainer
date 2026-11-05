from tests.helpers import API, auth_headers, login


def test_stage_confirmation_is_therapist_only(client, seed):
    token = login(client, "patient-a")
    response = client.post(
        f"{API}/patients/{seed['patient_a']}/stage-confirmations",
        json={"stage": 4, "confirmedAt": "2026-11-04T09:00:00Z"},
        headers=auth_headers(token),
    )
    assert response.status_code == 403


def test_therapist_confirms_stage(client, seed):
    token = login(client, "therapist-a")
    headers = auth_headers(token)

    response = client.post(
        f"{API}/patients/{seed['patient_unconfirmed']}/stage-confirmations",
        json={"stage": 4, "confirmedAt": "2026-11-04T09:00:00Z", "note": "复评"},
        headers=headers,
    )
    assert response.status_code == 201, response.text
    body = response.json()
    assert body["stage"] == 4
    assert body["status"] == "confirmed"
    assert body["confirmedBy"] == "therapist-a"

    patient = client.get(
        f"{API}/patients/{seed['patient_unconfirmed']}", headers=headers
    ).json()
    assert patient["hyStage"] == 4
    assert patient["stageConfirmationStatus"] == "confirmed"


def test_patient_submits_assessment_without_risk_conclusion(client, seed):
    token = login(client, "patient-unconfirmed")
    response = client.post(
        f"{API}/patients/{seed['patient_unconfirmed']}/assessments",
        json={
            "instrument": "RIGIDITY_5",
            "score": 2,
            "answers": {"item1": 2},
            "assessedAt": "2026-11-04T09:00:00Z",
        },
        headers=auth_headers(token),
    )
    assert response.status_code == 201, response.text
    body = response.json()
    assert body["instrument"] == "RIGIDITY_5"
    assert body["assessedBy"] == "patient-unconfirmed-account"
    # TODO 待临床签核：本版不得输出风险分级。
    assert body.get("riskLevel") is None


def test_caregiver_can_submit_for_linked_patient(client, seed):
    token = login(client, "caregiver-a")
    response = client.post(
        f"{API}/patients/{seed['patient_a']}/assessments",
        json={
            "instrument": "STEADI",
            "score": 1,
            "assessedAt": "2026-11-04T09:30:00Z",
        },
        headers=auth_headers(token),
    )
    assert response.status_code == 201
    assert response.json()["assessedBy"] == "caregiver-a"


def test_assessment_cross_org_rejected(client, seed):
    token = login(client, "therapist-a")
    response = client.post(
        f"{API}/patients/{seed['patient_b']}/assessments",
        json={"instrument": "STEADI", "assessedAt": "2026-11-04T09:30:00Z"},
        headers=auth_headers(token),
    )
    assert response.status_code == 403


def test_patient_can_read_back_daily_feeling_text(client, seed):
    token = login(client, "patient-unconfirmed")
    headers = auth_headers(token)
    created = client.post(
        f"{API}/patients/{seed['patient_unconfirmed']}/assessments",
        json={
            "instrument": "DAILY_FEELING",
            "answers": {"feeling": "散步很轻松，练习后休息了一会儿。"},
            "assessedAt": "2026-11-04T10:00:00Z",
        },
        headers=headers,
    )
    assert created.status_code == 201, created.text
    assert created.json()["answers"]["feeling"] == "散步很轻松，练习后休息了一会儿。"

    listed = client.get(
        f"{API}/patients/{seed['patient_unconfirmed']}/assessments",
        headers=headers,
    )
    assert listed.status_code == 200, listed.text
    body = listed.json()
    assert [item["instrument"] for item in body["items"]] == ["DAILY_FEELING"]
    assert body["items"][0]["answers"]["feeling"] == "散步很轻松，练习后休息了一会儿。"
    assert body["items"][0]["assessedBy"] == "patient-unconfirmed-account"


def test_assessment_list_is_org_scoped(client, seed):
    token = login(client, "therapist-a")
    response = client.get(
        f"{API}/patients/{seed['patient_b']}/assessments",
        headers=auth_headers(token),
    )
    assert response.status_code == 403


def test_patient_creation_roles_and_defaults(client, seed):
    patient_token = login(client, "patient-a")
    denied = client.post(
        f"{API}/patients",
        json={"displayName": "新患者"},
        headers=auth_headers(patient_token),
    )
    assert denied.status_code == 403

    admin_token = login(client, "admin-a")
    created = client.post(
        f"{API}/patients",
        json={"displayName": "新患者", "birthYear": 1950, "note": "初诊"},
        headers=auth_headers(admin_token),
    )
    assert created.status_code == 201, created.text
    body = created.json()
    assert body["orgId"] == "org-a"
    assert body["hyStage"] == 1
    assert body["stageConfirmationStatus"] == "suspected"
    assert body["status"] == "active"


def test_assessment_rejects_oversized_or_nested_answers(client, seed):
    token = login(client, "patient-unconfirmed")
    headers = auth_headers(token)

    too_long = client.post(
        f"{API}/patients/{seed['patient_unconfirmed']}/assessments",
        json={
            "instrument": "DAILY_FEELING",
            "answers": {"feeling": "x" * 5000},
            "assessedAt": "2026-11-04T10:00:00Z",
        },
        headers=headers,
    )
    assert too_long.status_code == 422

    nested = client.post(
        f"{API}/patients/{seed['patient_unconfirmed']}/assessments",
        json={
            "instrument": "DAILY_FEELING",
            "answers": {"feeling": {"nested": "value"}},
            "assessedAt": "2026-11-04T10:00:00Z",
        },
        headers=headers,
    )
    assert nested.status_code == 422

    too_many = client.post(
        f"{API}/patients/{seed['patient_unconfirmed']}/assessments",
        json={
            "instrument": "DAILY_FEELING",
            "answers": {f"k{index}": 1 for index in range(40)},
            "assessedAt": "2026-11-04T10:00:00Z",
        },
        headers=headers,
    )
    assert too_many.status_code == 422
