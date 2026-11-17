"""Application configuration via environment variables."""
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    app_name: str = "AI Trading Analyst"
    app_version: str = "0.3.0"
    debug: bool = False
    host: str = "0.0.0.0"
    port: int = 8000
    database_url: str = "sqlite:///./trading.db"
    cors_origins: str = "*"

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


settings = Settings()
