from fastapi import APIRouter, Depends, Query
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.access import get_patient_scoped
from app.core.auth import Principal, get_principal
from app.core.clinical import (
    HIGH_RISK_LEVEL,
    HIGH_RISK_REQUIRES_CONFIRMED_STAGE,
    TRAINING_MAX_HY_STAGE,
)
from app.core.database import get_db
from app.core.errors import api_error
from app.core.pagination import decode_cursor, encode_cursor
from app.models.clinical import Patient
from app.models.content import Exercise
from app.routers.media import resolve_video_payload
from app.schemas.enums import ExerciseCategory
from app.schemas.exercise import ExerciseOut, ExercisePage

router = APIRouter(prefix="/exercises", tags=["exercises"])


def _resolve_patient_context(
    db: Session, principal: Principal
) -> Patient | None:
    """解析训练条目列表/详情所用的患者上下文。

    患者或仅关联一名患者的照护者自动套用本人上下文；临床角色按内容库视图返回，
    不做个体准入过滤（个体准入在开具处方时强制校验）。
    """
    if principal.is_clinician or len(principal.patient_ids) != 1:
        return None
    return get_patient_scoped(db, principal, principal.patient_ids[0])


def _visible_for_patient(
    exercise: Exercise, patient: Patient, stage_filter: int | None
) -> bool:
    if exercise.publication_status != "published":
        return False
    # D5：H-Y 5 级仅宣教与转诊，不进入训练流程。
    if patient.hy_stage > TRAINING_MAX_HY_STAGE:
        return False
    stages = list(exercise.applicable_stages or [])
    # 空数组表示「适用分级尚未裁决」，不得下发给患者。
    if not stages or patient.hy_stage not in stages:
        return False
    if (
        HIGH_RISK_REQUIRES_CONFIRMED_STAGE
        and exercise.risk_level == HIGH_RISK_LEVEL
        and patient.stage_confirmation_status != "confirmed"
    ):
        return False
    if stage_filter is not None and stages and stage_filter not in stages:
        return False
    return True


def _visible_for_stage(exercise: Exercise, stage_filter: int) -> bool:
    stages = list(exercise.applicable_stages or [])
    # 空数组表示「适用分级尚未裁决」，不匹配任何具体分级。
    return bool(stages) and stage_filter in stages


def _with_media(
    db: Session,
    principal: Principal,
    rows: list[Exercise],
    patient: Patient | None,
    stage_filter: int | None = None,
) -> list[ExerciseOut]:
    """把服务端挑选的视频字段注入响应。

    患者上下文按本人 H-Y 分级挑选；临床角色按查询里的 stage 挑选，
    两者都没有时取不限分级的已上架资产。分级本身不回传（D20）。
    """
    if patient is not None:
        stage = patient.hy_stage
    else:
        stage = stage_filter
    outputs = []
    for row in rows:
        payload = resolve_video_payload(db, principal.org_id, row.code, stage)
        base = ExerciseOut.model_validate(row)
        outputs.append(base.model_copy(update=payload) if payload else base)
    return outputs


@router.get("", response_model=ExercisePage, response_model_exclude_none=True)
def list_exercises(
    principal: Principal = Depends(get_principal),
    db: Session = Depends(get_db),
    category: ExerciseCategory | None = Query(default=None),
    stage: int | None = Query(default=None, ge=1, le=5),
    cursor: str | None = Query(default=None),
    limit: int = Query(default=20, ge=1, le=100),
):
    keyset = decode_cursor(cursor, keys=("k",))
    patient = _resolve_patient_context(db, principal)

    stmt = select(Exercise).where(Exercise.publication_status == "published")
    if category is not None:
        stmt = stmt.where(Exercise.category == category.value)
    rows = db.scalars(stmt.order_by(Exercise.code.asc())).all()

    if patient is not None:
        visible = [row for row in rows if _visible_for_patient(row, patient, stage)]
    elif stage is not None:
        visible = [row for row in rows if _visible_for_stage(row, stage)]
    else:
        visible = rows

    # 训练条目按 code 升序且 code 唯一，游标只需记录最后一个 code。
    if keyset is not None:
        visible = [row for row in visible if row.code > keyset["k"]]

    page = visible[:limit]
    has_more = len(visible) > limit
    return ExercisePage(
        items=_with_media(db, principal, page, patient, stage),
        next_cursor=encode_cursor(k=page[-1].code) if has_more and page else None,
    )


@router.get(
    "/{code}", response_model=ExerciseOut, response_model_exclude_none=True
)
def get_exercise(
    code: str,
    principal: Principal = Depends(get_principal),
    db: Session = Depends(get_db),
):
    exercise = db.get(Exercise, code)
    if exercise is None:
        raise api_error(404, "NOT_FOUND", "训练条目不存在")

    patient = _resolve_patient_context(db, principal)
    if patient is not None and not _visible_for_patient(exercise, patient, None):
        # 患者上下文下，不可用条目与不存在同义，避免暴露未发布内容。
        raise api_error(404, "NOT_FOUND", "训练条目不存在")
    if not principal.is_clinician and exercise.publication_status != "published":
        # 非临床账号（含未绑定/多患者账号）同样不得读取草稿或已下架条目。
        raise api_error(404, "NOT_FOUND", "训练条目不存在")
    return _with_media(db, principal, [exercise], patient)[0]
