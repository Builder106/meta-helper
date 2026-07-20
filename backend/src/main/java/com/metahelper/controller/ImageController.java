package com.metahelper.controller;

import com.metahelper.service.AudioService;
import com.metahelper.service.TtsService;
import com.metahelper.service.VisionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
public class ImageController {
    private static final Logger logger = Logger.getLogger(ImageController.class.getName());

    private final VisionService visionService;
    private final TtsService ttsService;
    private final AudioService audioService;
    private final double audioAmplitudeMultiplier;

    public ImageController(VisionService visionService,
                           TtsService ttsService,
                           AudioService audioService,
                           @Value("${audio.amplitude.multiplier:0.1}") double audioAmplitudeMultiplier) {
        this.visionService = visionService;
        this.ttsService = ttsService;
        this.audioService = audioService;
        this.audioAmplitudeMultiplier = audioAmplitudeMultiplier;
    }

    @GetMapping("/")
    public ResponseEntity<java.util.Map<String, String>> root() {
        return ResponseEntity.ok(java.util.Map.of("message", "MetaHelper API is running"));
    }

    @PostMapping("/process-image")
    public ResponseEntity<byte[]> processImage(@RequestParam("file") MultipartFile file) {
        try {
            byte[] imageBytes = file.getBytes();
            
            String description = visionService.getDescription(imageBytes);
            if (description == null || description.isEmpty()) {
                description = "I'm sorry, I couldn't generate a description for this image. Please try taking a clearer photo.";
            }

            byte[] audioContent = ttsService.textToSpeech(description);

            byte[] quietAudio = audioService.scaleAmplitude(audioContent, audioAmplitudeMultiplier);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .body(quietAudio);
                    
        } catch (Exception e) {
            logger.log(Level.SEVERE, "ERROR DURING PROCESSING: " + e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process the image. Please try again.");
        }
    }
}
