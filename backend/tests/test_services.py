import pytest
from unittest.mock import patch, MagicMock
from app.services.vision import VisionService
from app.services.tts import TTSService
from app.services.audio import AudioProcessor
from pydub import AudioSegment
import io

@pytest.fixture
def vision_service():
    return VisionService(api_key="mock_key")

@pytest.fixture
def tts_service():
    return TTSService()

def test_vision_service_generate_description():
    # Mock PIL.Image.open to avoid UnidentifiedImageError
    with patch("PIL.Image.open") as mock_open:
        mock_image = MagicMock()
        mock_open.return_value = mock_image
        
        with patch("app.services.vision.genai.Client") as mock_client_class:
            mock_client = mock_client_class.return_value
            mock_client.models.generate_content.return_value.text = "I see a person wearing glasses."
            
            # Instantiate service INSIDE the patch
            service = VisionService(api_key="mock_key")
            description = service.get_description(b"fake_image_bytes")
            assert description == "I see a person wearing glasses."

def test_vision_service_unreadable_image_returns_guidance():
    # Corrupt bytes -> PIL raises UnidentifiedImageError, which get_description
    # catches and turns into retake-the-photo guidance.
    with patch("app.services.vision.genai.Client"):
        service = VisionService(api_key="mock_key")
        description = service.get_description(b"this is not an image")
        assert "couldn't read that image" in description


def test_vision_service_generate_content_error_returns_guidance():
    # generate_content raises -> get_description returns the "had trouble
    # analyzing" guidance string.
    with patch("PIL.Image.open") as mock_open:
        mock_open.return_value = MagicMock()

        with patch("app.services.vision.genai.Client") as mock_client_class:
            mock_client = mock_client_class.return_value
            mock_client.models.generate_content.side_effect = Exception("upstream 503")

            service = VisionService(api_key="mock_key")
            description = service.get_description(b"fake_image_bytes")
            assert "had trouble analyzing" in description


def test_vision_service_empty_response_returns_guidance():
    # response.text is None (e.g. a safety block) -> get_description returns the
    # "couldn't generate an answer" guidance string.
    with patch("PIL.Image.open") as mock_open:
        mock_open.return_value = MagicMock()

        with patch("app.services.vision.genai.Client") as mock_client_class:
            mock_client = mock_client_class.return_value
            mock_client.models.generate_content.return_value.text = None

            service = VisionService(api_key="mock_key")
            description = service.get_description(b"fake_image_bytes")
            assert "couldn't generate an answer" in description


def test_audio_amplitude_scaling():
    # Generate 1 second of a 440Hz sine wave instead of silence
    from pydub.generators import Sine
    tone = Sine(440).to_audio_segment(duration=1000)
    
    # Export tone to bytes so processor can read it
    buffer = io.BytesIO()
    tone.export(buffer, format="mp3")
    tone_bytes = buffer.getvalue()
    
    processor = AudioProcessor()
    
    # Scale to 10%
    multiplier = 0.1
    scaled_bytes = processor.scale_amplitude(tone_bytes, multiplier=multiplier)
    
    # Load back to compare dBFS
    scaled_audio = AudioSegment.from_file(io.BytesIO(scaled_bytes))
    
    # The dBFS should be lower. 
    # For a 0.1 multiplier, the change should be approx -20dB
    assert scaled_audio.dBFS < tone.dBFS
    assert abs((tone.dBFS - scaled_audio.dBFS) - 20) < 2 # Allow small codec margin
