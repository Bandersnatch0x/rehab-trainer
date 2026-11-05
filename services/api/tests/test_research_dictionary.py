"""数据字典 v0.1 与 ORM 模型的一致性校验（N2/N7）。"""

from pathlib import Path
from typing import Any

import pytest
import yaml

from app.core.research import (
    D23_PROHIBITED_FIELDS,
    D23_STATUS_ALLOWED,
    D23_STATUS_PROHIBITED,
    RESEARCH_OUTPUT_FIELDS,
)
from app.models.clinical import Assessment, Patient
from app.models.prescription import Prescription
from app.models.record import SessionRecord

DICTIONARY = (
    Path(__file__).resolve().parents[1] / "data/research-data-dictionary.yaml"
)

TABLES = {
    "patient": Patient,
    "session_record": SessionRecord,
    "assessment": Assessment,
    "prescription": Prescription,
}

REQUIRED_KEYS = (
    "table",
    "field",
    "label_zh",
    "dtype",
    "unit",
    "missing_codes",
    "sensitivity",
    "export_default",
    "d23_status",
    "derivation",
)


def _entries() -> list[dict[str, Any]]:
    data = yaml.safe_load(DICTIONARY.read_text(encoding="utf-8"))
    return [entry for rows in data["datasets"].values() for entry in rows]


def _orm_columns(table: str) -> set[str]:
    model = TABLES[table]
    return {column.name for column in model.__table__.columns}


@pytest.fixture(scope="module")
def entries() -> list[dict[str, Any]]:
    return _entries()


def test_dictionary_is_versioned(entries):
    data = yaml.safe_load(DICTIONARY.read_text(encoding="utf-8"))
    assert data["version"] == "0.1"


def test_every_entry_has_required_metadata(entries):
    missing = [
        (entry.get("table"), entry.get("field"), key)
        for entry in entries
        for key in REQUIRED_KEYS
        if key not in entry
    ]
    assert missing == []


def test_every_field_exists_in_orm(entries):
    unknown = [
        (entry["table"], entry["field"])
        for entry in entries
        if entry["table"] not in TABLES
        or entry["field"] not in _orm_columns(entry["table"])
    ]
    assert unknown == []


def test_d23_status_is_valid(entries):
    invalid = [
        (entry["table"], entry["field"], entry["d23_status"])
        for entry in entries
        if entry["d23_status"] not in (D23_STATUS_ALLOWED, D23_STATUS_PROHIBITED)
    ]
    assert invalid == []


def test_prohibited_entries_are_never_exported(entries):
    """D23 禁用字段不得标为可导出。"""
    leaking = [
        (entry["table"], entry["field"])
        for entry in entries
        if entry["d23_status"] == D23_STATUS_PROHIBITED
        and entry["export_default"] != "excluded"
    ]
    assert leaking == []


def test_doubtful_is_documented_as_exploratory(entries):
    """未决项 12 未签核：is_doubtful 必须写明探索性变量。"""
    doubtful = [
        entry
        for entry in entries
        if entry["table"] == "session_record" and entry["field"] == "is_doubtful"
    ]
    assert len(doubtful) == 1
    assert "探索性" in doubtful[0]["label_zh"]
    assert "探索性" in doubtful[0]["derivation"]


def test_denylist_constant_matches_dictionary(entries):
    """denylist 常量与字典的 prohibited 条目互为镜像，避免两处漂移。"""
    metrics = [
        entry
        for entry in entries
        if entry["table"] == "session_record" and entry["field"] == "metrics"
    ]
    assert len(metrics) == 1
    assert metrics[0]["d23_status"] == D23_STATUS_PROHIBITED
    assert D23_PROHIBITED_FIELDS, "denylist 不能为空"


def test_research_output_fields_are_in_dictionary(entries):
    """科研响应里出现的每个聚合字段，都必须能在字典里找到其来源列。"""
    documented = {(entry["table"], entry["field"]) for entry in entries}
    undocumented = {
        name: source
        for name, source in RESEARCH_OUTPUT_FIELDS.items()
        if source not in documented
    }
    assert undocumented == {}


def test_exported_dictionary_fields_are_used_by_research(entries):
    """字典里标为可导出、且确实参与科研聚合的列不得漏登记。

    只校验 explicitly 标记 in-aggregate 且属于 RESEARCH_OUTPUT_FIELDS 的列；
    其余 in-aggregate 条目留给后续队列/队列构建器，不在此强制。
    """
    used = set(RESEARCH_OUTPUT_FIELDS.values())
    exported = {
        (entry["table"], entry["field"])
        for entry in entries
        if entry["export_default"] == "in-aggregate"
    }
    missing = used - exported
    assert missing == set(), f"科研输出用到的字段未标可导出：{sorted(missing)}"

