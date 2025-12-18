import google.generativeai as genai
import PIL.Image
import io

class VisionService:
    def __init__(self, api_key: str):
        genai.configure(api_key=api_key)
        self.model = genai.GenerativeModel('gemini-2.5-flash')

    def get_description(self, image_bytes: bytes) -> str:
        image = PIL.Image.open(io.BytesIO(image_bytes))
        prompt = """You are an AI assistant helping a student with a Discrete Mathematics practice exam.
        Identify the math question in this image and provide a comprehensive, step-by-step solution.
        
        CRITICAL AUDIO GUIDELINES:
        1. Speak in plain English. Do NOT use LaTeX or complex mathematical notation (like ^, _, \\sum, etc.) as they are unreadable by TTS.
        2. Instead of "x^2", say "x squared". 
        3. Instead of "∑", say "the summation from...".
        4. Instead of "∈", say "is an element of".
        5. Describe logical steps clearly using words like "Therefore", "Next we see that", and "Specifically".
        
        Structure your response so the student can easily follow along and transcribe the logic onto their answer sheet. End with the final answer clearly stated.
        """
        response = self.model.generate_content([prompt, image])
        return response.text

