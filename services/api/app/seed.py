"""开发期演示数据。仅用于本地联调，生产环境不要执行。

用法：
    python -m app.seed
"""

from uuid import uuid4

from sqlalchemy import select

from app.core.database import Base, SessionLocal, engine, init_db
from app.core.security import hash_password
from app.core.timeutils import utcnow
from app.models.alert import AlertEvent, NotificationDelivery
from app.models.clinical import Patient, StageConfirmation
from app.models.content import Exercise, ExerciseVariant
from app.models.identity import Account, BindingToken, Org, RoleBinding
from app.models.prescription import Prescription, PrescriptionItem

ORG_ID = "org-demo"
PATIENT_ID = "patient-demo"
THERAPIST_ID = "therapist-demo"
ADMIN_ID = "admin-demo"
CAREGIVER_ID = "caregiver-demo"
PATIENT_ACCOUNT_ID = "account-patient-demo"


def _ensure_org(db) -> None:
    if db.get(Org, ORG_ID):
        return
    db.add(Org(org_id=ORG_ID, org_name="示例康复机构"))


def _add_account(db, account_id, username, display_name, password, role, patient_id=None):
    if db.get(Account, account_id):
        return
    db.add(
        Account(
            account_id=account_id,
            org_id=ORG_ID,
            username=username,
            display_name=display_name,
            password_hash=hash_password(password),
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
            org_id=ORG_ID,
            patient_id=patient_id,
        )
    )


def _seed_identities(db) -> None:
    _add_account(db, ADMIN_ID, "admin", "示例照护管理员", "admin123", "care_admin")
    _add_account(
        db, THERAPIST_ID, "therapist", "示例康复治疗师", "therapist123", "rehab_therapist"
    )
    _add_account(
        db,
        PATIENT_ACCOUNT_ID,
        "patient",
        "示例患者",
        "patient123",
        "patient",
        PATIENT_ID,
    )
    _add_account(
        db, CAREGIVER_ID, "caregiver", "示例照护者", "caregiver123", "caregiver", PATIENT_ID
    )


def _seed_patient(db) -> None:
    if db.get(Patient, PATIENT_ID):
        return
    confirmed_at = utcnow()
    db.add(
        Patient(
            patient_id=PATIENT_ID,
            org_id=ORG_ID,
            display_name="张阿姨",
            birth_year=1948,
            hy_stage=3,
            stage_confirmation_status="confirmed",
            stage_confirmed_by=THERAPIST_ID,
            stage_confirmed_at=confirmed_at,
            fall_risk="medium",
            symptoms=["Rigidity"],
            status="active",
            # 演示库一律标 demo，科研查询默认只取 real（N1）。
            data_origin="demo",
        )
    )
    db.flush()


def _seed_stage_confirmation(db) -> None:
    if db.scalar(
        select(StageConfirmation).where(
            StageConfirmation.patient_id == PATIENT_ID
        )
    ):
        return
    confirmed_at = utcnow()
    db.add(
        StageConfirmation(
            id=uuid4().hex,
            patient_id=PATIENT_ID,
            org_id=ORG_ID,
            stage=3,
            status="confirmed",
            confirmed_by=THERAPIST_ID,
            confirmed_at=confirmed_at,
            note="演示数据：治疗师确认 H-Y 3 级",
        )
    )


def _seed_exercises(db) -> None:
    if db.get(Exercise, "ex-stretch-01"):
        return
    exercises = [
        Exercise(
            code="ex-stretch-01",
            label="肩颈牵伸",
            category="stretching-relaxation",
            publication_status="published",
            risk_level="low",
            applicable_stages=[1, 2, 3, 4],
            body_parts=["颈部", "上肢"],
            joints=["肩关节"],
            contraindications=["急性疼痛"],
            danger_signals=["头晕", "明显疼痛"],
            default_dosage={
                "sets": 2,
                "repetitions": 8,
                "durationMinutes": 5,
                "frequencyPerWeek": 5,
                "restSeconds": 30,
            },
        ),
        Exercise(
            code="ex-strength-01",
            label="坐位抬腿",
            category="strength-maintenance",
            publication_status="published",
            risk_level="medium",
            applicable_stages=[1, 2, 3, 4],
            body_parts=["下肢"],
            joints=["膝关节"],
            contraindications=["膝关节急性损伤"],
            danger_signals=["关节剧痛"],
            default_dosage={
                "sets": 3,
                "repetitions": 10,
                "frequencyPerWeek": 3,
                "restSeconds": 60,
            },
        ),
        Exercise(
            code="ex-gait-01",
            label="节拍步行",
            category="rhythmic-gait",
            publication_status="published",
            risk_level="high",
            applicable_stages=[1, 2, 3, 4],
            body_parts=["全身", "下肢"],
            joints=["髋关节", "膝关节", "踝关节"],
            contraindications=["体位性低血压", "近期跌倒伴受伤"],
            danger_signals=["冻结步态发作", "胸闷", "头晕"],
            default_dosage={
                "durationMinutes": 10,
                "frequencyPerWeek": 3,
            },
        ),
        Exercise(
            code="ex-posture-01",
            label="靠墙站立矫正",
            category="postural-correction",
            publication_status="published",
            risk_level="medium",
            applicable_stages=[1, 2, 3, 4],
            body_parts=["躯干"],
            joints=["脊柱"],
            contraindications=["严重骨质疏松"],
            danger_signals=["腰背剧痛"],
        ),
    ]
    db.add_all(exercises)
    db.flush()
    db.add_all(
        [
            ExerciseVariant(
                code="ex-stretch-01-v1",
                exercise_code="ex-stretch-01",
                level=1,
                support_level="seated",
                applicable_stages=[1, 2, 3, 4],
            ),
            ExerciseVariant(
                code="ex-stretch-01-v2",
                exercise_code="ex-stretch-01",
                level=2,
                support_level="supported-standing",
                applicable_stages=[1, 2, 3, 4],
            ),
            ExerciseVariant(
                code="ex-gait-01-v1",
                exercise_code="ex-gait-01",
                level=1,
                support_level="supported-standing",
                applicable_stages=[3, 4],
            ),
        ]
    )


def _seed_prescription(db) -> None:
    if db.scalar(select(Prescription).where(Prescription.patient_id == PATIENT_ID)):
        return
    now = utcnow()
    prescription = Prescription(
        id="prescription-demo",
        org_id=ORG_ID,
        patient_id=PATIENT_ID,
        prescribed_by=THERAPIST_ID,
        version="1.0.0",
        mapping_table_version="2026.11.0",
        issued_at=now,
        valid_from=now,
        valid_until=now.replace(year=now.year + 1),
        status="active",
    )
    prescription.items = [
        PrescriptionItem(
            id=uuid4().hex,
            prescription_id=prescription.id,
            exercise_code="ex-stretch-01",
            position=0,
        ),
        PrescriptionItem(
            id=uuid4().hex,
            prescription_id=prescription.id,
            exercise_code="ex-strength-01",
            position=1,
        ),
    ]
    db.add(prescription)


def _seed_alert(db) -> None:
    if db.get(AlertEvent, "alert-demo"):
        return
    now = utcnow()
    alert = AlertEvent(
        id="alert-demo",
        org_id=ORG_ID,
        patient_id=PATIENT_ID,
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
        ),
        NotificationDelivery(
            id=uuid4().hex,
            alert_id=alert.id,
            channel="audit_log",
            status="sent",
            delivered_at=now,
        ),
    ]
    db.add(alert)


def _seed_binding_token(db) -> None:
    if db.get(BindingToken, "demo-binding-token"):
        return
    db.add(
        BindingToken(
            token="demo-binding-token",
            account_id=PATIENT_ACCOUNT_ID,
            org_id=ORG_ID,
            patient_id=PATIENT_ID,
            expires_at=utcnow().replace(year=utcnow().year + 1),
        )
    )


def seed() -> None:
    init_db()
    with SessionLocal() as db:
        _ensure_org(db)
        db.flush()
        # 患者先落库，角色绑定与分级确认才能引用。
        _seed_patient(db)
        _seed_identities(db)
        _seed_stage_confirmation(db)
        _seed_exercises(db)
        _seed_prescription(db)
        _seed_alert(db)
        _seed_binding_token(db)
        db.commit()
    print("演示数据已就绪：")
    print("  机构      org-demo")
    print("  治疗师    therapist / therapist123")
    print("  照护管理员 admin / admin123")
    print("  患者      patient / patient123")
    print("  照护者    caregiver / caregiver123")
    print("  绑定码    demo-binding-token（一次性）")
    print("  患者 ID   patient-demo")


if __name__ == "__main__":
    seed()
