"""真实 HTTP 联调。用服务端 venv 执行；默认启动独立数据库上的本地服务。"""

import argparse
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timedelta, timezone
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import ProxyHandler, Request, build_opener


ROOT = Path(__file__).resolve().parents[1]
API_DIR = ROOT / "services/api"

LOCAL_HOSTS = {"127.0.0.1", "localhost", "::1"}

# app.seed 固定写入的照护管理员；账号端点仅 care_admin 可用。
SEED_ADMIN_USERNAME = "admin"
SEED_ADMIN_PASSWORD = "admin123"


def opener_for(url):
    """本机地址必须绕开 HTTP_PROXY，否则请求会打到代理上得到 502。"""
    host = urlparse(url).hostname or ""
    return build_opener(ProxyHandler({})) if host in LOCAL_HOSTS else build_opener()


def run_smoke(base_url, username, password, binding_token):
    covered = set()
    token = None
    opener = opener_for(base_url)

    def request(method, path, payload=None, status=200, check=None, headers=None):
        operation = method + " " + re.sub(r"\?.*$", "", path)
        operation = re.sub(r"/patients/[^/]+", "/patients/{patientId}", operation)
        operation = re.sub(r"/prescriptions/[^/]+", "/prescriptions/{prescriptionId}", operation)
        operation = re.sub(r"/exercises/[^/]+", "/exercises/{code}", operation)
        operation = re.sub(r"/alerts/[^/]+", "/alerts/{alertId}", operation)
        operation = re.sub(r"/media-assets/[^/]+", "/media-assets/{assetId}", operation)
        operation = re.sub(r"/accounts/[^/]+", "/accounts/{accountId}", operation)
        request_headers = {"Content-Type": "application/json"}
        if token:
            request_headers["Authorization"] = f"Bearer {token}"
        request_headers.update(headers or {})
        req = Request(base_url + path, method=method, headers=request_headers,
                      data=None if payload is None else json.dumps(payload).encode())
        actual_status = None
        try:
            try:
                response = opener.open(req, timeout=10)
            except HTTPError as error:
                response = error
            with response:
                actual_status = response.status
                body = json.load(response)
                if actual_status != status:
                    raise AssertionError(f"expected HTTP {status}, got {actual_status}")
                if check is not None and not check(body):
                    raise AssertionError("关键字段断言失败")
            covered.add(operation)
            print(f"PASS {operation} HTTP {actual_status} status_and_fields", flush=True)
            return body
        except (AssertionError, ValueError, KeyError, TypeError, URLError, TimeoutError) as error:
            print(f"FAIL {operation} HTTP {actual_status}: {error}", flush=True)
            raise

    def upload(path, filename, file_bytes, fields, status=200, check=None):
        """multipart/form-data 上传；只用于媒体上传端点。"""
        operation = "POST " + path
        boundary = "----smoke" + secrets.token_hex(8)
        chunks = []
        for name, value in fields.items():
            chunks.append(
                f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode()
            )
        chunks.append(
            f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
            f"Content-Type: video/mp4\r\n\r\n".encode() + file_bytes + b"\r\n"
        )
        chunks.append(f"--{boundary}--\r\n".encode())
        headers = {"Content-Type": f"multipart/form-data; boundary={boundary}"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        req = Request(base_url + path, method="POST", headers=headers, data=b"".join(chunks))
        actual_status = None
        try:
            try:
                response = opener.open(req, timeout=60)
            except HTTPError as error:
                response = error
            with response:
                actual_status = response.status
                body = json.load(response)
                if actual_status != status:
                    raise AssertionError(f"expected HTTP {status}, got {actual_status}")
                if check is not None and not check(body):
                    raise AssertionError("关键字段断言失败")
            covered.add(operation)
            print(f"PASS {operation} HTTP {actual_status} status_and_fields", flush=True)
            return body
        except (AssertionError, ValueError, KeyError, TypeError, URLError, TimeoutError) as error:
            print(f"FAIL {operation} HTTP {actual_status}: {error}", flush=True)
            raise

    def valid_tokens(body):
        return (isinstance(body.get("accessToken"), str) and bool(body["accessToken"])
                and isinstance(body.get("refreshToken"), str) and bool(body["refreshToken"])
                and body.get("expiresIn", 0) > 0)

    bound = request("POST", "/auth/bind", {"bindingToken": binding_token}, check=valid_tokens)
    token = bound["accessToken"]
    request("GET", "/me", check=lambda b: "patient" in b["roles"] and b["orgId"] == "org-demo")
    request("POST", "/auth/bind", {"bindingToken": binding_token}, status=409,
            check=lambda b: b["code"] == "CONFLICT")
    logged_in = request("POST", "/auth/login", {"username": username, "password": password},
                        check=lambda b: valid_tokens(b) and "rehab_therapist" in b["profile"]["roles"])
    refreshed = request("POST", "/auth/refresh", {"refreshToken": logged_in["refreshToken"]},
                        check=valid_tokens)
    token = refreshed["accessToken"]
    request("GET", "/me", check=lambda b: b["accountId"] == "therapist-demo")

    # 二次验证自助启用：密钥先返回、确认后才真正生效；停用需要口令 + 验证码。
    # 脚本用服务端 venv 执行，直接复用服务端的 TOTP 实现算码，不另写一份。
    from app.core import totp as server_totp

    request("GET", "/auth/totp", check=lambda b: b["totpEnabled"] is False)
    # enroll 必须再校验登录口令；confirm 只认本次签发的 enrollmentToken。
    enrollment = request("POST", "/auth/totp/enroll", {"password": password},
                         check=lambda b: len(b["secret"]) >= 16
                         and b["provisioningUri"].startswith("otpauth://totp/")
                         and len(b["enrollmentToken"]) >= 16)
    request("GET", "/auth/totp", check=lambda b: b["totpEnabled"] is False)  # 确认前不生效
    request("POST", "/auth/totp/confirm",
            {"enrollmentToken": enrollment["enrollmentToken"],
             "code": server_totp.generate_code(enrollment["secret"])},
            check=lambda b: b["totpEnabled"] is True)
    # confirm 已消费当前时间步；停用必须用下一步的码。
    next_step_at = (server_totp.current_step() + 1) * server_totp.STEP_SECONDS
    request("POST", "/auth/totp/disable",
            {"password": password,
             "code": server_totp.generate_code(enrollment["secret"], at=next_step_at)},
            check=lambda b: b["totpEnabled"] is False)
    patient = request("POST", "/patients", {"displayName": "联调患者", "birthYear": 1950}, status=201,
                      check=lambda b: bool(b["id"]) and b["stageConfirmationStatus"] == "suspected")
    patient_id = patient["id"]
    listing = request("GET", "/patients?limit=100", check=lambda b: isinstance(b["items"], list))
    while not any(p["id"] == patient_id for p in listing["items"]):
        if not listing.get("nextCursor"):
            raise AssertionError("新增患者未出现在分页列表")
        listing = request("GET", "/patients?limit=100&cursor=" + listing["nextCursor"],
                          check=lambda b: isinstance(b["items"], list))
    now = datetime.now(timezone.utc)
    timestamp = now.isoformat()
    request("POST", f"/patients/{patient_id}/stage-confirmations",
            {"stage": 3, "confirmedAt": timestamp}, status=201,
            check=lambda b: b["patientId"] == patient_id and b["status"] == "confirmed" and b["stage"] == 3)
    request("GET", f"/patients/{patient_id}",
            check=lambda b: b["id"] == patient_id and b["hyStage"] == 3 and b["stageConfirmationStatus"] == "confirmed")
    request("POST", f"/patients/{patient_id}/assessments",
            {"instrument": "STEADI", "score": 1, "answers": {"item1": 1}, "assessedAt": timestamp}, status=201,
            check=lambda b: bool(b["id"]) and b["patientId"] == patient_id and b["score"] == 1)
    request("GET", f"/patients/{patient_id}/assessments",
            check=lambda b: len(b["items"]) == 1 and b["items"][0]["instrument"] == "STEADI"
            and b["items"][0]["answers"]["item1"] == 1)
    request("GET", "/exercises", check=lambda b: any(e["code"] == "ex-stretch-01" for e in b["items"]))
    request("GET", "/exercises/ex-stretch-01", check=lambda b: b["code"] == "ex-stretch-01" and bool(b["label"]))
    prescription = request("POST", "/prescriptions", {
        "patientId": patient_id, "items": [{"exerciseCode": "ex-stretch-01"}],
        "issuedAt": timestamp, "validFrom": timestamp,
        "validUntil": (now + timedelta(days=30)).isoformat(), "mappingTableVersion": "smoke.1",
    }, status=201, check=lambda b: bool(b["id"]) and b["patientId"] == patient_id and b["version"] == "1.0.0")
    prescription_id = prescription["id"]
    request("PATCH", f"/prescriptions/{prescription_id}", {"note": "联调调整"},
            headers={"If-Match": prescription["version"]},
            check=lambda b: b["id"] == prescription_id and b["version"] == "1.0.1")
    request("GET", f"/prescriptions?patientId={patient_id}",
            check=lambda b: b["items"][0]["id"] == prescription_id and b["items"][0]["version"] == "1.0.1")
    record = {
        "clientRecordId": "smoke-" + secrets.token_hex(12), "patientId": patient_id,
        "prescriptionId": prescription_id, "recordedAt": timestamp,
        "checkedIn": True, "completionCount": 12, "validFrameRatio": 0.92,
        "modelVersion": "smoke.1", "modelSha256": "a" * 64,
        "metricAlgorithmVersion": "smoke.1", "thresholdConfigVersion": "smoke.1",
    }
    batch = {"records": [record, record]}
    uploaded = request("POST", "/sessions", batch,
                       check=lambda b: len(b["results"]) == 2 and all(r["accepted"] and r["recordId"] for r in b["results"]))
    record_id = uploaded["results"][0]["recordId"]
    if uploaded["results"][1]["recordId"] != record_id:
        raise AssertionError("批内重复未去重")
    request("POST", "/sessions", batch,
            check=lambda b: len(b["results"]) == 2 and all(r["accepted"] and r["recordId"] == record_id for r in b["results"]))
    request("GET", f"/sessions?patientId={patient_id}",
            check=lambda b: len(b["items"]) == 1 and b["items"][0]["id"] == record_id and b["items"][0]["completionCount"] == 12)
    # shortcut: 后端尚无自动预警生成，使用种子预警；规则引擎落地后改为训练数据触发。
    request("GET", "/alerts?patientId=patient-demo",
            check=lambda b: any(a["id"] == "alert-demo" for a in b["items"]))
    request("PATCH", "/alerts/alert-demo", {"status": "acknowledged", "note": "联调确认"},
            check=lambda b: b["id"] == "alert-demo" and b["status"] == "acknowledged")
    request("GET", "/alerts?patientId=patient-demo",
            check=lambda b: any(a["id"] == "alert-demo" and a["status"] == "acknowledged" for a in b["items"]))

    # 审计：本机构事件可按动作筛选，处置人来自登录账号。
    request("GET", "/audit-events?action=alert.updated",
            check=lambda b: any(item["action"] == "alert.updated" and item["actorAccountId"] for item in b["items"]))

    # 机构账号：照护管理员开通、改名；响应不得回显口令或哈希。
    admin_login = request("POST", "/auth/login",
                          {"username": SEED_ADMIN_USERNAME, "password": SEED_ADMIN_PASSWORD},
                          check=lambda b: valid_tokens(b) and "care_admin" in b["profile"]["roles"])
    admin_headers = {"Authorization": f"Bearer {admin_login['accessToken']}"}
    new_username = "smoke-account-" + secrets.token_hex(4)
    created_account = request(
        "POST", "/accounts",
        {"displayName": "联调同事", "username": new_username,
         "password": "smoke-pass-123", "role": "rehab_therapist"},
        status=201, headers=admin_headers,
        check=lambda b: bool(b["accountId"]) and b["status"] == "active"
        and b["roles"] == ["rehab_therapist"]
        and "password" not in b and "passwordHash" not in b,
    )
    account_id = created_account["accountId"]
    request("GET", "/accounts?limit=100", headers=admin_headers,
            check=lambda b: any(a["accountId"] == account_id for a in b["items"]))
    request("PATCH", f"/accounts/{account_id}", {"displayName": "联调同事改名"},
            headers=admin_headers,
            check=lambda b: b["accountId"] == account_id and b["displayName"] == "联调同事改名")

    # 科研聚合端点：只返回聚合值，demo 数据被硬过滤，患者数应为 0。
    request("GET", "/research/overview",
            check=lambda b: b["dataOrigin"] == "real" and b["minCellCount"] >= 5
            and "patientCount" in b)
    request("GET", "/research/data-quality",
            check=lambda b: b["dataOrigin"] == "real" and "sessionRecordCount" in b)

    # 媒体资产：上传 → 列出 → 上架。上传的是随包演示片段，仅本地联调使用。
    sample = ROOT / "apps/android/app/src/main/res/raw/demo_exercise.mp4"
    if sample.exists():
        asset = upload(
            "/media-assets/upload", "smoke.mp4", sample.read_bytes(),
            {"exerciseCode": "ex-stretch-01", "applicableStages": "2,3",
             "watermarkText": "联调水印", "creditLine": "联调素材"},
            status=201,
            check=lambda b: b["status"] == "draft" and b["applicableStages"] == [2, 3]
            and len(b["sha256"]) == 64,
        )
        asset_id = asset["id"]
        request("GET", "/media-assets?exerciseCode=ex-stretch-01",
                check=lambda b: any(a["id"] == asset_id for a in b["items"]))
        request("PATCH", f"/media-assets/{asset_id}", {"status": "published"},
                check=lambda b: b["id"] == asset_id and b["status"] == "published")
    else:
        print("SKIP 媒体上传：找不到演示视频样本", flush=True)

    expected = set()
    path = None
    for line in (ROOT / "packages/api-contract/openapi.yaml").read_text(encoding="utf-8").splitlines():
        if re.fullmatch(r"  /[^:]+:", line):
            path = line.strip()[:-1]
        match = re.fullmatch(r"    (get|post|patch|put|delete):", line)
        if match and path:
            expected.add(match[1].upper() + " " + path)
    if not expected or expected != covered:
        raise AssertionError(f"操作覆盖不匹配 missing={expected - covered}, extra={covered - expected}")
    paths = {operation.split(" ", 1)[1] for operation in covered}
    print(f"PASS {len(paths)} paths; {len(covered)}/{len(expected)} contract operations; "
          "batch and replay idempotency", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True, type=Path,
                        help="本地 JSON 凭证文件，字段为 username、password、bindingToken")
    parser.add_argument("--base-url", help="测试已启动的服务；会新增数据并消耗绑定码，限本地测试环境")
    parser.add_argument("--port", type=int, default=8000, help="独立本地服务端口")
    args = parser.parse_args()
    config = json.loads(args.config.read_text(encoding="utf-8-sig"))
    if not isinstance(config, dict) or any(
        not isinstance(config.get(key), str) or not config[key].strip()
        for key in ("username", "password", "bindingToken")
    ):
        parser.error("配置必须提供非空字符串 username、password、bindingToken")
    username = config["username"]
    password = config["password"]
    binding_token = config["bindingToken"]
    if args.base_url:
        run_smoke(args.base_url.rstrip("/"), username, password, binding_token)
        return
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", args.port))
    output = ROOT / ".agent/tmp"
    output.mkdir(parents=True, exist_ok=True)
    run_dir = Path(tempfile.mkdtemp(prefix="smoke-api-", dir=output))
    env = {**os.environ, "REHAB_DATABASE_URL": "sqlite:///" + (run_dir / "smoke.db").as_posix(),
           "REHAB_JWT_SECRET": secrets.token_urlsafe(48), "REHAB_AUTO_CREATE_SCHEMA": "true",
           "PYTHONIOENCODING": "utf-8"}
    with (run_dir / "seed.log").open("w", encoding="utf-8") as log:
        subprocess.run([sys.executable, "-m", "app.seed"], cwd=API_DIR, env=env,
                       stdout=log, stderr=subprocess.STDOUT, check=True, timeout=30)
    base_url = f"http://127.0.0.1:{args.port}/v1"
    print(f"Evidence: {run_dir}", flush=True)
    print("PASS seed: python -m app.seed", flush=True)
    print(f"Starting: python -m uvicorn app.main:app --host 127.0.0.1 --port {args.port}",
          flush=True)
    with (run_dir / "server.log").open("w", encoding="utf-8") as log:
        server = subprocess.Popen([sys.executable, "-m", "uvicorn", "app.main:app", "--host",
                                   "127.0.0.1", "--port", str(args.port)], cwd=API_DIR,
                                  env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            for _ in range(100):
                if server.poll() is not None:
                    raise RuntimeError(f"服务启动失败，见 {run_dir / 'server.log'}")
                try:
                    probe_url = base_url.removesuffix("/v1") + "/openapi.json"
                    with opener_for(probe_url).open(probe_url, timeout=1) as response:
                        response.read()
                        break
                except (URLError, TimeoutError):
                    # Windows 上连接未监听端口抛 TimeoutError（OSError 子类），不是 URLError。
                    time.sleep(0.1)
            else:
                raise TimeoutError("服务启动超时")
            if server.poll() is not None:
                raise RuntimeError("本地服务已退出，检查端口冲突")
            print(f"PASS ready: {base_url}; PID {server.pid}", flush=True)
            run_smoke(base_url, username, password, binding_token)
        finally:
            server.terminate()
            try:
                server.wait(timeout=5)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait(timeout=5)


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, KeyError, OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"FAIL 联调中止，后续操作未验证: {error}", file=sys.stderr)
        sys.exit(1)
