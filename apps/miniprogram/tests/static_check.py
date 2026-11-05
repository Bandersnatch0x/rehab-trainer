#!/usr/bin/env python3
"""小程序静态一致性检查：不需要微信开发者工具与登录。

覆盖 app.json 与页面文件一致性、WXML 绑定根标识符与 data 键对齐、
事件处理函数存在性、D20 患者端禁用词扫描。可独立复跑。
"""

import json
import pathlib
import re
import sys


BANNED = ["H-Y", "hyStage", "跌倒风险", "帕金森", "症状"]
HANDLER = re.compile(
    r'bind(?:tap|input|change|submit|confirm|blur|focus|longpress)="([A-Za-z_$][\w$]*)"'
)
BINDING = re.compile(r"\{\{(.*?)\}\}", re.S)
ROOT_IDENT = re.compile(r"(?<![.\w$])[A-Za-z_$][\w$]*")
KEYWORDS = {"true", "false", "null", "undefined", "item", "index", "length", "Math"}


def data_keys(ts):
    match = re.search(r"data:\s*\{", ts)
    if not match:
        return set()
    start = match.end() - 1
    depth = 0
    block = ""
    for offset, char in enumerate(ts[start:], start):
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                block = ts[start : offset + 1]
                break
    return set(re.findall(r"(?<![.\w$])([A-Za-z_$][\w$]*)\s*:", block))


def roots(wxml):
    out = set()
    for expr in BINDING.findall(wxml):
        expr = re.sub(r"'[^']*'|\"[^\"]*\"", " ", expr)
        out |= {t for t in ROOT_IDENT.findall(expr) if t not in KEYWORDS}
    return out


def main(root):
    problems = []

    app = json.loads((root / "app.json").read_text(encoding="utf-8"))
    for page in app["pages"]:
        for ext in (".ts", ".wxml", ".json"):
            if not (root / (page + ext)).exists():
                problems.append("app.json 声明的 " + page + ext + " 不存在")
    for tab in app.get("tabBar", {}).get("list", []):
        if tab["pagePath"] not in app["pages"]:
            problems.append("tabBar 页面未在 pages 中：" + tab["pagePath"])

    if not (root / "sitemap.json").exists():
        problems.append("缺少 sitemap.json（微信开发者工具会提示）")

    for wxml in sorted(root.glob("pages/**/*.wxml")):
        text = wxml.read_text(encoding="utf-8")
        rel = str(wxml.relative_to(root))

        ts = wxml.with_suffix(".ts")
        if ts.exists():
            source = ts.read_text(encoding="utf-8")
            for handler in sorted(set(HANDLER.findall(text))):
                if handler not in source:
                    problems.append(rel + ": 绑定的处理函数 " + handler + " 在 .ts 中不存在")
            missing = sorted(roots(text) - data_keys(source))
            if missing:
                problems.append(rel + ": 绑定引用了 data 中不存在的键 " + str(missing))

        for word in BANNED:
            if word in text:
                problems.append(rel + ": 患者端出现禁用词 " + word + " (D20)")

    for json_file in sorted(root.glob("pages/**/*.json")):
        payload = json.loads(json_file.read_text(encoding="utf-8"))
        title = payload.get("navigationBarTitleText", "")
        for word in BANNED:
            if word in title:
                rel = str(json_file.relative_to(root))
                problems.append(rel + ": 导航标题含禁用词 " + word + " (D20)")

    for item in problems:
        print("FAIL " + item)
    print("")
    print(str(len(problems)) + " problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else "."
    sys.exit(main(pathlib.Path(target)))
