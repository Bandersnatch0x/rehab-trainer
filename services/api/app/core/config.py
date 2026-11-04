from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """运行时配置。环境变量统一使用 REHAB_ 前缀。"""

    model_config = SettingsConfigDict(
        env_prefix="REHAB_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    app_name: str = "rehab-trainer-api"
    app_version: str = "0.1.0"

    # 开发期用 SQLite；生产切 PostgreSQL 只需替换 DATABASE_URL，模型不变。
    database_url: str = "sqlite:///./rehab_trainer.db"
    auto_create_schema: bool = True

    jwt_secret: str = "dev-only-secret-change-in-production"
    jwt_algorithm: str = "HS256"
    access_token_ttl_seconds: int = 3600
    refresh_token_ttl_seconds: int = 7 * 24 * 3600

    # 登录限流：窗口内失败达到上限后返回 429。
    login_max_failures: int = 5
    login_window_seconds: int = 300


@lru_cache
def get_settings() -> Settings:
    return Settings()
