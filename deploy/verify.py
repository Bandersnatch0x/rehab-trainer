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
from sqlalchemy import create_engine, inspect

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
    with engine.connect() as connection:
        assert set(inspect(connection).get_table_names()) == set(Base.metadata.tables) | {"alembic_version"}
        assert compare_metadata(MigrationContext.configure(connection), Base.metadata) == []
        assert sum(len(inspect(connection).get_check_constraints(name)) for name in Base.metadata.tables) == 4
    command.downgrade(config, "base")
    assert inspect(engine).get_table_names() == ["alembic_version"]
    command.upgrade(config, "head")
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
print(f"PASS: {len(Base.metadata.tables)} tables; SQLite upgrade/repeat/downgrade/upgrade; model diff empty; PostgreSQL SQL; YAML")
