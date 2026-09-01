package com.metahelper.service;

import tools.jackson.databind.ObjectMapper;
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

        VisionService nullKeyService = new VisionService(null, "gemini-1.5-flash", new ObjectMapper());
        Exception exNull = assertThrows(RuntimeException.class, () -> {
            nullKeyService.getDescription("fake_image".getBytes());
        });
        assertTrue(exNull.getMessage().contains("VisionService is not configured"));
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

    @Test
    public void testDefaultConstructor() {
        VisionService defaultService = new VisionService("dummy-key", "gemini-1.5-flash", new ObjectMapper());
        assertNotNull(defaultService);
    }

    @Test
    public void testGetDescriptionJsonParseException() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VisionService visionService = new VisionService("test-key", "gemini-1.5-flash", new ObjectMapper(), builder.build());

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=test-key"))
                .andRespond(withSuccess("invalid-json{", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=test-key"))
                .andRespond(withSuccess("invalid-json{", MediaType.APPLICATION_JSON));

        String result = visionService.getDescription("fake_image".getBytes());
        assertTrue(result.contains("trouble analyzing") || result.contains("retake the photo"));
        server.verify();
    }

    @Test
    public void testGetDescriptionNullOrEmptyParts() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VisionService visionService = new VisionService("test-key", "gemini-1.5-flash", new ObjectMapper(), builder.build());

        String mockResponseBody = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": []
                  }
                }
              ]
            }
            """;

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=test-key"))
                .andRespond(withSuccess(mockResponseBody, MediaType.APPLICATION_JSON));

        String result = visionService.getDescription("fake_image".getBytes());
        assertTrue(result.contains("retake the photo"));
        server.verify();
    }

    @Test
    public void testGetDescriptionCandidateWithoutContent() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VisionService visionService = new VisionService("test-key", "gemini-1.5-flash", new ObjectMapper(), builder.build());

        String mockResponseBody = """
            {
              "candidates": [
                {}
              ]
            }
            """;

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=test-key"))
                .andRespond(withSuccess(mockResponseBody, MediaType.APPLICATION_JSON));

        String result = visionService.getDescription("fake_image".getBytes());
        assertTrue(result.contains("retake the photo"));
        server.verify();
    }

    @Test
    public void testGetDescriptionCandidatePartWithoutText() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VisionService visionService = new VisionService("test-key", "gemini-1.5-flash", new ObjectMapper(), builder.build());

        String mockResponseBody = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {}
                    ]
                  }
                }
              ]
            }
            """;

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=test-key"))
                .andRespond(withSuccess(mockResponseBody, MediaType.APPLICATION_JSON));

        String result = visionService.getDescription("fake_image".getBytes());
        assertTrue(result.contains("retake the photo"));
        server.verify();
    }

    @Test
    public void testGetDescriptionTransientErrorAllKeywords() {
        String[] errorKeywords = {"503", "UNAVAILABLE", "429", "RESOURCE_EXHAUSTED", "500", "high demand", "overloaded"};
        for (String keyword : errorKeywords) {
            RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            VisionService visionService = new VisionService("test-key", "gemini-1.5-flash", new ObjectMapper(), builder.build());

            server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=test-key"))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).body(keyword));
            server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=test-key"))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).body(keyword));
            server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=test-key"))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).body(keyword));

            server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=test-key"))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).body(keyword));
            server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=test-key"))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).body(keyword));
            server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=test-key"))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).body(keyword));

            String result = visionService.getDescription("fake_image".getBytes());
            assertTrue(result.contains("trouble analyzing"));
            server.verify();
        }
    }

    @Test
    public void testGetDescriptionNonTransientErrorBreaksEarly() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VisionService visionService = new VisionService("test-key", "gemini-1.5-flash", new ObjectMapper(), builder.build());

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=test-key"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest().body("INVALID_ARGUMENT"));
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=test-key"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest().body("INVALID_ARGUMENT"));

        String result = visionService.getDescription("fake_image".getBytes());
        assertTrue(result.contains("trouble analyzing"));
        server.verify();
    }

    @Test
    public void testGetDescriptionSecondModelSuccess() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VisionService visionService = new VisionService("test-key", "gemini-1.5-flash", new ObjectMapper(), builder.build());

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=test-key"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest().body("BAD_REQUEST"));

        String mockSuccessBody = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "Success from fallback model"
                      }
                    ]
                  }
                }
              ]
            }
            """;
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=test-key"))
                .andRespond(withSuccess(mockSuccessBody, MediaType.APPLICATION_JSON));

        String result = visionService.getDescription("fake_image".getBytes());
        assertEquals("Success from fallback model", result);
        server.verify();
    }

    @Test
    public void testGetDescriptionInterruptedDuringSleep() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VisionService visionService = new VisionService("test-key", "gemini-1.5-flash", new ObjectMapper(), builder.build());

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=test-key"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).body("503"));
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=test-key"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest().body("BAD_REQUEST"));
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=test-key"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest().body("BAD_REQUEST"));

        Thread.currentThread().interrupt();
        String result = visionService.getDescription("fake_image".getBytes());
        assertTrue(result.contains("trouble analyzing"));
        assertTrue(Thread.interrupted());
        server.verify();
    }
}
