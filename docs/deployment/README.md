# 部署 API 与数据库

本手册从仓库根目录执行命令。镜像使用 Python 3.13，运行用户为 UID 10001，数据库使用 PostgreSQL 17。当前部署只提供 API 与数据库，不包含管理端静态站点及账号初始化。

## 本地 Docker 启动

需要 Docker Engine 与 Compose v2。将 `.env.example` 复制到 `.agent/tmp/deployment.env`，填写数据库密码、JWT 密钥以及数据库 URL。此目录已被忽略；不要在仓库根保存真实 `.env`，当前忽略规则不保护它。

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

1. 在同一地域与 VPC 创建托管 PostgreSQL、容器运行环境及镜像仓库。数据库只允许容器网络访问。创建专用数据库与迁移账号；运行账号只授业务表读写及读取 `alembic_version` 权限，迁移账号持有 DDL 权限。
2. 构建镜像，使用发布版本标签推送到已授权的镜像仓库。上线固定镜像 digest，保留上一版本，构建记录中保存依赖清单。基础镜像及应用依赖仍使用版本范围，不能宣称字节级可重复构建。

   ```powershell
   docker build -f services/api/Dockerfile -t REGISTRY/rehab-api:RELEASE .
   docker push REGISTRY/rehab-api:RELEASE
   ```

3. 将数据库 URL 与随机 JWT 密钥注入云端秘密管理服务。所有 API 副本使用相同密钥，设置 `REHAB_AUTO_CREATE_SCHEMA=false`。不要将秘密传为构建参数。微信及 COS 示例项是预留配置，当前后端不消费它们。
4. 在发布前制作数据库备份。使用同一镜像运行一个迁移 Job，命令为 `alembic -c deploy/alembic.ini upgrade head`，注入迁移账号 URL。迁移作业必须串行，成功后才能更新 API；禁止多个副本同时执行迁移。
5. API 容器覆盖默认启动命令为 `uvicorn app.main:app --host 0.0.0.0 --port 8000`，使用运行账号 URL。初次发布先单副本验证，配置优雅终止与至少 30 秒停止窗口。将 `/openapi.json` 作为 HTTP 存活探针，将 `python /opt/rehab/deploy/healthcheck.py` 作为执行式就绪探针。容器平台不会自动采用 Dockerfile 的健康检查时，必须显式配置探针。
6. 在负载均衡器终止 HTTPS，通过安全组限制后端访问；不要将数据库暴露公网。管理端与 API 跨域配置未在本任务接入，优先通过同域网关转发 `/v1`，上线前验证客户端实际访问路径。
7. 创建私有 COS 桶，启用服务端加密、版本控制、生命周期与访问审计。只授必要桶路径权限，优先使用短期角色凭据。公开训练素材可通过带源站鉴权的 CDN 分发，患者资料不能放入公开缓存。配置 CDN 域名、证书、回源权限并实测缓存刷新。当前 API 无 COS 上传集成，配置占位不意味着文件会自动上传。
8. 执行容器健康检查，并以受控测试账号验证登录、处方读取、训练记录写入及重启后数据保留。通过后再开放流量。不要在生产运行开发 seed，它不是生产账号开通流程。

## 健康检查与日志

镜像健康检查先访问本机 `/openapi.json`，再读取数据库 `alembic_version`。这证明 HTTP 响应及数据库版本表可读，不证明所有业务查询正常，也不验证版本等于当前 head。发布 Job 的升级成功与业务冒烟测试仍是发布条件。

```powershell
docker compose --env-file .agent/tmp/deployment.env logs --tail 100 api postgres
docker compose --env-file .agent/tmp/deployment.env exec api alembic -c deploy/alembic.ini current
```

API 与迁移错误输出到标准输出及标准错误，云端接入容器日志采集，设置保留期限和告警。不要输出环境变量、完整数据库 URL、JWT 或患者请求体。Compose 的 `config --quiet` 只校验，直接打印完整 config 会展开秘密。Docker 的 unhealthy 状态本身不保证容器重启，需配合云平台探针策略和告警。

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

本次环境没有 Docker 与 hadolint，不能执行 `docker compose config`、镜像构建、容器启动及真实 PostgreSQL 验证。自检覆盖 YAML 解析、16 表 SQLite 迁移往返与重复升级、模型差异、检查约束数量以及 PostgreSQL 离线 SQL 生成。YAML 可解析不等于 Compose 语义或 Dockerfile 构建成功，上线前需在有 Docker 的环境补齐开头的启动验收。

2026-11-04 本地结果为部署自检通过、后端全量 36 项通过、Python 语法解析通过、`git diff --check` 通过。测试产生现有 HTTP 客户端弃用及依赖版本警告。未执行云部署，独立审查交由协调者完成。

尚未接入 CI/CD，未做蓝绿部署，未提供云资源编排、存量 SQLite 数据搬迁及生产账号初始化。现有登录限流使用进程内状态，多副本及重启不能共享计数；扩容前应单独治理。存量 ORM 与 PostgreSQL 的完整业务兼容性必须通过真实数据库测试确认。
