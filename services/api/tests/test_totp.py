from app.core import totp


def test_verify_accepts_current_and_adjacent_steps():
    secret = totp.generate_secret()
    code = totp.generate_code(secret)
    step = totp.current_step()
    assert totp.verify(secret, code) == step

    # 时钟偏差 ±1 步仍可接受。
    previous = totp.generate_code(secret, at=(step - 1) * totp.STEP_SECONDS)
    assert totp.verify(secret, previous) == step - 1


def test_verify_rejects_replay_old_steps_and_malformed_codes():
    secret = totp.generate_secret()
    step = totp.current_step()
    code = totp.generate_code(secret)
    assert totp.verify(secret, code, last_step=step) is None
    assert totp.verify(secret, "12345") is None
    assert totp.verify(secret, "abcdef") is None
    assert totp.verify("not-base32!!", code) is None


def test_provisioning_uri_contains_secret_and_issuer():
    secret = totp.generate_secret()
    uri = totp.provisioning_uri(secret, "admin-a")
    assert uri.startswith("otpauth://totp/")
    assert f"secret={secret}" in uri
    assert "issuer=rehab-trainer" in uri