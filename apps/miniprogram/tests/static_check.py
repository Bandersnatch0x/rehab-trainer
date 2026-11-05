#!/usr/bin/env python3
"""小程序静态一致性检查：不需要微信开发者工具与登录。

覆盖 app.json 与页面文件一致性、WXML 绑定根标识符与 data 键对齐、
事件处理函数存在性、D20 患者端禁用词扫描，以及 app.wxss 的 WCAG AA 对比度校验。
可独立复跑。
"""

import json
import pathlib
import re
import sys


BANNED = ["H-Y", "hyStage", "跌倒风险", "帕金森", "症状"]

# app.wxss 未使用 CSS 自定义属性，无法可靠自动配对前景/背景；
# 显式维护对照清单，注明出处选择器。下方同时校验色值仍出现在 app.wxss，
# 避免样式改色后清单静默失真。
# 最小对比度：正文 4.5:1，大字号（≥24px 或 ≥18.66px 加粗）3:1。
CONTRAST_PAIRS = [
    # (说明, 前景色, 背景色, 最小对比度, 出处选择器)
    ("页面正文", "#0f172a", "#f8fafc", 4.5, "page"),
    ("页面副标题", "#334155", "#f8fafc", 4.5, ".page-subtitle"),
    ("提示文字", "#475569", "#f8fafc", 4.5, ".hint"),
    ("卡片标题", "#0f172a", "#ffffff", 4.5, ".card-title"),
    ("卡片次级文字", "#334155", "#ffffff", 4.5, ".muted"),
    ("主按钮文字", "#ffffff", "#1a4fa0", 3.0, ".btn--primary / .btn--elderly / .btn--tall"),
    ("危险按钮文字", "#ffffff", "#b91c1c", 3.0, ".btn--danger"),
    ("次级按钮文字", "#1a4fa0", "#ffffff", 4.5, ".btn--secondary"),
    ("通知正文", "#0f3268", "#eaf1fb", 4.5, ".notice"),
    ("警告通知", "#78350f", "#fef3c7", 4.5, ".notice--warning"),
    ("危险通知", "#7f1d1d", "#fee2e2", 4.5, ".notice--danger"),
    ("信息徽标", "#0f3268", "#eaf1fb", 4.5, ".badge--info"),
    ("成功徽标", "#1b5e20", "#e8f5e9", 4.5, ".badge--success"),
    ("警告徽标", "#78350f", "#fef3c7", 4.5, ".badge--warning"),
    ("危险徽标", "#7f1d1d", "#fee2e2", 4.5, ".badge--danger"),
    ("中性徽标", "#334155", "#e2e8f0", 4.5, ".badge--neutral"),
    ("信息行标签", "#334155", "#ffffff", 4.5, ".info-row__label"),
    ("信息行值", "#0f172a", "#ffffff", 4.5, ".info-row__value"),
    ("筛选标签", "#0f172a", "#ffffff", 4.5, ".chip"),
    ("选中筛选标签", "#ffffff", "#1a4fa0", 3.0, ".chip--active"),
]

HEX_COLOR = re.compile(r"#(?:[0-9a-f]{3}|[0-9a-f]{6})\b")


def _rgb(color):
    value = color.lstrip("#")
    if len(value) == 3:
        value = "".join(char * 2 for char in value)
    return tuple(int(value[index : index + 2], 16) for index in (0, 2, 4))


def _linear(channel):
    srgb = channel / 255
    if srgb <= 0.03928:
        return srgb / 12.92
    return ((srgb + 0.055) / 1.055) ** 2.4


def relative_luminance(color):
    red, green, blue = (_linear(channel) for channel in _rgb(color))
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue


def contrast_ratio(foreground, background):
    light, dark = sorted(
        (relative_luminance(foreground), relative_luminance(background)),
        reverse=True,
    )
    return (light + 0.05) / (dark + 0.05)
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

    style_file = root / "app.wxss"
    if not style_file.exists():
        problems.append("缺少 app.wxss，无法校验对比度")
    else:
        style = style_file.read_text(encoding="utf-8").lower()
        palette = set(HEX_COLOR.findall(style))
        for label, foreground, background, minimum, source in CONTRAST_PAIRS:
            for role, color in (("前景", foreground), ("背景", background)):
                if color not in palette:
                    problems.append(
                        "app.wxss: " + label + " 的" + role + "色 " + color
                        + " 不存在（" + source + "）"
                    )
            ratio = contrast_ratio(foreground, background)
            if ratio < minimum:
                problems.append(
                    label + " 对比度 " + format(ratio, ".2f") + ":1 < "
                    + str(minimum) + ":1（" + source + "）"
                )

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
