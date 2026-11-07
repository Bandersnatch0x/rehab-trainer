"""运维探针：存活不依赖数据库，就绪使用应用自己的连接池。

两者的区别是真实故障下的分水岭：数据库挂了应该只让容器"未就绪"（暂停接流量），
而不是让编排反复重启一个本身没问题的进程。
"""

import pytest
from sqlalchemy.exc import OperationalError
from sqlalchemy.exc import TimeoutError as PoolTimeoutError

from app import main


class _FailingEngine:
    """engine.connect() 直接失败的替身；连接串不会出现在任何响应里。"""

    def __init__(self, error: Exception):
        self._error = error

    def connect(self):
        raise self._error


def test_healthz_is_up_without_database(client):
    response = client.get("/healthz")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_readyz_reports_ok_when_database_answers(client):
    response = client.get("/readyz")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_readyz_fails_when_database_is_unreachable(client, monkeypatch):
    monkeypatch.setattr(
        main,
        "engine",
        _FailingEngine(
            OperationalError(
                "SELECT 1",
                {},
                Exception("connection to server at db.internal, port 5432 failed"),
            )
        ),
    )

    response = client.get("/readyz")

    assert response.status_code == 503
    assert response.json() == {"status": "database_unavailable"}
    # 探针可能被匿名访问，错误细节只进日志。
    assert "db.internal" not in response.text


def test_readyz_fails_when_pool_is_exhausted(client, monkeypatch):
    # 池耗尽时另起连接的健康检查会报"健康"，而业务请求全在排队——这正是要区分开的场景。
    monkeypatch.setattr(
        main,
        "engine",
        _FailingEngine(
            PoolTimeoutError("QueuePool limit of size 5 overflow 10 reached")
        ),
    )

    response = client.get("/readyz")

    assert response.status_code == 503
    assert "QueuePool" not in response.text


def test_healthz_stays_up_when_database_is_down(client, monkeypatch):
    monkeypatch.setattr(
        main,
        "engine",
        _FailingEngine(OperationalError("SELECT 1", {}, Exception("down"))),
    )

    # 存活探针不看数据库：否则数据库短暂不可用会触发无意义的重启风暴。
    assert client.get("/healthz").status_code == 200
    assert client.get("/readyz").status_code == 503


def test_probes_are_not_part_of_the_client_contract():
    paths = {route.path for route in main.app.routes if hasattr(route, "path")}

    assert "/healthz" in paths and "/readyz" in paths
    # 运维接口不进 OpenAPI：它们描述进程状态，不是客户端可依赖的业务接口。
    schema = main.app.openapi()
    assert "/healthz" not in schema["paths"]
    assert "/readyz" not in schema["paths"]


@pytest.mark.parametrize("path", ["/healthz", "/readyz"])
def test_probes_need_no_auth(client, path):
    assert client.get(path).status_code in {200, 503}
