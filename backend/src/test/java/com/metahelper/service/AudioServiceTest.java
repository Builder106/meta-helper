package com.metahelper.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AudioServiceTest {

    private final AudioService audioService = new AudioService();

    @Test
    public void testScaleAmplitudeWithNullOrEmpty() throws Exception {
        byte[] resultNull = audioService.scaleAmplitude(null, 0.5);
        assertNotNull(resultNull);
        assertEquals(0, resultNull.length);

        byte[] resultEmpty = audioService.scaleAmplitude(new byte[0], 0.5);
        assertNotNull(resultEmpty);
        assertEquals(0, resultEmpty.length);
    }
}
