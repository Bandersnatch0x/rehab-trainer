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

    # 连接池（仅 PostgreSQL 生效；SQLite 保持默认，避免影响本地与单测）。
    #
    # 并发上限 = 副本数 × (pool_size + max_overflow)，必须低于数据库 max_connections 并留余量。
    # 基线压测见 docs/deployment/README.md：默认值下实测瓶颈正是这里的 15 条连接。
    db_pool_size: int = 5
    db_max_overflow: int = 10
    # 池耗尽时等待上限。宁可快速失败（503）也不要让客户端挂 30 秒：
    # 排队等待期间连接与线程都被占住，对调用方而言与宕机无异。
    db_pool_timeout_seconds: float = 5.0
    # 建立连接的超时（秒）。与 pool_timeout 不同：它约束的是 TCP 握手与认证本身。
    # 对端停服但端口仍被转发时，不设它会一直等到操作系统放弃（Windows 约 20 秒）。
    db_connect_timeout_seconds: int = 5
    # 取连接前探活：数据库重启或主从切换后，不会把已被对端关闭的连接当活的用。
    db_pool_pre_ping: bool = True
    # 主动回收，避开服务端 idle 超时留下的半死连接。
    db_pool_recycle_seconds: int = 1800

    jwt_secret: str = "dev-only-secret-change-in-production"
    jwt_algorithm: str = "HS256"
    access_token_ttl_seconds: int = 3600
    refresh_token_ttl_seconds: int = 7 * 24 * 3600

    # 登录限流：窗口内失败达到上限后返回 429。
    login_max_failures: int = 5
    login_window_seconds: int = 300

    # 媒体静态根：后端把上传文件写到这里，nginx 用同一路径对外托管。
    # 生产部署时两者必须指向同一目录（见 docs/deployment/README.md）。
    media_root: str = "./media"
    # 对外访问前缀；storage_path 拼在其后即为客户端拿到的 videoUri。
    media_public_base_url: str = "http://127.0.0.1:8000/media"
    # 单文件上限，默认 200 MB。
    media_max_upload_bytes: int = 200 * 1024 * 1024


@lru_cache
def get_settings() -> Settings:
    return Settings()
