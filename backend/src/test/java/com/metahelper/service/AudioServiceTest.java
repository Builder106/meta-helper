package com.metahelper.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    public void testScaleAmplitudeSuccessWithCommandRunner() throws Exception {
        AudioService customAudioService = new AudioService(pb -> {
            String outputPath = pb.command().get(pb.command().size() - 1);
            Files.write(Paths.get(outputPath), "scaled_audio_bytes".getBytes());
            return 0;
        });

        byte[] scaled = customAudioService.scaleAmplitude(new byte[]{1, 2, 3}, 0.5);
        assertNotNull(scaled);
        assertArrayEquals("scaled_audio_bytes".getBytes(), scaled);
    }
}
