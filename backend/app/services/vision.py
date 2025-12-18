import google.generativeai as genai
import PIL.Image
import io

class VisionService:
    def __init__(self, api_key: str):
        genai.configure(api_key=api_key)
        self.model = genai.GenerativeModel('gemini-1.5-flash')

    def get_description(self, image_bytes: bytes) -> str:
        image = PIL.Image.open(io.BytesIO(image_bytes))
        prompt = "You are an AI assistant helping a student wearing smart glasses. Identify any practice question in this image. If a question is found, provide the correct answer and a very brief explanation (max 2 sentences) so the student can hear it quickly."
        response = self.model.generate_content([prompt, image])
        return response.text

