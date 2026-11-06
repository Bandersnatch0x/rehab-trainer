"""生成客户汇报 PPT（基于需求规格与功能矩阵）。

用法（仓库根目录）：
    services/api/.venv/Scripts/python.exe scripts/make_client_deck.py

前置：
- 已安装 python-pptx；
- 原型截图位于 .agent/tmp/deck/（可用 .agent/tmp/deck-shots.cjs 生成）。
缺截图时自动跳过图片，不影响文字页。
"""

from pathlib import Path

from PIL import Image
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.util import Inches, Pt

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / ".agent" / "tmp" / "deck"
OUT = ROOT / "docs" / "delivery" / "client-briefing-2026-11-06.pptx"

NAVY = RGBColor(0x0B, 0x1B, 0x33)
NAVY_2 = RGBColor(0x13, 0x2E, 0x4F)
TEAL = RGBColor(0x0E, 0x74, 0x90)
TEAL_LIGHT = RGBColor(0xE0, 0xF2, 0xFE)
SLATE = RGBColor(0x47, 0x55, 0x69)
MUTED = RGBColor(0x64, 0x74, 0x8B)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
BG = RGBColor(0xF8, 0xFA, 0xFC)
GREEN = RGBColor(0x15, 0x80, 0x3D)
AMBER = RGBColor(0xB4, 0x53, 0x09)
RED = RGBColor(0xB9, 0x1C, 0x1C)
BORDER = RGBColor(0xE2, 0xE8, 0xF0)

FONT = "Microsoft YaHei"


def set_font(run, size, bold=False, color=SLATE, name=FONT):
    run.font.name = name
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = color
    rpr = run._r.get_or_add_rPr()
    ea = rpr.find("{http://schemas.openxmlformats.org/drawingml/2006/main}ea")
    if ea is None:
        from lxml import etree

        ea = etree.SubElement(
            rpr, "{http://schemas.openxmlformats.org/drawingml/2006/main}ea"
        )
    ea.set("typeface", name)


def textbox(slide, left, top, width, height, text, size=16, bold=False,
            color=SLATE, align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP):
    box = slide.shapes.add_textbox(left, top, width, height)
    frame = box.text_frame
    frame.word_wrap = True
    frame.vertical_anchor = anchor
    frame.margin_left = frame.margin_right = 0
    frame.margin_top = frame.margin_bottom = 0
    paragraph = frame.paragraphs[0]
    paragraph.alignment = align
    run = paragraph.add_run()
    run.text = text
    set_font(run, size, bold, color)
    return box


def bullets(slide, left, top, width, height, items, size=15, color=SLATE,
            spacing=10):
    box = slide.shapes.add_textbox(left, top, width, height)
    frame = box.text_frame
    frame.word_wrap = True
    frame.margin_left = frame.margin_right = 0
    for index, item in enumerate(items):
        paragraph = frame.paragraphs[0] if index == 0 else frame.add_paragraph()
        paragraph.space_after = Pt(spacing)
        if isinstance(item, tuple):
            head, body = item
            run = paragraph.add_run()
            run.text = "● "
            set_font(run, size - 2, True, TEAL)
            run = paragraph.add_run()
            run.text = head
            set_font(run, size, True, NAVY)
            if body:
                run = paragraph.add_run()
                run.text = " " + body
                set_font(run, size, False, SLATE)
        else:
            run = paragraph.add_run()
            run.text = "● "
            set_font(run, size - 2, True, TEAL)
            run = paragraph.add_run()
            run.text = item
            set_font(run, size, False, color)
    return box


def rect(slide, left, top, width, height, fill, line=None, radius=False):
    shape = slide.shapes.add_shape(
        MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE,
        left, top, width, height,
    )
    shape.fill.solid()
    shape.fill.fore_color.rgb = fill
    if line is None:
        shape.line.fill.background()
    else:
        shape.line.color.rgb = line
        shape.line.width = Pt(0.75)
    if radius:
        shape.adjustments[0] = 0.06
    return shape


def card(slide, left, top, width, height, title, body, accent=TEAL, body_size=13):
    rect(slide, left, top, width, height, WHITE, BORDER, radius=True)
    rect(slide, left, top, Inches(0.06), height, accent)
    textbox(slide, left + Inches(0.22), top + Inches(0.14),
            width - Inches(0.34), Inches(0.4), title, 15, True, NAVY)
    textbox(slide, left + Inches(0.22), top + Inches(0.62),
            width - Inches(0.34), height - Inches(0.72), body, body_size,
            False, SLATE)


def slide_header(slide, kicker, title, number, total):
    rect(slide, 0, 0, Inches(13.333), Inches(0.12), TEAL)
    textbox(slide, Inches(0.65), Inches(0.35), Inches(10), Inches(0.3),
            kicker, 11, True, TEAL)
    textbox(slide, Inches(0.65), Inches(0.65), Inches(11.5), Inches(0.55),
            title, 28, True, NAVY)
    rect(slide, Inches(0.65), Inches(1.28), Inches(0.7), Inches(0.04), TEAL)
    textbox(slide, Inches(11.6), Inches(0.4), Inches(1.1), Inches(0.3),
            f"{number:02d} / {total:02d}", 10, False, MUTED, PP_ALIGN.RIGHT)


def footer(slide):
    textbox(slide, Inches(0.65), Inches(7.08), Inches(8), Inches(0.25),
            "rehab-trainer · 帕金森病患者肌肉保护训练系统 · 演示交付版",
            9, False, MUTED)
    textbox(slide, Inches(11.4), Inches(7.08), Inches(1.3), Inches(0.25),
            "2026-11-06", 9, False, MUTED, PP_ALIGN.RIGHT)


def table(slide, rows, left, top, width, col_widths, header=True, size=11,
          row_height=0.34):
    shape = slide.shapes.add_table(
        len(rows), len(rows[0]), left, top, width, Inches(row_height * len(rows))
    )
    table_obj = shape.table
    for index, ratio in enumerate(col_widths):
        table_obj.columns[index].width = int(width * ratio)
    for row_index, row in enumerate(rows):
        table_obj.rows[row_index].height = Inches(row_height)
        for col_index, value in enumerate(row):
            cell = table_obj.cell(row_index, col_index)
            cell.margin_left = Inches(0.08)
            cell.margin_right = Inches(0.06)
            cell.margin_top = Inches(0.02)
            cell.margin_bottom = Inches(0.02)
            cell.vertical_anchor = MSO_ANCHOR.MIDDLE
            cell.fill.solid()
            if header and row_index == 0:
                cell.fill.fore_color.rgb = NAVY
                color, bold = WHITE, True
            else:
                cell.fill.fore_color.rgb = WHITE if row_index % 2 else BG
                color, bold = SLATE, False
            frame = cell.text_frame
            frame.word_wrap = True
            paragraph = frame.paragraphs[0]
            paragraph.alignment = PP_ALIGN.LEFT
            run = paragraph.add_run()
            run.text = str(value)
            set_font(run, size, bold, color)
    return table_obj


def picture_fit(slide, filename, left, top, max_width, max_height, caption=""):
    path = ASSETS / filename
    if not path.exists():
        rect(slide, left, top, max_width, max_height, TEAL_LIGHT, BORDER, True)
        textbox(slide, left + Inches(0.3), top + max_height / 2,
                max_width - Inches(0.6), Inches(0.4),
                "原型截图缺失", 14, False, MUTED, PP_ALIGN.CENTER)
        return
    with Image.open(path) as image:
        ratio = image.width / image.height
    width = max_width
    height = int(width / ratio)
    if height > max_height:
        height = max_height
        width = int(height * ratio)
    x = left + int((max_width - width) / 2)
    y = top + int((max_height - height) / 2)
    rect(slide, x - Inches(0.05), y - Inches(0.05),
         width + Inches(0.1), height + Inches(0.1), WHITE, BORDER, True)
    slide.shapes.add_picture(str(path), x, y, width=width, height=height)
    if caption:
        textbox(slide, left, top + max_height + Inches(0.08), max_width,
                Inches(0.3), caption, 10, False, MUTED, PP_ALIGN.CENTER)


def main():
    prs = Presentation()
    prs.slide_width = Inches(13.333)
    prs.slide_height = Inches(7.5)
    total = 16

    # 1. 封面
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    rect(slide, 0, 0, prs.slide_width, prs.slide_height, NAVY)
    rect(slide, Inches(0.75), Inches(1.55), Inches(0.08), Inches(2.5), TEAL)
    textbox(slide, Inches(1.05), Inches(1.5), Inches(9.5), Inches(0.4),
            "演示交付版 · 客户汇报", 15, True, RGBColor(0x67, 0xE8, 0xF9))
    textbox(slide, Inches(1.05), Inches(2.05), Inches(10.5), Inches(1.5),
            "帕金森病患者肌肉保护训练系统", 40, True, WHITE)
    textbox(slide, Inches(1.05), Inches(3.35), Inches(10), Inches(0.6),
            "患者训练 · 照护协助 · 机构管理 · 科研数据底座", 18, False,
            RGBColor(0xCB, 0xD5, 0xE1))
    textbox(slide, Inches(1.05), Inches(4.6), Inches(10), Inches(0.4),
            "Android App · 微信小程序 · Web 管理端 · FastAPI 后端", 14,
            False, RGBColor(0x94, 0xA3, 0xB8))
    textbox(slide, Inches(1.05), Inches(6.45), Inches(6), Inches(0.3),
            "2026-11-06 · 演示基线", 12, False, RGBColor(0x94, 0xA3, 0xB8))
    textbox(slide, Inches(9.8), Inches(6.4), Inches(2.9), Inches(0.4),
            "内部编号：D30 / 票 0015–0016", 10, False,
            RGBColor(0x64, 0x74, 0x8B), PP_ALIGN.RIGHT)

    # 2. 背景与目标
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    rect(slide, 0, 0, prs.slide_width, prs.slide_height, BG)
    slide_header(slide, "01 · 项目背景", "为什么需要这套系统", 2, total)
    bullets(slide, Inches(0.65), Inches(1.65), Inches(5.6), Inches(4.6), [
        ("训练不连续", "帕金森患者需要长期、规律、训练量可控的居家训练。"),
        ("动作不安全", "动作不适宜、训练过量、头晕或跌倒时缺少及时提示。"),
        ("机构看不见", "居家训练结果难以及时进入机构随访与科研数据链路。"),
    ], size=15)
    goals = [
        ("安全训练", "实时计数、节拍/语音提示、脚部门禁、紧急停止。"),
        ("照护闭环", "照护者代操作，机构端处置预警并登记随访。"),
        ("机构可控", "治疗师确认分级、制定与调整训练方案。"),
        ("数据可用", "科研聚合画像，小样本抑制，不返回行级数据。"),
    ]
    for index, (title, body) in enumerate(goals):
        top = Inches(1.65 + index * 1.2)
        card(slide, Inches(6.7), top, Inches(5.95), Inches(1.02),
             title, body, accent=TEAL if index % 2 == 0 else GREEN, body_size=12)
    footer(slide)

    # 3. 用户与角色
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    rect(slide, 0, 0, prs.slide_width, prs.slide_height, BG)
    slide_header(slide, "02 · 用户", "四类角色，职责与边界清晰", 3, total)
    table(slide, [
        ["角色", "主要使用端", "能做什么", "不能做什么"],
        ["患者", "Android / 小程序", "执行训练、记录身体感受、查看训练记录与提醒", "查看分级/症状标签；自行修改训练方案"],
        ["照护者", "微信小程序", "绑定患者、代操作、记录身体感受、查看提醒", "确认分级、制定训练方案、处置预警"],
        ["康复治疗师", "Web 管理端", "确认分级、制定/调整训练方案、处置预警、查看科研与审计", "跨机构访问；替代医生下诊断"],
        ["照护管理员", "Web 管理端", "新建患者、处置预警、随访台账、审计、科研、视频管理", "确认分级；制定或调整训练方案"],
    ], Inches(0.65), Inches(1.65), Inches(12.0),
        [0.14, 0.18, 0.42, 0.26], row_height=0.9)
    textbox(slide, Inches(0.65), Inches(6.2), Inches(12), Inches(0.5),
            "机构隔离贯穿全部角色：跨机构访问返回 403 并写审计；患者端只显示正向训练统计。",
            12, False, MUTED)
    footer(slide)

    # 4. 范围与边界
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    rect(slide, 0, 0, prs.slide_width, prs.slide_height, BG)
    slide_header(slide, "03 · 范围", "本期交付什么，不交付什么", 4, total)
    rect(slide, Inches(0.65), Inches(1.65), Inches(5.95), Inches(4.85), WHITE,
         BORDER, True)
    textbox(slide, Inches(0.95), Inches(1.85), Inches(5.3), Inches(0.4),
            "本期已交付", 18, True, GREEN)
    bullets(slide, Inches(0.95), Inches(2.4), Inches(5.3), Inches(3.9), [
        "三端载体：Android、微信小程序、Web 管理端",
        "患者建档、分级确认、训练方案（多条目/训练量/乐观锁）",
        "训练执行、实时计数与规则法达标提示",
        "身体感受与评估、预警处置、随访台账",
        "审计日志与 CSV、科研聚合、训练视频管理",
        "机构隔离、TOTP、登录限流、部署自检",
    ], size=13, spacing=6)
    rect(slide, Inches(6.85), Inches(1.65), Inches(5.8), Inches(4.85), WHITE,
         BORDER, True)
    textbox(slide, Inches(7.15), Inches(1.85), Inches(5.2), Inches(0.4),
            "本期不做 / 待后续", 18, True, AMBER)
    bullets(slide, Inches(7.15), Inches(2.4), Inches(5.2), Inches(3.9), [
        "云端异步 CV（T5）与小程序视频上传",
        "iOS App、在线问诊、支付、社交",
        "冻结步态自动检测、诊断结论、风险分级",
        "Android 登录/绑定与训练记录上传",
        "跨端真实数据闭环、生产环境部署与 CI",
    ], size=13, spacing=10)
    footer(slide)

    # 5. 系统架构
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    rect(slide, 0, 0, prs.slide_width, prs.slide_height, BG)
    slide_header(slide, "04 · 架构", "三端 + 统一契约 + 机构隔离", 5, total)
    boxes = [
        ("Android App", "患者端\nCameraX + MediaPipe\n实时计数与达标提示", Inches(0.9), Inches(2.0)),
        ("微信小程序", "照护者/患者\n绑定、记录、评估、提醒\n当前不录制上传", Inches(0.9), Inches(4.05)),
        ("Web 管理端", "治疗师/照护管理员\n建档、分级、方案、预警、\n随访、审计、科研、视频", Inches(9.3), Inches(2.0)),
        ("FastAPI /v1", "21 路径 · 25 操作\nJWT + TOTP + 限流\n机构隔离与审计", Inches(4.4), Inches(2.4)),
        ("数据库", "PostgreSQL 17 / SQLite\n18 张表 · 迁移 head 0005\nSQLite 开发/自检", Inches(4.4), Inches(4.45)),
    ]
    positions = {}
    for title, body, left, top in boxes:
        width = Inches(3.1)
        height = Inches(1.55) if title != "FastAPI /v1" else Inches(1.3)
        rect(slide, left, top, width, height, WHITE, TEAL if "FastAPI" in title else BORDER, True)
        textbox(slide, left + Inches(0.18), top + Inches(0.12), width - Inches(0.36),
                Inches(0.4), title, 13, True, NAVY)
        textbox(slide, left + Inches(0.18), top + Inches(0.52), width - Inches(0.36),
                height - Inches(0.6), body, 10.5, False, SLATE)
        positions[title] = (left, top, width, height)
    textbox(slide, Inches(4.4), Inches(6.3), Inches(7), Inches(0.5),
            "契约为唯一真源：管理端与小程序类型由 openapi-typescript 生成。",
            11, False, MUTED)
    footer(slide)

    # 6. 训练执行
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    rect(slide, 0, 0, prs.slide_width, prs.slide_height, BG)
    slide_header(slide, "05 · 核心流程", "训练执行：双画面、计数与安全", 6, total)
    picture_fit(slide, "android-training.png", Inches(0.65), Inches(1.6),
                Inches(4.2), Inches(5.0))
    bullets(slide, Inches(5.2), Inches(1.7), Inches(7.2), Inches(4.9), [
        ("双画面 / 纯实景", "手机竖屏上下、平板横屏左右；可切换只显示实景 + 语音提示。"),
        ("按条目计数", "原地踏步、抬举、靠墙微蹲分别计数；姿势矫正待接入。"),
        ("规则法达标提示", "幅度不足、躯干前倾、膝盖内扣、左右不对称逐次提示。"),
        ("采集门禁", "脚部可见度低于 0.5 时冻结计数并提示调整站位，恢复后继续。"),
        ("安全优先", "紧急停止 ≥80dp 常驻；暂停/旋转保持状态；停止先给可见反馈。"),
    ], size=13, spacing=9)
    textbox(slide, Inches(5.2), Inches(6.15), Inches(7), Inches(0.4),
            "演示版训练结束不写入记录、不上传机构端；规则阈值标注待专业签核。",
            11.5, False, AMBER)
    footer(slide)

    # 7. 训练方案与分级
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    rect(slide, 0, 0, prs.slide_width, prs.slide_height, BG)
    slide_header(slide, "06 · 核心流程", "从建档到训练方案：分级先行", 7, total)
    picture_fit(slide, "admin-training-plan.png", Inches(2.7), Inches(1.6),
                Inches(9.9), Inches(4.9))
    bullets(slide, Inches(0.65), Inches(1.7), Inches(1.9), Inches(4.8), [
        ("建档", ""),
        ("分级确认", ""),
        ("多条目方案", ""),
        ("乐观锁", ""),
    ], size=12, spacing=18)
    textbox(slide, Inches(0.65), Inches(5.1), Inches(2.0), Inches(1.5),
            "未确认分级时，高风险条目与训练准入保持关闭。", 11, False, AMBER)
    footer(slide)

    # 8. 预警 / 随访 / 审计
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    rect(slide, 0, 0, prs.slide_width, prs.slide_height, BG)
    slide_header(slide, "07 · 核心流程", "预警处置、随访台账与审计留痕", 8, total)
    picture_fit(slide, "admin-alerts.png", Inches(0.65), Inches(1.6),
                Inches(6.0), Inches(4.2))
    picture_fit(slide, "admin-audit.png", Inches(6.85), Inches(1.6),
                Inches(5.85), Inches(4.2))
    bullets(slide, Inches(0.65), Inches(5.95), Inches(12.0), Inches(0.9), [
        ("预警：", "确认 → 登记随访 → 关闭；关闭为终态；处理人与处置说明持久化。"),
        ("审计：", "按动作/资源/操作人/日期服务端筛选；CSV 导出并中和公式注入。"),
    ], size=11.5, spacing=3)
    footer(slide)

    # 9. 功能矩阵总览
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    rect(slide, 0, 0, prs.slide_width, prs.slide_height, BG)
    slide_header(slide, "08 · 功能矩阵", "24 项功能，按端与状态逐项核对", 9, total)
    stats = [
        ("24", "功能行", NAVY), ("7", "已实现", GREEN),
        ("15", "部分", AMBER), ("1", "演示数据", TEAL), ("1", "未实现", RED),
    ]
    for index, (value, label, color) in enumerate(stats):
        left = Inches(0.65 + index * 2.45)
        rect(slide, left, Inches(1.55), Inches(2.2), Inches(1.05), WHITE, BORDER, True)
        textbox(slide, left, Inches(1.63), Inches(2.2), Inches(0.5),
                value, 26, True, color, PP_ALIGN.CENTER)
        textbox(slide, left, Inches(2.14), Inches(2.2), Inches(0.3),
                label, 11, False, MUTED, PP_ALIGN.CENTER)
    table(slide, [
        ["模块", "Android", "小程序", "Web", "状态", "关键说明"],
        ["训练执行", "部分", "未实现", "不适用", "部分", "计数与达标提示已覆盖抬举/踏步/微蹲"],
        ["训练方案", "不适用", "部分", "已实现", "部分", "多条目、训练量、乐观锁"],
        ["预警/随访", "演示数据", "部分", "已实现", "部分", "确认/随访/关闭；处理人可追溯"],
        ["审计/科研", "不适用", "不适用", "已实现", "已实现", "服务端筛选、CSV、小样本抑制"],
        ["视频管理", "部分", "未实现", "已实现", "部分", "上传/上架/水印/署名/按分级下发"],
        ["权限/隔离", "不适用", "已实现", "演示数据", "部分", "机构隔离真实；权限页为演示"],
    ], Inches(0.65), Inches(2.85), Inches(12.0),
        [0.15, 0.12, 0.12, 0.1, 0.1, 0.41], row_height=0.48)
    footer(slide)

    # 10. 三端能力对照
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    rect(slide, 0, 0, prs.slide_width, prs.slide_height, BG)
    slide_header(slide, "09 · 三端", "同一套业务，不同端承担不同职责", 10, total)
    table(slide, [
        ["能力", "Android App", "微信小程序", "Web 管理端"],
        ["训练执行", "双画面/纯实景、实时计数、达标提示", "仅展示选中条目，不录制上传", "不承担"],
        ["训练记录", "演示数据，不上传", "真实接口读取", "患者详情查看最近 100 条"],
        ["身体感受", "无独立入口", "提交 1–1000 字", "查看原文与评估记录"],
        ["预警", "演示提醒 + 呼叫治疗师", "只读查看", "确认/随访/关闭"],
        ["训练方案", "只读执行", "只读展示", "多条目制定与调整"],
        ["建档/分级", "不承担", "不承担", "建档；治疗师确认分级"],
        ["审计/科研/视频", "不承担", "不承担", "三项均已接入"],
    ], Inches(0.65), Inches(1.65), Inches(12.0),
        [0.16, 0.29, 0.27, 0.28], row_height=0.55)
    footer(slide)

    # 11. 安全与适老化
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    rect(slide, 0, 0, prs.slide_width, prs.slide_height, BG)
    slide_header(slide, "10 · 安全与体验", "安全底线与适老化不是可选项", 11, total)
    cards = [
        ("紧急停止", "所有布局常驻，高度 ≥80dp；停止后先给可见反馈再退出。", RED),
        ("采集门禁", "踝/脚跟/脚尖可见度 <0.5 冻结计数；恢复站位后自动继续。", TEAL),
        ("停止规则", "胸痛、晕厥、卒中样症状、跌倒伴伤按急症处理，不等待算法判断。", AMBER),
        ("适老化", "主按钮 56dp、正文 ≥18sp、高对比度、TTS/节拍、振动反馈。", GREEN),
    ]
    for index, (title, body, accent) in enumerate(cards):
        left = Inches(0.65 + (index % 2) * 6.2)
        top = Inches(1.7 + (index // 2) * 2.25)
        card(slide, left, top, Inches(5.95), Inches(1.9), title, body,
             accent=accent, body_size=13)
    textbox(slide, Inches(0.65), Inches(6.25), Inches(12), Inches(0.4),
            "合规边界：系统不输出诊断、风险分级或自动预警；专业阈值签核前两个开关保持关闭。",
            12, False, MUTED)
    footer(slide)

    # 12. 专业阈值预设
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    rect(slide, 0, 0, prs.slide_width, prs.slide_height, BG)
    slide_header(slide, "11 · 专业阈值", "文献预设已就位，签核前不启用", 12, total)
    table(slide, [
        ["项目", "文献预设", "证据强度", "当前状态"],
        ["SARC-F / SARC-CalF", "≥4 / ≥11", "高 / 中", "不参与分级"],
        ["Short FES-I", "≥9（中度及以上担忧）", "中", "不参与分级"],
        ["STEADI / TUG", "任一 yes / ≥11.5 s（PD）", "高 / 中", "不参与分级"],
        ["疼痛持续", "NRS ≥4 或 Δ≥2，7 天内 ≥4 天", "低", "自动预警关闭"],
        ["跌倒反复", "12 个月 ≥2 次，或 1 次伴伤", "高", "自动预警关闭"],
        ["RAS 节拍", "默认 100 步/分（90–110）", "中", "界面可设置"],
    ], Inches(0.65), Inches(1.65), Inches(12.0),
        [0.22, 0.38, 0.14, 0.26], row_height=0.56)
    textbox(slide, Inches(0.65), Inches(5.85), Inches(12), Inches(0.8),
            "版本 literature-2026.11 · 来源与逐条证据见 docs/clinical/literature-presets.md；"
            "AUTO_ALERTS_ENABLED 与 RISK_LEVELS_ENABLED 均为 False。",
            11.5, False, AMBER)
    footer(slide)

    # 13. 数据与隐私
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    rect(slide, 0, 0, prs.slide_width, prs.slide_height, BG)
    slide_header(slide, "12 · 数据与合规", "机构隔离、最小必要与可追溯", 13, total)
    cards = [
        ("机构隔离", "所有资源按 org_id 隔离；跨机构 403 并写审计，不提供跨机构切换。", TEAL),
        ("演示标注", "演示数据在页面明确标注，不当作真实患者或真实训练结果。", AMBER),
        ("科研抑制", "计数 1–4 抑制为「—」；比例与二次抑制；不返回行级数据或出生年份。", GREEN),
        ("审计留痕", "建档、分级、方案、预警、视频与科研访问均写审计；支持 CSV 导出。", NAVY_2),
        ("账号安全", "TOTP 校验与重放拒绝；失败计数落库，多副本共享。", RED),
        ("数据边界", "不输出步态绝对长度、m/s、摆动相百分比或 RAS 适用性结论。", MUTED),
    ]
    for index, (title, body, accent) in enumerate(cards):
        left = Inches(0.65 + (index % 3) * 4.13)
        top = Inches(1.7 + (index // 3) * 2.2)
        card(slide, left, top, Inches(3.9), Inches(1.85), title, body,
             accent=accent, body_size=12)
    footer(slide)

    # 14. 验证证据
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    rect(slide, 0, 0, prs.slide_width, prs.slide_height, BG)
    slide_header(slide, "13 · 验证", "可复跑的交付证据", 14, total)
    numbers = [
        ("120", "后端测试", "pytest 全通过"),
        ("90", "Android 单测", "13 个测试类"),
        ("23", "管理端 E2E", "Playwright 全通过"),
        ("17", "小程序行为测试", "另有静态检查"),
        ("25/25", "契约联调", "21 个路径"),
        ("18", "数据库表", "迁移 head 0005 · 部署自检通过"),
    ]
    for index, (value, label, note) in enumerate(numbers):
        left = Inches(0.65 + (index % 3) * 4.13)
        top = Inches(1.65 + (index // 3) * 2.05)
        rect(slide, left, top, Inches(3.9), Inches(1.55), WHITE, BORDER, True)
        textbox(slide, left + Inches(0.2), top + Inches(0.15), Inches(3.5),
                Inches(0.55), value, 28, True, TEAL)
        textbox(slide, left + Inches(0.2), top + Inches(0.72), Inches(3.5),
                Inches(0.3), label, 13, True, NAVY)
        textbox(slide, left + Inches(0.2), top + Inches(1.05), Inches(3.5),
                Inches(0.3), note, 10.5, False, MUTED)
    textbox(slide, Inches(0.65), Inches(5.95), Inches(12), Inches(0.6),
            "全量校验：python scripts/verify_all.py → ALL PASS（后端 → 部署自检 → 管理端构建/E2E → 小程序 → Android）。",
            12, True, GREEN)
    footer(slide)

    # 15. 已知限制
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    rect(slide, 0, 0, prs.slide_width, prs.slide_height, BG)
    slide_header(slide, "14 · 边界与路线", "哪些已验证，哪些还需补验", 15, total)
    columns = [
        ("需设备补验", [
            "Android 平板 sw≥600dp / TalkBack / 200% 字体 / 深色模式",
            "断网播放与离线缓存实测",
            "微信真机渲染、授权与网络流程",
        ], AMBER),
        ("需专业签核", [
            "量表分界、四类预警阈值、BPM 个体化",
            "每条目禁忌/危险信号、急症升级路径",
            "动作计数阈值的临床准确度（T6）",
        ], RED),
        ("后续版本", [
            "云端异步 CV（T5）、小程序视频上传",
            "Android 登录/绑定与记录上传",
            "跨端数据闭环、生产部署与 CI",
        ], TEAL),
    ]
    for index, (title, items, accent) in enumerate(columns):
        left = Inches(0.65 + index * 4.13)
        rect(slide, left, Inches(1.65), Inches(3.9), Inches(4.75), WHITE,
             BORDER, True)
        rect(slide, left, Inches(1.65), Inches(3.9), Inches(0.08), accent)
        textbox(slide, left + Inches(0.2), Inches(1.85), Inches(3.5),
                Inches(0.35), title, 16, True, NAVY)
        bullets(slide, left + Inches(0.2), Inches(2.35), Inches(3.5),
                Inches(3.8), items, size=11.5, spacing=10)
    footer(slide)

    # 16. 下一步
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    rect(slide, 0, 0, prs.slide_width, prs.slide_height, NAVY)
    textbox(slide, Inches(0.9), Inches(0.75), Inches(11), Inches(0.4),
            "15 · 下一步", 12, True, RGBColor(0x67, 0xE8, 0xF9))
    textbox(slide, Inches(0.9), Inches(1.15), Inches(11.5), Inches(0.7),
            "建议的推进顺序", 34, True, WHITE)
    steps = [
        ("1", "确认范围与文案", "客户确认本期范围、术语与原型，冻结演示基线。"),
        ("2", "完成专业签核", "逐条确认文献预设与急症路径；签核后开启自动预警/风险分级。"),
        ("3", "设备与准确度补验", "平板、读屏、大字体、深色模式、断网播放与 T6 实测。"),
        ("4", "试点与上线", "预生产部署、真实账号初始化、机构试点与验收。"),
    ]
    for index, (number, title, body) in enumerate(steps):
        top = Inches(2.15 + index * 1.15)
        rect(slide, Inches(0.95), top, Inches(0.55), Inches(0.55), TEAL, None, True)
        textbox(slide, Inches(0.95), top + Inches(0.05), Inches(0.55), Inches(0.45),
                number, 18, True, WHITE, PP_ALIGN.CENTER)
        textbox(slide, Inches(1.75), top - Inches(0.05), Inches(3.2), Inches(0.4),
                title, 17, True, WHITE)
        textbox(slide, Inches(1.75), top + Inches(0.38), Inches(9.5), Inches(0.45),
                body, 12, False, RGBColor(0xCB, 0xD5, 0xE1))
    textbox(slide, Inches(0.95), Inches(6.65), Inches(10), Inches(0.35),
            "谢谢 · 期待您的确认与反馈", 16, True, RGBColor(0x67, 0xE8, 0xF9))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    prs.save(OUT)
    print(f"written {OUT} ({len(prs.slides)} slides)")


if __name__ == "__main__":
    main()