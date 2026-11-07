"""连接池配置与过载反馈（票 0026）。

背景：`create_engine` 原先未配池，取 SQLAlchemy 默认的 pool_size=5 + max_overflow=10，
并发超过 15 的请求会排队到 pool_timeout（默认 30 秒）后才失败——对调用方等于挂起。
这里覆盖三件事：池参数确实生效、耗尽时快速返回 503、以及业务错误不会被误判成 503。
"""

import pytest
from sqlalchemy import create_engine
from sqlalchemy.exc import OperationalError
from sqlalchemy.exc import TimeoutError as PoolTimeoutError

from app.core.config import Settings
from app.core.database import engine_options
from app.main import app

POSTGRES_URL = "postgresql+psycopg://probe_user:probe_secret@db.internal:5432/rehab"


def test_sqlite_keeps_default_pool():
    # SQLite 走本地单测与开发，显式配池没有意义且会改变既有行为。
    options = engine_options("sqlite:///./x.db")

    assert options == {"connect_args": {"check_same_thread": False}}


def test_postgres_pool_is_configured_explicitly():
    options = engine_options(POSTGRES_URL, Settings())

    assert options["pool_size"] == 5
    assert options["max_overflow"] == 10
    assert options["pool_timeout"] == 5.0
    # 探活与回收：数据库重启或闲置后不会取到已被对端关闭的连接。
    assert options["pool_pre_ping"] is True
    assert options["pool_recycle"] == 1800
    # 建连超时与排队超时是两件事：对端停服但端口仍被转发时，不设它会一直等到系统放弃。
    assert options["connect_args"]["connect_timeout"] == 5


def test_pool_settings_come_from_environment(monkeypatch):
    monkeypatch.setenv("REHAB_DB_POOL_SIZE", "3")
    monkeypatch.setenv("REHAB_DB_MAX_OVERFLOW", "2")
    monkeypatch.setenv("REHAB_DB_POOL_TIMEOUT_SECONDS", "1.5")
    monkeypatch.setenv("REHAB_DB_CONNECT_TIMEOUT_SECONDS", "2")

    options = engine_options(POSTGRES_URL, Settings())

    # 部署形态决定池大小：副本数 ×(pool_size+max_overflow) 必须低于数据库 max_connections。
    assert options["pool_size"] == 3
    assert options["max_overflow"] == 2
    assert options["pool_timeout"] == 1.5
    assert options["connect_args"]["connect_timeout"] == 2


def _add_probe_route(path: str, error: Exception) -> None:
    def probe() -> None:
        raise error

    app.add_api_route(path, probe, methods=["GET"])


@pytest.fixture
def probe_paths():
    """用完即摘，避免测试路由留在全局 app 上。"""
    created: list[str] = []
    yield created
    app.router.routes = [
        route for route in app.router.routes if getattr(route, "path", None) not in created
    ]


def test_pool_exhaustion_returns_503_without_leaking_internals(client, probe_paths):
    path = "/__test__/pool-exhausted"
    probe_paths.append(path)
    _add_probe_route(
        path,
        PoolTimeoutError(
            "QueuePool limit of size 5 overflow 10 reached, connection timed out, "
            "timeout 30.00"
        ),
    )

    response = client.get(path)

    assert response.status_code == 503
    body = response.json()
    assert body["code"] == "SERVICE_UNAVAILABLE"
    # 可重试标记让客户端知道该退避重试，而不是当成业务失败。
    assert body["detail"]["retryable"] is True
    # 连接池内部信息只进日志，不能出现在响应里。
    assert "QueuePool" not in response.text
    assert "size 5" not in response.text


def test_connection_failure_returns_503_without_leaking_connection_string(
    client, probe_paths
):
    path = "/__test__/db-down"
    probe_paths.append(path)
    _add_probe_route(
        path,
        OperationalError(
            "SELECT 1",
            {},
            Exception("connection to server at db.internal, port 5432 failed"),
        ),
    )

    response = client.get(path)

    assert response.status_code == 503
    assert response.json()["code"] == "SERVICE_UNAVAILABLE"
    assert "db.internal" not in response.text
    assert "secret" not in response.text


def test_business_errors_are_not_turned_into_503(client, seed):
    # 对照组：普通业务错误必须保持原状态码，否则 503 会掩盖真实的业务语义。
    response = client.get(f"/v1/patients/{seed['patient_a']}", headers={})

    assert response.status_code == 401
    assert response.json()["code"] == "UNAUTHORIZED"

    missing = client.get(
        "/v1/patients/does-not-exist",
        headers={"Authorization": "Bearer not-a-real-token"},
    )
    assert missing.status_code in {401, 403, 404}


def test_options_are_accepted_by_create_engine():
    # 参数名写错时 create_engine 会直接报错，这里让它在 PostgreSQL 方言上真正构造一次。
    # 不建立连接：create_engine 是惰性的。
    probe = create_engine(POSTGRES_URL, **engine_options(POSTGRES_URL, Settings()))
    try:
        assert probe.pool._pre_ping is True
        assert probe.pool.size() == 5
    finally:
        probe.dispose()
