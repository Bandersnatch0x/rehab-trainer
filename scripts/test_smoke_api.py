"""验证联调命令的输入和断网退出；成功链路由真实服务冒烟覆盖。"""

import json
import os
from pathlib import Path
import socket
import socketserver
import subprocess
import sys
import tempfile
import threading
import unittest


class _CountingProxy(socketserver.BaseRequestHandler):
    """只统计连接数的假代理：收到连接就记一笔，然后关闭。"""

    def handle(self):
        self.server.hits += 1


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/smoke-api.py"


class SmokeCommandTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        (ROOT / ".agent/tmp").mkdir(parents=True, exist_ok=True)

    def test_invalid_config_is_rejected_before_connecting(self):
        with tempfile.TemporaryDirectory(dir=ROOT / ".agent/tmp") as directory:
            config = Path(directory) / "config.json"
            for payload in ({}, [], {"username": "", "password": "unused", "bindingToken": "unused"}):
                with self.subTest(payload=payload):
                    config.write_text(json.dumps(payload), encoding="utf-8")
                    result = subprocess.run(
                        [sys.executable, "-X", "utf8", str(SCRIPT), "--config", str(config),
                         "--base-url", "http://127.0.0.1:1/v1"],
                        capture_output=True, text=True, encoding="utf-8", timeout=10,
                    )
                    self.assertEqual(result.returncode, 2)
                    self.assertIn("配置必须提供非空字符串", result.stderr)
                    self.assertNotIn("Starting:", result.stdout)

    def test_local_requests_bypass_proxy_and_fail_honestly(self):
        with tempfile.TemporaryDirectory(dir=ROOT / ".agent/tmp") as directory:
            # 目标端口：绑定后立刻关闭，保证没有服务在听。
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", 0))
                target_port = probe.getsockname()[1]

            config = Path(directory) / "config.json"
            credentials = {
                "username": "unused", "password": "not-a-real-secret",
                "bindingToken": "not-a-real-binding-token",
            }
            config.write_text(json.dumps(credentials), encoding="utf-8-sig")

            # 一个会记录连接数的假代理。如果脚本没有对本机地址绕开代理，
            # 请求就会落到这里，hits 不为 0。
            with socketserver.TCPServer(("127.0.0.1", 0), _CountingProxy) as proxy:
                proxy.hits = 0
                thread = threading.Thread(target=proxy.serve_forever, daemon=True)
                thread.start()
                env = {
                    **os.environ,
                    "HTTP_PROXY": f"http://127.0.0.1:{proxy.server_address[1]}",
                    "http_proxy": f"http://127.0.0.1:{proxy.server_address[1]}",
                    "NO_PROXY": "",
                    "no_proxy": "",
                }
                try:
                    result = subprocess.run(
                        [sys.executable, "-X", "utf8", str(SCRIPT), "--config", str(config),
                         "--base-url", f"http://127.0.0.1:{target_port}/v1"],
                        capture_output=True, text=True, encoding="utf-8", timeout=15, env=env,
                    )
                finally:
                    proxy.shutdown()
                    thread.join(timeout=5)

            self.assertEqual(result.returncode, 1)
            self.assertTrue(
                result.stdout.startswith("FAIL POST /auth/bind"),
                result.stdout,
            )
            self.assertIn("后续操作未验证", result.stderr)
            self.assertNotIn("PASS", result.stdout)
            self.assertNotIn(credentials["password"], result.stdout + result.stderr)
            self.assertNotIn(credentials["bindingToken"], result.stdout + result.stderr)
            # 关键断言：本机地址没有经过代理。
            self.assertEqual(proxy.hits, 0, "本机请求不应走代理")


if __name__ == "__main__":
    unittest.main()
