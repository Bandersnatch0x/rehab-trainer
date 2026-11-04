from pathlib import Path
from urllib.parse import urlparse

import yaml

from app.main import app

CONTRACT = Path(__file__).resolve().parents[3] / "packages/api-contract/openapi.yaml"

HTTP_METHODS = {"get", "post", "put", "patch", "delete"}


def _contract_spec() -> dict:
    return yaml.safe_load(CONTRACT.read_text(encoding="utf-8"))


def _prefix() -> str:
    """契约 servers[0].url 可能是绝对地址；取其中的路径前缀（如 /v1）。"""
    url = _contract_spec()["servers"][0]["url"]
    prefix = urlparse(url).path.rstrip("/")
    return "" if prefix in ("", "/") else prefix


def _strip(path: str) -> str:
    prefix = _prefix()
    if prefix and path.startswith(prefix):
        return path[len(prefix) :] or "/"
    return path


def _contract_operations() -> set[tuple[str, str]]:
    spec = _contract_spec()
    return {
        (_strip(path), method)
        for path, item in spec["paths"].items()
        for method in item
        if method in HTTP_METHODS
    }


def _app_operations() -> set[tuple[str, str]]:
    spec = app.openapi()
    return {
        (_strip(path), method)
        for path, item in spec["paths"].items()
        for method in item
        if method in HTTP_METHODS
    }


def test_contract_and_app_expose_same_operations():
    """契约是唯一真源：实现多一个或少一个操作都要在这里失败。"""
    contract = _contract_operations()
    implemented = _app_operations()

    assert implemented - contract == set(), f"实现有契约未声明的操作：{implemented - contract}"
    assert contract - implemented == set(), f"契约有未实现的操作：{contract - implemented}"
