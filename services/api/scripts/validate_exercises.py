#!/usr/bin/env python3
"""校验训练条目骨架数据。

默认按「骨架模式」校验：24 条、4 类各 6 条、每条 2 个难度变体，
且所有临床字段仍是待签核占位。临床内容填完后用 --allow-filled 跳过占位检查，
但结构、编码、变体与「不得出现 H-Y 5 级」等规则始终生效。

用法：
    python services/api/scripts/validate_exercises.py
    python services/api/scripts/validate_exercises.py --allow-filled
    python services/api/scripts/validate_exercises.py --file path/to/exercises.json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Annotated, Literal, get_args

from pydantic import AnyUrl, BaseModel, ConfigDict, Field, ValidationError

try:
    import yaml
except ImportError:  # pragma: no cover - 契约对齐检查会给出警告
    yaml = None

REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_DATA = REPO_ROOT / "services" / "api" / "data" / "exercises.example.json"
DEFAULT_CONTRACT = REPO_ROOT / "packages" / "api-contract" / "openapi.yaml"

TODO_MARKER = "TODO: 待临床签核"
EXPECTED_TOTAL = 24
EXPECTED_PER_CATEGORY = 6
EXPECTED_VARIANTS = 2

CATEGORY_VALUES = (
    "stretching-relaxation",
    "strength-maintenance",
    "rhythmic-gait",
    "postural-correction",
)
CATEGORY_PREFIX = {
    "stretching-relaxation": "STR",
    "strength-maintenance": "STG",
    "rhythmic-gait": "GAIT",
    "postural-correction": "POST",
}

BODY_PART_VOCABULARY = {"上肢", "下肢", "躯干", "颈部", "全身"}
JOINT_VOCABULARY = {"髋关节", "膝关节", "踝关节", "肩关节", "肘关节", "腕关节", "脊柱"}

TODO_KEYS = (
    "contraindications",
    "dangerSignals",
    "applicableStages",
    "defaultDosage",
    "riskLevel",
)

LABEL_FORBIDDEN = re.compile(r"[0-9０-９]|组数|次数|分钟|禁忌|禁止|BPM", re.IGNORECASE)

Category = Literal[
    "stretching-relaxation",
    "strength-maintenance",
    "rhythmic-gait",
    "postural-correction",
]
RiskLevel = Literal["low", "medium", "high"]
PublicationStatus = Literal["draft", "published", "retired"]
SupportLevel = Literal["unsupported-standing", "supported-standing", "seated"]
Stage = Annotated[int, Field(ge=1, le=5)]


class Dosage(BaseModel):
    """契约 Dosage；字段均可缺省，空对象表示尚未裁决。"""

    model_config = ConfigDict(extra="forbid")

    sets: int | None = Field(default=None, ge=1)
    repetitions: int | None = Field(default=None, ge=1)
    bpm: int | None = None
    durationMinutes: float | None = Field(default=None, gt=0)
    frequencyPerWeek: int | None = Field(default=None, ge=1)
    restSeconds: int | None = Field(default=None, ge=0)


class Variant(BaseModel):
    """契约 ExerciseVariant；supportLevel 按任务要求必填。"""

    model_config = ConfigDict(extra="forbid")

    code: str = Field(min_length=1)
    level: int = Field(ge=1, le=3)
    supportLevel: SupportLevel
    applicableStages: list[Stage] = Field(default_factory=list)
    defaultDosage: Dosage = Field(default_factory=Dosage)


class Exercise(BaseModel):
    """契约 Exercise；额外保留 _todo 作为临床签核台账。"""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    code: str = Field(min_length=1)
    label: str = Field(min_length=1)
    category: Category
    publicationStatus: PublicationStatus
    riskLevel: RiskLevel
    applicableStages: list[Stage] = Field(default_factory=list)
    bodyParts: list[str] = Field(min_length=1)
    joints: list[str] = Field(min_length=1)
    contraindications: list[str] = Field(min_length=1)
    dangerSignals: list[str] = Field(min_length=1)
    variants: list[Variant]
    defaultDosage: Dosage
    videoUri: AnyUrl
    audioUri: AnyUrl
    todo: dict[str, str] = Field(default_factory=dict, alias="_todo")


class ExerciseFile(BaseModel):
    """顶层文件；items 对齐 ExercisePage，_meta 为文件级说明。"""

    model_config = ConfigDict(extra="allow")

    items: list[Exercise]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="校验训练条目骨架数据")
    parser.add_argument(
        "--file",
        type=Path,
        default=DEFAULT_DATA,
        help=f"待校验 JSON（默认 {DEFAULT_DATA}）",
    )
    parser.add_argument(
        "--contract",
        type=Path,
        default=DEFAULT_CONTRACT,
        help=f"OpenAPI 契约（默认 {DEFAULT_CONTRACT}）",
    )
    parser.add_argument(
        "--allow-filled",
        action="store_true",
        help="允许临床字段已填写；仅跳过骨架占位检查，结构规则仍生效",
    )
    return parser.parse_args()


def check_structure(
    doc: ExerciseFile,
    allow_filled: bool,
    errors: list[str],
    warnings: list[str],
) -> None:
    items = doc.items

    if len(items) != EXPECTED_TOTAL:
        errors.append(f"条目总数为 {len(items)}，应为 {EXPECTED_TOTAL}")

    counts = Counter(item.category for item in items)
    for category in CATEGORY_VALUES:
        if counts[category] != EXPECTED_PER_CATEGORY:
            errors.append(
                f"训练大类 {category} 有 {counts[category]} 条，应为 {EXPECTED_PER_CATEGORY}"
            )
        expected_codes = {
            f"{CATEGORY_PREFIX[category]}-{number:03d}"
            for number in range(1, EXPECTED_PER_CATEGORY + 1)
        }
        actual_codes = {item.code for item in items if item.category == category}
        if actual_codes != expected_codes:
            errors.append(
                f"训练大类 {category} 的编码应为 {sorted(expected_codes)}，"
                f"实际为 {sorted(actual_codes)}"
            )

    code_counts = Counter(item.code for item in items)
    for code, count in code_counts.items():
        if count > 1:
            errors.append(f"训练条目编码重复：{code}")

    variant_counts = Counter(
        variant.code for item in items for variant in item.variants
    )
    for code, count in variant_counts.items():
        if count > 1:
            errors.append(f"难度变体编码重复：{code}")

    pending_contra: list[str] = []
    pending_danger: list[str] = []

    for item in items:
        prefix = item.code.split("-", 1)[0]
        expected_prefix = CATEGORY_PREFIX[item.category]
        if prefix != expected_prefix:
            errors.append(
                f"{item.code} 的前缀 {prefix} 与训练大类 {item.category} "
                f"应为的前缀 {expected_prefix} 不一致"
            )

        if LABEL_FORBIDDEN.search(item.label):
            errors.append(
                f"{item.code} 的 label「{item.label}」含剂量、数字或禁忌措辞；"
                "骨架标签只写动作类型"
            )

        unknown_parts = set(item.bodyParts) - BODY_PART_VOCABULARY
        if unknown_parts:
            errors.append(
                f"{item.code} 的 bodyParts 含本体词汇表之外的值："
                f"{sorted(unknown_parts)}"
            )
        unknown_joints = set(item.joints) - JOINT_VOCABULARY
        if unknown_joints:
            errors.append(
                f"{item.code} 的 joints 含本体词汇表之外的值："
                f"{sorted(unknown_joints)}"
            )

        for label, stages in [
            ("applicableStages", item.applicableStages),
            *[
                (f"variants[{variant.code}].applicableStages", variant.applicableStages)
                for variant in item.variants
            ],
        ]:
            if 5 in stages:
                errors.append(
                    f"{item.code} 的 {label} 含 H-Y 5 级；5 级仅宣教与转诊（D5）"
                )

        if allow_filled:
            if any(value.startswith(TODO_MARKER) for value in item.contraindications):
                pending_contra.append(item.code)
            if any(value.startswith(TODO_MARKER) for value in item.dangerSignals):
                pending_danger.append(item.code)
        else:
            if not any(
                value.startswith(TODO_MARKER) for value in item.contraindications
            ):
                errors.append(f"{item.code} 缺少禁忌占位（至少 1 条）")
            if not any(
                value.startswith(TODO_MARKER) for value in item.dangerSignals
            ):
                errors.append(f"{item.code} 缺少危险信号占位（至少 1 条）")

        if len(item.variants) != EXPECTED_VARIANTS:
            errors.append(
                f"{item.code} 有 {len(item.variants)} 个变体，应为 {EXPECTED_VARIANTS}"
            )
            continue

        expected_variants = [
            (f"{item.code}-V1", 1),
            (f"{item.code}-V2", 2),
        ]
        actual_variants = [(variant.code, variant.level) for variant in item.variants]
        if actual_variants != expected_variants:
            errors.append(
                f"{item.code} 的变体应为 {expected_variants}，实际为 {actual_variants}"
            )

        if allow_filled:
            continue

        if item.publicationStatus != "draft":
            errors.append(
                f"{item.code} 的 publicationStatus 为 {item.publicationStatus}；"
                "未签核骨架只能是 draft"
            )
        if item.applicableStages:
            errors.append(
                f"{item.code} 的 applicableStages 非空；未签核前不得填写适用分级"
            )
        if item.defaultDosage.model_dump(exclude_none=True):
            errors.append(
                f"{item.code} 的 defaultDosage 非空；未签核前不得填写剂量"
            )
        for variant in item.variants:
            if variant.applicableStages:
                errors.append(
                    f"{variant.code} 的 applicableStages 非空；"
                    "未签核前不得填写适用分级"
                )
            if variant.defaultDosage.model_dump(exclude_none=True):
                errors.append(
                    f"{variant.code} 的 defaultDosage 非空；未签核前不得填写剂量"
                )

        for key in TODO_KEYS:
            value = item.todo.get(key, "")
            if not value.startswith(TODO_MARKER):
                errors.append(
                    f"{item.code} 的 _todo.{key} 缺少「{TODO_MARKER}」占位"
                )

    if pending_contra:
        warnings.append(
            f"{len(pending_contra)} 条条目的 contraindications 仍含待签核占位"
        )
    if pending_danger:
        warnings.append(
            f"{len(pending_danger)} 条条目的 dangerSignals 仍含待签核占位"
        )
    if allow_filled:
        leftover_todo = [item.code for item in items if item.todo]
        if leftover_todo:
            warnings.append(
                f"{len(leftover_todo)} 条条目仍保留 _todo 台账；签核完成后可删除"
            )


def _literal_values(literal_type: object) -> tuple[str, ...]:
    return tuple(str(value) for value in get_args(literal_type))


def check_contract(contract_path: Path, errors: list[str], warnings: list[str]) -> None:
    if yaml is None:
        warnings.append("未安装 PyYAML，跳过契约字段与枚举对齐检查")
        return
    if not contract_path.exists():
        warnings.append(f"契约文件不存在，跳过对齐检查：{contract_path}")
        return

    try:
        spec = yaml.safe_load(contract_path.read_text(encoding="utf-8"))
        schemas = spec["components"]["schemas"]
        exercise_schema = schemas["Exercise"]
        variant_schema = schemas["ExerciseVariant"]
    except (KeyError, TypeError, yaml.YAMLError) as exc:
        warnings.append(f"无法读取契约中的 Exercise/ExerciseVariant：{exc}")
        return

    model_fields = set(Exercise.model_fields) - {"todo"}
    contract_props = set(exercise_schema.get("properties", {}))
    missing_in_contract = model_fields - contract_props
    if missing_in_contract:
        errors.append(
            f"以下模型字段不在契约 Exercise 中：{sorted(missing_in_contract)}"
        )
    extra_in_contract = contract_props - model_fields
    if extra_in_contract:
        warnings.append(
            f"契约 Exercise 有模型未覆盖的字段（可能尚未填充）："
            f"{sorted(extra_in_contract)}"
        )

    contract_required = set(exercise_schema.get("required", []))
    model_required = {
        name for name, field_info in Exercise.model_fields.items() if field_info.is_required()
    }
    missing_required = contract_required - model_required
    if missing_required:
        errors.append(
            f"契约必填字段在模型中非必填：{sorted(missing_required)}"
        )

    enum_checks = [
        (
            "ExerciseCategory",
            _literal_values(Category),
            schemas["ExerciseCategory"].get("enum", []),
        ),
        (
            "RiskLevel",
            _literal_values(RiskLevel),
            schemas["RiskLevel"].get("enum", []),
        ),
        (
            "publicationStatus",
            _literal_values(PublicationStatus),
            exercise_schema["properties"]["publicationStatus"].get("enum", []),
        ),
        (
            "ExerciseVariant.supportLevel",
            _literal_values(SupportLevel),
            variant_schema["properties"]["supportLevel"].get("enum", []),
        ),
    ]
    for name, model_values, contract_values in enum_checks:
        if set(model_values) != set(contract_values):
            errors.append(
                f"{name} 枚举与契约不一致：模型 {sorted(model_values)}，"
                f"契约 {sorted(contract_values)}"
            )


def main() -> int:
    args = parse_args()

    print("训练条目骨架校验")
    print(f"数据文件：{args.file}")
    print(f"契约文件：{args.contract}")
    print(f"模式：{'已填写模式（跳过占位检查）' if args.allow_filled else '骨架模式'}")
    print("-" * 60)

    if not args.file.exists():
        print(f"失败：数据文件不存在：{args.file}")
        return 1

    try:
        raw = json.loads(args.file.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"失败：无法读取 JSON：{exc}")
        return 1

    try:
        doc = ExerciseFile.model_validate(raw)
    except ValidationError as exc:
        print("结构校验：失败")
        for error in exc.errors():
            location = ".".join(str(part) for part in error["loc"])
            print(f"  - {location or '<root>'}: {error['msg']}")
        print("-" * 60)
        print(f"失败：结构校验发现 {exc.error_count()} 个问题")
        return 1

    errors: list[str] = []
    warnings: list[str] = []
    check_structure(doc, args.allow_filled, errors, warnings)
    check_contract(args.contract, errors, warnings)

    print("结构校验：通过（Pydantic 模型对齐契约 Exercise/ExerciseVariant）")

    if warnings:
        print("警告：")
        for warning in warnings:
            print(f"  - {warning}")

    if errors:
        print("规则校验：失败")
        for error in errors:
            print(f"  - {error}")
        print("-" * 60)
        print(f"失败：规则校验发现 {len(errors)} 个问题")
        return 1

    counts = Counter(item.category for item in doc.items)
    variant_total = sum(len(item.variants) for item in doc.items)
    print("规则校验：通过")
    print(f"  - 条目：{len(doc.items)} 条（应为 {EXPECTED_TOTAL}）")
    for category in CATEGORY_VALUES:
        print(f"    {category}: {counts[category]} 条")
    print(f"  - 难度变体：{variant_total} 个（每条 {EXPECTED_VARIANTS} 个）")
    if args.allow_filled:
        print("  - 每条禁忌与危险信号非空：通过")
    else:
        print("  - 每条禁忌与危险信号均有占位：通过")
    print("  - 适用分级均不含 H-Y 5 级：通过")
    if not args.allow_filled:
        print("  - 临床字段均为待签核占位：通过")
        print("  - 发布状态均为 draft：通过")
    print("-" * 60)
    print(f"通过：{len(doc.items)} 条骨架，{variant_total} 个难度变体。")
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
