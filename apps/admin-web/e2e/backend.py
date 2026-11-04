"""真实后端启动及每例隔离数据；只允许写入本仓临时测试数据库。"""

import json
import os
from datetime import timedelta
from pathlib import Path
import sys
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[3]
database_url = os.environ["REHAB_DATABASE_URL"]
database_path = Path(database_url.removeprefix("sqlite:///")).resolve()
artifact_dir = (ROOT / ".agent/tmp/admin-e2e").resolve()
if not database_url.startswith("sqlite:///") or not database_path.is_relative_to(artifact_dir):
    raise ValueError("E2E database must be inside .agent/tmp/admin-e2e")

sys.path.insert(0, str(ROOT / "services/api"))

from app.core.database import SessionLocal, init_db
from app.core.security import hash_password
from app.core.timeutils import utcnow
from app.models.alert import AlertEvent
from app.models.clinical import Patient
from app.models.identity import Account, Org, RoleBinding
from app.models.prescription import Prescription, PrescriptionItem
from app.seed import _seed_exercises


def seed_case(extra_patients: int = 0):
    case_id = uuid4().hex
    patient_id = f"patient-{case_id}"
    other_id = f"other-{case_id}"
    therapist_id = f"therapist-{case_id}"
    prescription_id = f"rx-{case_id}"
    alert_id = f"alert-{case_id}"
    now = utcnow()
    accounts = {role: f"{role}-{case_id}" for role in ("care_admin", "rehab_therapist", "patient")}
    with SessionLocal() as db:
        db.add(Org(org_id=case_id, org_name=f"测试机构-{case_id}"))
        db.flush()
        for identifier, name, stage in [(patient_id, "测试三期患者", 3), (other_id, "测试二期患者", 2)]:
            db.add(Patient(patient_id=identifier, org_id=case_id, display_name=name,
                           birth_year=1950, hy_stage=stage, stage_confirmation_status="confirmed",
                           stage_confirmed_by=therapist_id, stage_confirmed_at=now,
                           fall_risk="medium", symptoms=[], status="active"))
        # 分页回归用：同一机构下再加若干患者，超过单页上限。
        for index in range(extra_patients):
            db.add(Patient(patient_id=f"extra-{case_id}-{index}", org_id=case_id,
                           display_name=f"批量患者 {index + 1}", birth_year=1950,
                           hy_stage=2, stage_confirmation_status="confirmed",
                           stage_confirmed_by=therapist_id, stage_confirmed_at=now,
                           fall_risk="low", symptoms=[], status="active"))
        db.flush()
        password_hash = hash_password(os.environ["E2E_PASSWORD"])
        for role, username in accounts.items():
            account_id = therapist_id if role == "rehab_therapist" else username
            db.add(Account(account_id=account_id, org_id=case_id, username=username,
                           display_name=role, password_hash=password_hash, status="active"))
            db.flush()
            db.add(RoleBinding(id=uuid4().hex, account_id=account_id, role=role,
                               org_id=case_id, patient_id=patient_id if role == "patient" else None))
        db.flush()
        prescription = Prescription(id=prescription_id, org_id=case_id, patient_id=patient_id,
                                    prescribed_by=therapist_id, version="1.0.0",
                                    mapping_table_version="2026.11.0", issued_at=now,
                                    valid_from=now, valid_until=now + timedelta(days=30), status="active")
        # 两条条目：用于验证调整处方时不会把未编辑的条目一起删掉。
        prescription.items = [
            PrescriptionItem(id=uuid4().hex, prescription_id=prescription_id,
                             exercise_code="ex-stretch-01", position=0),
            PrescriptionItem(id=uuid4().hex, prescription_id=prescription_id,
                             exercise_code="ex-strength-01", position=1),
        ]
        db.add(prescription)
        db.add(AlertEvent(id=alert_id, org_id=case_id, patient_id=patient_id,
                          rule_code="PAIN_PERSISTENT", level="warning", triggered_at=now, status="open"))
        db.commit()
    print(json.dumps(dict(patientId=patient_id, otherPatientId=other_id,
                         prescriptionId=prescription_id, alertId=alert_id, accounts=accounts)))


if __name__ == "__main__":
    if sys.argv[1:] == ["serve"]:
        init_db()
        with SessionLocal() as db:
            _seed_exercises(db)
            db.commit()
        import uvicorn
        port = int(os.environ.get("E2E_BACKEND_PORT", "18765"))
        uvicorn.run("app.main:app", host="127.0.0.1", port=port)
    elif sys.argv[1:] == ["seed"]:
        seed_case()
    elif sys.argv[1:] == ["seed-many"]:
        # 105 位患者 = 两页（页面每页 100），用于验证列表真的会翻页而不是只取第一页。
        seed_case(extra_patients=103)
    else:
        raise SystemExit("Usage: python e2e/backend.py serve|seed")
