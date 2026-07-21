package com.metahelper.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TtsServiceTest {

    private final TtsService ttsService = new TtsService();

    @Test
    public void testTextToSpeechEmptyOrNull() throws Exception {
        byte[] nullResult = ttsService.textToSpeech(null);
        assertNotNull(nullResult);
        assertEquals(0, nullResult.length);

        byte[] emptyResult = ttsService.textToSpeech("   ");
        assertNotNull(emptyResult);
        assertEquals(0, emptyResult.length);
    }
}
