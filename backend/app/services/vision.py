from google import genai
from PIL import Image
import io

class VisionService:
    def __init__(self, api_key: str):
        self.client = genai.Client(api_key=api_key)
        self.model_id = 'gemini-3-pro-preview'

    def get_description(self, image_bytes: bytes) -> str:
        image = Image.open(io.BytesIO(image_bytes))
        prompt = """You are an AI assistant helping a student with a Computer Science practice exam, specifically focusing on C programming.
        Identify the coding question in this image and provide a comprehensive solution.
        
        IMPORTANT: If you cannot see the entire question, or if the image is too blurry to read, explicitly tell the student to retake the picture.
        
        DUAL-LAYER AUDIO CODING GUIDELINES:
        For every block of code or logic, you MUST provide two layers of description in this order:
        
        1. VERBATIM LAYER: Read the code exactly as written so the student can transcribe it. 
           - Use "open brace" and "close brace".
           - Use "semicolon", "plus plus", and "equals".
           - Example: "for open-parenthesis int i equals zero semicolon i less than ten semicolon i plus plus close-parenthesis"
        
        2. NARRATIVE LAYER: Explain the technical logic of that code in plain English.
           - Example: "This creates a for-loop using an integer i starting at zero. It runs as long as i is less than ten, incrementing i each time."
        
        CRITICAL AUDIO GUIDELINES:
        1. Speak in plain English. Do NOT use LaTeX or complex mathematical notation as they are unreadable by TTS.
        2. Describe logical steps clearly using words like "Next", "We then define", and "Resulting in".
        
        Structure your response so the student can easily follow along and transcribe both the syntax and the logic.
        """
        response = self.client.models.generate_content(
            model=self.model_id,
            contents=[prompt, image]
        )
        return response.text

