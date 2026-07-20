package com.metahelper;

import com.metahelper.service.AudioService;
import com.metahelper.service.TtsService;
import com.metahelper.service.VisionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VisionService visionService;

    @MockBean
    private TtsService ttsService;

    @MockBean
    private AudioService audioService;

    @Test
    public void testProcessImage() throws Exception {
        byte[] fakeImage = "fake_image_content".getBytes();
        String fakeDescription = "This is a fake description.";
        byte[] fakeAudio = "fake_audio_content".getBytes();
        byte[] fakeScaledAudio = "fake_scaled_audio_content".getBytes();

        when(visionService.getDescription(any(byte[].class))).thenReturn(fakeDescription);
        when(ttsService.textToSpeech(fakeDescription)).thenReturn(fakeAudio);
        when(audioService.scaleAmplitude(any(byte[].class), anyDouble())).thenReturn(fakeScaledAudio);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                fakeImage
        );

        mockMvc.perform(multipart("/process-image").file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(content().bytes(fakeScaledAudio));
    }
}
