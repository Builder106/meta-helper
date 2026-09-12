package com.metahelper.service;

import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisOutputFormat;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final Pattern VERBATIM_SECTION = Pattern.compile("(?im)^\\s*VERBATIM READ-OUT\\s*:\\s*$");
    private static final Pattern EXPLANATION_SECTION = Pattern.compile("(?im)^\\s*EXPLANATION\\s*:\\s*$");
    private static final Pattern SENTENCE_END = Pattern.compile("([.!?])(?=\\s|$)");
    private static final Pattern TRANSITION_WORD = Pattern.compile(
            "(?i)\\b(First|Next|Then|Finally|Inside|Otherwise|If)\\b");
    private static final Pattern COMPLEMENT_WORD = Pattern.compile("(?i)\\bcomplement\\b");
    private static final String DEFAULT_STYLE = "narration-professional";
    private static final String DEFAULT_RATE = "-4%";
    private static final String CODE_LINE_BREAK = "400ms";
    private static final String CODE_COMMENT_BREAK = "550ms";
    private static final String SENTENCE_BREAK = "250ms";
    private static final String TRAILING_BREAK = "300ms";
    private static final String PARAGRAPH_BREAK = "500ms";
    private static final String SECTION_BREAK = "650ms";
    private static final String TRANSITION_PITCH = "+2%";

    @FunctionalInterface
    interface SpeechSynthesizerFunction {
        byte[] synthesize(String subscriptionKey, String region, String voice, String text) throws Exception;
    }

    private final String subscriptionKey;
    private final String region;
    private final String defaultVoice;
    private final String speechStyle;
    private final String speechRate;
    private final SpeechSynthesizerFunction speechSynthesizerFunction;

    @Autowired
    public TtsService(
            @Value("$" + "{azure.speech.key:}") String subscriptionKey,
            @Value("$" + "{azure.speech.region:}") String region,
            @Value("$" + "{azure.speech.voice:en-US-AriaNeural}") String defaultVoice,
            @Value("$" + "{azure.speech.style:narration-professional}") String speechStyle,
            @Value("$" + "{azure.speech.rate:-4%}") String speechRate) {
        this(subscriptionKey, region, defaultVoice, speechStyle, speechRate, TtsService::defaultSynthesize);
    }

    TtsService(
            String subscriptionKey,
            String region,
            String defaultVoice) {
        this(subscriptionKey, region, defaultVoice, DEFAULT_STYLE, DEFAULT_RATE, TtsService::defaultSynthesize);
    }

    TtsService(
            String subscriptionKey,
            String region,
            String defaultVoice,
            SpeechSynthesizerFunction speechSynthesizerFunction) {
        this(subscriptionKey, region, defaultVoice, DEFAULT_STYLE, DEFAULT_RATE, speechSynthesizerFunction);
    }

    TtsService(
            String subscriptionKey,
            String region,
            String defaultVoice,
            String speechStyle,
            String speechRate,
            SpeechSynthesizerFunction speechSynthesizerFunction) {
        this.subscriptionKey = subscriptionKey;
        this.region = region;
        this.defaultVoice = defaultVoice;
        this.speechStyle = speechStyle == null ? "" : speechStyle.trim();
        this.speechRate = speechRate == null ? "" : speechRate.trim();
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
            return speechSynthesizerFunction.synthesize(
                    subscriptionKey, region, defaultVoice, buildSsml(cleanText, defaultVoice, speechStyle, speechRate));
        } catch (Exception firstFailure) {
            String fallbackVoice = defaultVoice.equals("en-US-AriaNeural") ? "en-US-GuyNeural" : "en-US-AriaNeural";
            logger.warning("TTS Error with " + defaultVoice + ": " + firstFailure.getMessage());
            try {
                return speechSynthesizerFunction.synthesize(
                        subscriptionKey, region, fallbackVoice, buildSsml(cleanText, fallbackVoice, "", speechRate));
            } catch (Exception fallbackFailure) {
                logger.severe("Final TTS Failure: " + fallbackFailure.getMessage());
                if (fallbackFailure instanceof IOException ioException) throw ioException;
                throw new IOException("Azure Speech synthesis failed", fallbackFailure);
            }
        }
    }

    interface SpeechSynthesizerAdapter extends AutoCloseable {
        SpeechSynthesisResult speakSsml(String ssml) throws Exception;
        @Override
        void close();
    }

    @FunctionalInterface
    interface SpeechSynthesizerFactory {
        SpeechSynthesizerAdapter create(SpeechConfig config);
    }

    @FunctionalInterface
    interface SpeechSynthesizerCreator {
        SpeechSynthesizer create(SpeechConfig config);
    }

    @FunctionalInterface
    interface SynthesizerExecutor {
        byte[] execute(SpeechConfig speechConfig, String text) throws Exception;
    }

    static SpeechSynthesizerCreator defaultSpeechSynthesizerCreator = SpeechSynthesizer::new;

    static SpeechSynthesizerFactory defaultSynthesizerFactory = config -> {
        SpeechSynthesizer synthesizer = defaultSpeechSynthesizerCreator.create(config);
        return new SpeechSynthesizerAdapter() {
            @Override
            public SpeechSynthesisResult speakSsml(String ssml) throws Exception {
                return synthesizer.SpeakSsmlAsync(ssml).get();
            }

            @Override
            public void close() {
                synthesizer.close();
            }
        };
    };

    static SynthesizerExecutor defaultSynthesizerExecutor = (speechConfig, text) -> {
        try (SpeechSynthesizerAdapter synthesizer = defaultSynthesizerFactory.create(speechConfig);
             SpeechSynthesisResult result = synthesizer.speakSsml(text)) {
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

    static String buildSsml(String text, String voice, String style, String rate) {
        String cleanText = text == null ? "" : text.trim();
        StringBuilder body = new StringBuilder();
        boolean hasSections = VERBATIM_SECTION.matcher(cleanText).find() || EXPLANATION_SECTION.matcher(cleanText).find();

        if (hasSections) {
            appendSection(body, cleanText, VERBATIM_SECTION, "Code read-out", true);
            appendSection(body, cleanText, EXPLANATION_SECTION, "Explanation", false);
        } else {
            appendNarration(body, cleanText, false);
            body.append("<break time=\"").append(TRAILING_BREAK).append("\"/>");
        }

        StringBuilder ssml = new StringBuilder("<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" ");
        ssml.append("xmlns:mstts=\"https://www.w3.org/2001/mstts\" xml:lang=\"en-US\">");
        ssml.append("<voice name=\"").append(escapeXmlAttribute(voice)).append("\">");
        if (rate != null && !rate.isBlank()) {
            ssml.append("<prosody rate=\"").append(escapeXmlAttribute(rate)).append("\">");
        }
        if (style != null && !style.isBlank()) {
            ssml.append("<mstts:express-as style=\"").append(escapeXmlAttribute(style)).append("\">");
        }
        ssml.append(body);
        if (style != null && !style.isBlank()) ssml.append("</mstts:express-as>");
        if (rate != null && !rate.isBlank()) ssml.append("</prosody>");
        return ssml.append("</voice></speak>").toString();
    }

    private static void appendSection(StringBuilder body, String text, Pattern sectionPattern, String spokenLabel, boolean code) {
        var matcher = sectionPattern.matcher(text);
        if (!matcher.find()) return;
        int contentStart = matcher.end();
        int contentEnd = text.length();
        Pattern nextSection = sectionPattern == VERBATIM_SECTION ? EXPLANATION_SECTION : VERBATIM_SECTION;
        var nextMatcher = nextSection.matcher(text);
        if (nextMatcher.find(contentStart)) contentEnd = nextMatcher.start();
        String content = text.substring(contentStart, contentEnd).trim();
        if (content.isBlank()) return;
        body.append("<p><prosody pitch=\"").append(TRANSITION_PITCH).append("\">")
                .append(escapeXmlText(spokenLabel)).append(".</prosody><break time=\"")
                .append(SECTION_BREAK).append("\"/></p>");
        appendNarration(body, content, code);
        body.append("<break time=\"").append(SECTION_BREAK).append("\"/>");
    }

    private static void appendNarration(StringBuilder body, String text, boolean code) {
        String[] lines = text.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isBlank()) {
                body.append("<break time=\"").append(PARAGRAPH_BREAK).append("\"/>");
                continue;
            }
            String spokenLine = escapeXmlText(line);
            if (!code) {
                spokenLine = emphasizeTransitions(spokenLine);
                spokenLine = pronounceComplement(spokenLine);
                spokenLine = SENTENCE_END.matcher(spokenLine)
                        .replaceAll("$1<break time=\"" + SENTENCE_BREAK + "\"/>");
            } else {
                spokenLine = pronounceComplement(spokenLine);
            }
            body.append("<p>").append(spokenLine);
            if (code || index < lines.length - 1) {
                String pause = code
                        ? (isCodeComment(line) ? CODE_COMMENT_BREAK : CODE_LINE_BREAK)
                        : PARAGRAPH_BREAK;
                body.append("<break time=\"").append(pause).append("\"/>");
            }
            body.append("</p>");
        }
    }

    private static boolean isCodeComment(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("//")
                || trimmed.startsWith("#")
                || trimmed.startsWith("/*")
                || trimmed.startsWith("*");
    }

    private static String emphasizeTransitions(String escapedText) {
        return TRANSITION_WORD.matcher(escapedText)
                .replaceAll("<prosody pitch=\"" + TRANSITION_PITCH + "\">$1</prosody>");
    }

    private static String pronounceComplement(String escapedText) {
        return COMPLEMENT_WORD.matcher(escapedText)
                .replaceAll("<phoneme alphabet=\"ipa\" ph=\"ˈkɑmpləmənt\">$0</phoneme>");
    }

    private static String escapeXmlText(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String escapeXmlAttribute(String value) {
        return escapeXmlText(value == null ? "" : value);
    }
}
