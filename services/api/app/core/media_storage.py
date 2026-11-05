"""视频文件落盘：类型校验、服务端命名、路径约束与摘要计算。

安全约束（全部在本模块收口，路由层只拿结果）：
- 只接受 `video/mp4`，按文件头魔数判断，不信任扩展名与 Content-Type；
- 文件名由服务端生成（UUID + `.mp4`），完全不使用客户端提供的文件名；
- 落盘路径固定在配置的静态根下，写前再次确认解析后的绝对路径没有逃出根目录；
- 计算 SHA-256，供去重与完整性校验。
"""

import hashlib
import os
import tempfile
from dataclasses import dataclass
from pathlib import Path
from uuid import uuid4

from app.core.errors import api_error

# ISO BMFF：`ftyp` box 从偏移 4 开始，前 4 字节是 box 长度。
MP4_MAGIC_OFFSET = 4
MP4_MAGIC = b"ftyp"
# 允许的 ftyp 品牌（major brand）。只列本系统会产出的常见 MP4 品牌。
MP4_BRANDS = {
    b"isom",
    b"iso2",
    b"mp41",
    b"mp42",
    b"avc1",
    b"M4V ",
    b"dash",
    b"qt  ",
}

VIDEO_SUBDIR = "videos"
MEDIA_SUFFIX = ".mp4"


@dataclass(frozen=True, slots=True)
class SavedVideo:
    """一次落盘的结果；storage_path 相对静态根，public_url 由调用方拼装。"""

    storage_path: str
    size_bytes: int
    sha256: str


def sniff_mp4(head: bytes) -> bool:
    """按魔数判断字节流是否为 MP4；仅看头部，不解析完整容器。"""
    if len(head) < MP4_MAGIC_OFFSET + len(MP4_MAGIC) + 4:
        return False
    if head[MP4_MAGIC_OFFSET : MP4_MAGIC_OFFSET + len(MP4_MAGIC)] != MP4_MAGIC:
        return False
    brand = head[MP4_MAGIC_OFFSET + len(MP4_MAGIC) : MP4_MAGIC_OFFSET + len(MP4_MAGIC) + 4]
    return brand in MP4_BRANDS


def _media_root() -> Path:
    from app.core.config import get_settings

    return Path(get_settings().media_root)


def resolve_under_root(storage_path: str, root: Path | None = None) -> Path:
    """把相对路径解析到静态根下；任何逃逸（`..`、绝对路径、盘符）一律拒绝。"""
    base = (root or _media_root()).resolve()
    candidate = Path(storage_path)
    if candidate.is_absolute() or candidate.drive or candidate.root:
        raise api_error(422, "VALIDATION_FAILED", "媒体路径非法")
    # 相对路径里出现 `..` 视为越界意图，不做「先拼后判断」的宽容处理。
    if any(part == ".." for part in candidate.parts):
        raise api_error(422, "VALIDATION_FAILED", "媒体路径非法")
    resolved = (base / candidate).resolve()
    if not resolved.is_relative_to(base):
        raise api_error(422, "VALIDATION_FAILED", "媒体路径非法")
    return resolved


def save_mp4(chunk_source, *, max_bytes: int, root: Path | None = None) -> SavedVideo:
    """把上传流写入静态根，返回相对路径与摘要。

    `chunk_source` 是可按块迭代的字节源（路由层传 SpooledUploadFile.file）。
    超过上限立刻中止并清理临时文件，不把半截文件留在静态目录。
    """
    base = (root or _media_root()).resolve()
    base.mkdir(parents=True, exist_ok=True)
    target_dir = resolve_under_root(VIDEO_SUBDIR, base)
    target_dir.mkdir(parents=True, exist_ok=True)

    # 服务端命名：UUID + 固定后缀。客户端文件名不参与任何路径计算。
    filename = f"{uuid4().hex}{MEDIA_SUFFIX}"
    storage_path = f"{VIDEO_SUBDIR}/{filename}"
    final_path = resolve_under_root(storage_path, base)

    digest = hashlib.sha256()
    size = 0
    head = b""
    fd, temp_name = tempfile.mkstemp(dir=target_dir, prefix=".upload-", suffix=".part")
    try:
        with os.fdopen(fd, "wb") as temp:
            for chunk in chunk_source:
                if not chunk:
                    continue
                if len(head) < MP4_MAGIC_OFFSET + len(MP4_MAGIC) + 4:
                    head += chunk[: 32 - len(head)]
                size += len(chunk)
                if size > max_bytes:
                    raise api_error(
                        413,
                        "PAYLOAD_TOO_LARGE",
                        f"文件超过上限 {max_bytes // (1024 * 1024)} MB",
                        {"maxBytes": max_bytes},
                    )
                digest.update(chunk)
                temp.write(chunk)
        if size == 0:
            raise api_error(422, "VALIDATION_FAILED", "上传文件为空")
        if not sniff_mp4(head):
            raise api_error(
                422,
                "UNSUPPORTED_MEDIA_TYPE",
                "只接受 MP4 视频（按文件头校验，不信任扩展名）",
            )
        # 同一文件系统内 rename 是原子的：读端不会看到半截文件。
        os.replace(temp_name, final_path)
    except BaseException:
        if os.path.exists(temp_name):
            os.unlink(temp_name)
        raise

    return SavedVideo(
        storage_path=storage_path, size_bytes=size, sha256=digest.hexdigest()
    )


def delete_stored(storage_path: str, root: Path | None = None) -> None:
    """删除已落盘文件；路径非法或文件不存在时静默返回。"""
    try:
        path = resolve_under_root(storage_path, root)
    except Exception:  # noqa: BLE001 — 路径非法等同于「无可删」
        return
    if path.is_file():
        path.unlink()
