"""验证压测脚本自身的输入校验与统计口径；真实负载由人工按需跑。"""

import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/loadtest-api.py"


def load_module():
    spec = importlib.util.spec_from_file_location("loadtest_api", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


loadtest = load_module()


class PercentileTests(unittest.TestCase):
    def test_empty_returns_zero(self):
        self.assertEqual(0.0, loadtest.percentile([], 0.95))

    def test_nearest_rank_picks_an_observed_sample(self):
        # 10 个样本：p50 = 第 5 个，p95 = 第 10 个，p99 不越过最大值。
        samples = [float(value) for value in range(1, 11)]
        self.assertEqual(5.0, loadtest.percentile(samples, 0.50))
        self.assertEqual(10.0, loadtest.percentile(samples, 0.95))
        self.assertEqual(10.0, loadtest.percentile(samples, 0.99))

    def test_single_sample_is_returned_for_every_fraction(self):
        self.assertEqual(7.0, loadtest.percentile([7.0], 0.01))
        self.assertEqual(7.0, loadtest.percentile([7.0], 1.0))


class CommandTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(dir=(ROOT / ".agent/tmp"))
        self.config = Path(self.directory.name) / "config.json"
        self.config.write_text(
            json.dumps({"username": "unused", "password": "unused", "bindingToken": "unused"}),
            encoding="utf-8",
        )

    def tearDown(self):
        self.directory.cleanup()

    def run_cli(self, *extra):
        return subprocess.run(
            [sys.executable, "-X", "utf8", str(SCRIPT), "--config", str(self.config),
             "--base-url", "http://127.0.0.1:1/v1", *extra],
            capture_output=True, text=True, encoding="utf-8", timeout=30,
        )

    def test_unknown_path_is_rejected_before_connecting(self):
        # 端口 1 上没有服务；非法路径必须在发起任何请求前就以退出码 2 结束。
        result = self.run_cli("--mix", "DELETE /patients")
        self.assertEqual(2, result.returncode)
        self.assertIn("不支持的路径", result.stderr)
        self.assertNotIn("准备完成", result.stdout)

    def test_missing_config_argument_is_an_usage_error(self):
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--base-url", "http://127.0.0.1:1/v1"],
            capture_output=True, text=True, encoding="utf-8", timeout=30,
        )
        self.assertEqual(2, result.returncode)

    def test_connection_failure_is_not_silently_successful(self):
        result = self.run_cli()
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("合计", result.stdout)

    def test_recorded_errors_force_a_nonzero_exit(self):
        # 统计里出现错误时不得返回 0，否则一次失败的压测会被当成基线。
        clean = loadtest.Stats()
        clean.record("GET /patients", 0.01)
        self.assertEqual(0, loadtest.exit_code(clean, clean.errors))

        failing = loadtest.Stats()
        failing.record("GET /patients", 0.01, "TimeoutError")
        self.assertEqual(1, loadtest.exit_code(failing, failing.errors))

        aborted = loadtest.Stats()
        aborted.worker_aborted = 2
        self.assertEqual(1, loadtest.exit_code(aborted, aborted.errors))


if __name__ == "__main__":
    unittest.main()
