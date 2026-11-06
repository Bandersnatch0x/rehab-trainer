"""训练视频资产：上传、登记、上下架与按分级下发。"""

from datetime import datetime
from typing import Annotated
from uuid import uuid4

from fastapi import APIRouter, Depends, File, Form, Path, Query, UploadFile
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.audit import record_audit
from app.core.auth import Principal, get_principal, require_roles
from app.core.config import get_settings
from app.core.database import get_db
from app.core.errors import api_error
from app.core.media_storage import save_mp4
from app.core.pagination import decode_cursor, encode_cursor
from app.core.timeutils import utcnow
from app.models.content import Exercise
from app.models.media import MediaAsset
from app.schemas.enums import MediaAssetStatus
from app.schemas.media import MediaAssetOut, MediaAssetPage, MediaAssetPatch

router = APIRouter(prefix="/media-assets", tags=["media"])

CLINICIAN_ROLES = ("care_admin", "rehab_therapist")


def _public_url(storage_path: str) -> str:
    base = get_settings().media_public_base_url.rstrip("/")
    return f"{base}/{storage_path}"


def _get_asset(db: Session, principal: Principal, asset_id: str) -> MediaAsset:
    asset = db.get(MediaAsset, asset_id)
    if asset is None:
        raise api_error(404, "NOT_FOUND", "媒体资产不存在")
    if asset.org_id != principal.org_id:
        # 与既有资源一致：跨机构不掩盖存在性，返回 403 并写审计。
        record_audit(
            db,
            org_id=principal.org_id,
            actor_account_id=principal.account_id,
            action="authz.cross_org_access",
            outcome="denied",
            resource_type="media_asset",
            resource_id=asset_id,
            detail={
                "tokenOrgId": principal.org_id,
                "resourceOrgId": asset.org_id,
            },
        )
        db.commit()
        raise api_error(403, "FORBIDDEN", "跨机构访问被拒绝")
    return asset


def _parse_stages(raw: str | None) -> list[int]:
    """表单里的适用分级用逗号分隔；空串表示「尚未裁决」。"""
    if not raw or not raw.strip():
        return []
    try:
        stages = sorted({int(part) for part in raw.split(",") if part.strip()})
    except ValueError:
        raise api_error(422, "VALIDATION_FAILED", "applicableStages 必须是 1–5 的整数")
    if any(stage < 1 or stage > 5 for stage in stages):
        raise api_error(422, "VALIDATION_FAILED", "适用分级必须在 1–5 之间")
    return stages


@router.get("", response_model=MediaAssetPage, response_model_exclude_none=True)
def list_media_assets(
    principal: Principal = Depends(require_roles(*CLINICIAN_ROLES)),
    db: Session = Depends(get_db),
    status: MediaAssetStatus | None = Query(default=None),
    exercise_code: str | None = Query(default=None, alias="exerciseCode"),
    cursor: str | None = Query(default=None),
    limit: int = Query(default=20, ge=1, le=100),
):
    keyset = decode_cursor(cursor, keys=("k",))
    stmt = select(MediaAsset).where(MediaAsset.org_id == principal.org_id)
    if status is not None:
        stmt = stmt.where(MediaAsset.status == status.value)
    if exercise_code is not None:
        stmt = stmt.where(MediaAsset.exercise_code == exercise_code)
    rows = db.scalars(stmt.order_by(MediaAsset.id.asc())).all()

    if keyset is not None:
        rows = [row for row in rows if row.id > keyset["k"]]

    page = rows[:limit]
    has_more = len(rows) > limit
    return MediaAssetPage(
        items=[MediaAssetOut.model_validate(row) for row in page],
        next_cursor=encode_cursor(k=page[-1].id) if has_more and page else None,
    )


@router.post(
    "/upload",
    response_model=MediaAssetOut,
    response_model_exclude_none=True,
    status_code=201,
)
def upload_media_asset(
    file: Annotated[UploadFile, File()],
    principal: Principal = Depends(require_roles(*CLINICIAN_ROLES)),
    db: Session = Depends(get_db),
    exercise_code: Annotated[str | None, Form(alias="exerciseCode")] = None,
    applicable_stages: Annotated[str | None, Form(alias="applicableStages")] = None,
    watermark_text: Annotated[str | None, Form(alias="watermarkText")] = None,
    credit_line: Annotated[str | None, Form(alias="creditLine")] = None,
    note: Annotated[str | None, Form()] = None,
):
    """上传一个 MP4 并登记为草稿资产；上架需另行 PATCH status。

    类型只看文件头魔数，文件名由服务端生成，客户端提供的文件名被完全忽略。
    """
    if exercise_code is not None:
        if db.get(Exercise, exercise_code) is None:
            raise api_error(422, "VALIDATION_FAILED", f"训练条目不存在：{exercise_code}")

    stages = _parse_stages(applicable_stages)
    saved = save_mp4(
        file.file, max_bytes=get_settings().media_max_upload_bytes
    )

    asset = MediaAsset(
        id=uuid4().hex,
        org_id=principal.org_id,
        exercise_code=exercise_code,
        storage_path=saved.storage_path,
        public_url=_public_url(saved.storage_path),
        content_type="video/mp4",
        size_bytes=saved.size_bytes,
        sha256=saved.sha256,
        watermark_text=(watermark_text or None),
        credit_line=(credit_line or None),
        status="draft",
        applicable_stages=stages,
        created_by=principal.account_id,
        note=note,
        created_at=utcnow(),
        updated_at=utcnow(),
    )
    db.add(asset)
    record_audit(
        db,
        org_id=principal.org_id,
        actor_account_id=principal.account_id,
        action="media.uploaded",
        resource_type="media_asset",
        resource_id=asset.id,
        detail={
            "exerciseCode": exercise_code,
            "sizeBytes": saved.size_bytes,
            "sha256": saved.sha256,
        },
    )
    db.commit()
    return MediaAssetOut.model_validate(asset)


@router.patch(
    "/{assetId}",
    response_model=MediaAssetOut,
    response_model_exclude_none=True,
)
def update_media_asset(
    asset_id: Annotated[str, Path(alias="assetId")],
    payload: MediaAssetPatch,
    principal: Principal = Depends(require_roles(*CLINICIAN_ROLES)),
    db: Session = Depends(get_db),
):
    """修改水印、署名、适用分级与上下架状态；状态变更写审计。"""
    asset = _get_asset(db, principal, asset_id)
    previous_status = asset.status

    if payload.watermark_text is not None:
        asset.watermark_text = payload.watermark_text or None
    if payload.credit_line is not None:
        asset.credit_line = payload.credit_line or None
    if payload.note is not None:
        asset.note = payload.note
    if payload.applicable_stages is not None:
        asset.applicable_stages = payload.applicable_stages

    now = utcnow()
    if payload.status is not None and payload.status.value != previous_status:
        if payload.status.value == "published":
            if not list(asset.applicable_stages or []):
                raise api_error(
                    422,
                    "VALIDATION_FAILED",
                    "未设置适用分级，上架后不会下发任何患者",
                )
            asset.status = "published"
            asset.published_at = now
            asset.retired_at = None
        elif payload.status.value == "retired":
            asset.status = "retired"
            asset.retired_at = now
        else:
            asset.status = payload.status.value

    asset.updated_at = now
    record_audit(
        db,
        org_id=asset.org_id,
        actor_account_id=principal.account_id,
        action="media.updated",
        resource_type="media_asset",
        resource_id=asset.id,
        detail={
            "previousStatus": previous_status,
            "status": asset.status,
            "applicableStages": list(asset.applicable_stages or []),
        },
    )
    if asset.status != previous_status and asset.status in ("published", "retired"):
        record_audit(
            db,
            org_id=asset.org_id,
            actor_account_id=principal.account_id,
            action="media.published" if asset.status == "published" else "media.retired",
            resource_type="media_asset",
            resource_id=asset.id,
            detail={
                "exerciseCode": asset.exercise_code,
                "applicableStages": list(asset.applicable_stages or []),
                "previousStatus": previous_status,
            },
        )
    db.commit()
    db.refresh(asset)
    return MediaAssetOut.model_validate(asset)


# ---------- 下发：供 exercises 路由复用 ----------


def latest_published_for_stage(
    db: Session,
    org_id: str,
    exercise_code: str,
    stage: int | None,
) -> MediaAsset | None:
    """取该条目在指定分级下已上架的最新一条；stage 为 None 时按「不限分级」挑选。

    分级匹配只在服务端做，返回给患者端的响应里不含分级字段（D20）。
    """
    stmt = select(MediaAsset).where(
        MediaAsset.org_id == org_id,
        MediaAsset.exercise_code == exercise_code,
        MediaAsset.status == "published",
    )
    assets = db.scalars(
        stmt.order_by(MediaAsset.published_at.desc(), MediaAsset.id.desc())
    ).all()
    for asset in assets:
        stages = list(asset.applicable_stages or [])
        if not stages:
            # 空数组表示未裁决，不匹配任何分级。
            continue
        if stage is None or stage in stages:
            return asset
    return None


def resolve_video_payload(
    db: Session,
    org_id: str,
    exercise_code: str,
    stage: int | None,
) -> dict[str, str] | None:
    """返回待注入到 Exercise 响应的视频字段（模型字段名）；无匹配时返回 None。"""
    asset = latest_published_for_stage(db, org_id, exercise_code, stage)
    if asset is None:
        return None
    payload = {"video_uri": asset.public_url}
    if asset.credit_line:
        payload["video_credit_line"] = asset.credit_line
    if asset.watermark_text:
        payload["video_watermark_text"] = asset.watermark_text
    return payload
