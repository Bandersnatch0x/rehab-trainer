#!/usr/bin/env python3
"""用 rdflib + pyshacl 校验 rehab-trainer 的 RDF 实例。

用法：
    python validate.py examples/valid-minimal.ttl
    python validate.py --expect violation examples/invalid-hy5-prescription.ttl
    python validate.py --suite examples

依赖：
    python -m pip install rdflib pyshacl
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from rdflib import Graph, Namespace
from rdflib.namespace import RDF

try:
    from pyshacl import validate as shacl_validate
except ImportError:  # pragma: no cover - 安装提示
    print(
        "缺少 pyshacl。请先安装：python -m pip install rdflib pyshacl",
        file=sys.stderr,
    )
    raise SystemExit(2)

HERE = Path(__file__).resolve().parent
DEFAULT_ONTOLOGY = HERE / "rehab.ttl"
DEFAULT_SHAPES = HERE / "shapes.ttl"

SH = Namespace("http://www.w3.org/ns/shacl#")
RT = Namespace("https://rehab-trainer.local/ontology#")
RTSH = Namespace("https://rehab-trainer.local/shapes#")


def load_graph(path: Path) -> Graph:
    """解析一个 Turtle 文件；失败时给出可读错误。"""
    graph = Graph()
    try:
        graph.parse(path, format="turtle")
    except Exception as exc:  # noqa: BLE001 - 需要把解析错误原样报给使用者
        raise SystemExit(f"解析失败：{path}\n{exc}") from exc
    return graph


def load_data(paths: list[Path]) -> Graph:
    """把多个实例文件合并成一个数据图。"""
    graph = Graph()
    for path in paths:
        if not path.is_file():
            raise SystemExit(f"实例文件不存在：{path}")
        graph += load_graph(path)
    return graph


def run_validation(
    data_paths: list[Path],
    ontology_path: Path,
    shapes_path: Path,
    inference: str,
) -> tuple[bool, Graph, str]:
    """返回 (conforms, results_graph, results_text)。"""
    if not ontology_path.is_file():
        raise SystemExit(f"本体文件不存在：{ontology_path}")
    if not shapes_path.is_file():
        raise SystemExit(f"SHACL 文件不存在：{shapes_path}")

    data_graph = load_data(data_paths)
    ontology_graph = load_graph(ontology_path)
    shapes_graph = load_graph(shapes_path)

    conforms, results_graph, results_text = shacl_validate(
        data_graph,
        shacl_graph=shapes_graph,
        ont_graph=ontology_graph,
        inference=inference,
        abort_on_first=False,
        allow_infos=True,
        allow_warnings=True,
        meta_shacl=False,
        advanced=False,
        debug=False,
    )
    if not isinstance(results_graph, Graph):
        raise SystemExit(f"SHACL 校验引擎报错：{results_text}")
    return conforms, results_graph, results_text


def _term(graph: Graph, term) -> str:
    """把 RDF 项显示成尽量短的名字。"""
    if term is None:
        return "-"
    if isinstance(term, str):
        return term
    try:
        return graph.qname(term)
    except Exception:  # noqa: BLE001 - 未知命名空间时退回完整 IRI
        return str(term)


def validation_rows(results_graph: Graph) -> list[dict[str, str]]:
    """提取每条校验结果的关键字段。"""
    for prefix, namespace in (("sh", SH), ("rt", RT), ("rtsh", RTSH)):
        results_graph.bind(prefix, namespace)

    rows: list[dict[str, str]] = []
    for result in results_graph.subjects(RDF.type, SH.ValidationResult):
        rows.append(
            {
                "severity": _term(results_graph, results_graph.value(result, SH.resultSeverity)),
                "focus": _term(results_graph, results_graph.value(result, SH.focusNode)),
                "path": _term(results_graph, results_graph.value(result, SH.resultPath)),
                "message": str(results_graph.value(result, SH.resultMessage) or ""),
                "source": _term(results_graph, results_graph.value(result, SH.sourceShape)),
            }
        )
    rows.sort(key=lambda row: (row["severity"], row["focus"], row["path"], row["message"]))
    return rows


def print_result(
    title: str,
    conforms: bool,
    rows: list[dict[str, str]],
    results_text: str,
    full: bool,
) -> None:
    """打印单个实例的校验结论与逐条违规。"""
    print(f"\n=== {title} ===")
    if conforms:
        print("结论：CONFORMS（0 条违规）")
        if full:
            print(results_text)
        return

    print(f"结论：NON-CONFORMS（{len(rows)} 条结果）")
    for index, row in enumerate(rows, start=1):
        print(f"[{index}] {row['severity']}")
        print(f"    focus:   {row['focus']}")
        print(f"    path:    {row['path']}")
        print(f"    source:  {row['source']}")
        print(f"    message: {row['message']}")
    if full:
        print("\n--- pyshacl 原始报告 ---")
        print(results_text)


def run_suite(suite_dir: Path, ontology: Path, shapes: Path, inference: str, full: bool) -> int:
    """跑目录内全部实例；valid-* 期望通过，invalid-* 期望违规。"""
    files = sorted(suite_dir.glob("*.ttl"))
    if not files:
        print(f"目录内没有 .ttl 实例：{suite_dir}", file=sys.stderr)
        return 2

    failures = 0
    for path in files:
        expected = "conform" if path.name.startswith("valid-") else "violation"
        conforms, results_graph, results_text = run_validation(
            [path], ontology, shapes, inference
        )
        rows = validation_rows(results_graph)
        passed = (expected == "conform" and conforms) or (
            expected == "violation" and not conforms
        )
        status = "PASS" if passed else "FAIL"
        actual = "CONFORMS" if conforms else "NON-CONFORMS"
        print(
            f"{status} {path.name}: 期望 {expected}，实际 {actual}，"
            f"{len(rows)} 条结果"
        )
        if not passed or full:
            for row in rows:
                print(f"      - [{row['severity']}] {row['message']} ({row['focus']})")
        if not passed:
            failures += 1

    print(f"\n汇总：{len(files) - failures}/{len(files)} 通过")
    return 0 if failures == 0 else 1


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="用 OWL 本体与 SHACL 形状校验 rehab-trainer RDF 实例。"
    )
    parser.add_argument(
        "data",
        nargs="*",
        type=Path,
        help="一个或多个 Turtle 实例文件；多个文件会合并成一个数据图。",
    )
    parser.add_argument(
        "--ontology",
        type=Path,
        default=DEFAULT_ONTOLOGY,
        help=f"OWL 本体路径（默认：{DEFAULT_ONTOLOGY}）",
    )
    parser.add_argument(
        "--shapes",
        type=Path,
        default=DEFAULT_SHAPES,
        help=f"SHACL 形状路径（默认：{DEFAULT_SHAPES}）",
    )
    parser.add_argument(
        "--inference",
        choices=("none", "rdfs", "owlrl", "both"),
        default="rdfs",
        help="推理模式（默认：rdfs）",
    )
    parser.add_argument(
        "--expect",
        choices=("conform", "violation"),
        help="期望结果；用于把违规实例的校验纳入自动化。",
    )
    parser.add_argument(
        "--suite",
        type=Path,
        help="跑目录内全部实例；valid-* 期望通过，invalid-* 期望违规。",
    )
    parser.add_argument(
        "--full",
        action="store_true",
        help="额外打印 pyshacl 的完整文本报告。",
    )
    return parser


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    args = build_parser().parse_args()

    if args.suite:
        return run_suite(args.suite, args.ontology, args.shapes, args.inference, args.full)

    if not args.data:
        build_parser().print_help()
        return 2

    conforms, results_graph, results_text = run_validation(
        args.data, args.ontology, args.shapes, args.inference
    )
    rows = validation_rows(results_graph)
    title = ", ".join(path.name for path in args.data)
    print_result(title, conforms, rows, results_text, args.full)

    if args.expect == "conform":
        return 0 if conforms else 1
    if args.expect == "violation":
        return 0 if not conforms else 1
    return 0 if conforms else 1


if __name__ == "__main__":
    raise SystemExit(main())
