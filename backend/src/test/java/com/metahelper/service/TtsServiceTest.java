package com.metahelper.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    public void testTextToSpeechSuccessWithSynthesizer() throws Exception {
        TtsService service = new TtsService(
                "key",
                "eastus",
                "en-US-GuyNeural",
                (key, reg, voice, text) -> "synthesized_audio".getBytes()
        );

        byte[] result = service.textToSpeech("Hello world");
        assertArrayEquals("synthesized_audio".getBytes(), result);
    }

    @Test
    public void testTextToSpeechFallbackSuccess() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        TtsService service = new TtsService(
                "key",
                "eastus",
                "en-US-GuyNeural",
                (key, reg, voice, text) -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new RuntimeException("Primary voice failed");
                    }
                    return ("fallback_audio_" + voice).getBytes();
                }
        );

        byte[] result = service.textToSpeech("Hello world");
        assertArrayEquals("fallback_audio_en-US-AriaNeural".getBytes(), result);
        assertEquals(2, attempts.get());
    }

    @Test
    public void testTextToSpeechFallbackFailure() {
        TtsService service = new TtsService(
                "key",
                "eastus",
                "en-US-AriaNeural",
                (key, reg, voice, text) -> {
                    throw new IOException("Speech engine error on " + voice);
                }
        );

        IOException ex = assertThrows(IOException.class, () -> {
            service.textToSpeech("Hello world");
        });
        assertTrue(ex.getMessage().contains("Speech engine error"));
    }
}
