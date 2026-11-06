"""为内部账号启用或关闭 TOTP 二次验证。

用法（仓库根目录，使用服务端 venv）：
    services/api/.venv/Scripts/python.exe scripts/set-totp.py --username admin-a
    services/api/.venv/Scripts/python.exe scripts/set-totp.py --username admin-a --clear

启用后该账号登录必须提交认证器生成的 6 位验证码；同一时间步不可重放。
数据库由 REHAB_DATABASE_URL 指定，默认与服务端一致。
"""

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "services/api"))

from app.core import totp  # noqa: E402
from app.core.database import SessionLocal  # noqa: E402
from app.models.identity import Account  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--username", required=True, help="内部账号用户名")
    parser.add_argument("--issuer", default="rehab-trainer", help="认证器显示的发证方")
    parser.add_argument("--clear", action="store_true", help="关闭该账号的二次验证")
    args = parser.parse_args()

    with SessionLocal() as db:
        account = (
            db.query(Account).filter(Account.username == args.username).one_or_none()
        )
        if account is None:
            print(f"账号不存在：{args.username}", file=sys.stderr)
            return 1

        if args.clear:
            account.totp_secret = None
            account.totp_last_step = None
            db.commit()
            print(f"已关闭 {args.username} 的二次验证。")
            return 0

        secret = totp.generate_secret()
        account.totp_secret = secret
        account.totp_last_step = None
        db.commit()

        print(f"已为 {args.username} 启用二次验证。")
        print("密钥（请只交给账号本人）：")
        print(f"  {secret}")
        print("认证器录入 URI：")
        print(f"  {totp.provisioning_uri(secret, args.username, args.issuer)}")
        print("登录时需在「二次验证码」中填入认证器生成的 6 位数字。")
        return 0


if __name__ == "__main__":
    sys.exit(main())