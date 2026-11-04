import os
import tempfile
from datetime import timedelta
from pathlib import Path
from uuid import uuid4

# 必须在导入 app 之前设置，确保应用引擎指向测试库。
_TEST_DIR = Path(tempfile.mkdtemp(prefix="rehab-api-tests-"))
os.environ["REHAB_DATABASE_URL"] = f"sqlite:///{_TEST_DIR.as_posix()}/test.db"
os.environ["REHAB_JWT_SECRET"] = "test-only-secret-0123456789abcdef-0123"
os.environ["REHAB_AUTO_CREATE_SCHEMA"] = "true"

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app.core.database import Base, SessionLocal, engine  # noqa: E402
from app.core.security import hash_password  # noqa: E402
from app.core.timeutils import utcnow  # noqa: E402
from app.main import app  # noqa: E402
from app.models.alert import AlertEvent, NotificationDelivery  # noqa: E402
from app.models.clinical import Patient, StageConfirmation  # noqa: E402
from app.models.content import Exercise, ExerciseVariant  # noqa: E402
from app.models.identity import (  # noqa: E402
    Account,
    BindingToken,
    Org,
    RoleBinding,
)
from app.models.prescription import Prescription, PrescriptionItem  # noqa: E402

COMMON_PASSWORD_HASH = hash_password("secret123")


def _add_account(db, account_id, org_id, username, display_name, role, patient_id=None):
    db.add(
        Account(
            account_id=account_id,
            org_id=org_id,
            username=username,
            display_name=display_name,
            password_hash=COMMON_PASSWORD_HASH,
            status="active",
        )
    )
    # 无 relationship 的跨表外键需显式 flush 才能保证插入顺序。
    db.flush()
    db.add(
        RoleBinding(
            id=uuid4().hex,
            account_id=account_id,
            role=role,
            org_id=org_id,
            patient_id=patient_id,
        )
    )


def _seed() -> dict[str, str]:
    now = utcnow()
    with SessionLocal() as db:
        db.add_all(
            [
                Org(org_id="org-a", org_name="甲机构"),
                Org(org_id="org-b", org_name="乙机构"),
            ]
        )
        db.flush()
        db.add_all(
            [
                Patient(
                    patient_id="patient-a",
                    org_id="org-a",
                    display_name="患者甲",
                    hy_stage=3,
                    stage_confirmation_status="confirmed",
                    stage_confirmed_by="therapist-a",
                    stage_confirmed_at=now,
                    symptoms=["Rigidity"],
                    status="active",
                ),
                Patient(
                    patient_id="patient-unconfirmed",
                    org_id="org-a",
                    display_name="待确认患者",
                    hy_stage=3,
                    stage_confirmation_status="suspected",
                    status="active",
                ),
                Patient(
                    patient_id="patient-hy5",
                    org_id="org-a",
                    display_name="五级患者",
                    hy_stage=5,
                    stage_confirmation_status="confirmed",
                    stage_confirmed_by="therapist-a",
                    stage_confirmed_at=now,
                    status="active",
                ),
                Patient(
                    patient_id="patient-b",
                    org_id="org-b",
                    display_name="患者乙",
                    hy_stage=3,
                    stage_confirmation_status="confirmed",
                    stage_confirmed_by="therapist-b",
                    stage_confirmed_at=now,
                    status="active",
                ),
            ]
        )
        db.flush()

        _add_account(db, "therapist-a", "org-a", "therapist-a", "治疗师甲", "rehab_therapist")
        _add_account(db, "admin-a", "org-a", "admin-a", "管理员甲", "care_admin")
        _add_account(
            db, "patient-a-account", "org-a", "patient-a", "患者甲", "patient", "patient-a"
        )
        _add_account(
            db,
            "caregiver-a",
            "org-a",
            "caregiver-a",
            "照护者甲",
            "caregiver",
            "patient-a",
        )
        _add_account(
            db,
            "patient-unconfirmed-account",
            "org-a",
            "patient-unconfirmed",
            "待确认患者",
            "patient",
            "patient-unconfirmed",
        )
        _add_account(
            db,
            "patient-hy5-account",
            "org-a",
            "patient-hy5",
            "五级患者",
            "patient",
            "patient-hy5",
        )
        _add_account(db, "therapist-b", "org-b", "therapist-b", "治疗师乙", "rehab_therapist")
        _add_account(
            db, "patient-b-account", "org-b", "patient-b", "患者乙", "patient", "patient-b"
        )
        db.flush()

        db.add(
            StageConfirmation(
                id=uuid4().hex,
                patient_id="patient-a",
                org_id="org-a",
                stage=3,
                status="confirmed",
                confirmed_by="therapist-a",
                confirmed_at=now,
            )
        )

        db.add_all(
            [
                Exercise(
                    code="ex-low",
                    label="低风险牵伸",
                    category="stretching-relaxation",
                    publication_status="published",
                    risk_level="low",
                    applicable_stages=[1, 2, 3, 4],
                    contraindications=["急性疼痛"],
                    danger_signals=["明显疼痛"],
                ),
                Exercise(
                    code="ex-medium",
                    label="中风险肌力",
                    category="strength-maintenance",
                    publication_status="published",
                    risk_level="medium",
                    applicable_stages=[1, 2, 3, 4],
                    contraindications=["关节急性损伤"],
                    danger_signals=["关节剧痛"],
                ),
                Exercise(
                    code="ex-high",
                    label="高风险节律步态",
                    category="rhythmic-gait",
                    publication_status="published",
                    risk_level="high",
                    applicable_stages=[1, 2, 3, 4],
                    contraindications=["近期跌倒伴受伤"],
                    danger_signals=["胸闷", "头晕"],
                ),
                Exercise(
                    code="ex-draft",
                    label="未发布条目",
                    category="postural-correction",
                    publication_status="draft",
                    risk_level="low",
                    applicable_stages=[1, 2, 3, 4],
                    contraindications=["无"],
                    danger_signals=["无"],
                ),
            ]
        )
        db.flush()
        db.add(
            ExerciseVariant(
                code="ex-low-v1",
                exercise_code="ex-low",
                level=1,
                support_level="seated",
                applicable_stages=[1, 2, 3, 4],
            )
        )
        db.flush()

        prescription = Prescription(
            id="prescription-a",
            org_id="org-a",
            patient_id="patient-a",
            prescribed_by="therapist-a",
            version="1.0.0",
            mapping_table_version="test.1",
            issued_at=now,
            valid_from=now - timedelta(days=1),
            valid_until=now + timedelta(days=30),
            status="active",
        )
        prescription.items = [
            PrescriptionItem(
                id=uuid4().hex,
                prescription_id=prescription.id,
                exercise_code="ex-low",
                position=0,
            )
        ]
        db.add(prescription)

        alert = AlertEvent(
            id="alert-a",
            org_id="org-a",
            patient_id="patient-a",
            rule_code="PAIN_PERSISTENT",
            level="warning",
            triggered_at=now,
            status="open",
        )
        alert.deliveries = [
            NotificationDelivery(
                id=uuid4().hex,
                alert_id=alert.id,
                channel="in_app_popup",
                status="sent",
                delivered_at=now,
            )
        ]
        db.add(alert)

        db.add_all(
            [
                BindingToken(
                    token="bind-once",
                    account_id="patient-a-account",
                    org_id="org-a",
                    patient_id="patient-a",
                    expires_at=now + timedelta(days=1),
                ),
                BindingToken(
                    token="bind-expired",
                    account_id="patient-a-account",
                    org_id="org-a",
                    patient_id="patient-a",
                    expires_at=now - timedelta(days=1),
                ),
                BindingToken(
                    token="bind-used",
                    account_id="patient-a-account",
                    org_id="org-a",
                    patient_id="patient-a",
                    expires_at=now + timedelta(days=1),
                    used_at=now - timedelta(hours=1),
                ),
            ]
        )
        db.commit()

    return {
        "org_a": "org-a",
        "org_b": "org-b",
        "patient_a": "patient-a",
        "patient_unconfirmed": "patient-unconfirmed",
        "patient_hy5": "patient-hy5",
        "patient_b": "patient-b",
        "therapist_a": "therapist-a",
        "therapist_b": "therapist-b",
        "admin_a": "admin-a",
        "patient_a_account": "patient-a-account",
        "caregiver_a": "caregiver-a",
        "patient_unconfirmed_account": "patient-unconfirmed-account",
        "prescription_a": "prescription-a",
        "alert_a": "alert-a",
    }


@pytest.fixture(autouse=True)
def seed() -> dict[str, str]:
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    return _seed()


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


@pytest.fixture
def db_session():
    with SessionLocal() as session:
        yield session
