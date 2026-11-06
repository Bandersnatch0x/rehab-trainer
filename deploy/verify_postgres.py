"""在真实 PostgreSQL 上验证 0001→0005 迁移：升级、逐级降级、数据回读与模型一致性。

与 `deploy/verify.py` 的分工：
- `verify.py` 用 SQLite 验证迁移往返、重复升级与 PostgreSQL 离线 SQL 生成（不需要数据库服务）。
- 本脚本补齐它覆盖不到的部分：真实 PostgreSQL 上的 DDL 语义（检查约束、JSON 列、外键、索引名）
  以及降级/再升级后既有数据是否仍在。

前提：目标库必须是空库。脚本会拒绝在已有业务表的库上运行，避免误删数据。

    $env:REHAB_DATABASE_URL = "postgresql+psycopg://rehab:rehabtest@127.0.0.1:55432/rehab"
    python deploy/verify_postgres.py

只读断言，不修改仓库文件；失败时抛出 AssertionError 并保留现场以便排查。
"""
from pathlib import Path
import os
import sys

from alembic import command
from alembic.autogenerate import compare_metadata
from alembic.config import Config
from alembic.migration import MigrationContext
from sqlalchemy import create_engine, inspect, text
from sqlalchemy.orm import Session

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / "services/api"))

url = os.environ.get("REHAB_DATABASE_URL")
assert url, "必须通过 REHAB_DATABASE_URL 指定目标库"
assert url.startswith("postgresql"), f"本脚本只验证 PostgreSQL，收到：{url.split('://')[0]}"

os.environ.setdefault("REHAB_AUTO_CREATE_SCHEMA", "false")

from app.core.database import Base  # noqa: E402
from app.models import Account, Org, Patient  # noqa: E402

HEAD = "0005"
TOTP_SECRET = "JBSWY3DPEHPK3PXP"
engine = create_engine(url)
config = Config(str(root / "deploy/alembic.ini"))


def columns(inspector, table):
    return {column["name"] for column in inspector.get_columns(table)}


def indexes(inspector, table):
    return {index["name"] for index in inspector.get_indexes(table)}


def checks(inspector, table):
    return {constraint["name"] for constraint in inspector.get_check_constraints(table)}


def foreign_key_targets(inspector, table):
    return {
        (tuple(fk["constrained_columns"]), fk["referred_table"])
        for fk in inspector.get_foreign_keys(table)
    }


def assert_migrations_applied(inspector):
    """0002–0005 各自引入的结构必须真实存在于 PostgreSQL。"""
    assert "data_origin" in columns(inspector, "patient"), "0002：patient.data_origin 缺失"
    assert "ck_patient_data_origin" in checks(inspector, "patient"), "0002：检查约束缺失"
    assert "ix_patient_data_origin" in indexes(inspector, "patient"), "0002：索引缺失"

    assert "media_asset" in inspector.get_table_names(), "0003：media_asset 表缺失"
    assert indexes(inspector, "media_asset") >= {
        "ix_media_asset_org_id",
        "ix_media_asset_exercise_code",
        "ix_media_asset_sha256",
        "ix_media_asset_status",
    }, "0003：索引缺失"
    assert foreign_key_targets(inspector, "media_asset") >= {
        (("org_id",), "org"),
        (("exercise_code",), "exercise"),
        (("created_by",), "account"),
    }, "0003：外键缺失"
    assert "applicable_stages" in columns(inspector, "media_asset"), "0003：JSON 列缺失"

    assert "ix_audit_event_org_created" in indexes(inspector, "audit_event"), "0004：审计索引缺失"
    assert "ix_assessment_patient_assessed" in indexes(
        inspector, "assessment"
    ), "0004：评估索引缺失"

    assert {"totp_secret", "totp_last_step"} <= columns(inspector, "account"), "0005：列缺失"
    assert "login_failure" in inspector.get_table_names(), "0005：login_failure 表缺失"
    assert "ix_login_failure_username_created" in indexes(
        inspector, "login_failure"
    ), "0005：限流索引缺失"


def assert_migrations_reverted(inspector):
    assert "data_origin" not in columns(inspector, "patient"), "0002 未回退"
    assert "media_asset" not in inspector.get_table_names(), "0003 未回退"
    assert "ix_audit_event_org_created" not in indexes(
        inspector, "audit_event"
    ), "0004 未回退"
    assert "totp_secret" not in columns(inspector, "account"), "0005 未回退"
    assert "login_failure" not in inspector.get_table_names(), "0005 未回退"


def current_version(connection):
    return connection.execute(text("SELECT version_num FROM alembic_version")).scalar_one()


with engine.connect() as connection:
    inspector = inspect(connection)
    existing = set(inspector.get_table_names()) - {"alembic_version"}
    assert not existing, (
        f"目标库非空，拒绝运行（会降级删除对象）：{sorted(existing)}"
    )

try:
    # 1) 0001 → 0005：真实 DDL。
    command.upgrade(config, "head")
    with engine.connect() as connection:
        inspector = inspect(connection)
        assert current_version(connection) == HEAD, f"版本应为 {HEAD}"
        assert set(inspector.get_table_names()) == set(Base.metadata.tables) | {
            "alembic_version"
        }
        assert_migrations_applied(inspector)
        # 真实 PostgreSQL 上的模型差异比 SQLite 更能暴露类型/约束漂移。
        assert compare_metadata(MigrationContext.configure(connection), Base.metadata) == [], (
            "模型与数据库存在差异"
        )

    # 2) 数据回读：写入后能读回，且 0005 新增列可用。
    with Session(engine) as session:
        session.add(Org(org_id="org-verify", org_name="迁移验证机构"))
        # 模型之间没有 relationship()，SQLAlchemy 无法自行排序插入；先刷出被引用的行。
        session.flush()
        session.add(
            Account(
                account_id="acc-verify",
                org_id="org-verify",
                display_name="迁移验证账号",
                totp_secret=TOTP_SECRET,
                totp_last_step=42,
            )
        )
        session.add(
            Patient(
                patient_id="pat-verify",
                org_id="org-verify",
                display_name="迁移验证患者",
                birth_year=1948,
                hy_stage=2,
            )
        )
        session.commit()

    with Session(engine) as session:
        patient = session.get(Patient, "pat-verify")
        assert patient is not None and patient.display_name == "迁移验证患者"
        account = session.get(Account, "acc-verify")
        assert account is not None and account.totp_secret == TOTP_SECRET
        assert account.totp_last_step == 42

    # 3) 0005 → 0001：逐级降级在真实 PostgreSQL 上必须可执行。
    command.downgrade(config, "0001")
    with engine.connect() as connection:
        inspector = inspect(connection)
        assert current_version(connection) == "0001"
        assert_migrations_reverted(inspector)
        # 0002 只删列，既有的患者行必须原样保留。
        # 此刻 ORM 模型按 head 定义、含已删除的 data_origin 列，无法用于查询，故用显式 SQL。
        assert (
            connection.execute(
                text("SELECT display_name FROM patient WHERE patient_id = 'pat-verify'")
            ).scalar_one()
            == "迁移验证患者"
        ), "降级丢失了既有患者行"

    # 4) 再升级：既有数据保留，新增列按 server_default 回填。
    command.upgrade(config, "head")
    with engine.connect() as connection:
        assert current_version(connection) == HEAD
        inspector = inspect(connection)
        assert_migrations_applied(inspector)
        with Session(engine) as session:
            patient = session.get(Patient, "pat-verify")
            assert patient is not None, "再次升级后患者行丢失"
            assert patient.data_origin == "real", "0002 的 server_default 未回填既有行"

    # 5) 全量往返：downgrade base 后重建，证明迁移可从零重复执行。
    command.downgrade(config, "base")
    with engine.connect() as connection:
        assert inspect(connection).get_table_names() == ["alembic_version"]
    command.upgrade(config, "head")
    with engine.connect() as connection:
        inspector = inspect(connection)
        assert set(inspector.get_table_names()) == set(Base.metadata.tables) | {
            "alembic_version"
        }
        assert current_version(connection) == HEAD
finally:
    engine.dispose()

print(
    f"PASS: PostgreSQL {HEAD} 升级/降级/再升级；{len(Base.metadata.tables)} 张表；"
    "0002–0005 结构与数据回读；模型差异为空"
)
