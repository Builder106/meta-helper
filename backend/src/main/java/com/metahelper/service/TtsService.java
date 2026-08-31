package com.metahelper.service;

import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisOutputFormat;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Service
public class TtsService {
    private static final Logger logger = Logger.getLogger(TtsService.class.getName());
    private static final Pattern HTTP_LINK = Pattern.compile("\\[([^\\]]+)\\]\\(https?://[^)]*\\)");
    private static final Pattern FENCE = Pattern.compile("\\x60{3}[^\\n]*");
    private static final Pattern HEADING = Pattern.compile("(?m)^\\s{0,3}#{1,6}\\s+");
    private static final Pattern BULLET = Pattern.compile("(?m)^\\s*[-*+]\\s+");

    @FunctionalInterface
    interface SpeechSynthesizerFunction {
        byte[] synthesize(String subscriptionKey, String region, String voice, String text) throws Exception;
    }

    private final String subscriptionKey;
    private final String region;
    private final String defaultVoice;
    private final SpeechSynthesizerFunction speechSynthesizerFunction;

    public TtsService(
            @Value("$" + "{azure.speech.key:}") String subscriptionKey,
            @Value("$" + "{azure.speech.region:}") String region,
            @Value("$" + "{azure.speech.voice:en-US-GuyNeural}") String defaultVoice) {
        this(subscriptionKey, region, defaultVoice, TtsService::defaultSynthesize);
    }

    TtsService(
            String subscriptionKey,
            String region,
            String defaultVoice,
            SpeechSynthesizerFunction speechSynthesizerFunction) {
        this.subscriptionKey = subscriptionKey;
        this.region = region;
        this.defaultVoice = defaultVoice;
        this.speechSynthesizerFunction = speechSynthesizerFunction;
    }

    public byte[] textToSpeech(String text) throws IOException {
        String cleanText = stripMarkdownForSpeech(text);
        if (cleanText == null || cleanText.isBlank()) {
            logger.warning("TTS Warning: Received empty text, returning empty audio.");
            return new byte[0];
        }
        if (subscriptionKey.isBlank() || region.isBlank()) {
            throw new IOException("Azure Speech is not configured: set AZURE_SPEECH_KEY and AZURE_SPEECH_REGION.");
        }
        logger.info("Synthesizing speech for " + cleanText.length() + " characters...");
        try {
            return speechSynthesizerFunction.synthesize(subscriptionKey, region, defaultVoice, cleanText);
        } catch (Exception firstFailure) {
            String fallbackVoice = defaultVoice.equals("en-US-AriaNeural") ? "en-US-GuyNeural" : "en-US-AriaNeural";
            logger.warning("TTS Error with " + defaultVoice + ": " + firstFailure.getMessage());
            try {
                return speechSynthesizerFunction.synthesize(subscriptionKey, region, fallbackVoice, cleanText);
            } catch (Exception fallbackFailure) {
                logger.severe("Final TTS Failure: " + fallbackFailure.getMessage());
                if (fallbackFailure instanceof IOException ioException) throw ioException;
                throw new IOException("Azure Speech synthesis failed", fallbackFailure);
            }
        }
    }

    @FunctionalInterface
    interface SynthesizerExecutor {
        byte[] execute(SpeechConfig speechConfig, String text) throws Exception;
    }

    static SynthesizerExecutor defaultSynthesizerExecutor = (speechConfig, text) -> {
        try (SpeechSynthesizer synthesizer = new SpeechSynthesizer(speechConfig);
             SpeechSynthesisResult result = synthesizer.SpeakTextAsync(text).get()) {
            if (result.getReason() != ResultReason.SynthesizingAudioCompleted) {
                throw new IOException("Azure Speech synthesis did not complete: " + result.getReason());
            }
            byte[] audioData = result.getAudioData();
            if (audioData == null || audioData.length == 0) {
                throw new IOException("Azure Speech returned no audio data");
            }
            return audioData;
        }
    };

    static byte[] defaultSynthesize(String subscriptionKey, String region, String voice, String text) throws Exception {
        return defaultSynthesize(subscriptionKey, region, voice, text, defaultSynthesizerExecutor);
    }

    static byte[] defaultSynthesize(
            String subscriptionKey,
            String region,
            String voice,
            String text,
            SynthesizerExecutor executor) throws Exception {
        try (SpeechConfig speechConfig = SpeechConfig.fromSubscription(subscriptionKey, region)) {
            speechConfig.setSpeechSynthesisVoiceName(voice);
            speechConfig.setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Audio24Khz160KBitRateMonoMp3);
            try {
                return executor.execute(speechConfig, text);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IOException("Azure Speech synthesis was interrupted", interruptedException);
            } catch (Exception exception) {
                if (exception instanceof IOException ioException) throw ioException;
                throw new IOException("Azure Speech synthesis failed", exception);
            }
        }
    }

    private String stripMarkdownForSpeech(String text) {
        if (text == null || text.isBlank()) return text;
        text = HTTP_LINK.matcher(text).replaceAll("$1");
        text = FENCE.matcher(text).replaceAll("");
        text = text.replace(String.valueOf((char) 96), "");
        text = HEADING.matcher(text).replaceAll("");
        text = BULLET.matcher(text).replaceAll("");
        text = text.replace("|", " ");
        return text.replaceAll("[ \\t]{2,}", " ").trim();
    }
}
