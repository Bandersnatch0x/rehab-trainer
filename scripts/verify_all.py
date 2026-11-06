#!/usr/bin/env python3
"""串行跑全部本地门禁，失败即停。

顺序：后端 pytest → 契约/部署自检 → 管理端构建 → 管理端 E2E → 小程序 → Android。
任一步失败立即停止并打印失败步骤，避免多步同时报错难以定位。

用法：
    python scripts/verify_all.py                 # 全跑
    python scripts/verify_all.py --skip-e2e      # 提交前快速回路
    python scripts/verify_all.py --skip-android  # 无 SDK 环境
    python scripts/verify_all.py --skip-miniprogram
"""

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API_DIR = ROOT / "services/api"
ADMIN_DIR = ROOT / "apps/admin-web"
MP_DIR = ROOT / "apps/miniprogram"
ANDROID_DIR = ROOT / "apps/android"

# Windows 控制台常为 GBK；失败输出里可能含 › 等字符，避免打印时二次崩溃。
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

# 仓库自带 venv 优先；CI 上通常直接是 python。
VENV_PYTHON = API_DIR / ".venv" / "Scripts" / "python.exe"
PYTHON = VENV_PYTHON if VENV_PYTHON.exists() else Path(sys.executable)

# E2E 端口避开 Windows Hyper-V 保留段（4078–4377 常见被占）。
E2E_ENV = {"E2E_BACKEND_PORT": "18800", "E2E_PREVIEW_PORT": "5100"}


class StepFailed(RuntimeError):
    pass


def run(label, command, cwd, env=None, shell=False):
    print(f"\n=== {label} ===", flush=True)
    print("$ " + " ".join(str(part) for part in command), flush=True)
    merged = {**os.environ, **(env or {})}
    result = subprocess.run(
        command, cwd=cwd, env=merged, shell=shell,
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if result.returncode != 0:
        tail = (result.stdout or "")[-3000:] + (result.stderr or "")[-3000:]
        print(tail, flush=True)
        raise StepFailed(f"{label} 退出码 {result.returncode}")
    print(f"PASS {label}", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-e2e", action="store_true")
    parser.add_argument("--skip-android", action="store_true")
    parser.add_argument("--skip-miniprogram", action="store_true")
    args = parser.parse_args()

    steps = [
        ("backend pytest", [str(PYTHON), "-m", "pytest", "-q", "services/api/tests"], ROOT, None, False),
        ("deploy verify", [str(PYTHON), "deploy/verify.py"], ROOT, None, False),
        ("admin build", ["npx", "tsc", "--noEmit"], ADMIN_DIR, None, True),
    ]
    if not args.skip_e2e:
        steps.append(("admin e2e", ["npx", "playwright", "test"], ADMIN_DIR, E2E_ENV, True))
    if not args.skip_miniprogram:
        steps += [
            ("miniprogram typecheck", ["npx", "--no-install", "tsc", "--noEmit"], MP_DIR, None, True),
            ("miniprogram tests", ["node", "--test", "tests/patient-data.cjs"], MP_DIR, None, False),
            ("miniprogram static", [str(PYTHON), "tests/static_check.py", "."], MP_DIR, None, False),
        ]
    if not args.skip_android:
        # Windows 的 CreateProcess 不解析当前目录里的裸文件名，必须给绝对路径。
        gradlew = ANDROID_DIR / ("gradlew.bat" if os.name == "nt" else "gradlew")
        java_home = os.environ.get("JAVA_HOME", r"E:\jdk\jdk-17.0.11")
        android_home = os.environ.get("ANDROID_HOME", r"D:\Android\Sdk")
        android_env = {
            "JAVA_HOME": java_home,
            "ANDROID_HOME": android_home,
            "ANDROID_SDK_ROOT": android_home,
        }
        steps.append((
            "android build+test",
            [str(gradlew), ":app:testDebugUnitTest", ":app:assembleDebug", "--no-daemon"],
            ANDROID_DIR, android_env, False,
        ))

    for label, command, cwd, env, shell in steps:
        if shell and shutil.which(command[0]) is None:
            print(f"SKIP {label}（找不到 {command[0]}）", flush=True)
            continue
        try:
            run(label, command, cwd, env, shell)
        except StepFailed as error:
            print(f"\nFAILED at: {label}", flush=True)
            print(str(error), flush=True)
            return 1

    print("\nALL PASS", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())