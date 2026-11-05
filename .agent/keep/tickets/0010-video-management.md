---
id: "0010"
type: task
status: closed
assignee: claude
part-of: null
blocked-by: []
created: 2026-11-05
---

# 训练视频管理与按分期下发

目标：管理端上传训练视频、设置适用 H-Y 分期与版权标注（水印文字、来源署名）；患者端只拿到「已上架且分期匹配」的视频地址，水印与署名由客户端叠加显示。

范围：

- `services/api/`：`media_asset` 模型与迁移 `0003`；`POST /v1/media-assets/upload`（multipart）、`GET /v1/media-assets`、`PATCH /v1/media-assets/{assetId}`；`GET /exercises` 与 `GET /exercises/{code}` 的服务端视频挑选（按患者分期，回传 `videoUri` / `videoCreditLine` / `videoWatermarkText`）。
- `packages/api-contract/openapi.yaml` 与两份生成的 `d.ts`。
- `apps/admin-web/`：训练视频页（上传进度、状态筛选、上下架二次确认、标注编辑、叠加预览）。
- `apps/android/`：示范视频优先播放服务端地址，失败或缺失时回退内置演示视频；叠加水印与署名。
- `.env.example`、`docker-compose.yml`、`docs/deployment/README.md`：媒体目录、对外前缀、上限与静态托管说明。

## Log

### 2026-11-05 实现与验收

- 上传安全（服务端强制）：只按文件头魔数接受 MP4（`ftyp` + 品牌白名单），不信任扩展名与 `Content-Type`；文件名由服务端按 UUID 生成；落盘前把解析后的绝对路径再次限定在媒体根内（拒绝 `..`、绝对路径、盘符）；流式读取并按上限（默认 200 MB，可配置）中止并清理临时文件；计算 SHA-256 入库。
- 下发策略在服务端完成：只取 `status = published` 且 `applicable_stages` 含患者 H-Y 分期的资产，按 `published_at` 取最新；分期本身不进入患者响应（D20）。空分期视为未裁决，不匹配任何患者，且不允许上架。
- 审计：`media.uploaded` / `media.updated` / `media.published` / `media.retired`，跨机构访问沿用 `authz.cross_org_access`。
- 管理端上传走后端 XHR 以获得真实进度；页面样式独立在 `media.css`，不改公共样式。
- 被否选项：把水印烧录进视频（需转码流水线，改文字要重编码，本轮不做）；把上传拆成「先传文件再登记元数据」两步（多一次失败面与孤儿文件，合并为单个 multipart 请求）。
- 已知限制：Android 端 UI 仍使用本地演示元数据，尚未装配登录/绑定流程，因此真机只能验证回退路径；远端播放路径由 `InstructionalVideoSource` 纯函数单测覆盖，接上会话后可直接生效。小程序训练会话页仍为视频占位（另票）。

验收证据：后端 `pytest` 97 项通过（含新增 `tests/test_media.py` 24 项）；管理端 `tsc --noEmit` + `vite build` 通过，Playwright 17 项通过（含 2 项视频管理用例）；Android `testDebugUnitTest` 通过（含新增 6 项）；`alembic upgrade head` 与 `alembic check` 通过；`git diff --check` 通过。
