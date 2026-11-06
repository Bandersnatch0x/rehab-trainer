"""从仓库根运行，验证迁移往返、模型一致性及部署配置。"""
import io
import os
from pathlib import Path
import sys
import tempfile

import yaml
from alembic import command
from alembic.autogenerate import compare_metadata
from alembic.config import Config
from alembic.migration import MigrationContext
from sqlalchemy import CheckConstraint, create_engine, inspect

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / "services/api"))
scratch = root / ".agent/tmp"
scratch.mkdir(parents=True, exist_ok=True)

with tempfile.TemporaryDirectory(dir=scratch) as temporary:
    os.environ["REHAB_DATABASE_URL"] = "sqlite:///" + (Path(temporary) / "migration.db").as_posix()
    os.environ["REHAB_AUTO_CREATE_SCHEMA"] = "false"
    from app import models  # noqa: F401
    from app.core.database import Base

    config = Config(str(root / "deploy/alembic.ini"))
    command.upgrade(config, "head")
    command.upgrade(config, "head")
    engine = create_engine(os.environ["REHAB_DATABASE_URL"])
    try:
        with engine.connect() as connection:
            assert set(inspect(connection).get_table_names()) == set(Base.metadata.tables) | {"alembic_version"}
            assert compare_metadata(MigrationContext.configure(connection), Base.metadata) == []
            # 从模型推导期望的检查约束数，避免新增约束后自检静默失效。
            expected_checks = sum(
                1
                for table in Base.metadata.tables.values()
                for constraint in table.constraints
                if isinstance(constraint, CheckConstraint)
            )
            actual_checks = sum(
                len(inspect(connection).get_check_constraints(name))
                for name in Base.metadata.tables
            )
            assert actual_checks == expected_checks, (
                f"检查约束数量不符：数据库 {actual_checks}，模型 {expected_checks}"
            )
        command.downgrade(config, "base")
        assert inspect(engine).get_table_names() == ["alembic_version"]
        command.upgrade(config, "head")
    finally:
        # Windows 下未释放连接会让临时目录删除报 WinError 32，掩盖真实断言失败。
        engine.dispose()

from app.core.config import get_settings

get_settings().database_url = "postgresql+psycopg://user:unused@localhost/unused"
output = io.StringIO()
config.output_buffer = output
command.upgrade(config, "head", sql=True)
assert output.getvalue().count("CREATE TABLE ") == len(Base.metadata.tables) + 1
assert "ck_session_record_valid_frame_ratio" in output.getvalue()
compose = yaml.safe_load((root / "docker-compose.yml").read_text(encoding="utf-8"))
assert set(compose["services"]) == {"api", "postgres"}
assert compose["services"]["api"]["environment"]["REHAB_AUTO_CREATE_SCHEMA"] == "false"
assert compose["services"]["postgres"]["volumes"]
# 不设上限的 json-file 日志会把宿主磁盘写满；两个服务都必须声明轮转。
for name, service in compose["services"].items():
    options = service.get("logging", {}).get("options", {})
    assert service.get("logging", {}).get("driver") == "json-file", f"{name} 未声明日志驱动"
    assert options.get("max-size") and options.get("max-file"), f"{name} 未声明日志轮转上限"
print(f"PASS: {len(Base.metadata.tables)} tables; SQLite upgrade/repeat/downgrade/upgrade; model diff empty; PostgreSQL SQL; YAML")
