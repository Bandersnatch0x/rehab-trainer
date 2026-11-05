---
id: "0008"
type: task
status: open
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

### 2026-11-05 落码与验收

改动：新增 `pages/bind/index`（绑定码 + 演示模式入口）；五个数据页错误态加「去绑定」；训练页改为数据页，按 `GET /exercises` 的 `riskLevel` 判定准入，中/高风险与缺失分级一律禁用并显示中性原因；演示解锁开关默认关闭。演示标记存 `rehab.auth.demo.v1`。

门禁：`node --test apps/miniprogram/tests/patient-data.cjs` 17/17 通过；`python apps/miniprogram/tests/static_check.py apps/miniprogram` 0 problem(s)；`npx tsc --noEmit` 无错误；`git diff --check` 通过。微信开发者工具自动化 13/13 与 6/6 通过（未登录「去绑定」入口可点并进入绑定页、绑定页有输入框与提交按钮、训练页 2 个中/高条目为禁用态、演示开关默认关闭）。

未决：`127.0.0.1:8000` 上当前服务不是本仓 FastAPI（`/v1/me` 返回 HTML），真实绑定码与演示账号登录未对真实后端跑通，仅在请求桩中验证；多患者选择（F13）与训练筛选 chips（F10）超出本票范围。证据与复跑方式见 `.agent/tmp/MP-FIX.md`。
