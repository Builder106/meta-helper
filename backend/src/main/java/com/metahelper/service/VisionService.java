package com.metahelper.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Service
public class VisionService {
    private static final Logger logger = Logger.getLogger(VisionService.class.getName());
    
    private final RestClient restClient;
    private final String apiKey;
    private final String modelId;
    private final ObjectMapper objectMapper;

    private static final String PROMPT = """
        You are MetaHelper, an audio assistant that reads and explains code and technical content for someone looking at a screen, whiteboard, slide, or printed page who needs to consume it by ear — for example a developer or student who is blind or has low vision.

        Identify what the image contains (source code, terminal/error output, a diagram, or technical text) and detect the programming language automatically.
        If it's too blurry, cropped, or unreadable, say so plainly and ask the user to reframe and retake the photo — never guess at hidden content.

        For SOURCE CODE, give TWO layers, in this order:

        1. VERBATIM READ-OUT: read the code exactly as written so the listener can follow and transcribe it.
           - Speak symbols as words: "open brace", "close brace", "semicolon", "equals", "plus plus", "open paren", "close paren".
           - Make block structure clear (e.g. "inside the loop", "back at the top level").
           - Example: "for open-paren int i equals zero semicolon i less than ten semicolon i plus plus close-paren open-brace".

        2. EXPLANATION: in plain English, what the code does and why, block by block.
           - Use connective words like "first", "then", "this returns".
           - Example: "This is a for-loop with an integer i starting at zero that runs while i is less than ten, incrementing i each time."

        For an ERROR or TERMINAL OUTPUT: read the key message verbatim, then explain the likely cause and a concrete next step.
        For a DIAGRAM or TECHNICAL TEXT: describe its structure and meaning concisely.

        AUDIO GUIDELINES (this is read aloud by text-to-speech):
        - Plain spoken English only. Do NOT use LaTeX, Markdown, tables, or notation that doesn't speak well.
        - Keep it tight — favor clarity over completeness; the listener can ask for a re-read.
        - Don't comment on image quality unless it is actually unreadable.
        """;

    public VisionService(@Value("${google.api.key:}") String apiKey,
                         @Value("${gemini.model:gemini-1.5-flash}") String modelId,
                         ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.modelId = modelId;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create("https://generativelanguage.googleapis.com");
    }

    public String getDescription(byte[] imageBytes) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("VisionService is not configured: set GOOGLE_API_KEY in the environment.");
        }

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        List<String> modelsToTry = List.of(this.modelId, "gemini-2.5-flash-lite");

        Exception lastError = null;
        for (String model : modelsToTry) {
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    logger.info("Calling Gemini with model: " + model + ", attempt: " + (attempt + 1));
                    String response = makeApiCall(model, base64Image);
                    
                    if (response != null && !response.isBlank()) {
                        return response;
                    }
                    
                    logger.warning(model + ": empty response (possible safety block).");
                    return "I couldn't generate an answer for that image. Please retake the photo with the full question in view.";
                    
                } catch (Exception e) {
                    lastError = e;
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                    boolean transientError = errorMsg.contains("503") || errorMsg.contains("UNAVAILABLE") || 
                                             errorMsg.contains("429") || errorMsg.contains("RESOURCE_EXHAUSTED") ||
                                             errorMsg.contains("500") || errorMsg.contains("high demand") || 
                                             errorMsg.contains("overloaded");
                    
                    logger.warning(String.format("Gemini %s attempt %d failed (transient=%b): %s", model, attempt + 1, transientError, errorMsg));
                    
                    if (transientError && attempt < 2) {
                        try {
                            Thread.sleep((long) (1500 * (attempt + 1)));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }
                    break;
                }
            }
        }

        logger.severe("Gemini request failed after retries: " + lastError);
        return "I had trouble analyzing that image. Please try again in a moment.";
    }

    private String makeApiCall(String model, String base64Image) {
        Map<String, Object> requestBody = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(
                    Map.of("text", PROMPT),
                    Map.of("inline_data", Map.of(
                        "mime_type", "image/jpeg",
                        "data", base64Image
                    ))
                ))
            )
        );

        String jsonResponse = restClient.post()
            .uri("/v1beta/models/{model}:generateContent?key={apiKey}", model, apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(String.class);
            
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            if (root.has("candidates") && root.get("candidates").isArray() && root.get("candidates").size() > 0) {
                JsonNode content = root.get("candidates").get(0).get("content");
                if (content != null && content.has("parts") && content.get("parts").isArray() && content.get("parts").size() > 0) {
                    JsonNode textNode = content.get("parts").get(0).get("text");
                    if (textNode != null) {
                        return textNode.asText();
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("Failed to parse Gemini response: " + e.getMessage());
            throw new RuntimeException("Parse error", e);
        }
        
        return null;
    }
}
