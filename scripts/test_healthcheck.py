"""健康检查脚本的退出码语义：探针的全部价值就是这个退出码。"""

import http.server
import importlib.util
import threading
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load_healthcheck():
    spec = importlib.util.spec_from_file_location(
        "healthcheck", ROOT / "deploy/healthcheck.py"
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class _Handler(http.server.BaseHTTPRequestHandler):
    status = 200

    def do_GET(self):  # noqa: N802 - BaseHTTPRequestHandler 的约定
        self.send_response(type(self).status)
        self.end_headers()
        self.wfile.write(b'{"status":"ok"}')

    def log_message(self, *args):
        pass


class ProbeTests(unittest.TestCase):
    def serve(self, status):
        handler = type("H", (_Handler,), {"status": status})
        server = http.server.HTTPServer(("127.0.0.1", 0), handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()

        def stop():
            # 先让 serve_forever 退出再关 socket；反过来关会在 select 里抛 WinError 10038。
            server.shutdown()
            thread.join(timeout=5)
            server.server_close()

        self.addCleanup(stop)
        return f"http://127.0.0.1:{server.server_address[1]}/readyz"

    def test_returns_zero_when_service_is_ready(self):
        self.assertEqual(0, load_healthcheck().probe(self.serve(200)))

    def test_returns_nonzero_when_service_is_not_ready(self):
        # 503 是「还没就绪」的否定结果，必须让容器被判为 unhealthy，
        # 而不是当成脚本出错或干脆返回成功。
        self.assertEqual(1, load_healthcheck().probe(self.serve(503)))

    def test_returns_nonzero_when_unreachable(self):
        # 端口 1 上没人监听：连接被拒绝应当映射成非零，而不是抛异常出栈。
        self.assertEqual(
            1, load_healthcheck().probe("http://127.0.0.1:1/readyz", timeout=1)
        )


if __name__ == "__main__":
    unittest.main()
