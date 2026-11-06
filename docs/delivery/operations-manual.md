# 部署与运维手册

**本文档面向谁**：机构 IT 与运维人员。

**不覆盖什么**：患者、照护者、治疗师与机构管理员的操作说明见 `user-manual.md`；交付范围、验证证据与专业签核见 `acceptance-report.md`；开发实现与接口定义不在本文范围。

## 1. 系统组成与部署边界

当前交付包含：

| 组件 | 说明 |
| --- | --- |
| API 服务 | Python 3.13 + FastAPI，挂载 `/v1` 前缀，提供患者、分级、评估、训练方案、训练条目、训练记录、预警、科研聚合接口 |
| 数据库 | PostgreSQL 17；开发与自检可用 SQLite |
| Android App | 端上姿态识别与训练执行；当前版本不上传训练记录 |
| 微信小程序 | 患者与照护者查看训练安排、记录、身体感受与提醒 |
| Web 后台 | 治疗师与照护管理员使用 |
| 云端 CV 服务 | 当前为占位，不参与部署 |

当前部署只提供 API 与数据库，不包含管理端静态站点托管、账号初始化、微信服务端集成、对象存储上传或通知发送。生产环境不要执行开发期 seed。

## 2. 环境要求

| 项 | 要求 | 说明 |
| --- | --- | --- |
| Python | 3.13 | API、迁移、部署自检与联调脚本 |
| Node.js | 22 及以上 | 管理端与小程序构建；本次验证使用 24.19.0 |
| pnpm | 10.7.1 | 仓库锁定版本 |
| JDK | 17 或 21 | Android 构建；CI 使用 17，本次本地验证使用 21 |
| Android SDK | 含 platform 36 与 build-tools 36 | Android 构建 |
| Docker Engine + Compose v2 | 本地容器部署 | 生产可改用托管容器平台 |
| PostgreSQL | 17 | 容器镜像或云托管实例 |

## 3. 配置项

配置统一使用 `REHAB_` 前缀。`.env.example` 是模板，不得把真实秘密写回仓库。

### 3.1 Compose 与 API 必需项

| 变量 | 必需 | 默认值 | 用途 |
| --- | --- | --- | --- |
| `POSTGRES_DB` | 否 | `rehab` | 容器数据库名 |
| `POSTGRES_USER` | 否 | `rehab` | 容器数据库账号 |
| `POSTGRES_PASSWORD` | 是 | 无 | 容器数据库密码；缺失时 Compose 拒绝启动 |
| `API_PORT` | 否 | `8000` | API 映射到宿主机的端口；只绑定 `127.0.0.1` |
| `REHAB_DATABASE_URL` | 是 | 无 | API 使用的数据库 URL；Compose 缺失时拒绝启动 |
| `REHAB_JWT_SECRET` | 是 | 无 | 访问令牌与刷新令牌签名密钥；缺失时 Compose 拒绝启动 |
| `REHAB_AUTO_CREATE_SCHEMA` | 是 | 无（Compose 固定 `false`） | 为 `false` 时由 Alembic 管理表结构；生产必须为 `false` |

数据库 URL 必须与数据库账号一致。PostgreSQL 使用 `postgresql+psycopg://USER:<password>@HOST:5432/DB`；不要把 libpq 的 `postgresql://` 前缀与 SQLAlchemy 驱动前缀混用。密码含 URL 特殊字符时必须编码。

### 3.2 API 可选配置

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `REHAB_JWT_ALGORITHM` | `HS256` | 令牌签名算法 |
| `REHAB_ACCESS_TOKEN_TTL_SECONDS` | `3600` | 访问令牌有效期 |
| `REHAB_REFRESH_TOKEN_TTL_SECONDS` | `604800` | 刷新令牌有效期（7 天） |
| `REHAB_LOGIN_MAX_FAILURES` | `5` | 登录失败计数上限 |
| `REHAB_LOGIN_WINDOW_SECONDS` | `300` | 登录失败计数窗口 |

### 3.3 预留但当前未消费的配置

以下变量可写入环境，但当前后端不读取、也不会因此自动接入：

- `REHAB_WECHAT_APP_ID`、`REHAB_WECHAT_APP_SECRET`
- `REHAB_COS_REGION`、`REHAB_COS_BUCKET`、`REHAB_COS_SECRET_ID`、`REHAB_COS_SECRET_KEY`
- `REHAB_CDN_BASE_URL`

不要因为配置了这些变量就认为微信登录、对象存储或 CDN 已接通。

## 4. 部署流程

### 4.1 本地容器部署

逐条命令、参数与预期输出以 `docs/deployment/README.md` 的「本地 Docker 启动」一节为准；本文只列执行顺序与判据，避免两处漂移。

1. 准备环境文件：从 `.env.example` 复制到 `.agent/tmp/deployment.env`，填写数据库密码、JWT 密钥与数据库 URL。该目录已被忽略。
2. 校验 Compose 配置。预期：命令无输出、退出码 0。
3. 构建并后台启动。预期：API 与数据库均为 healthy；API 只在 `127.0.0.1` 上提供访问；数据库没有宿主机端口。
4. 执行容器内健康检查。预期：退出码 0。
5. 打开 API 文档地址确认服务可用。预期：`/docs` 可访问。

停止服务时保留数据库卷。不要附加 `--volumes`，它会删除数据库数据。修改数据库密码环境变量不会更新已有卷内的账号密码，必须按数据库账号管理流程轮换并同步 API 的数据库 URL。

### 4.2 非容器本地运行

适用于开发、演示与排障。生产不要用 SQLite 承载真实数据。

1. 安装 API 依赖与迁移依赖。
2. 设置 `REHAB_DATABASE_URL`，例如 `sqlite:///.agent/tmp/local.db`，并设置 `REHAB_AUTO_CREATE_SCHEMA=false`。
3. 执行 `python -m alembic -c deploy/alembic.ini upgrade head`。预期：数据库升级到当前 head。
4. 从 `services/api` 目录启动 API：
   `cd services/api && python -m uvicorn app.main:app --host 127.0.0.1 --port 8000`。
5. 预期：`http://127.0.0.1:8000/docs` 可访问。

仅在演示或本地联调时，从 `services/api` 目录执行 `python -m app.seed` 写入演示数据。seed 会创建演示账号与演示患者，生产环境不得执行。

二次验证（TOTP，可选）：从仓库根目录执行

```powershell
services/api/.venv/Scripts/python.exe scripts/set-totp.py --username <账号>
```

脚本会写入密钥并打印认证器 URI；该账号下次登录必须提交 6 位验证码，同一时间步不可重放。关闭用 `--clear`。此操作直接改数据库，生产环境须先备份并遵守密钥交付流程。登录失败计数存储于 `login_failure` 表，多副本共享，重启不清零。

管理端本地开发从仓库根目录执行 `pnpm dev:admin-web`，Vite 开发服务器会把 `/v1` 代理到 `http://127.0.0.1:8000`。生产托管方式不在本次交付范围。

### 4.3 云上生产部署

云上部署需要单独的人工授权。本次只交付配置与步骤，没有创建任何云资源。逐条步骤、探针、账号权限、对象存储与 CDN 配置见 `docs/deployment/README.md` 的「腾讯云生产部署」一节。

执行时必须保留以下判据：

- 数据库只允许容器网络访问，不暴露公网。
- 迁移作业串行执行，成功后才能更新 API；多个副本不得同时迁移。
- 运行账号只持有业务表读写与 `alembic_version` 读取权限；迁移账号持有 DDL 权限。新迁移建出的表不会自动继承运行账号权限，必须配置默认权限或在每次迁移后重新授权。
- API 使用运行账号启动；镜像固定 digest；秘密来自秘密管理服务，不作为构建参数。
- 负载均衡器终止 HTTPS；`/openapi.json` 作为存活探针，容器内执行健康检查脚本作为就绪探针。
- 发布前备份数据库；初次发布先单副本验证登录、训练方案读取、训练记录写入与重启后数据保留。

## 5. 数据库迁移

当前 Alembic head 为 `0002`：

| 版本 | 内容 |
| --- | --- |
| `0001` | 初始结构，16 张业务表、索引、唯一约束、外键与检查约束 |
| `0002` | 患者表新增 `data_origin` 字段，用于区分真实数据与演示数据 |

迁移命令从仓库根目录执行。以下命令已在本地 SQLite 上实际跑通：

```powershell
$env:REHAB_DATABASE_URL = 'sqlite:///.agent/tmp/doc-migrate.db'
$env:REHAB_AUTO_CREATE_SCHEMA = 'false'
python -m alembic -c deploy/alembic.ini upgrade head
python -m alembic -c deploy/alembic.ini current
python -m alembic -c deploy/alembic.ini check
```

预期：

- `upgrade head` 成功，不报错。
- `current` 输出 `0002 (head)`。
- `check` 输出 `No new upgrade operations detected.`，说明模型与迁移一致。

生产注意事项：

- 迁移前先备份数据库。
- 已有 `create_all` 数据库不能直接执行初始迁移；先核对结构，再决定是否 `stamp 0001`。`stamp` 不创建表也不验证结构，不能用来跳过结构差异。
- 后续模型修改必须新增迁移版本，不改初始快照；生成后人工检查约束、数据转换与降级操作。
- 初始 `downgrade base` 会删除全部业务表，只适合空的验证数据库。生产故障优先前向修复或从备份恢复。

## 6. 备份与恢复

逐条命令与预期输出见 `docs/deployment/README.md` 的「备份与恢复」一节。执行要点：

1. 在数据库容器内用 `pg_dump -Fc` 生成自定义格式备份，再复制到受控加密存储。
2. 记录备份时间、镜像版本、迁移版本与校验和。
3. 恢复演练使用新数据库，不覆盖现有数据库。
4. 恢复后核对行数、约束、迁移版本与抽样业务读写，再停写切换 API 的数据库 URL。
5. 失败时保留原库与备份，不运行覆盖性清理命令。
6. 数据库备份不包含对象存储内容；如果后续启用对象存储，需要单独核对对象引用与版本控制。

生产应启用托管数据库的自动备份与时间点恢复，并按业务批准的 RPO/RTO 配置保留周期，定期恢复到隔离实例演练。

## 7. 健康检查与日志

### 7.1 健康检查

容器内健康检查脚本会：

1. 访问本机 `/openapi.json`，确认 HTTP 服务可响应。
2. 连接数据库并读取 `alembic_version`，确认版本表可读。

它**不能**证明所有业务查询正常，也不验证数据库版本等于当前 head。发布时仍必须执行迁移作业并做业务冒烟测试。

### 7.2 日志

- API 与迁移日志输出到标准输出与标准错误，由容器平台采集。
- 查看容器日志的命令见 `docs/deployment/README.md` 的「健康检查与日志」一节。
- 设置保留期限与告警；不要输出环境变量、完整数据库 URL、JWT 或患者请求体。
- Compose 的 `config --quiet` 只做校验；直接打印完整配置会展开秘密。
- 容器进入 unhealthy 状态本身不保证自动重启，需要平台探针策略与告警配合。

## 8. 常见故障

| 现象 | 可能原因 | 处理 |
| --- | --- | --- |
| API 启动即退出，日志含 `password authentication failed` | 数据库 URL 的账号或密码与数据库不一致 | 修正 `REHAB_DATABASE_URL` 与 `POSTGRES_PASSWORD`；已有数据卷的密码不会因改环境变量自动更新 |
| Compose 报缺少变量并拒绝启动 | `POSTGRES_PASSWORD`、`REHAB_DATABASE_URL` 或 `REHAB_JWT_SECRET` 未设置 | 补全 `.agent/tmp/deployment.env` 后重试 |
| API 端口被占用 | 本机已有进程监听 8000 | 修改 `API_PORT`，或停止占用进程 |
| 健康检查失败，提示 `alembic_version` 读取失败 | 迁移未执行或数据库不可达 | 先确认数据库可连接，再串行执行迁移作业 |
| 登录返回 429 | 同一用户名在 5 分钟内失败 5 次 | 等待窗口结束；确认没有脚本反复重试 |
| 本机调用 API 返回 502 | 请求走了系统代理 | 对本机地址绕过代理；联调脚本已内置该处理 |
| 管理端 E2E 端口被占用 | 默认端口与 Hyper-V 保留段冲突 | 用 `E2E_BACKEND_PORT` 与 `E2E_PREVIEW_PORT` 覆盖 |
| `npx tsc` 报找不到命令 | 未安装依赖 | 在仓库根目录执行 `pnpm install --frozen-lockfile` |
| Playwright 报缺少浏览器 | 未安装 Chromium | 在 `apps/admin-web` 执行 `npx playwright install chromium` |
| Android 构建报 SDK location not found | 未设置 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT` | 设置 SDK 路径，或在 `apps/android/local.properties` 写入 `sdk.dir` |
| 迁移后 API 在新表上返回权限错误 | 运行账号没有新表权限 | 配置 `ALTER DEFAULT PRIVILEGES`，或在每次迁移后重新授权 |

## 9. 本地校验与验收命令

以下命令已在当前交付提交上实际执行。

### 9.1 快速回路

```powershell
python scripts/verify_all.py --skip-e2e --skip-android
```

预期：`ALL PASS`。该命令依次执行后端测试、部署自检、管理端类型检查、小程序类型检查、小程序行为测试与小程序静态检查。

### 9.2 后端

```powershell
python -m pytest -q services/api/tests
python deploy/verify.py
python -m unittest discover -s scripts -p "test_*.py"
```

预期：

- 后端测试 120 项全部通过。
- 部署自检输出 `PASS: 18 tables; SQLite upgrade/repeat/downgrade/upgrade; model diff empty; PostgreSQL SQL; YAML`。
- 联调脚本回归测试 2 项通过。

### 9.3 管理端

```powershell
cd apps/admin-web
npx tsc --noEmit
npx playwright test
```

预期：类型检查退出 0；E2E 23 项全部通过。

### 9.4 小程序

```powershell
cd apps/miniprogram
npx --no-install tsc --noEmit
node --test tests/patient-data.cjs
python tests/static_check.py .
```

预期：类型检查退出 0；行为测试 17 项全部通过；静态检查退出 0。

### 9.5 Android

```powershell
cd apps/android
$env:ANDROID_HOME = 'D:\Android\Sdk'
$env:ANDROID_SDK_ROOT = 'D:\Android\Sdk'
$env:JAVA_HOME = 'E:\jdk\microsoft-jdk-21.0.10-windows-x64\jdk-21.0.10+7'
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

预期：124 项单元测试全部通过，`assembleDebug` 成功。以上路径是本次验证环境；现场按实际 JDK 与 SDK 安装位置替换。`scripts/verify_all.py` 在 Windows 上会用绝对路径直接调用 `gradlew.bat`，无需先跑 `--skip-android`。

### 9.6 API 联调

先在 `.agent/tmp/smoke-config.json` 写入演示账号与绑定码，再执行：

```powershell
python scripts/smoke-api.py --config .agent/tmp/smoke-config.json
```

预期：脚本自建临时数据库、启动本地服务并逐项打印 `PASS`，覆盖全部 25 个契约操作（含两个科研聚合端点与媒体上传/上架），退出码 0。

### 9.7 全量脚本

```powershell
python scripts/verify_all.py
```

在 Windows 上，该脚本会依次跑完后端、部署自检、管理端构建、管理端 E2E、小程序与 Android 各步；任一步失败立即停止并打印失败步骤。

### 9.8 提交门禁

```powershell
git diff --check
```

预期：无输出、退出码 0。

## 10. 已知运维限制

- API 只提供 `/v1` 业务接口，没有独立的健康检查端点；健康检查脚本组合 `/openapi.json` 与数据库版本表。
- 没有内置日志轮转、指标、链路追踪或告警配置，需要容器平台补齐。
- 没有通知投递与兜底通道；预警只持久化到数据库，不会自动发送短信、电话或推送。
- 没有管理端静态站点托管、账号初始化与生产 seed 流程。
- 没有对象存储上传集成；相关环境变量是预留项。
- 当前 Docker/PostgreSQL 容器验证记录对应迁移 `0001` 且当时契约为 18 个操作；`0002`–`0005` 的 SQLite 往返与模型差异由部署自检覆盖，但未在容器内复跑。
- CI 配置文件存在，但仓库没有远端流水线，未在真实 CI 上运行。
