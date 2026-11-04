import urllib.request

from sqlalchemy import create_engine, text

from app.core.config import get_settings

with urllib.request.urlopen("http://127.0.0.1:8000/openapi.json", timeout=3) as response:
    assert response.status == 200

engine = create_engine(get_settings().database_url)
with engine.connect() as connection:
    assert connection.execute(text("SELECT version_num FROM alembic_version")).scalar()
engine.dispose()
