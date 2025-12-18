from pydub import AudioSegment
import io

class AudioProcessor:
    def scale_amplitude(self, audio_bytes: bytes, multiplier: float = 0.1) -> bytes:
        import math
        # Load audio from bytes
        audio = AudioSegment.from_file(io.BytesIO(audio_bytes))
        
        # Calculate dB change: gain_db = 20 * log10(multiplier)
        # e.g., 0.1 multiplier = -20dB
        if multiplier <= 0:
            db_change = -100 # Silence
        else:
            db_change = 20 * math.log10(multiplier)
            
        scaled_audio = audio + db_change
        
        # Export back to bytes
        buffer = io.BytesIO()
        scaled_audio.export(buffer, format="mp3")
        return buffer.getvalue()

