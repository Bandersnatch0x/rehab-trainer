from pathlib import Path
from urllib.parse import urlparse

import yaml

from app.main import API_PREFIX, app

CONTRACT = Path(__file__).resolve().parents[3] / "packages/api-contract/openapi.yaml"

HTTP_METHODS = {"get", "post", "put", "patch", "delete"}


def _contract_spec() -> dict:
    return yaml.safe_load(CONTRACT.read_text(encoding="utf-8"))


def _contract_prefix() -> str:
    """契约 servers[0].url 可能是绝对地址；取其中的路径前缀（如 /v1）。"""
    url = _contract_spec()["servers"][0]["url"]
    prefix = urlparse(url).path.rstrip("/")
    return "" if prefix in ("", "/") else prefix


def _contract_operations() -> set[tuple[str, str]]:
    # 契约路径是相对路径；补上前缀后再与实现比较，不做双向剥离。
    prefix = _contract_prefix()
    return {
        (prefix + path, method)
        for path, item in _contract_spec()["paths"].items()
        for method in item
        if method in HTTP_METHODS
    }


def _app_operations() -> set[tuple[str, str]]:
    return {
        (path, method)
        for path, item in app.openapi()["paths"].items()
        for method in item
        if method in HTTP_METHODS
    }


def test_app_prefix_matches_contract_server():
    """实现挂载前缀必须等于契约 servers[0] 的路径前缀。

    否则客户端按契约拼出的 URL 会 404，而只比较操作集时可能仍然全绿。
    """
    assert API_PREFIX == _contract_prefix()


def test_contract_and_app_expose_same_operations():
    """契约是唯一真源：实现多一个或少一个操作都要在这里失败。"""
    contract = _contract_operations()
    implemented = _app_operations()

    assert implemented - contract == set(), f"实现有契约未声明的操作：{implemented - contract}"
    assert contract - implemented == set(), f"契约有未实现的操作：{contract - implemented}"
