from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.responses import Response
import os
from dotenv import load_dotenv
from app.services.vision import VisionService
from app.services.tts import TTSService
from app.services.audio import AudioProcessor

load_dotenv()

app = FastAPI(title="MetaHelper API")

# Initialize services
api_key = os.getenv("GOOGLE_API_KEY")
if not api_key:
    print("WARNING: GOOGLE_API_KEY is not set. Vision service will fail.")

vision_service = VisionService(api_key=api_key)
tts_service = TTSService()
audio_processor = AudioProcessor()

@app.get("/")
async def root():
    return {"message": "MetaHelper API is running"}

@app.post("/process-image")
async def process_image(file: UploadFile = File(...)):
    try:
        # 1. Read Image
        image_bytes = await file.read()
        
        # 2. Get Description from Gemini
        description = vision_service.get_description(image_bytes)
        
        # 3. Convert to Speech
        audio_content = await tts_service.text_to_speech(description)
        
        # 4. Scale Amplitude (Quiet Mode)
        multiplier = float(os.getenv("AUDIO_AMPLITUDE_MULTIPLIER", "0.1"))
        quiet_audio = audio_processor.scale_amplitude(audio_content, multiplier=multiplier)
        
        # 5. Return Audio File
        return Response(content=quiet_audio, media_type="audio/mpeg")
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
