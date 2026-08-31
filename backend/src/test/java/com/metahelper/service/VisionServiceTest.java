package com.metahelper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VisionServiceTest {

    @Test
    public void testGetDescriptionUnconfiguredApiKey() {
        VisionService visionService = new VisionService("", "gemini-1.5-flash", new ObjectMapper());
        Exception exception = assertThrows(RuntimeException.class, () -> {
            visionService.getDescription("fake_image".getBytes());
        });

        assertTrue(exception.getMessage().contains("VisionService is not configured"));
    }

    @Test
    public void testGetDescriptionErrorFallback() {
        VisionService visionService = new VisionService("dummy-api-key", "gemini-1.5-flash", new ObjectMapper());
        String result = visionService.getDescription("fake_image".getBytes());
        assertNotNull(result);
        assertTrue(result.contains("trouble analyzing") || result.contains("retake the photo"));
    }
}
