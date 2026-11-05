---
id: "0014"
type: task
status: closed
assignee: null
part-of: null
blocked-by: []
created: 2026-11-05
---

# 三份交付文档

目标：按 `.agent/tmp/orca-specs/15-delivery-docs.md` 与 `.agent/tmp/DECISIONS-NEXT.md` 第 1 条，产出《用户操作手册》《部署与运维手册》《验收与合规说明》，分别面向使用者、机构运维与验收方。

范围：仅 `docs/delivery/`、`README.md` 目录表、本票与 `.agent/tmp/`。不改代码、契约、决策记录与 `docs/deployment/README.md`。

验收：

- 三个文件存在且结构完整。
- `README.md` 目录表新增 `docs/delivery/` 一行。
- 文档中的命令均可在仓库根目录执行；测试数字与实际运行一致。
- 未验证项如实列出，临床阈值标为待签核。
- `git diff --check` 通过。

## Log

### 2026-11-05 建票

来源：`.agent/tmp/orca-specs/15-delivery-docs.md`（主仓 `.agent/tmp/orca-specs/`）。三份文档的组成与读者已由 D7/D24 定案，不再讨论分份方式。

### 2026-11-05 交付

产出：

- `docs/delivery/user-manual.md`：按患者/照护者、治疗师、机构管理员分节；患者端文案避开 H-Y 分级、症状名与跌倒风险等级（D20）；对小程序训练执行页、Android 记录上传等未实现能力明确标注。
- `docs/delivery/operations-manual.md`：环境变量、部署顺序、迁移、备份恢复、健康检查、日志、常见故障；部署细节引用 `docs/deployment/README.md`，不复制命令。
- `docs/delivery/acceptance-report.md`：交付范围、已验证项、未验证项、已知限制、临床待签核清单；临床阈值全部标为待签核。
- `README.md` 目录表新增 `docs/delivery/`。

实际计数（本轮在本仓实跑）：

- 后端 pytest：73 项通过。
- 管理端 E2E：15 项通过。
- 小程序行为测试：17 项通过。
- Android 单元测试：36 项通过；`assembleDebug` 成功。
- 契约：17 个路径、20 个操作。
- API 联调：18/20 操作通过；脚本因未覆盖两个科研端点而在覆盖断言处退出 1。
- 部署自检：16 张表；迁移 head `0002`。
- `git diff --check`：通过。

未验证项已写入验收报告：微信真机、Android 真平板/TalkBack/200% 字体/深色模式、云上生产部署、CI 流水线、迁移 `0002` 的容器验证、端上姿态识别准确度。

边界说明：`README.md` 的「当前状态」表仍有旧计数（41 项后端测试等），本票写入边界只允许改目录表，故未改动；三份交付文档一律使用本轮实测计数（73/15/17/36，契约 17 路径 20 操作）。

决议：三份文档按已定读者边界落盘，不合并、不增补商务承诺；后续如需更新数字，以 `python scripts/verify_all.py --skip-e2e --skip-android` 与分项命令的实际输出为准。
