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


def _parameters(spec: dict, op: dict) -> list[tuple]:
    """把参数归一化成 (in, name, required, default, minimum, maximum)。

    只比较数值边界与默认值：契约是唯一真源，`limit` 的默认值或上限不一致
    会让生成客户端按错误范围校验。字符串格式与枚举暂不在此断言。
    """
    resolved = []
    for param in op.get("parameters", []):
        if "$ref" in param:
            ref = param["$ref"].split("/")[-1]
            param = spec["components"]["parameters"][ref]
        schema = param.get("schema") or {}
        if "$ref" in schema:
            name = schema["$ref"].split("/")[-1]
            schema = spec["components"]["schemas"][name]
        # FastAPI 对 Optional 参数生成 anyOf[<真实 schema>, null]，边界在非 null 分支。
        if "anyOf" in schema:
            schema = next(
                (part for part in schema["anyOf"] if part.get("type") != "null"),
                {},
            )
        resolved.append(
            (
                param.get("in"),
                param.get("name"),
                bool(param.get("required")),
                schema.get("default"),
                schema.get("minimum"),
                schema.get("maximum"),
            )
        )
    return sorted(resolved)


def _contract_parameters() -> dict[tuple[str, str], list[tuple]]:
    spec = _contract_spec()
    prefix = _contract_prefix()
    out = {}
    for path, item in spec["paths"].items():
        for method, op in item.items():
            if method in HTTP_METHODS:
                out[(prefix + path, method)] = _parameters(spec, op)
    return out


def _app_parameters() -> dict[tuple[str, str], list[tuple]]:
    spec = app.openapi()
    out = {}
    for path, item in spec["paths"].items():
        for method, op in item.items():
            if method in HTTP_METHODS:
                out[(path, method)] = _parameters(spec, op)
    return out


def test_contract_and_app_expose_same_parameters():
    """参数名/位置/必填/默认值与数值边界也要一致。

    改名会让生成客户端 422；默认值或上限漂移会让客户端按错误范围校验，
    而仅比较操作集时看不出来。
    """
    contract = _contract_parameters()
    implemented = _app_parameters()

    mismatches = {
        key: (contract.get(key), implemented.get(key))
        for key in contract.keys() | implemented.keys()
        if contract.get(key) != implemented.get(key)
    }
    assert mismatches == {}, f"参数不一致：{mismatches}"


def _flatten(spec: dict, schema: dict | None, depth: int = 0) -> set[str]:
    """展开 $ref 与 allOf，返回响应对象的顶层字段名集合。"""
    if not schema or depth > 8:
        return set()
    if "$ref" in schema:
        name = schema["$ref"].split("/")[-1]
        return _flatten(spec, spec["components"]["schemas"][name], depth + 1)

    props = set(schema.get("properties") or {})
    for sub in schema.get("allOf") or []:
        props |= _flatten(spec, sub, depth + 1)
    return props


def _response_fields(spec: dict, op: dict, code: str) -> set[str] | None:
    response = (op.get("responses") or {}).get(code)
    if response is None:
        return None
    if "$ref" in response:
        name = response["$ref"].split("/")[-1]
        response = spec["components"]["responses"][name]
    schema = (response.get("content") or {}).get("application/json", {}).get("schema")
    if not schema:
        return None
    return _flatten(spec, schema)


def test_success_response_fields_match_contract():
    """成功响应的字段名必须与契约一致：加字段/改名会让生成客户端类型漂移。

    只比较字段名集合，不比较 required 与嵌套类型（FastAPI 与契约对默认值的
    表达不同，required 会误报）。
    """
    contract_spec = _contract_spec()
    app_spec = app.openapi()
    prefix = _contract_prefix()

    mismatches = {}
    for path, item in contract_spec["paths"].items():
        for method, op in item.items():
            if method not in HTTP_METHODS:
                continue
            app_op = app_spec["paths"].get(prefix + path, {}).get(method, {})
            for code in ("200", "201"):
                expected = _response_fields(contract_spec, op, code)
                if expected is None:
                    continue
                actual = _response_fields(app_spec, app_op, code) or set()
                if expected != actual:
                    mismatches[f"{method.upper()} {path} {code}"] = {
                        "contractOnly": sorted(expected - actual),
                        "appOnly": sorted(actual - expected),
                    }

    assert mismatches == {}, f"响应字段与契约不一致：{mismatches}"


def test_contract_and_app_expose_same_operations():
    """契约是唯一真源：实现多一个或少一个操作都要在这里失败。"""
    contract = _contract_operations()
    implemented = _app_operations()

    assert implemented - contract == set(), f"实现有契约未声明的操作：{implemented - contract}"
    assert contract - implemented == set(), f"契约有未实现的操作：{contract - implemented}"
