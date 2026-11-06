from collections.abc import Iterator

from sqlalchemy import create_engine, event
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.core.config import Settings, get_settings

settings = get_settings()


def engine_options(database_url: str, config: Settings | None = None) -> dict:
    """create_engine 的参数。

    PostgreSQL 显式配置连接池：默认值（pool_size=5、max_overflow=10、pool_timeout=30）会让
    并发超过 15 的请求排队到 30 秒后才失败，见 docs/deployment/README.md 的性能基线。
    SQLite 保持默认，避免影响本地开发与单测的既有行为。
    """
    settings_for_pool = config or settings
    if database_url.startswith("sqlite"):
        return {"connect_args": {"check_same_thread": False}}
    return {
        "pool_size": settings_for_pool.db_pool_size,
        "max_overflow": settings_for_pool.db_max_overflow,
        "pool_timeout": settings_for_pool.db_pool_timeout_seconds,
        "pool_pre_ping": settings_for_pool.db_pool_pre_ping,
        "pool_recycle": settings_for_pool.db_pool_recycle_seconds,
    }


engine = create_engine(settings.database_url, future=True, **engine_options(settings.database_url))
SessionLocal = sessionmaker(
    bind=engine,
    class_=Session,
    autoflush=False,
    expire_on_commit=False,
)


class Base(DeclarativeBase):
    pass


if settings.database_url.startswith("sqlite"):

    @event.listens_for(engine, "connect")
    def _enable_sqlite_foreign_keys(dbapi_connection, connection_record):  # noqa: ANN001
        cursor = dbapi_connection.cursor()
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()


def get_db() -> Iterator[Session]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def init_db() -> None:
    # 导入 models 以注册全部表元数据。
    from app import models  # noqa: F401

    Base.metadata.create_all(bind=engine)
