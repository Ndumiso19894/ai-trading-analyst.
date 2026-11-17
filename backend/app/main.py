"""AI Trading Analyst — FastAPI application entry point."""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.api.routes import router as api_router

app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="Free-first AI-assisted trading analysis platform (XAU/USD first).",
)

app.include_router(api_router)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[o.strip() for o in settings.cors_origins.split(",") if o.strip()],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health", tags=["system"])
def health() -> dict:
    return {"status": "ok", "app": settings.app_name, "version": settings.app_version}


@app.get("/", tags=["system"])
def root() -> dict:
    return {
        "message": "AI Trading Analyst API",
        "docs": "/docs",
        "health": "/health",
        "status": "Phase 1 foundation — market data & analysis engines in development",
    }
