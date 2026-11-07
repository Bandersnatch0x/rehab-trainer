"""切客户交付包：把指定 PPT、上一版包里的交互原型与更新后的说明打成一个 zip。

为什么要有这个脚本：交付包此前是手工打的，包里那份说明钉着「代码基线 <commit>」，
一旦手工维护就会和文档一起漂。这里把「取哪份 PPT、沿用哪份原型、写什么说明」固定成可复跑的动作。

用法（仓库根目录）：

    python scripts/make_delivery_package.py --date 2026-11-07 \
        --deck docs/delivery/client-briefing-2026-11-07.pptx

交互原型（02-交互原型.html）目前**没有仓库内源码**，只能从上一版包里沿用；
换原型时用 --prototype 指定新文件。
"""

import argparse
import re
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DELIVERY = ROOT / "docs" / "delivery"

README_TEMPLATE = """帕金森病患者肌肉保护训练系统 · 客户交付包
==========================================

版本：演示交付版 · {date}
代码基线：{commit}

包内文件
--------
01-客户汇报-帕金森康复训练系统.pptx
    {slides} 页客户汇报，基于《需求规格说明书》与《功能矩阵》。
    建议使用 Microsoft PowerPoint 或 WPS 打开；中文字体为微软雅黑。

02-交互原型.html
    单文件离线原型，双击用 Chrome / Edge 打开即可。
    顶部可切换 Android 手机、Android 平板、微信小程序、Web 管理端与功能矩阵。
    所有数据均为原型示例，不代表真实患者或真实训练结果。

说明
----
- 临床阈值（量表分界、预警规则、BPM 等）为文献预设，尚未完成专业签核；
  系统中自动预警与风险分级保持关闭。
- 演示版中标注“演示数据”的内容不得作为真实业务数据使用。
- 原型与 PPT 仅用于需求确认与演示，不构成医疗器械注册文件或临床指南。
"""


def latest_package(before: str) -> Path:
    candidates = sorted(DELIVERY.glob("客户交付包-*.zip"))
    earlier = [p for p in candidates if p.stem.split("-")[-1] < before]
    if not earlier:
        raise SystemExit(f"找不到 {before} 之前的交付包，无法沿用原型；请用 --prototype 指定")
    return earlier[-1]


def read_member(package: Path, suffix: str) -> bytes:
    with zipfile.ZipFile(package) as archive:
        for info in archive.infolist():
            if info.filename.endswith(suffix):
                return archive.read(info)
    raise SystemExit(f"{package.name} 里没有 {suffix}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--date", required=True, help="版本日期，如 2026-11-07")
    parser.add_argument("--deck", required=True, type=Path, help="PPT 路径")
    parser.add_argument("--prototype", type=Path, help="原型 HTML；缺省沿用上一版包里的")
    parser.add_argument("--commit", help="代码基线 commit；缺省取当前 HEAD 短哈希")
    args = parser.parse_args()

    deck = args.deck if args.deck.is_absolute() else ROOT / args.deck
    if not deck.exists():
        raise SystemExit(f"找不到 PPT：{deck}")

    prototype = args.prototype
    if prototype is None:
        previous = latest_package(args.date)
        prototype_bytes = read_member(previous, "02-交互原型.html")
        print(f"原型沿用自 {previous.name}")
    else:
        prototype_bytes = prototype.read_bytes()

    commit = args.commit or subprocess.run(
        ["git", "rev-parse", "--short", "HEAD"],
        cwd=ROOT, capture_output=True, text=True, check=True
    ).stdout.strip()

    from pptx import Presentation

    slides = len(Presentation(str(deck)).slides)
    folder = f"客户交付包-{args.date}"
    target = DELIVERY / f"{folder}.zip"

    readme = README_TEMPLATE.format(date=args.date, commit=commit, slides=slides)
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(f"{folder}/00-使用说明.txt", readme)
        archive.write(deck, f"{folder}/01-客户汇报-帕金森康复训练系统.pptx")
        archive.writestr(f"{folder}/02-交互原型.html", prototype_bytes)

    print(f"written {target.name}（{target.stat().st_size} 字节，代码基线 {commit}）")


if __name__ == "__main__":
    main()
