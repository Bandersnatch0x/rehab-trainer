# 部署 API 与数据库

本手册从仓库根目录执行命令。镜像使用 Python 3.13，运行用户为 UID 10001，数据库使用 PostgreSQL 17。当前部署只提供 API 与数据库，不包含管理端静态站点及账号初始化。

## 本地 Docker 启动

需要 Docker Engine 与 Compose v2。将 `.env.example` 复制到 `.agent/tmp/deployment.env`，填写数据库密码、JWT 密钥以及数据库 URL。该目录已被忽略；根目录的 `.env`、`.env.local` 与 `.env.*.local` 也已纳入忽略规则，但秘密仍应优先来自环境变量或秘密管理服务，不要长期留在工作区。

```powershell
New-Item -ItemType Directory -Force .agent/tmp
Copy-Item .env.example .agent/tmp/deployment.env
python -c "import secrets; print(secrets.token_hex(32))"
```

分别生成数据库密码与 JWT 密钥。数据库 URL 中的密码必须与数据库容器密码一致。十六进制值无需 URL 编码，其他密码须对 URL 中的特殊字符编码。示例 URL 的主机 `postgres` 只在 Compose 网络内可用。

```powershell
docker compose --env-file .agent/tmp/deployment.env config --quiet
docker compose --env-file .agent/tmp/deployment.env up --build -d --wait
docker compose --env-file .agent/tmp/deployment.env ps
docker compose --env-file .agent/tmp/deployment.env exec api python deploy/healthcheck.py
```

预期 API 为 healthy，接口说明位于 `http://127.0.0.1:8000/docs`。启动先等数据库可连接，再执行 `alembic upgrade head`；迁移失败时 API 不启动。数据库无宿主机端口，API 仅绑定本机回环地址。数据库数据存入命名卷。

```powershell
docker compose --env-file .agent/tmp/deployment.env down
```

停止命令保留数据库卷。不要附加 `--volumes`，它会删除数据库。修改数据库密码环境变量不会更新已有卷内的账号密码，必须通过数据库账号管理流程轮换并同步 API URL。

## 数据库迁移与 SQLite 切换

迁移依赖放在 `deploy/requirements.txt`，后端已有 `postgres` 驱动依赖组。Alembic 记录数据库版本，初始脚本固定 16 张模型表、索引、唯一约束、外键与检查约束，不调用运行时 `create_all`。Python 侧默认值仍由 ORM 写入，不额外改变业务语义。

本地不使用容器时，可安装并运行同一迁移。

```powershell
python -m pip install './services/api[postgres]' -r deploy/requirements.txt
$env:REHAB_DATABASE_URL = 'sqlite:///.agent/tmp/local.db'
$env:REHAB_AUTO_CREATE_SCHEMA = 'false'
python -m alembic -c deploy/alembic.ini upgrade head
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

迁到 PostgreSQL 时，将 `REHAB_DATABASE_URL` 改为 `postgresql+psycopg://USER:ENCODED_SECRET@HOST:5432/DB`，保持 `REHAB_AUTO_CREATE_SCHEMA=false`，在空目标数据库上运行相同升级命令。生产 URL 追加 `?sslmode=verify-full&sslrootcert=/run/certs/postgres-ca.pem`，挂载云数据库 CA，并使用与证书匹配的主机名。不要将 libpq 的 `postgresql://` URL 与 SQLAlchemy 的 `postgresql+psycopg://` 混用。

切换 URL 只改变连接目标，不搬迁 SQLite 数据。已有数据时，先停写、备份 SQLite 文件，在隔离 PostgreSQL 数据库建立结构，再按外键依赖顺序导入并核对行数、主键、JSON、布尔值、时间及抽样业务读写。本任务不提供数据转换工具。验收前保留原库，切换后如需回退，必须处理新库增量，不能直接切回旧库丢弃新写入。

已有 `create_all` 数据库不能直接执行初始升级，否则会因表已存在而失败。先备份，并用 `alembic check` 核验已纳管数据库，或在隔离副本用 SQLAlchemy 元数据逐表核对旧库结构。仅在旧库与初始快照全部一致时执行 `alembic stamp 0001`；stamp 不创建表也不验证结构，禁止用它跳过结构差异。

后续模型修改需增加新版本，不改初始快照。生成后必须人工检查约束、数据转换与降级操作。

```powershell
python -m alembic -c deploy/alembic.ini revision --autogenerate -m change_description
python -m alembic -c deploy/alembic.ini upgrade head
python -m alembic -c deploy/alembic.ini check
```

初始 `downgrade base` 会删除全部业务表，仅适合空的验证数据库。生产故障优先前向修复或从备份恢复。

## 腾讯云生产部署

生产执行需另行人工授权。本次仅交付配置与步骤，不创建云资源。

1. 在同一地域与 VPC 创建托管 PostgreSQL、容器运行环境及镜像仓库。数据库只允许容器网络访问。创建专用数据库与迁移账号；运行账号只授业务表读写及读取 `alembic_version` 权限，迁移账号持有 DDL 权限。新迁移建出的表不会自动继承运行账号权限，需在迁移账号上配置 `ALTER DEFAULT PRIVILEGES ... GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO rehab_runtime`，或在每次迁移后重新授权；否则 API 会在新表上返回权限错误。
2. 构建镜像，使用发布版本标签推送到已授权的镜像仓库。上线固定镜像 digest，保留上一版本，构建记录中保存依赖清单。基础镜像及应用依赖仍使用版本范围，不能宣称字节级可重复构建。

   ```powershell
   docker build -f services/api/Dockerfile -t REGISTRY/rehab-api:RELEASE .
   docker push REGISTRY/rehab-api:RELEASE
   ```

3. 将数据库 URL 与随机 JWT 密钥注入云端秘密管理服务。所有 API 副本使用相同密钥，设置 `REHAB_AUTO_CREATE_SCHEMA=false`。不要将秘密传为构建参数。微信及 COS 示例项是预留配置，当前后端不消费它们。
4. 在发布前制作数据库备份。使用同一镜像运行一个迁移 Job，命令为 `alembic -c deploy/alembic.ini upgrade head`，注入迁移账号 URL。迁移作业必须串行，成功后才能更新 API；禁止多个副本同时执行迁移。
5. API 容器覆盖默认启动命令为 `uvicorn app.main:app --host 0.0.0.0 --port 8000`，使用运行账号 URL。初次发布先单副本验证，配置优雅终止与至少 30 秒停止窗口。将 `/healthz` 作为 HTTP 存活探针，将 `/readyz` 作为就绪探针（Dockerfile 的 `HEALTHCHECK` 经 `python /opt/rehab/deploy/healthcheck.py` 探它）。容器平台不会自动采用 Dockerfile 的健康检查时，必须显式配置探针。
6. 在负载均衡器终止 HTTPS，通过安全组限制后端访问；不要将数据库暴露公网。管理端与 API 跨域配置未在本任务接入，优先通过同域网关转发 `/v1`，上线前验证客户端实际访问路径。
7. 创建私有 COS 桶，启用服务端加密、版本控制、生命周期与访问审计。只授必要桶路径权限，优先使用短期角色凭据。公开训练素材可通过带源站鉴权的 CDN 分发，患者资料不能放入公开缓存。配置 CDN 域名、证书、回源权限并实测缓存刷新。当前 API 无 COS 上传集成，配置占位不意味着文件会自动上传。
8. 执行容器健康检查，并以受控测试账号验证登录、处方读取、训练记录写入及重启后数据保留。通过后再开放流量。不要在生产运行开发 seed，它不是生产账号开通流程。

## 健康检查与日志

两个探针职责分开：

| 探针 | 路径 | 检查内容 | 失败含义 |
|---|---|---|---|
| 存活 | `/healthz` | 仅进程能响应，**不碰数据库** | 进程死了，该重启 |
| 就绪 | `/readyz` | 用**应用自己的连接池**执行 `SELECT 1` | 暂时不能接流量，摘掉即可，不必重启 |

分开的理由是真实故障下的分水岭：数据库不可用时，若存活探针也探库，编排会反复重启一个本身没问题的进程，
而重启对数据库故障毫无帮助。反过来，`/readyz` 必须走应用的连接池——此前脚本另起引擎直连数据库，
连接池耗尽时它会报"健康"，而实际上所有业务请求都在排队等连接。

两者都不进 OpenAPI：它们描述进程状态，不是客户端可依赖的业务接口（`include_in_schema=False`）。
就绪通过只证明连接池可用，不证明所有业务查询正常，也不验证数据库版本等于当前 head；
发布 Job 的升级成功与业务冒烟测试仍是发布条件。

```powershell
docker compose --env-file .agent/tmp/deployment.env logs --tail 100 api postgres
docker compose --env-file .agent/tmp/deployment.env exec api alembic -c deploy/alembic.ini current
```

API 与迁移错误输出到标准输出及标准错误，云端接入容器日志采集，设置保留期限和告警。不要输出环境变量、完整数据库 URL、JWT 或患者请求体。Compose 的 `config --quiet` 只校验，直接打印完整 config 会展开秘密。Docker 的 unhealthy 状态本身不保证容器重启，需配合云平台探针策略和告警。

### 告警锚点

平台侧按下面的锚点配告警。`event=` 是**稳定键**：日志文案可以改，键不能改（有单测拦着）。

| 信号 | 锚点 | 含义与处置 |
| --- | --- | --- |
| 进程不可用 | `/healthz` 连续失败 | 重启 |
| 不能接流量 | `/readyz` 连续失败 | 摘流量；先查数据库可达性与连接池 |
| 连接池耗尽 | 日志 `event=db_pool_exhausted` | 并发已超过 副本数 ×(pool_size+max_overflow)；扩容或排查慢查询 |
| 连接层故障 | 日志 `event=db_unavailable` | 数据库重启/切换/网络中断；跟随数据库侧告警 |
| 就绪失败 | 日志 `event=db_readiness_failed` | 与 `/readyz` 同步，异常原文只在这里 |

窗口与阈值按试点规模定，不要照抄：`db_pool_exhausted` 建议 5 分钟窗口内**持续**出现才告警
（偶发一条是正常削峰），`/readyz` 连续 3 次失败即摘流量。

## 备份与恢复

本地备份使用容器内文件，避免 Windows PowerShell 对二进制重定向进行编码转换。

```powershell
docker compose --env-file .agent/tmp/deployment.env exec postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/rehab.dump'
docker compose --env-file .agent/tmp/deployment.env cp postgres:/tmp/rehab.dump .agent/tmp/rehab.dump
```

将备份移至受控加密存储并记录时间、镜像版本、迁移版本与校验和。恢复演练使用新数据库，避免覆盖现有数据。

```powershell
docker compose --env-file .agent/tmp/deployment.env cp .agent/tmp/rehab.dump postgres:/tmp/restore.dump
docker compose --env-file .agent/tmp/deployment.env exec postgres sh -c 'createdb -U "$POSTGRES_USER" rehab_restore'
docker compose --env-file .agent/tmp/deployment.env exec postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d rehab_restore --no-owner --no-acl --exit-on-error /tmp/restore.dump'
```

核对恢复库行数、约束、迁移版本与业务读写后，停写再切换 API URL。失败时保留原库与备份，不运行覆盖性清理命令。生产启用托管数据库自动备份与时间点恢复，按业务批准的 RPO 和 RTO 配置保留周期，并定期恢复到隔离实例演练。COS 单独依赖版本控制及备份策略，数据库备份不包含对象内容；恢复时还需核对对象引用。

## 验证与已知限制

可复跑部署自检如下，需要 Python 3.13、应用依赖、Alembic、psycopg 与 PyYAML。

```powershell
python -m pip install './services/api[postgres,dev]' -r deploy/requirements.txt PyYAML
python deploy/verify.py
python -m pytest services/api/tests
git diff --check
```

静态自检覆盖 YAML 解析、16 表 SQLite 迁移往返与重复升级、模型差异、检查约束数量以及 PostgreSQL 离线 SQL 生成。

上面全部不需要数据库服务。真实 PostgreSQL 上的迁移验证另有一个脚本，它要求目标库为空（非空库会被拒绝，避免降级删除数据）：

```powershell
$env:REHAB_DATABASE_URL = "postgresql+psycopg://rehab:<密码>@127.0.0.1:55432/rehab"
services/api/.venv/Scripts/python.exe deploy/verify_postgres.py   # 仓库 venv 已含 psycopg
```

它做四件事：`upgrade head` 后逐条断言 0002–0005 引入的列、检查约束、外键、索引与 JSON 列真实存在；用 ORM 写入并读回数据；`downgrade 0001` 后再 `upgrade head`，断言既有行未被降级破坏且新列按 `server_default` 回填；`downgrade base` 后重建，证明迁移可从零重复执行。同时在真实 PostgreSQL 上跑 `compare_metadata`，比 SQLite 更容易暴露类型与约束漂移。

### 容器与真实 PostgreSQL 验证（2026-11-05）

在 WSL2 的 kali-linux2 发行版内用 Docker 27.5.1 / Compose 2.32.4 实跑，使用本仓 `docker-compose.yml` 与 `.env.example` 生成的临时 env（随机密码与 JWT）：

| 步骤 | 结果 |
| --- | --- |
| `docker compose config --quiet` | 通过 |
| `docker compose up --build -d --wait` | 通过；api 与 postgres 均 `healthy` |
| 构建上下文 | 120 KB（新增 `.dockerignore`，此前会把约 1 GB 仓库送进 daemon） |
| `alembic current` | `0001 (head)` |
| `python deploy/healthcheck.py` | 退出 0 |
| PostgreSQL 表数 | 17（16 张业务表 + `alembic_version`） |
| `pg_dump -Fc` | 46,593 字节 |
| `createdb` + `pg_restore --exit-on-error` | 成功；恢复库表结构与 `alembic_version=0001` 与源库一致，行数等于备份时点 |
| 联调脚本对容器 API | **18/18 契约操作通过**，含批量幂等与重放 |
| 运行身份 | api 进程 `uid=10001(rehab)`，非 root |
| 端口暴露 | api 仅 `127.0.0.1:8000`；postgres 无宿主机端口 |
| 迁移失败不启动 | 用错误数据库密码启动：api 退出码 1，日志为 `password authentication failed`，8010 未监听。`restart: unless-stopped` 会让它反复重启，但始终不提供服务，平台侧仍需失败告警 |
| 迁移/运行账号分离 | 在全新库上用 `rehab_migrator` 跑 `alembic upgrade head` 成功（退出 0）；`rehab_runtime` 执行 `CREATE TABLE` 被拒（`permission denied for schema public`）；API 用运行账号启动，healthcheck 退出 0，seed 写入成功 |
| 管理端跨进程联调 | 管理端构建后经 Vite 预览代理 `/v1` 打到容器 API：治疗师登录成功、患者列表显示种子患者「张阿姨」、页面无 JS 错误 |
| hadolint 静态检查 | 在 WSL 内用 hadolint 2.12.0 跑 `services/api/Dockerfile`：通过（`--failure-threshold=warning`，退出 0）。唯一的 DL3013（pip 未固定版本）已按实际情况豁免并注明理由：wheel 由构建阶段按 `pyproject.toml` 与 `deploy/requirements.txt` 生成后本地安装，版本在构建产物层锁定 |

补充说明：该环境无法直连 Docker Hub，基础镜像经 `docker.m.daocloud.io` 镜像拉取后按原名打标签；这与镜像内容无关，但生产构建应配置可用的镜像仓库。未执行云上部署，也未做 hadolint 静态检查。

### 迁移 0002–0005 与 PostgreSQL 主链路复验（2026-11-07）

上表的 PostgreSQL 结果取自只有 `0001` 的时点。此后新增 `0002`（科研数据底座）、`0003`（视频资产）、`0004`（查询索引）、`0005`（TOTP 与跨副本登录限流），已在 `postgres:17-bookworm`（PostgreSQL 17.11）上重新验证；**2026-11-08 复验，下表全部步骤重跑**：

| 步骤 | 结果 |
| --- | --- |
| `alembic upgrade head`（空库） | 通过；`alembic current` 为 `0005 (head)` |
| 表数 | 19（18 张业务表 + `alembic_version`） |
| `python deploy/verify_postgres.py` | PASS：0002–0005 的列/检查约束/外键/索引/JSON 列齐备；`compare_metadata` 差异为空 |
| 数据回读 | ORM 写入机构/账号/患者后可读回，`0005` 的 `totp_secret`、`totp_last_step` 生效 |
| `downgrade 0001` → `upgrade head` | 通过；既有患者行保留，`patient.data_origin` 按 `server_default` 回填为 `real` |
| `downgrade base` → `upgrade head` | 通过；迁移可从零重复执行 |
| 迁移建库后的接口联调 | **32/32 契约操作通过**（27 条路径），含批量幂等与重放、二次验证自助启用/停用，schema 全部由迁移建立（`REHAB_AUTO_CREATE_SCHEMA=false`） |

复现方式：在 WSL 内 `docker run -d --name rehab-pg -e POSTGRES_PASSWORD=... -p 55432:5432 postgres:17-bookworm`（Windows 侧等价走 `wslc run -d --name rehab-pg ... -p 55432:5432`，注意 `wslc run` 没有 `--restart` 参数），宿主机用 `REHAB_DATABASE_URL` 指向它；先用 `python -m alembic -c deploy/alembic.ini upgrade head` 建库，再 `python -m app.seed`，最后 `python scripts/smoke-api.py --base-url ...`。登录限流自 `0005` 起改为数据库表实现，因此跨副本共享计数；`0005` 之前"进程内状态、多副本不共享"的说法已失效。

### 单实例性能基线（2026-11-07）

用 `scripts/loadtest-api.py` 对上面这套 PostgreSQL 环境压测，三条热点路径轮转：`GET /patients?limit=20`、`GET /sessions?patientId=…&limit=20`、`POST /sessions`（单条记录，唯一 `clientRecordId`）。

环境：Windows 11 / 20 逻辑核；API 为 Windows Python 3.13 + 单进程 uvicorn（未配 workers）；PostgreSQL 17.11 跑在 WSL2 容器内；压测脚本与 API、数据库同机，因此负载发生器本身也在抢同一份 CPU。

| 并发 | 路径 | RPS | p50 | p95 | p99 | 错误 |
| --- | --- | --- | --- | --- | --- | --- |
| 8 | 三条轮转 | 111 | 59–79 ms | 86–121 ms | 107–166 ms | 0 |
| 16 | 三条轮转 | 144 | 96–118 ms | 133–160 ms | 147–187 ms | 0 |
| 32 | 三条轮转 | 133 | 218–251 ms | 304–334 ms | 349–373 ms | 0 |
| 64 | 三条轮转 | 1.6 | 约 29 s | 约 29.6 s | 约 29.7 s | 客户端超时（统计不完整） |
| 40 | 仅 `GET /patients` | 124 | 284 ms | 421 ms | 566 ms | 0 |

**上限约 140 RPS，瓶颈是数据库连接池而不是 CPU。** 依据：并发 40 时持续采样 `pg_stat_activity`，`rehab_smoke` 库的后端连接数稳定停在 **15**，压测结束回落到 5。15 = `create_engine()` 默认 `pool_size=5` + `max_overflow=10`（`app/core/database.py` 未显式配置池）。超过 15 个并发数据库请求后请求只能排队，队列等待上限是 `pool_timeout` 默认 30 秒——这与并发 64 时 p50 恰为 29 秒的行为一致：**服务不是降级，而是挂起后再失败**。

复现：

```powershell
# 前两步见上节；随后启动 API 与压测
python -m uvicorn app.main:app --host 127.0.0.1 --port 8011        # 环境变量同上节
python scripts/loadtest-api.py --config .agent/tmp/smoke-config.json `
    --base-url http://127.0.0.1:8011/v1 --concurrency 8 --seconds 20
```

压测脚本自身的输入校验、分位口径与退出码由 `scripts/test_loadtest_api.py` 覆盖（7 项，不需要数据库）。

关于并发 64 那一行：该行取自修复前的脚本，读数并不完整——30 秒里只有 `GET /patients` 拿到 46 个样本、`GET /sessions` 拿到 2 个，`POST /sessions` 一个都没完成，线程因客户端读超时而中止，脚本却仍以 0 退出，容易把一次失败的压测读成基线。现已改正：记录到的错误或提前退出的线程都会让脚本以非零码退出，并在汇总里点明「本轮吞吐被低估」。**并发 64 的量级结论（服务挂起而非降级）不受影响**，因为连接池上限 15 由 `pg_stat_activity` 直接观测到，且并发 40 那一行是在其之后跑通、读数正常的。

限制：负载发生器与 API 同机、未做长时浸泡、未覆盖管理端与小程序、登录路径只出现在每线程一次的准备阶段。这些数字用于同环境的改动前后对比与量级判断，不构成容量承诺。

### 连接池与过载反馈（2026-11-07，票 0026）

上面的"挂起而非降级"已修：

- **池显式配置**（`app/core/database.py`，环境变量可调）：`REHAB_DB_POOL_SIZE`（默认 5）、
  `REHAB_DB_MAX_OVERFLOW`（10）、`REHAB_DB_POOL_TIMEOUT_SECONDS`（**5**，原默认 30）、
  `REHAB_DB_POOL_PRE_PING`（true）、`REHAB_DB_POOL_RECYCLE_SECONDS`（1800）。SQLite 分支保持默认。
- **建连超时**：`REHAB_DB_CONNECT_TIMEOUT_SECONDS`（默认 5）。它与 `pool_timeout` 是两件事——
  后者只限制排队等池，**不约束建立连接本身**。对端停服但端口仍被转发时，不设它会一直等到
  操作系统放弃（Windows 约 20 秒），就绪探针会挂在那里、业务线程也白占。
  **平台的就绪探针超时必须 ≥ 这个值 + 1 秒**，否则平台先超时、拿不到我们返回的 503。
- **探活**：取连接前 `SELECT 1`。数据库重启或主从切换后，不会再拿已被对端关闭的连接去用。
- **快速失败**：池等待超时与连接层故障都返回 `503` + `{"code":"SERVICE_UNAVAILABLE","detail":{"retryable":true}}`，
  原文只进日志（`连接池等待超时 POST /v1/auth/login：并发已超过 pool_size+max_overflow=15`）。
- **契约**：`components.responses.ServiceUnavailable` 已定义，25 个 operation 全部声明 `503`。

**部署时必须核算**：副本数 × (pool_size + max_overflow) 要低于数据库 `max_connections` 并留余量。

修复前后同一环境实测（方法同上）：

| 并发 | 修复前 | 修复后 |
| --- | --- | --- |
| 8 | 111 RPS，p50 59–79 ms | 102 RPS，p50 59–78 ms |
| 32 | 133 RPS，p50 218–251 ms | 84 RPS，p50 309–370 ms |
| 64 | **1.6 RPS，p50 约 29 s，服务近乎停摆** | **73 RPS，p99 ≤ 5.0 s，20 次快速 503，服务继续可用** |

并发 8 与修复前基本一致（未触及池上限）。并发 32/64 吞吐低于修复前，是"无界排队换成有界等待 + 快速失败"的直接结果：
原先靠让请求排队 30 秒换来了一点吞吐，代价是调用方完全无法区分"慢"与"死"。修复后最坏等待被钉在 `pool_timeout`，
超出部分立刻拿到可重试的 503；`pool_pre_ping` 的每次取连接探测是次要因素，未单独量化。

线上形态（1 条连接的池 + 200 并发，抓到的真实响应）：

```json
{"code":"SERVICE_UNAVAILABLE","message":"服务繁忙，请稍后重试","detail":{"retryable":true}}
```

同时 35 次失败对应 35 行 `连接池等待超时` 错误日志——响应给客户端、细节给运维。

**复测（2026-11-07，WSLC 容器 + PostgreSQL 16，本机 127.0.0.1 直连）**：换到稳定链路后重跑同一脚本，
确认交付配置的行为形状没变、且延迟有上界——并发 8 → 142 RPS（p50 46–63 ms）、32 → 122 RPS、
64 → 62 RPS 且最大延迟 4.5 s（≈ `pool_timeout`），6 次快速 503 打在登录上。
绝对 RPS 与上表不可直接比较（数据库版本与链路都换了），**可比的只有形状**：并发上去后吞吐不再增长、
延迟被钉在超时值附近、超出的请求立刻拿到可重试的 503。

**未做**：多副本下的池总量核算（需确定副本数与数据库 `max_connections`）；客户端对 503 的统一退避重试
（Android 训练记录本就有本地队列重试，其余读取路径目前只提示失败）。

### 备份恢复演练（2026-11-07）

`deploy/verify_backup_restore.sh` 把「备份可用」从人工一次性动作变成可复跑的检查：转储 → 恢复到新库 → 对比结构、行数、内容摘要 → 清理恢复库（无论成败都清理）。此前只在 `0001` 时点人工做过一次，之后新增了 `0002`–`0005`。

```bash
# 在装有 PostgreSQL 客户端的主机上（容器内亦可，镜像自带 pg_dump）
PGHOST=127.0.0.1 PGPORT=55432 PGUSER=rehab PGPASSWORD=… SOURCE_DB=rehab_drill \
    bash deploy/verify_backup_restore.sh
```

在 head = `0005` 的库上实测（库内含种子数据，并额外插入一行带 JSON 的 `session_record` 和一行带 JSON 数组的 `media_asset`）：

| 项 | 结果 |
| --- | --- |
| `pg_dump -Fc` | 53,730 字节 |
| `pg_restore --exit-on-error` | 通过 |
| `alembic_version` / 表数量 | `0005` / 19，两侧一致 |
| 检查约束 / 索引数量 | 6 / 54，两侧一致 |
| 逐表行数（19 张） | 全部一致，含 `media_asset=1`、`session_record=1` |
| 逐表内容摘要（`md5` of 行文本序列） | 全部一致，含两张带 JSON 的表 |
| 恢复库清理 | 已删除，转储文件已删除 |

摘要这一项不是装饰：它证明 JSON 列真的走了 dump/restore 往返。为确认这个比较**能失败**（不会永远 PASS 的比较等于没有比较），单独做过敏感性验证——改 `session_record.completion_count` 一个字段，摘要从 `5b54ede2…` 变为 `d50b9cdd…`，改回来原值复原，且与演练中恢复库的摘要一致。

注意：演练应在**停止写入**时执行。源库在转储后若继续写入，行数/摘要对比会如实报出差异——这是设计如此，不是误报。

CI 与部署现状：仓库**没有配置 git remote**，因此 `.github/workflows/ci.yml` 仍未被任何真实流水线执行过（P4-3 阻塞在远端仓库与凭据）；预生产部署（P4-1）需要云资源，未开始。

2026-11-04 本地结果为部署自检通过、后端全量 36 项通过、Python 语法解析通过、`git diff --check` 通过。测试产生现有 HTTP 客户端弃用及依赖版本警告。未执行云部署，独立审查交由协调者完成。

仓库内有 `.github/workflows/ci.yml`（按路径触发 Android 构建、小程序类型检查、管理端构建、后端 pytest 与契约校验），但仓库没有远端流水线，**该配置未在真实 CI 上运行过**。未做蓝绿部署，未提供云资源编排、存量 SQLite 数据搬迁及生产账号初始化。登录限流自 `0005` 起由 `login_failure` 表实现，多副本共享计数；清理依赖每次登录时的窗口裁剪，尚未做独立清理任务与容量上限。存量 ORM 与 PostgreSQL 的完整业务兼容性已在本机容器内的 PostgreSQL 17 上跑通主链路（32/32 契约操作，见下节），但未做长时压测与并发验证。

## 训练视频存储与静态托管

训练视频由管理端上传，接口为 `POST /v1/media-assets/upload`（multipart），资产元数据落库，文件落盘到 API 的媒体静态根。

配置项（均为非秘密，可随环境覆盖）：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `REHAB_MEDIA_ROOT` | `./media` | 上传写入目录；容器内建议挂载持久卷（如 `/data/media`） |
| `REHAB_MEDIA_PUBLIC_BASE_URL` | `http://127.0.0.1:8000/media` | 对外访问前缀，决定客户端拿到的 `videoUri` |
| `REHAB_MEDIA_MAX_UPLOAD_BYTES` | `209715200`（200 MB） | 单文件上限 |

上传安全约束由服务端强制执行，不依赖客户端：

- 只接受 MP4，按文件头魔数（`ftyp` + 品牌）判定，不信任扩展名与 `Content-Type`；
- 文件名由服务端生成（UUID + `.mp4`），客户端文件名被忽略，避免路径穿越；
- 落盘路径固定在 `REHAB_MEDIA_ROOT` 下，任何 `..`、绝对路径或盘符都会被拒绝；
- 超过上限立即中止并清理临时文件，不会留下半截文件；
- 计算 SHA-256 供去重与完整性校验；
- 上传结果为 `draft`，需在管理端「上架」且设置适用分期后才会下发。

静态文件托管：

- API 在本地开发时直接挂载 `/media`（`REHAB_MEDIA_PUBLIC_BASE_URL` 指向它即可）。生产建议由 nginx 或对象存储前缀托管同一目录，只读挂载给静态服务器，API 保持写权限。
- 反向代理需关闭目录列表，仅允许视频类型，并按 UUID 文件名设置长缓存（文件内容不可变）。
- `docker-compose.yml` 已把媒体目录挂到命名卷 `media_data`（容器内 `/data/media`）。若在前面加 nginx，把它以只读方式挂载同一卷即可；compose 当前不包含 nginx 服务，按实际网关补充。
- 上传目录必须对 API 运行用户（镜像内 UID 10001）可写；权限不足时接口返回 500，不会静默丢文件。

表结构由 `deploy/migrations/versions/0003_media_asset.py` 建立，发布时随 `alembic -c deploy/alembic.ini upgrade head` 一起执行。
