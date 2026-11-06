"""单实例 API 基线压测：登录、列表读取、训练记录写入三条热点路径。

目的不是给出绝对容量，而是给出一条可重复的基线：同一台机器、同一数据库上改动前后各跑一次，
用于发现数量级回退，并给出「本机单实例大致能承受多少」的量级判断。

只依赖标准库；每个工作线程保持一条 HTTP/1.1 keep-alive 连接，因此结果不含每请求建连开销，
但仍受本机环回、数据库与本进程 GIL 限制。

用法：
    python scripts/loadtest-api.py --config .agent/tmp/smoke-config.json \
        --base-url http://127.0.0.1:8011/v1 --concurrency 8 --seconds 20

前置：目标服务已启动，且 `--config` 指向的账号具有治疗师角色（需要建训练方案与写记录）。
脚本会写入数据（新患者方案与训练记录），只应对测试库运行。
"""

import argparse
import http.client
import json
import re
import secrets
import statistics
import sys
import threading
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[1]


def configure_stdout():
    # Windows 控制台常为 GBK，表格与中文混排时会二次崩溃。
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")


class Api:
    """一条 keep-alive 连接 + 最小请求封装；线程内独占使用。"""

    def __init__(self, host, port, prefix, token=None):
        self.host = host
        self.port = port
        self.prefix = prefix
        self.token = token
        self.conn = http.client.HTTPConnection(host, port, timeout=30)

    def request(self, method, path, payload=None, headers=None):
        body = None if payload is None else json.dumps(payload).encode()
        merged = {"Content-Type": "application/json", "Accept": "application/json"}
        if self.token:
            merged["Authorization"] = f"Bearer {self.token}"
        merged.update(headers or {})
        self.conn.request(method, self.prefix + path, body=body, headers=merged)
        response = self.conn.getresponse()
        raw = response.read()
        status = response.status
        parsed = None
        if raw:
            try:
                parsed = json.loads(raw)
            except ValueError:
                parsed = None
        return status, parsed

    def close(self):
        self.conn.close()


def percentile(sorted_values, fraction):
    """nearest-rank 分位；样本少时也不做插值，避免给出虚假精度。"""
    if not sorted_values:
        return 0.0
    index = min(len(sorted_values) - 1, max(0, int(round(fraction * len(sorted_values))) - 1))
    return sorted_values[index]


def setup(args, config):
    """建一次会话与一次训练方案，之后的压测只做热点路径。"""
    parsed = urlparse(args.base_url)
    prefix = parsed.path.rstrip("/")
    admin = Api(parsed.hostname, parsed.port, prefix)
    status, body = admin.request(
        "POST", "/auth/login",
        {"username": config["username"], "password": config["password"]},
    )
    assert status == 200, f"登录失败 HTTP {status}: {body}"
    admin.token = body["accessToken"]

    status, body = admin.request("POST", "/patients", {"displayName": "压测患者", "birthYear": 1950})
    assert status == 201, f"建患者失败 HTTP {status}: {body}"
    patient_id = body["id"]

    now = datetime.now(timezone.utc)
    status, body = admin.request("POST", "/prescriptions", {
        "patientId": patient_id,
        "items": [{"exerciseCode": "ex-stretch-01"}],
        "issuedAt": now.isoformat(),
        "validFrom": now.isoformat(),
        "validUntil": (now + timedelta(days=30)).isoformat(),
        "mappingTableVersion": "loadtest.1",
    })
    assert status == 201, f"建训练方案失败 HTTP {status}: {body}"
    return prefix, patient_id, body["id"]


class Stats:
    def __init__(self):
        self.lock = threading.Lock()
        self.latencies = {}
        self.errors = {}

    def record(self, operation, seconds, error=None):
        with self.lock:
            self.latencies.setdefault(operation, []).append(seconds)
            if error is not None:
                self.errors.setdefault(operation, []).append(error)

    def snapshot(self):
        with self.lock:
            return (
                {key: list(value) for key, value in self.latencies.items()},
                {key: list(value) for key, value in self.errors.items()},
            )


def worker(args, config, prefix, patient_id, prescription_id, stats, stop_at, mix):
    api = Api(urlparse(args.base_url).hostname, urlparse(args.base_url).port, prefix)
    status, body = api.request(
        "POST", "/auth/login",
        {"username": config["username"], "password": config["password"]},
    )
    if status != 200:
        stats.record("POST /auth/login", 0.0, f"HTTP {status}")
        return
    api.token = body["accessToken"]

    index = 0
    try:
        while time.perf_counter() < stop_at:
            operation = mix[index % len(mix)]
            index += 1
            started = time.perf_counter()
            try:
                if operation == "GET /patients":
                    status, body = api.request("GET", "/patients?limit=20")
                    ok = status == 200 and isinstance(body, dict) and isinstance(body.get("items"), list)
                elif operation == "GET /sessions":
                    status, body = api.request("GET", f"/sessions?patientId={patient_id}&limit=20")
                    ok = status == 200 and isinstance(body, dict) and isinstance(body.get("items"), list)
                else:
                    record = {
                        "clientRecordId": f"load-{secrets.token_hex(12)}",
                        "patientId": patient_id,
                        "prescriptionId": prescription_id,
                        "recordedAt": datetime.now(timezone.utc).isoformat(),
                        "checkedIn": True,
                        "completionCount": 12,
                        "movementErrorCount": 1,
                        "interruptionCount": 0,
                        "validFrameRatio": 0.92,
                        "modelVersion": "loadtest.1",
                        "modelSha256": "a" * 64,
                        "metricAlgorithmVersion": "loadtest.1",
                        "thresholdConfigVersion": "loadtest.1",
                    }
                    status, body = api.request("POST", "/sessions", {"records": [record]})
                    ok = (
                        status == 200
                        and isinstance(body, dict)
                        and len(body.get("results", [])) == 1
                        and body["results"][0].get("accepted") is True
                    )
                elapsed = time.perf_counter() - started
                stats.record(operation, elapsed, None if ok else f"HTTP {status}")
            except Exception as error:  # 连接被服务端关闭等：记一次错误并继续，不中断整轮
                stats.record(operation, time.perf_counter() - started, type(error).__name__)
                try:
                    api.conn.close()
                except Exception:
                    pass
                api.conn = http.client.HTTPConnection(
                    urlparse(args.base_url).hostname, urlparse(args.base_url).port, timeout=30
                )
    finally:
        api.close()


def main():
    configure_stdout()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--concurrency", type=int, default=8)
    parser.add_argument("--seconds", type=int, default=20)
    parser.add_argument(
        "--mix",
        default="GET /patients,GET /sessions,POST /sessions",
        help="逗号分隔的轮转路径，默认三条热点各占三分之一",
    )
    args = parser.parse_args()

    config = json.loads(args.config.read_text(encoding="utf-8-sig"))
    mix = [part.strip() for part in args.mix.split(",") if part.strip()]
    for operation in mix:
        if not re.fullmatch(r"(GET|POST) /[a-z-]+", operation):
            parser.error(f"不支持的路径：{operation}")

    prefix, patient_id, prescription_id = setup(args, config)
    print(f"准备完成：患者 {patient_id}，方案 {prescription_id}，并发 {args.concurrency}，"
          f"时长 {args.seconds}s，轮转 {mix}", flush=True)

    stats = Stats()
    stop_at = time.perf_counter() + args.seconds
    threads = [
        threading.Thread(
            target=worker,
            args=(args, config, prefix, patient_id, prescription_id, stats, stop_at, mix),
            daemon=True,
        )
        for _ in range(args.concurrency)
    ]
    started = time.perf_counter()
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    wall = time.perf_counter() - started

    latencies, errors = stats.snapshot()
    print(f"\n{'路径':<16}{'请求':>8}{'错误':>8}{'RPS':>9}{'p50 ms':>10}{'p95 ms':>10}{'p99 ms':>10}{'max ms':>10}")
    total = 0
    for operation in mix:
        samples = sorted(latencies.get(operation, []))
        failures = errors.get(operation, [])
        total += len(samples)
        if not samples:
            print(f"{operation:<16}{0:>8}{len(failures):>8}{'-':>9}{'-':>10}{'-':>10}{'-':>10}{'-':>10}")
            continue
        print(
            f"{operation:<16}{len(samples):>8}{len(failures):>8}{len(samples) / wall:>9.1f}"
            f"{percentile(samples, 0.50) * 1000:>10.1f}"
            f"{percentile(samples, 0.95) * 1000:>10.1f}"
            f"{percentile(samples, 0.99) * 1000:>10.1f}"
            f"{samples[-1] * 1000:>10.1f}"
        )
    print(f"\n合计 {total} 次请求，{wall:.1f}s，{total / wall:.1f} RPS，"
          f"错误 {sum(len(v) for v in errors.values())} 次")
    if any(errors.values()):
        for operation, samples in errors.items():
            kinds = {}
            for sample in samples:
                kinds[sample] = kinds.get(sample, 0) + 1
            print(f"  错误明细 {operation}: {kinds}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
