package com.metahelper.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class TtsServiceTest {

    private final TtsService ttsService = new TtsService("", "", "en-US-GuyNeural");

    @Test
    public void testTextToSpeechEmptyOrNull() throws Exception {
        byte[] nullResult = ttsService.textToSpeech(null);
        assertNotNull(nullResult);
        assertEquals(0, nullResult.length);

        byte[] emptyResult = ttsService.textToSpeech("   ");
        assertNotNull(emptyResult);
        assertEquals(0, emptyResult.length);
    }

    @Test
    public void testTextToSpeechUnconfiguredThrowsIOException() {
        String markdownText = """
                # Overview
                - Bullet item with [link](https://example.com)
                ```java
                int x = 10;
                ```
                | col1 | col2 |
                `inline code`
                """;

        IOException exception = assertThrows(IOException.class, () -> {
            ttsService.textToSpeech(markdownText);
        });

        assertTrue(exception.getMessage().contains("Azure Speech is not configured"));
    }
}
