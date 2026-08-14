package com.neulbom.backend.speech.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.config.ExternalApiExecutor;
import com.neulbom.backend.config.ExternalApiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleCloudTextToSpeechClientTest {

    @Test
    void returnsDecodedMp3Audio() {
        ExternalApiProperties properties = properties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://texttospeech.googleapis.com/v1/text:synthesize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(header("x-goog-user-project", "neulbom-tts-test"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ko-KR-Neural2-A")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("MP3")))
                .andRespond(withSuccess("{\"audioContent\":\"AQID\"}", MediaType.APPLICATION_JSON));

        GoogleCloudTextToSpeechClient client = new GoogleCloudTextToSpeechClient(
                builder.build(), properties, new ExternalApiExecutor(properties), new ObjectMapper(),
                () -> "test-token");
        TextToSpeechClient.SynthesisResult result = client.synthesize(
                "안녕하세요", "ko-KR", "ko-KR-Neural2-A", new BigDecimal("0.90"));

        assertThat(result.audio()).containsExactly(1, 2, 3);
        assertThat(result.contentType()).isEqualTo("audio/mpeg");
        assertThat(result.voiceName()).isEqualTo("ko-KR-Neural2-A");
        server.verify();
    }

    private ExternalApiProperties properties() {
        return new ExternalApiProperties(
                Duration.ofSeconds(1), Duration.ofSeconds(1), 0, false,
                "google", "", "https://api.openai.com", "whisper-1",
                "", "", "whisper-1", "", "neulbom-stt-test",
                "asia-northeast1", "chirp_3", "ko-KR",
                "https://texttospeech.googleapis.com", "neulbom-tts-test", "ko-KR",
                "ko-KR-Neural2-A", "ko-KR-Neural2-C",
                "", "", "v1", "", "", "v1",
                "", "https://generativelanguage.googleapis.com", "gemini-2.5-flash");
    }
}
