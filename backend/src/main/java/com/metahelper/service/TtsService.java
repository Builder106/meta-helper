package com.metahelper.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Service
public class TtsService {
    private static final Logger logger = Logger.getLogger(TtsService.class.getName());

    private static final Pattern HTTP_LINK = Pattern.compile("\\[([^\\]]+)\\]\\(https?://[^)]*\\)");
    private static final Pattern FENCE = Pattern.compile("```[^\\n]*");
    private static final Pattern HEADING = Pattern.compile("(?m)^\\s{0,3}#{1,6}\\s+");
    private static final Pattern BULLET = Pattern.compile("(?m)^\\s*[-*+]\\s+");

    private final String defaultVoice = "en-US-GuyNeural";

    private String stripMarkdownForSpeech(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        text = HTTP_LINK.matcher(text).replaceAll("$1");
        text = FENCE.matcher(text).replaceAll("");
        text = text.replace("`", "");
        text = HEADING.matcher(text).replaceAll("");
        text = BULLET.matcher(text).replaceAll("");
        text = text.replace("|", " ");
        text = text.replaceAll("[ \\t]{2,}", " ");
        return text.trim();
    }

    public byte[] textToSpeech(String text) throws IOException, InterruptedException {
        String cleanText = stripMarkdownForSpeech(text);
        if (cleanText == null || cleanText.isBlank()) {
            logger.warning("TTS Warning: Received empty text, returning empty audio.");
            return new byte[0];
        }

        logger.info("Synthesizing speech for " + cleanText.length() + " characters...");

        try {
            return runEdgeTts(cleanText, defaultVoice);
        } catch (Exception e) {
            logger.warning("TTS Error with " + defaultVoice + ": " + e.getMessage());
            String fallbackVoice = defaultVoice.equals("en-US-AndrewNeural") ? "en-GB-SoniaNeural" : "en-US-AndrewNeural";
            logger.info("Attempting fallback to " + fallbackVoice + "...");
            try {
                return runEdgeTts(cleanText, fallbackVoice);
            } catch (Exception e2) {
                logger.severe("Final TTS Failure: " + e2.getMessage());
                throw e2;
            }
        }
    }

    private byte[] runEdgeTts(String text, String voice) throws IOException, InterruptedException {
        Path tempTextFile = Files.createTempFile("tts_input", ".txt");
        Path tempAudioFile = Files.createTempFile("tts_output", ".mp3");

        try {
            Files.writeString(tempTextFile, text);

            ProcessBuilder pb = new ProcessBuilder(
                    "edge-tts",
                    "--voice", voice,
                    "-f", tempTextFile.toAbsolutePath().toString(),
                    "--write-media", tempAudioFile.toAbsolutePath().toString()
            );
            
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new IOException("edge-tts command failed with exit code " + exitCode);
            }

            byte[] audioData = Files.readAllBytes(tempAudioFile);
            if (audioData.length == 0) {
                throw new IOException("No audio data received from TTS stream");
            }

            return audioData;

        } finally {
            Files.deleteIfExists(tempTextFile);
            Files.deleteIfExists(tempAudioFile);
        }
    }
}
