import io

from PIL import Image

# Alias the import: pytest.ini sets `python_classes = Test*`, so a bare
# `TestClient` symbol would be collected as a test class and error on __init__
# (see the note in test_main.py).
from fastapi.testclient import TestClient as APIClient
from app import main

client = APIClient(main.app)


def _tiny_png_bytes() -> bytes:
    # Generate a tiny real PNG so the multipart upload carries valid image data.
    buffer = io.BytesIO()
    Image.new("RGB", (4, 4), color=(120, 200, 80)).save(buffer, format="PNG")
    return buffer.getvalue()


def test_process_image_happy_path(monkeypatch):
    # Monkeypatch the module-level service objects so no network / API key is
    # needed: vision returns a fixed string, TTS is an async stub returning
    # MP3-ish bytes, and amplitude scaling is a passthrough returning bytes.
    monkeypatch.setattr(
        main.vision_service,
        "get_description",
        lambda image_bytes: "for-loop reading: for open-parenthesis int i equals zero.",
    )

    async def fake_tts(text):
        # ID3 header makes this look like a real MP3 payload.
        return b"ID3\x04\x00fake-mp3-audio-bytes"

    monkeypatch.setattr(main.tts_service, "text_to_speech", fake_tts)
    monkeypatch.setattr(
        main.audio_processor,
        "scale_amplitude",
        lambda audio_bytes, multiplier=0.1: b"ID3\x04\x00scaled-mp3-audio-bytes",
    )

    response = client.post(
        "/process-image",
        files={"file": ("problem.png", _tiny_png_bytes(), "image/png")},
    )

    assert response.status_code == 200
    assert response.headers["content-type"] == "audio/mpeg"
    assert len(response.content) > 0
    assert response.content == b"ID3\x04\x00scaled-mp3-audio-bytes"


def test_process_image_error_path_hides_exception(monkeypatch):
    # When vision raises, the endpoint must return 500 with a generic message,
    # never leaking the raw exception text to the client.
    secret = "boom-internal-stacktrace-detail-12345"

    def raise_boom(image_bytes):
        raise RuntimeError(secret)

    monkeypatch.setattr(main.vision_service, "get_description", raise_boom)

    response = client.post(
        "/process-image",
        files={"file": ("problem.png", _tiny_png_bytes(), "image/png")},
    )

    assert response.status_code == 500
    assert secret not in response.text
    # The generic message is what the client should see instead.
    assert "Failed to process the image" in response.json()["detail"]
