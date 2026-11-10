"""训练视频资产：上传安全、上下架审计与按分期下发。"""

import os
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.core.media_storage import resolve_under_root
from tests.helpers import API, auth_headers, login

# 最小可识别的 MP4 头：box 长度 + ftyp + major brand isom。
MP4_BYTES = b"\x00\x00\x00\x18ftypisom\x00\x00\x02\x00isom"


def _upload(
    client: TestClient,
    token: str,
    *,
    content: bytes = MP4_BYTES,
    filename: str = "demo.mp4",
    content_type: str = "video/mp4",
    **form: str,
):
    return client.post(
        f"{API}/media-assets/upload",
        headers=auth_headers(token),
        files={"file": (filename, content, content_type)},
        data=form,
    )


def _publish(client: TestClient, token: str, asset_id: str, **patch):
    body = {"status": "published", **patch}
    return client.patch(
        f"{API}/media-assets/{asset_id}",
        headers=auth_headers(token),
        json=body,
    )


# ---------- 上传安全 ----------


def test_upload_rejects_non_mp4_magic(client, seed):
    token = login(client, "therapist-a")
    response = _upload(
        client,
        token,
        content=b"not a video at all, just text padding to satisfy length",
        filename="fake.mp4",
        content_type="video/mp4",
    )
    assert response.status_code == 422
    assert response.json()["code"] == "UNSUPPORTED_MEDIA_TYPE"


def test_upload_ignores_client_filename_and_generates_uuid(client, seed):
    token = login(client, "therapist-a")
    response = _upload(
        client,
        token,
        filename="../../etc/passwd.mp4",
        content_type="application/octet-stream",
        applicableStages="1,2,3",
        watermarkText="演示素材",
        creditLine="素材来源：示例机构提供",
    )
    assert response.status_code == 201, response.text
    body = response.json()
    # 客户端文件名完全不参与落盘：存储路径只能是 videos/<32 hex>.mp4。
    stored = body["publicUrl"].rsplit("/", 2)
    assert stored[-2] == "videos"
    assert stored[-1].endswith(".mp4")
    assert len(stored[-1]) == len("x" * 32 + ".mp4")
    assert "passwd" not in body["publicUrl"]
    assert body["contentType"] == "video/mp4"
    assert body["applicableStages"] == [1, 2, 3]
    assert body["watermarkText"] == "演示素材"
    assert body["creditLine"] == "素材来源：示例机构提供"
    assert body["status"] == "draft"


def test_upload_enforces_size_limit(client, seed, monkeypatch):
    monkeypatch.setattr(get_settings(), "media_max_upload_bytes", len(MP4_BYTES) - 1)
    token = login(client, "therapist-a")
    response = _upload(client, token, content=MP4_BYTES)
    assert response.status_code == 413
    assert response.json()["code"] == "PAYLOAD_TOO_LARGE"


def test_upload_writes_inside_media_root(client, seed):
    token = login(client, "therapist-a")
    response = _upload(client, token)
    assert response.status_code == 201
    storage_path = response.json()["publicUrl"].split("/media/", 1)[1]
    written = resolve_under_root(storage_path)
    assert written.is_file()
    assert written.parent.name == "videos"


# 越权路径按平台分别取：Windows 绝对路径（另一个盘符）必须拒绝；
# 在 POSIX 上同样的字符串只是含反斜杠的普通文件名，落在 root 内——拒绝它反而说明实现错了。
TRAVERSAL_PATHS = ["../escape.mp4", "/etc/passwd", "videos/../../etc/passwd"]
if os.name == "nt":
    TRAVERSAL_PATHS.append("C:\\temp\\x.mp4")


@pytest.mark.parametrize("bad_path", TRAVERSAL_PATHS)
def test_resolve_under_root_rejects_traversal(bad_path):
    with pytest.raises(Exception):
        resolve_under_root(bad_path, Path(get_settings().media_root))


def test_upload_rejects_unknown_exercise_code(client, seed):
    token = login(client, "therapist-a")
    response = _upload(client, token, exerciseCode="ex-missing")
    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_FAILED"


def test_upload_rejects_out_of_range_stage(client, seed):
    token = login(client, "therapist-a")
    response = _upload(client, token, applicableStages="0,6")
    assert response.status_code == 422


# ---------- 权限 ----------


@pytest.mark.parametrize("username", ["patient-a", "caregiver-a"])
def test_non_clinician_cannot_manage_media(client, seed, username):
    token = login(client, username)
    assert client.get(f"{API}/media-assets", headers=auth_headers(token)).status_code == 403
    assert _upload(client, token).status_code == 403


def test_cross_org_asset_is_forbidden(client, seed):
    therapist_a = login(client, "therapist-a")
    created = _upload(client, therapist_a).json()

    therapist_b = login(client, "therapist-b")
    response = client.patch(
        f"{API}/media-assets/{created['id']}",
        headers=auth_headers(therapist_b),
        json={"watermarkText": "越界"},
    )
    assert response.status_code == 403


def test_list_is_org_scoped(client, seed):
    therapist_a = login(client, "therapist-a")
    created = _upload(client, therapist_a).json()

    therapist_b = login(client, "therapist-b")
    listing = client.get(f"{API}/media-assets", headers=auth_headers(therapist_b))
    assert listing.status_code == 200
    assert listing.json()["items"] == []

    own = client.get(f"{API}/media-assets", headers=auth_headers(therapist_a))
    assert {item["id"] for item in own.json()["items"]} == {created["id"]}


# ---------- 上下架与审计 ----------


def test_publish_requires_applicable_stages(client, seed):
    token = login(client, "therapist-a")
    asset = _upload(client, token).json()
    response = _publish(client, token, asset["id"])
    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_FAILED"


def test_published_asset_cannot_clear_stages(client, seed):
    """已上架资产单独清空分级也必须拒绝，否则患者端会静默停止收到视频。"""
    token = login(client, "therapist-a")
    asset = _upload(client, token, applicableStages="1,2,3").json()
    assert _publish(client, token, asset["id"]).status_code == 200

    response = client.patch(
        f"{API}/media-assets/{asset['id']}",
        headers=auth_headers(token),
        json={"applicableStages": []},
    )
    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_FAILED"


def test_publish_and_retire_write_audit(client, seed, db_session):
    token = login(client, "therapist-a")
    asset = _upload(client, token, applicableStages="1,2,3").json()

    published = _publish(client, token, asset["id"])
    assert published.status_code == 200
    assert published.json()["status"] == "published"
    assert published.json()["publishedAt"] is not None

    retired = client.patch(
        f"{API}/media-assets/{asset['id']}",
        headers=auth_headers(token),
        json={"status": "retired"},
    )
    assert retired.status_code == 200
    assert retired.json()["status"] == "retired"
    assert retired.json()["retiredAt"] is not None

    from app.models.audit import AuditEvent  # noqa: PLC0415
    from sqlalchemy import select  # noqa: PLC0415

    actions = set(
        db_session.scalars(
            select(AuditEvent.action).where(
                AuditEvent.resource_id == asset["id"],
                AuditEvent.action.in_(["media.published", "media.retired"]),
            )
        ).all()
    )
    assert actions == {"media.published", "media.retired"}


def test_list_filters_by_status_and_exercise(client, seed):
    token = login(client, "therapist-a")
    draft = _upload(client, token, exerciseCode="ex-low", applicableStages="1").json()
    published = _upload(client, token, exerciseCode="ex-low", applicableStages="1").json()
    _publish(client, token, published["id"])

    by_status = client.get(
        f"{API}/media-assets",
        params={"status": "published"},
        headers=auth_headers(token),
    )
    assert [item["id"] for item in by_status.json()["items"]] == [published["id"]]

    by_exercise = client.get(
        f"{API}/media-assets",
        params={"exerciseCode": "ex-low"},
        headers=auth_headers(token),
    )
    assert {item["id"] for item in by_exercise.json()["items"]} == {
        draft["id"],
        published["id"],
    }


# ---------- 下发策略 ----------


def _video_for(client, token, code="ex-low"):
    response = client.get(f"{API}/exercises/{code}", headers=auth_headers(token))
    assert response.status_code == 200, response.text
    return response.json()


def test_draft_asset_is_not_delivered(client, seed):
    token = login(client, "patient-a")
    therapist = login(client, "therapist-a")
    _upload(client, therapist, exerciseCode="ex-low", applicableStages="1,2,3")
    assert "videoUri" not in _video_for(client, token)


def test_published_asset_is_delivered_with_credit_and_watermark(client, seed):
    therapist = login(client, "therapist-a")
    asset = _upload(
        client,
        therapist,
        exerciseCode="ex-low",
        applicableStages="3",
        watermarkText="演示素材 · 禁止转播",
        creditLine="素材来源：示例机构提供",
    ).json()
    _publish(client, therapist, asset["id"])

    patient = _video_for(client, login(client, "patient-a"), "ex-low")
    assert patient["videoUri"] == asset["publicUrl"]
    assert patient["videoWatermarkText"] == "演示素材 · 禁止转播"
    assert patient["videoCreditLine"] == "素材来源：示例机构提供"
    # D20：媒体资产的分期只用于服务端挑选，不得回传给患者端。
    assert "hyStage" not in patient
    assert set(patient) & {"mediaAssetStage", "videoApplicableStages"} == set()


def test_stage_mismatch_is_not_delivered(client, seed):
    therapist = login(client, "therapist-a")
    asset = _upload(
        client, therapist, exerciseCode="ex-low", applicableStages="1,2"
    ).json()
    _publish(client, therapist, asset["id"])

    # patient-a 是 H-Y 3 期，不在 1–2 里。
    assert "videoUri" not in _video_for(client, login(client, "patient-a"), "ex-low")


def test_retired_asset_is_not_delivered(client, seed):
    therapist = login(client, "therapist-a")
    asset = _upload(
        client, therapist, exerciseCode="ex-low", applicableStages="1,2,3"
    ).json()
    _publish(client, therapist, asset["id"])
    client.patch(
        f"{API}/media-assets/{asset['id']}",
        headers=auth_headers(therapist),
        json={"status": "retired"},
    )
    assert "videoUri" not in _video_for(client, login(client, "patient-a"), "ex-low")


def test_latest_published_asset_wins(client, seed):
    therapist = login(client, "therapist-a")
    older = _upload(
        client, therapist, exerciseCode="ex-low", applicableStages="3",
        watermarkText="旧",
    ).json()
    _publish(client, therapist, older["id"])
    newer = _upload(
        client, therapist, exerciseCode="ex-low", applicableStages="3",
        watermarkText="新",
    ).json()
    _publish(client, therapist, newer["id"])

    delivered = _video_for(client, login(client, "patient-a"), "ex-low")
    assert delivered["videoUri"] == newer["publicUrl"]
    assert delivered["videoWatermarkText"] == "新"


def test_asset_for_other_exercise_does_not_leak(client, seed):
    therapist = login(client, "therapist-a")
    asset = _upload(
        client, therapist, exerciseCode="ex-medium", applicableStages="1,2,3"
    ).json()
    _publish(client, therapist, asset["id"])
    assert "videoUri" not in _video_for(client, login(client, "patient-a"), "ex-low")


def test_list_delivers_video_per_item(client, seed):
    therapist = login(client, "therapist-a")
    asset = _upload(
        client, therapist, exerciseCode="ex-low", applicableStages="1,2,3"
    ).json()
    _publish(client, therapist, asset["id"])

    response = client.get(f"{API}/exercises", headers=auth_headers(login(client, "patient-a")))
    assert response.status_code == 200
    items = {item["code"]: item for item in response.json()["items"]}
    assert items["ex-low"]["videoUri"] == asset["publicUrl"]
    assert "videoUri" not in items["ex-medium"]
