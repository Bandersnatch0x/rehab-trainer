---
id: "0002"
type: feature
status: closed
assignee: coordinator
part-of: D3
blocked-by: []
created: 2026-11-05
---

# 管理端页面接入真实 API

`apps/admin-web/src/api/operations.ts` 已封装契约 15 个操作，但页面层只有 `LoginPage` 真正调用后端，其余页面用 `MOCK_*` 常量与 `setState` 模拟。E2E 实测 5 passed / 5 failed，失败集中在依赖真实数据的用例。本票把页面层接到真实后端。

## Log

### 2026-11-05 成票与现状

E2E 证据 `.agent/tmp/playwright.log`（e2e-tests-codex2）显示 5 条失败：403 跳转、H-Y 筛选、患者详情、处方 409、预警持久化。源码核实 `patients`/`alerts`/`sessions`/`exercises` 契约操作均已封装，缺的是页面调用。

### 2026-11-05 落码

- `PatientsPage`：`listPatients`，H-Y 走服务端 `stage` 查询参数；确认状态为本地筛选（契约无该查询参数）。「最近训练」列改为「建档时间」，因为 `Patient` 契约没有最近训练字段。
- `PatientDetailPage`：`getPatient` + `listSessions`。处方卡片不展示内容：契约只有 POST/PATCH，没有按患者读取处方的操作，凭本地状态假装版本会伪造乐观锁。
- `StageConfirmationPage`：`confirmStage`，提交 `stage`/`confirmedAt`/`note`，失败显示后端错误。
- `PrescriptionEditPage`：训练条目改为 `listExercises` 动态拉取；提交 `createPrescription`，展示服务端返回版本。
- `AlertsPage`：`listAlerts` 按 `level` 服务端筛选；`updateAlert` 登记随访，并以服务端返回结果覆盖本地行。
- `LoginPage`：账号有效但无管理端角色时改跳 `/forbidden`，不再停在登录页报错。

### 2026-11-05 验收与遗留

`npx tsc --noEmit` 退出 0，`vite build` 通过（359.07 kB）。未运行浏览器实测；E2E 套件在 `e2e-tests-codex2` 工作区，待合并后复跑。

遗留缺口（不掩盖）：

1. 契约缺 `GET /prescriptions`（按患者读当前处方），因此「调整处方 + If-Match 版本冲突」在页面上无法真实完成，E2E 该条仍会失败。
2. `Patient` 无最近训练字段，列表页该列改显示建档时间。
3. `FollowUpsPage`、`PermissionsPage` 无对应契约操作，保持演示数据并保留「演示数据」性质。
