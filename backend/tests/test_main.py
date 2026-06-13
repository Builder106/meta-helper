# Alias the import: pytest.ini sets `python_classes = Test*`, so a bare
# `TestClient` symbol gets picked up as a test class and errors out on its
# __init__ ("cannot collect test class 'TestClient'") — which is what put
# tests/test_main.py::TestClient in the lastfailed cache.
from fastapi.testclient import TestClient as APIClient
from app.main import app

client = APIClient(app)

def test_read_root():
    response = client.get("/")
    assert response.status_code == 200
    assert response.json() == {"message": "MetaHelper API is running"}

def test_process_image_endpoint_exists():
    # We just check if the endpoint is reachable (returns 422 if no file provided)
    response = client.post("/process-image")
    assert response.status_code == 422

