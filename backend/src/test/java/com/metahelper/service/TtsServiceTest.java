package com.metahelper.service;

import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import org.mockito.Mockito;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

        TtsService keyOnly = new TtsService("key", "", "en-US-GuyNeural");
        IOException exKeyOnly = assertThrows(IOException.class, () -> keyOnly.textToSpeech("Hello"));
        assertTrue(exKeyOnly.getMessage().contains("Azure Speech is not configured"));

        TtsService regionOnly = new TtsService("", "eastus", "en-US-GuyNeural");
        IOException exRegionOnly = assertThrows(IOException.class, () -> regionOnly.textToSpeech("Hello"));
        assertTrue(exRegionOnly.getMessage().contains("Azure Speech is not configured"));
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

    @Test
    public void testTextToSpeechFallbackFailureNonIOException() {
        TtsService service = new TtsService(
                "key",
                "eastus",
                "en-US-AriaNeural",
                (key, reg, voice, text) -> {
                    throw new RuntimeException("Unexpected runtime error on " + voice);
                }
        );

        IOException ex = assertThrows(IOException.class, () -> {
            service.textToSpeech("Hello world");
        });
        assertTrue(ex.getMessage().contains("Azure Speech synthesis failed"));
    }

    @Test
    public void testDefaultConstructor() {
        TtsService defaultService = new TtsService("key", "eastus", "en-US-GuyNeural");
        assertNotNull(defaultService);
    }

    @Test
    public void testDefaultSynthesizerFactoryCreatesAdapter() throws Exception {
        SpeechSynthesisResult result = Mockito.mock(SpeechSynthesisResult.class);
        AtomicReference<StubSpeechSynthesizer> created = new AtomicReference<>();
        TtsService.SpeechSynthesizerCreator original = TtsService.defaultSpeechSynthesizerCreator;
        try {
            TtsService.defaultSpeechSynthesizerCreator = config -> {
                StubSpeechSynthesizer synthesizer = new StubSpeechSynthesizer(config, result);
                created.set(synthesizer);
                return synthesizer;
            };
            try (SpeechConfig config = SpeechConfig.fromSubscription("key", "eastus");
                 TtsService.SpeechSynthesizerAdapter synthesizer = TtsService.defaultSynthesizerFactory.create(config)) {
                assertSame(result, synthesizer.speakText("Hello"));
            }
            assertEquals("Hello", created.get().text);
            assertTrue(created.get().closed);
        } finally {
            TtsService.defaultSpeechSynthesizerCreator = original;
        }
    }

    @Test
    public void testDefaultSynthesizeSuccess() throws Exception {
        byte[] expected = "audio_bytes".getBytes();
        byte[] result = TtsService.defaultSynthesize("key", "eastus", "voice", "text", (cfg, txt) -> expected);
        assertArrayEquals(expected, result);
    }

    @Test
    public void testDefaultSynthesizeInterrupted() {
        IOException ex = assertThrows(IOException.class, () -> {
            TtsService.defaultSynthesize("key", "eastus", "voice", "text", (cfg, txt) -> {
                throw new InterruptedException("simulated interrupt");
            });
        });
        assertTrue(ex.getMessage().contains("interrupted"));
    }

    @Test
    public void testDefaultSynthesizeIOException() {
        IOException ex = assertThrows(IOException.class, () -> {
            TtsService.defaultSynthesize("key", "eastus", "voice", "text", (cfg, txt) -> {
                throw new IOException("simulated io error");
            });
        });
        assertTrue(ex.getMessage().contains("simulated io error"));
    }

    @Test
    public void testDefaultSynthesizeGenericException() {
        IOException ex = assertThrows(IOException.class, () -> {
            TtsService.defaultSynthesize("key", "eastus", "voice", "text", (cfg, txt) -> {
                throw new RuntimeException("simulated generic error");
            });
        });
        assertTrue(ex.getMessage().contains("Azure Speech synthesis failed"));
    }

    @Test
    public void testDefaultSynthesize4ArgOverload() throws Exception {
        TtsService.SynthesizerExecutor original = TtsService.defaultSynthesizerExecutor;
        try {
            TtsService.defaultSynthesizerExecutor = (cfg, txt) -> "synthesized".getBytes();
            byte[] result = TtsService.defaultSynthesize("key", "eastus", "voice", "text");
            assertArrayEquals("synthesized".getBytes(), result);
        } finally {
            TtsService.defaultSynthesizerExecutor = original;
        }
    }

    @Test
    public void testDefaultSynthesizerExecutorSuccess() throws Exception {
        TtsService.SpeechSynthesizerAdapter mockSynthesizer =
                org.mockito.Mockito.mock(TtsService.SpeechSynthesizerAdapter.class);
        com.microsoft.cognitiveservices.speech.SpeechSynthesisResult mockResult =
                org.mockito.Mockito.mock(com.microsoft.cognitiveservices.speech.SpeechSynthesisResult.class);

        org.mockito.Mockito.when(mockSynthesizer.speakText(org.mockito.ArgumentMatchers.anyString())).thenReturn(mockResult);
        org.mockito.Mockito.when(mockResult.getReason()).thenReturn(com.microsoft.cognitiveservices.speech.ResultReason.SynthesizingAudioCompleted);
        org.mockito.Mockito.when(mockResult.getAudioData()).thenReturn("test-audio".getBytes());

        TtsService.SpeechSynthesizerFactory original = TtsService.defaultSynthesizerFactory;
        try {
            TtsService.defaultSynthesizerFactory = cfg -> mockSynthesizer;
            byte[] bytes = TtsService.defaultSynthesizerExecutor.execute(null, "Hello");
            assertArrayEquals("test-audio".getBytes(), bytes);
        } finally {
            TtsService.defaultSynthesizerFactory = original;
        }
    }

    @Test
    public void testDefaultSynthesizerExecutorIncompleteReason() throws Exception {
        TtsService.SpeechSynthesizerAdapter mockSynthesizer =
                org.mockito.Mockito.mock(TtsService.SpeechSynthesizerAdapter.class);
        com.microsoft.cognitiveservices.speech.SpeechSynthesisResult mockResult =
                org.mockito.Mockito.mock(com.microsoft.cognitiveservices.speech.SpeechSynthesisResult.class);

        org.mockito.Mockito.when(mockSynthesizer.speakText(org.mockito.ArgumentMatchers.anyString())).thenReturn(mockResult);
        org.mockito.Mockito.when(mockResult.getReason()).thenReturn(com.microsoft.cognitiveservices.speech.ResultReason.Canceled);

        TtsService.SpeechSynthesizerFactory original = TtsService.defaultSynthesizerFactory;
        try {
            TtsService.defaultSynthesizerFactory = cfg -> mockSynthesizer;
            IOException ex = assertThrows(IOException.class, () -> {
                TtsService.defaultSynthesizerExecutor.execute(null, "Hello");
            });
            assertTrue(ex.getMessage().contains("did not complete"));
        } finally {
            TtsService.defaultSynthesizerFactory = original;
        }
    }

    @Test
    public void testDefaultSynthesizerExecutorNullOrEmptyAudioData() throws Exception {
        TtsService.SpeechSynthesizerAdapter mockSynthesizer =
                org.mockito.Mockito.mock(TtsService.SpeechSynthesizerAdapter.class);
        com.microsoft.cognitiveservices.speech.SpeechSynthesisResult mockResult =
                org.mockito.Mockito.mock(com.microsoft.cognitiveservices.speech.SpeechSynthesisResult.class);

        org.mockito.Mockito.when(mockSynthesizer.speakText(org.mockito.ArgumentMatchers.anyString())).thenReturn(mockResult);
        org.mockito.Mockito.when(mockResult.getReason()).thenReturn(com.microsoft.cognitiveservices.speech.ResultReason.SynthesizingAudioCompleted);
        org.mockito.Mockito.when(mockResult.getAudioData()).thenReturn(null);

        TtsService.SpeechSynthesizerFactory original = TtsService.defaultSynthesizerFactory;
        try {
            TtsService.defaultSynthesizerFactory = cfg -> mockSynthesizer;
            IOException exNull = assertThrows(IOException.class, () -> {
                TtsService.defaultSynthesizerExecutor.execute(null, "Hello");
            });
            assertTrue(exNull.getMessage().contains("returned no audio data"));

            org.mockito.Mockito.when(mockResult.getAudioData()).thenReturn(new byte[0]);
            IOException exEmpty = assertThrows(IOException.class, () -> {
                TtsService.defaultSynthesizerExecutor.execute(null, "Hello");
            });
            assertTrue(exEmpty.getMessage().contains("returned no audio data"));
        } finally {
            TtsService.defaultSynthesizerFactory = original;
        }
    }

    private static final class StubSpeechSynthesizer extends SpeechSynthesizer {
        private final SpeechSynthesisResult result;
        private String text;
        private boolean closed;

        private StubSpeechSynthesizer(SpeechConfig config, SpeechSynthesisResult result) {
            super(config);
            this.result = result;
        }

        @Override
        public Future<SpeechSynthesisResult> SpeakTextAsync(String text) {
            this.text = text;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public void close() {
            closed = true;
            super.close();
        }
    }
}
