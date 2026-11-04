from alembic import context
from sqlalchemy import create_engine, pool

from app import models  # noqa: F401
from app.core.config import get_settings
from app.core.database import Base

url = get_settings().database_url

if context.is_offline_mode():
    context.configure(
        url=url, target_metadata=Base.metadata, literal_binds=True,
        dialect_opts={"paramstyle": "named"}, compare_type=True,
    )
    with context.begin_transaction():
        context.run_migrations()
else:
    engine = create_engine(url, poolclass=pool.NullPool)
    with engine.connect() as connection:
        context.configure(
            connection=connection, target_metadata=Base.metadata, compare_type=True,
        )
        with context.begin_transaction():
            context.run_migrations()
    engine.dispose()
