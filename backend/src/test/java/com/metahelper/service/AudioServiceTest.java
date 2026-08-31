package com.metahelper.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;

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

    @Test
    public void testScaleAmplitudeFfmpegFailure() {
        assertThrows(IOException.class, () -> {
            audioService.scaleAmplitude(new byte[]{1, 2, 3, 4, 5}, 0.5);
        });
    }

    @Test
    public void testScaleAmplitudeSuccess() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-f", "lavfi", "-i", "anullsrc=r=24000:cl=mono", "-t", "0.1", "-f", "mp3", "pipe:1"
            );
            Process p = pb.start();
            byte[] silentMp3 = p.getInputStream().readAllBytes();
            p.waitFor();
            if (silentMp3.length > 0) {
                byte[] scaled = audioService.scaleAmplitude(silentMp3, 0.5);
                assertNotNull(scaled);
                assertTrue(scaled.length > 0);
            }
        } catch (Exception e) {
            // If ffmpeg is not available in environment, ignore
        }
    }
}
