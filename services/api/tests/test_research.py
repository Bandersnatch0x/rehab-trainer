"""科研聚合端点：demo 过滤、n<5 抑制、越权 403、D23 denylist（N1/N3/N5/N7）。"""

from datetime import timedelta
from uuid import uuid4

import pytest
from sqlalchemy import select

from app.core.research import D23_PROHIBITED_FIELDS, find_denylisted_keys
from app.core.timeutils import utcnow
from app.models.audit import AuditEvent
from app.models.clinical import Patient
from app.models.record import SessionRecord
from tests.helpers import API, auth_headers, login

OVERVIEW = f"{API}/research/overview"
DATA_QUALITY = f"{API}/research/data-quality"


def _add_patients(db, org_id, count, *, data_origin="real", hy_stage=3):
    for index in range(count):
        db.add(
            Patient(
                patient_id=f"{org_id}-real-{data_origin}-{index}-{uuid4().hex[:6]}",
                org_id=org_id,
                display_name=f"科研患者 {index}",
                birth_year=1950,
                hy_stage=hy_stage,
                stage_confirmation_status="confirmed",
                symptoms=[],
                status="active",
                data_origin=data_origin,
            )
        )


def _add_session_records(db, org_id, count, *, valid_frame_ratio=0.9, doubtful=False):
    now = utcnow()
    for index in range(count):
        db.add(
            SessionRecord(
                id=uuid4().hex,
                client_record_id=f"{org_id}-record-{index}-{uuid4().hex[:6]}",
                org_id=org_id,
                patient_id="patient-a",
                prescription_id="prescription-a",
                recorded_by="therapist-a",
                recorded_at=now - timedelta(minutes=index),
                checked_in=True,
                completion_count=10,
                movement_error_count=1,
                interruption_count=0,
                valid_frame_ratio=valid_frame_ratio,
                is_doubtful=doubtful,
                model_version="pose-full-1.2.2",
                model_sha256="a" * 64,
                metric_algorithm_version="metrics-0.1.0",
                threshold_config_version="thresholds-2026.11",
                # D23 禁用字段即便出现在记录载荷里，也不得回显到科研响应。
                metrics={
                    "cadenceSpm": 96.5,
                    "strideLengthCm": 33.8,
                    "gaitSpeedMps": 0.6,
                },
            )
        )
    db.commit()


@pytest.fixture
def research_data(seed, db_session):
    _add_patients(db_session, "org-a", 5)
    _add_patients(db_session, "org-a", 2, data_origin="demo", hy_stage=1)
    _add_session_records(db_session, "org-a", 6)
    return seed


def test_overview_excludes_demo_patients(client, research_data):
    token = login(client, "therapist-a")
    body = client.get(OVERVIEW, headers=auth_headers(token)).json()

    # 5 位真实 + 3 位 seed 患者（seed 默认 real）= 8；2 位 demo 不得计入。
    assert body["patientCount"]["count"] == 8
    assert body["patientCount"]["suppressed"] is False
    assert body["dataOrigin"] == "real"
    assert body["minCellCount"] == 5

    demo_rows = [
        row
        for row in body["hyStageDistribution"]
        if row["key"] == "1" and row["count"] not in (None, 0)
    ]
    # demo 患者全部 hy_stage=1，计入会得到 2（n<5 仍被抑制），但真实 1 级患者为 0。
    assert all(row["count"] in (None, 0) for row in demo_rows)


def test_overview_suppresses_small_cells(client, seed):
    token = login(client, "therapist-b")
    body = client.get(OVERVIEW, headers=auth_headers(token)).json()

    # org-b 只有 1 位真实患者：总数抑制，1–4 的分段抑制；0 按真值公布。
    assert body["patientCount"] == {"count": None, "suppressed": True}
    hy_rows = {row["key"]: row for row in body["hyStageDistribution"]}
    assert hy_rows["3"]["suppressed"] is True
    assert [key for key, row in hy_rows.items() if row["suppressed"]] == ["3"]
    assert all(
        row["count"] == 0 for key, row in hy_rows.items() if key != "3"
    )


def test_research_requires_clinical_role_and_audits_denial(client, seed, db_session):
    token = login(client, "patient-a")
    response = client.get(OVERVIEW, headers=auth_headers(token))
    assert response.status_code == 403
    assert response.json()["code"] == "FORBIDDEN"

    audit = db_session.scalar(
        select(AuditEvent).where(AuditEvent.action == "authz.role_denied")
    )
    assert audit is not None
    assert audit.actor_account_id == "patient-a-account"
    assert audit.org_id == "org-a"
    assert audit.outcome == "denied"


def test_research_view_writes_audit_with_actor_org_and_detail(client, research_data, db_session):
    token = login(client, "therapist-a")
    client.get(OVERVIEW, headers=auth_headers(token))
    client.get(DATA_QUALITY, headers=auth_headers(token))

    actions = {
        event.action: event
        for event in db_session.scalars(
            select(AuditEvent).where(AuditEvent.action.like("research.%"))
        )
    }
    assert set(actions) == {
        "research.overview.viewed",
        "research.data_quality.viewed",
    }
    for event in actions.values():
        assert event.actor_account_id == "therapist-a"
        assert event.org_id == "org-a"
        assert event.outcome == "success"
        assert event.created_at is not None
        assert event.detail["dataOrigin"] == "real"
        assert event.detail["minCellCount"] == 5
        assert event.detail["filters"] == {}


def test_data_quality_distributions_and_ratios(client, research_data):
    token = login(client, "therapist-a")
    body = client.get(DATA_QUALITY, headers=auth_headers(token)).json()

    assert body["sessionRecordCount"]["count"] == 6
    assert body["missingRatio"] == {"value": 0.0, "suppressed": False}
    assert body["doubtfulRatio"] == {"value": 0.0, "suppressed": False}
    ratio_bands = {row["key"]: row for row in body["validFrameRatioDistribution"]}
    assert ratio_bands["gte-0.8"]["count"] == 6
    # 计数为 0 不指向任何个体，按真实值公布。
    assert ratio_bands["missing"] == {"key": "missing", "count": 0, "suppressed": False}
    assert body["modelVersionDistribution"] == [
        {"key": "pose-full-1.2.2", "count": 6, "suppressed": False}
    ]
    assert "探索性变量" in body["note"]


def test_data_quality_suppresses_small_denominator(client, seed, db_session):
    _add_session_records(db_session, "org-a", 2)
    token = login(client, "therapist-a")
    body = client.get(DATA_QUALITY, headers=auth_headers(token)).json()

    # 2 条记录 < 5：总数抑制，比例随之抑制，版本分布为空。
    assert body["sessionRecordCount"] == {"count": None, "suppressed": True}
    assert body["doubtfulRatio"]["suppressed"] is True
    assert body["missingRatio"]["suppressed"] is True
    assert body["modelVersionDistribution"] == []


def test_data_quality_excludes_demo_patient_records(client, seed, db_session):
    """demo 患者的训练记录同样不得计入科研数据质量。"""
    db_session.add(
        Patient(
            patient_id="patient-demo-research",
            org_id="org-a",
            display_name="演示患者",
            hy_stage=3,
            stage_confirmation_status="confirmed",
            symptoms=[],
            status="active",
            data_origin="demo",
        )
    )
    db_session.commit()
    _add_session_records(db_session, "org-a", 6)
    db_session.execute(
        SessionRecord.__table__.update()
        .where(SessionRecord.client_record_id.like("org-a-record-%"))
        .values(patient_id="patient-demo-research")
    )
    db_session.commit()

    token = login(client, "therapist-a")
    body = client.get(DATA_QUALITY, headers=auth_headers(token)).json()

    # 6 条记录全部属于 demo 患者，过滤后为 0；0 不指向个体，按真值公布。
    assert body["sessionRecordCount"] == {"count": 0, "suppressed": False}
    assert body["modelVersionDistribution"] == []


def test_ratio_is_suppressed_when_numerator_is_small(client, seed, db_session):
    """分母够大但分子 <5 时，精确比例能反解出分子，必须抑制。"""
    _add_session_records(db_session, "org-a", 10)
    db_session.execute(
        SessionRecord.__table__.update()
        .where(SessionRecord.client_record_id.like("org-a-record-0-%"))
        .values(is_doubtful=True, valid_frame_ratio=None)
    )
    db_session.commit()

    token = login(client, "therapist-a")
    body = client.get(DATA_QUALITY, headers=auth_headers(token)).json()

    assert body["doubtfulRatio"] == {"value": None, "suppressed": True}
    assert body["missingRatio"] == {"value": None, "suppressed": True}


def test_bucket_distribution_applies_secondary_suppression():
    """只隐藏一个单元格时，其余单元格之和可由总数反推，必须再隐藏一格。"""
    from app.core.research import buckets

    rows = buckets({"a": 1, "b": 10, "c": 20}, ("a", "b", "c"))
    hidden = [row for row in rows if row["suppressed"]]
    # a 因 1–4 隐藏；b 是最小的可见格，必须同时隐藏，残差才落在两个未知格上。
    assert [row["key"] for row in hidden] == ["a", "b"]
    assert [row["count"] for row in rows] == [None, None, 20]


def test_bucket_distribution_leaves_two_small_cells_alone():
    from app.core.research import buckets

    rows = buckets({"a": 1, "b": 2, "c": 20}, ("a", "b", "c"))
    assert [row["key"] for row in rows if row["suppressed"]] == ["a", "b"]
    assert rows[2]["count"] == 20


def test_zero_cells_are_not_suppressed():
    """计数为 0 不指向任何个体，公布它是真实的质量信号。"""
    from app.core.research import buckets

    rows = buckets({"a": 0, "b": 20}, ("a", "b"))
    assert rows == [
        {"key": "a", "count": 0, "suppressed": False},
        {"key": "b", "count": 20, "suppressed": False},
    ]


def test_version_distribution_applies_secondary_suppression():
    """只丢一个版本时，其条数可由总数反推，必须再丢一个可见版本。"""
    from app.core.research import version_buckets

    assert version_buckets({"v1": 3, "v2": 10, "v3": 20}) == [("v3", 20)]
    assert version_buckets({"v1": 3, "v2": 4, "v3": 20}) == [("v3", 20)]
    # 没有可见版本可丢时保持为空，不编造占位。
    assert version_buckets({"v1": 3}) == []


def test_d23_denylisted_fields_never_appear(client, research_data):
    token = login(client, "therapist-a")
    for path in (OVERVIEW, DATA_QUALITY):
        body = client.get(path, headers=auth_headers(token)).json()
        leaked = find_denylisted_keys(body) & D23_PROHIBITED_FIELDS
        assert leaked == set(), f"{path} 泄露 D23 字段：{sorted(leaked)}"


def test_research_is_org_scoped(client, seed, db_session):
    _add_patients(db_session, "org-b", 6)
    db_session.commit()
    token = login(client, "therapist-b")
    body = client.get(OVERVIEW, headers=auth_headers(token)).json()

    assert body["orgId"] == "org-b"
    # org-b 的 6 位新患者 + seed 的 patient-b；org-a 的患者不得混入。
    assert body["patientCount"]["count"] == 7
