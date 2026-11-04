"""验证联调命令的输入和断网退出；成功链路由真实服务冒烟覆盖。"""

import json
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import unittest


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

    def test_connection_failure_is_not_reported_as_success(self):
        with tempfile.TemporaryDirectory(dir=ROOT / ".agent/tmp") as directory, socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            config = Path(directory) / "config.json"
            credentials = {
                "username": "unused", "password": "not-a-real-secret",
                "bindingToken": "not-a-real-binding-token",
            }
            config.write_text(json.dumps(credentials), encoding="utf-8-sig")
            result = subprocess.run(
                [sys.executable, "-X", "utf8", str(SCRIPT), "--config", str(config),
                 "--base-url", f"http://127.0.0.1:{listener.getsockname()[1]}/v1"],
                capture_output=True, text=True, encoding="utf-8", timeout=15,
            )
            self.assertEqual(result.returncode, 1)
            self.assertIn("FAIL POST /auth/bind HTTP None", result.stdout)
            self.assertIn("后续操作未验证", result.stderr)
            self.assertNotIn("PASS", result.stdout)
            self.assertNotIn(credentials["password"], result.stdout + result.stderr)
            self.assertNotIn(credentials["bindingToken"], result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
