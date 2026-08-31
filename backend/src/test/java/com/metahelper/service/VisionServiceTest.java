package com.metahelper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

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

    @Test
    public void testGetDescriptionSuccess() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VisionService visionService = new VisionService("test-key", "gemini-1.5-flash", new ObjectMapper(), builder.build());

        String mockResponseBody = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "This is Python code defining a function."
                      }
                    ]
                  }
                }
              ]
            }
            """;

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=test-key"))
                .andRespond(withSuccess(mockResponseBody, MediaType.APPLICATION_JSON));

        String result = visionService.getDescription("fake_image".getBytes());
        assertEquals("This is Python code defining a function.", result);
        server.verify();
    }

    @Test
    public void testGetDescriptionEmptyCandidatesSafetyBlock() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VisionService visionService = new VisionService("test-key", "gemini-1.5-flash", new ObjectMapper(), builder.build());

        String mockResponseBody = """
            {
              "candidates": []
            }
            """;

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=test-key"))
                .andRespond(withSuccess(mockResponseBody, MediaType.APPLICATION_JSON));

        String result = visionService.getDescription("fake_image".getBytes());
        assertTrue(result.contains("Please retake the photo"));
        server.verify();
    }

    @Test
    public void testGetDescriptionTransientErrorRetry() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VisionService visionService = new VisionService("test-key", "gemini-1.5-flash", new ObjectMapper(), builder.build());

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=test-key"))
                .andRespond(withServerError());

        String mockSuccessBody = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "Recovered after retry"
                      }
                    ]
                  }
                }
              ]
            }
            """;
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=test-key"))
                .andRespond(withSuccess(mockSuccessBody, MediaType.APPLICATION_JSON));

        String result = visionService.getDescription("fake_image".getBytes());
        assertEquals("Recovered after retry", result);
        server.verify();
    }
}
