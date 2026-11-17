def test_top_level_entrypoint_exports_existing_fastapi_app():
    import main
    from app.main import app

    assert main.app is app
    assert main.app.title == "AI Trading Analyst"
