---
id: "0008"
type: task
status: closed
assignee: null
part-of: null
blocked-by: []
created: 2026-11-05
---

# 小程序绑定入口与训练准入修复

目标：修复评审报告 F1、F2 两项阻塞/高危缺陷。未登录时提供可到达的绑定入口与演示路径；训练条目按风险分级控制可操作性，且患者端不出现分级字样。

范围：仅 `apps/miniprogram/`、本票与 `.agent/tmp/`。不修改 `apps/miniprogram/app.wxss`（另一路 worker 正在修改视觉层）。不改契约、后端与决策记录。

验收：

- `node --test apps/miniprogram/tests/patient-data.cjs` 通过（含新增用例）。
- `python apps/miniprogram/tests/static_check.py apps/miniprogram` 输出 `0 problem(s)`。
- 微信开发者工具自动化：未登录首页出现「去绑定」入口并可进入绑定页；绑定页有输入框与提交按钮；训练页中/高风险条目按钮为禁用态。
- `git diff --check` 通过。

## Log

### 2026-11-05 建票

来源：`.agent/tmp/orca-specs/10-mp-functional-fixes.md` 与 `.agent/tmp/REVIEW-MINIPROGRAM.md` 的 F1、F2。视觉层（F3–F6、F12 横幅覆盖等）由另一路 worker 负责，本票不碰 `app.wxss`。

### 2026-11-05 协调者验收与关票

由协调者复核并合并（1a69d85 + 合并）。已用微信开发者工具自动化实测 6/6：未登录出现「去绑定」、绑定页可打开、训练页无风险分级字样、需治疗师确认。遗留：演示账号密码明文写在 utils/config.ts，生产构建须移除。
