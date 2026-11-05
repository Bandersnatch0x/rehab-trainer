"""科研聚合端点（N3/N5/N7）。

只返回聚合值：计数、分段分布、比例。任何 n<5 的单元格抑制为 null。
所有查询默认限定当前账号 org_id 且只取 data_origin='real'。
"""

from collections import Counter

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.audit import record_audit
from app.core.auth import Principal, require_roles
from app.core.database import get_db
from app.core.research import (
    AGE_BANDS,
    AGE_UNKNOWN_KEY,
    MIN_CELL_COUNT,
    RESEARCH_DATA_ORIGIN,
    age_band,
    buckets,
    rate,
    suppressed,
    valid_frame_ratio_band,
    version_buckets,
)
from app.core.timeutils import utcnow
from app.models.clinical import Patient
from app.models.record import SessionRecord
from app.schemas.research import (
    Bucket,
    CountCell,
    RateCell,
    ResearchDataQuality,
    ResearchOverview,
    VersionDistribution,
)

router = APIRouter(prefix="/research", tags=["research"])

RESEARCH_ROLES = ("rehab_therapist", "care_admin")

HY_STAGE_KEYS = ("1", "2", "3", "4", "5")
CONFIRMATION_KEYS = ("confirmed", "suspected")
VALID_FRAME_RATIO_KEYS = ("missing", "lt-0.5", "0.5-0.8", "gte-0.8")


def _cell(count: int) -> CountCell:
    payload = suppressed(count)
    return CountCell(count=payload["count"], suppressed=payload["suppressed"])


def _rate(numerator: int, denominator: int) -> RateCell:
    payload = rate(numerator, denominator)
    return RateCell(value=payload["value"], suppressed=payload["suppressed"])


def _buckets(counts: Counter[str], keys: tuple[str, ...]) -> list[Bucket]:
    return [
        Bucket(key=item["key"], count=item["count"], suppressed=item["suppressed"])
        for item in buckets(counts, keys)
    ]


def _version_distribution(counts: Counter[str]) -> list[VersionDistribution]:
    return [
        VersionDistribution(key=key, count=count, suppressed=False)
        for key, count in version_buckets(counts)
    ]


def _real_patients(db: Session, org_id: str):
    return db.scalars(
        select(Patient).where(
            Patient.org_id == org_id,
            Patient.data_origin == RESEARCH_DATA_ORIGIN,
        )
    ).all()


@router.get("/overview", response_model=ResearchOverview)
def research_overview(
    principal: Principal = Depends(require_roles(*RESEARCH_ROLES)),
    db: Session = Depends(get_db),
):
    patients = _real_patients(db, principal.org_id)

    hy_counts: Counter[str] = Counter(str(p.hy_stage) for p in patients)
    confirmation_counts: Counter[str] = Counter(
        p.stage_confirmation_status for p in patients
    )
    age_counts: Counter[str] = Counter(age_band(p.birth_year) for p in patients)
    age_keys = tuple(key for key, _, _ in AGE_BANDS) + (AGE_UNKNOWN_KEY,)

    payload = ResearchOverview(
        org_id=principal.org_id,
        generated_at=utcnow(),
        data_origin=RESEARCH_DATA_ORIGIN,
        min_cell_count=MIN_CELL_COUNT,
        patient_count=_cell(len(patients)),
        hy_stage_distribution=_buckets(hy_counts, HY_STAGE_KEYS),
        stage_confirmation_distribution=_buckets(confirmation_counts, CONFIRMATION_KEYS),
        age_distribution=_buckets(age_counts, age_keys),
    )
    record_audit(
        db,
        org_id=principal.org_id,
        actor_account_id=principal.account_id,
        action="research.overview.viewed",
        resource_type="research",
        resource_id=principal.org_id,
        detail={
            "dataOrigin": RESEARCH_DATA_ORIGIN,
            "minCellCount": MIN_CELL_COUNT,
            "filters": {},
        },
    )
    db.commit()
    return payload


@router.get("/data-quality", response_model=ResearchDataQuality)
def research_data_quality(
    principal: Principal = Depends(require_roles(*RESEARCH_ROLES)),
    db: Session = Depends(get_db),
):
    # ponytail: 单机构全量拉取后在内存计数；样本上千万时改为 SQL group by。
    # 联查患者表以应用 demo 硬过滤：demo 患者的训练记录不得计入科研输出。
    rows = db.execute(
        select(
            SessionRecord.valid_frame_ratio,
            SessionRecord.is_doubtful,
            SessionRecord.model_version,
            SessionRecord.metric_algorithm_version,
            SessionRecord.threshold_config_version,
        )
        .join(Patient, Patient.patient_id == SessionRecord.patient_id)
        .where(
            SessionRecord.org_id == principal.org_id,
            Patient.data_origin == RESEARCH_DATA_ORIGIN,
        )
    ).all()

    total = len(rows)
    ratio_counts: Counter[str] = Counter(
        valid_frame_ratio_band(row.valid_frame_ratio) for row in rows
    )
    doubtful_count = sum(1 for row in rows if row.is_doubtful)
    missing_count = sum(1 for row in rows if row.valid_frame_ratio is None)

    model_counts: Counter[str] = Counter(row.model_version for row in rows)
    algorithm_counts: Counter[str] = Counter(
        row.metric_algorithm_version for row in rows
    )
    threshold_counts: Counter[str] = Counter(
        row.threshold_config_version for row in rows
    )

    payload = ResearchDataQuality(
        org_id=principal.org_id,
        generated_at=utcnow(),
        data_origin=RESEARCH_DATA_ORIGIN,
        min_cell_count=MIN_CELL_COUNT,
        session_record_count=_cell(total),
        valid_frame_ratio_distribution=_buckets(ratio_counts, VALID_FRAME_RATIO_KEYS),
        doubtful_ratio=_rate(doubtful_count, total),
        missing_ratio=_rate(missing_count, total),
        model_version_distribution=_version_distribution(model_counts),
        metric_algorithm_version_distribution=_version_distribution(algorithm_counts),
        threshold_config_version_distribution=_version_distribution(threshold_counts),
    )
    record_audit(
        db,
        org_id=principal.org_id,
        actor_account_id=principal.account_id,
        action="research.data_quality.viewed",
        resource_type="research",
        resource_id=principal.org_id,
        detail={
            "dataOrigin": RESEARCH_DATA_ORIGIN,
            "minCellCount": MIN_CELL_COUNT,
            "filters": {},
        },
    )
    db.commit()
    return payload
