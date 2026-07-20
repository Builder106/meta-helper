package com.metahelper.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

@Service
public class AudioService {
    private static final Logger logger = Logger.getLogger(AudioService.class.getName());

    public byte[] scaleAmplitude(byte[] audioBytes, double multiplier) throws IOException, InterruptedException {
        if (audioBytes == null || audioBytes.length == 0) {
            return new byte[0];
        }

        Path tempInput = Files.createTempFile("audio_input", ".mp3");
        Path tempOutput = Files.createTempFile("audio_output", ".mp3");

        try {
            Files.write(tempInput, audioBytes);

            String volumeFilter = "volume=" + multiplier;
            
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-y", // Overwrite output if exists
                    "-i", tempInput.toAbsolutePath().toString(),
                    "-filter:a", volumeFilter,
                    tempOutput.toAbsolutePath().toString()
            );
            
            // redirect error to discard it or log it
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new IOException("ffmpeg command failed with exit code " + exitCode);
            }

            return Files.readAllBytes(tempOutput);

        } finally {
            Files.deleteIfExists(tempInput);
            Files.deleteIfExists(tempOutput);
        }
    }
}
