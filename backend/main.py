"""Top-level ASGI entrypoint for Enzonic Deploy's FastAPI detector.

The application and route definitions remain in ``app.main``; this module exposes the same
FastAPI instance at the backend root so monorepo deployment scanners can find it.
"""
from app.main import app

__all__ = ["app"]
