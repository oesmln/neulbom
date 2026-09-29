package com.neulbom.backend.analysis.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.analysis.api.QaPair;
import com.neulbom.backend.config.ExternalApiExecutor;
import com.neulbom.backend.config.ExternalApiProperties;
import com.neulbom.backend.common.exception.EmptyTranscriptException;
import com.neulbom.backend.diary.integration.GeminiDailyDiaryWriter;
import com.neulbom.backend.session.integration.GeminiConversationQuestionClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ExternalApiClientTest {

    @Test
    void whisperResponseIsMappedWithoutExposingTheApiKey() {
        ExternalApiProperties properties = properties("whisper-secret", "", "", "");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/audio/transcriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer whisper-secret"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("whisper-1")))
                .andRespond(withSuccess("""
                        {"text":"안녕하세요","duration":1.25,"language":"ko","segments":[{"avg_logprob":-0.1}]}
                        """, MediaType.APPLICATION_JSON));

        OpenAiWhisperClient client = new OpenAiWhisperClient(
                builder.build(), properties, new ExternalApiExecutor(properties));
        SpeechToTextClient.TranscriptionResult result = client.transcribe(
                new SpeechToTextClient.AudioFile(new byte[]{1, 2, 3}, "sample.wav", "audio/wav"));

        assertThat(result.transcript()).isEqualTo("안녕하세요");
        assertThat(result.durationSec()).isEqualByComparingTo("1.25");
        assertThat(result.language()).isEqualTo("ko");
        server.verify();
    }

    @Test
    void localWhisperUsesTheOpenAiCompatibleContractWithoutAnApiKey() {
        ExternalApiProperties properties = sttProperties("local", "http://local-whisper.test", "");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://local-whisper.test/v1/audio/transcriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("local-model")))
                .andRespond(withSuccess("""
                        {"text":"로컬 전사","duration":2.5,"language":"ko","model":"local-model"}
                        """, MediaType.APPLICATION_JSON));

        LocalWhisperClient client = new LocalWhisperClient(
                builder.build(), properties, new ExternalApiExecutor(properties));
        SpeechToTextClient.TranscriptionResult result = client.transcribe(
                new SpeechToTextClient.AudioFile(new byte[]{1, 2}, "sample.wav", "audio/wav"));

        assertThat(result.transcript()).isEqualTo("로컬 전사");
        assertThat(result.modelName()).isEqualTo("local-model");
        server.verify();
    }

    @Test
    void googleSttV2ResponseIsMappedUsingApplicationDefaultCredentials() {
        ExternalApiProperties properties = sttProperties(
                "google", "", "neulbom-test");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://us-speech.googleapis.com/v2/projects/neulbom-test/locations/us/recognizers/_:recognize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(header("x-goog-user-project", "neulbom-test"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("chirp_3")))
                .andRespond(withSuccess("""
                        {"results":[{"languageCode":"ko-KR","alternatives":[{"transcript":"구글 전사","confidence":0.91}]}],"metadata":{"totalBilledDuration":"2.5s"}}
                        """, MediaType.APPLICATION_JSON));

        GoogleCloudSpeechToTextClient client = new GoogleCloudSpeechToTextClient(
                builder.build(), properties, new ExternalApiExecutor(properties), new ObjectMapper(),
                new GoogleSttAudioConverter(),
                () -> "test-token");
        SpeechToTextClient.TranscriptionResult result = client.transcribe(
                new SpeechToTextClient.AudioFile(new byte[]{1, 2}, "sample.webm", "audio/webm"));

        assertThat(result.transcript()).isEqualTo("구글 전사");
        assertThat(result.durationSec()).isEqualByComparingTo("2.5");
        assertThat(result.confidence()).isEqualByComparingTo("0.91");
        assertThat(result.language()).isEqualTo("ko-KR");
        assertThat(result.modelName()).isEqualTo("chirp_3");
        server.verify();
    }

    @Test
    void googleSttV2MapsAnEmptyResultToTheDomainError() {
        ExternalApiProperties properties = sttProperties("google", "", "neulbom-test");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://us-speech.googleapis.com/v2/projects/neulbom-test/locations/us/recognizers/_:recognize"))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        GoogleCloudSpeechToTextClient client = new GoogleCloudSpeechToTextClient(
                builder.build(), properties, new ExternalApiExecutor(properties), new ObjectMapper(),
                new GoogleSttAudioConverter(),
                () -> "test-token");

        assertThatThrownBy(() -> client.transcribe(
                new SpeechToTextClient.AudioFile(new byte[]{1}, "sample.webm", "audio/webm")))
                .isInstanceOf(EmptyTranscriptException.class);
        server.verify();
    }

    @Test
    void googleSttSendsConvertedWavForM4aRecording() {
        ExternalApiProperties properties = sttProperties("google", "", "neulbom-test");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        byte[] wav = new byte[]{'R', 'I', 'F', 'F'};
        SpeechToTextClient.AudioFile m4a = new SpeechToTextClient.AudioFile(
                new byte[]{1, 2, 3}, "answer.m4a", "audio/mp4");
        GoogleSttAudioConverter converter = mock(GoogleSttAudioConverter.class);
        when(converter.convert(m4a)).thenReturn(wav);
        server.expect(requestTo("https://us-speech.googleapis.com/v2/projects/neulbom-test/locations/us/recognizers/_:recognize"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"content\":\"" + Base64.getEncoder().encodeToString(wav) + "\"")))
                .andRespond(withSuccess("""
                        {"results":[{"alternatives":[{"transcript":"변환 후 전사"}]}]}
                        """, MediaType.APPLICATION_JSON));

        GoogleCloudSpeechToTextClient client = new GoogleCloudSpeechToTextClient(
                builder.build(), properties, new ExternalApiExecutor(properties), new ObjectMapper(),
                converter, () -> "test-token");

        assertThat(client.transcribe(m4a).transcript()).isEqualTo("변환 후 전사");
        verify(converter).convert(m4a);
        server.verify();
    }

    @Test
    void geminiResponseMapsStructuredSummary() {
        ExternalApiProperties properties = properties("", "", "", "gemini-secret");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "gemini-secret"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"{\\"summary\\":\\"오늘은 가족과 즐거운 이야기를 나눴어요.\\",\\"vocabulary_score\\":72,\\"keywords\\":[\\"가족\\",\\"즐거움\\"]}"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        GeminiSessionSummaryClient client = new GeminiSessionSummaryClient(
                builder.build(), properties, new ExternalApiExecutor(properties), new ObjectMapper());
        SessionSummaryClient.SummaryResult result = client.summarize(java.util.List.of(
                new com.neulbom.backend.analysis.api.QaPair(
                        java.util.UUID.randomUUID(), "오늘 기분은 어떠세요?", "가족과 이야기해서 즐거워요.", "emotion")));

        assertThat(result.summary()).contains("가족");
        assertThat(result.vocabularyScore()).isEqualByComparingTo("72.00");
        assertThat(result.keywords()).containsExactly("가족", "즐거움");
        assertThat(result.modelVersion()).isEqualTo("gemini-3.5-flash-lite");
        server.verify();
    }

    @Test
    void geminiQuestionUsesTheSameDefaultModelAsSessionSummary() {
        ExternalApiProperties properties = properties("", "", "", "gemini-secret");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "gemini-secret"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("공원에서 산책했어요")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("답변에 없는 식사, 외출, 사람, 감정")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("그 산책에 대해 조금 더 들려주시겠어요?")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("이 이야기를 조금 더 나누고 싶으세요, 아니면 다른 이야기를 해볼까요?")))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"{\\"question\\":\\"공원에서 무엇이 가장 기억에 남으셨어요?\\"}"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        GeminiConversationQuestionClient client = new GeminiConversationQuestionClient(
                builder.build(), properties, new ExternalApiExecutor(properties), new ObjectMapper());
        String question = client.generateNextQuestion(
                List.of(new QaPair(UUID.randomUUID(), "오늘 무엇을 하셨어요?", "공원에서 산책했어요", "emotion")),
                2, 6);

        assertThat(question).isEqualTo("공원에서 무엇이 가장 기억에 남으셨어요?");
        server.verify();
    }

    @Test
    void geminiDailyDiaryWriterReceivesAllConversationsAndReturnsOneDiary() {
        ExternalApiProperties properties = properties("", "", "", "gemini-secret");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "gemini-secret"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("대화 1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("대화 2")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("답변에 명시된 사실만 사용")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("하루의 기분이나 평가")))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"{\\"diary\\":\\"오늘은 아들과 비빔밥을 먹고 공원을 걸었다. 저녁에는 친구와 통화했다.\\"}"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        GeminiDailyDiaryWriter writer = new GeminiDailyDiaryWriter(
                builder.build(), properties, new ExternalApiExecutor(properties), new ObjectMapper());
        String diary = writer.write(java.time.LocalDate.of(2026, 9, 28), List.of(
                List.of(new QaPair(UUID.randomUUID(), "점심에 무엇을 드셨어요?", "아들과 비빔밥을 먹었어요.", "emotion")),
                List.of(new QaPair(UUID.randomUUID(), "오늘 누구와 이야기하셨어요?", "친구와 통화했어요.", "emotion"))));

        assertThat(writer.isConfigured()).isTrue();
        assertThat(diary).contains("아들과 비빔밥", "친구와 통화");
        server.verify();
    }

    @Test
    void astResponseMapsAcousticFeatures() {
        ExternalApiProperties properties = properties("", "http://ast.test/analyze", "", "");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ast.test/analyze"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"acoustic_reference_score":0.81,"acoustic_flags":{"pause":true},"speech_rate":3.1,"pause_ratio":0.2,"energy_variability":0.4,"speech_stability":0.9,"model_name":"AST","model_version":"ast-v2"}
                        """, MediaType.APPLICATION_JSON));

        HttpAstAnalysisClient client = new HttpAstAnalysisClient(
                builder.build(), properties, new ExternalApiExecutor(properties));
        AcousticAnalysisClient.AcousticResult result = client.analyze(
                "recording-1", new SpeechToTextClient.AudioFile(new byte[]{1}, "sample.wav", "audio/wav"), 8, "ast-v2");

        assertThat(result.acousticReferenceScore()).isEqualByComparingTo("0.81");
        assertThat(result.speechStability()).isEqualByComparingTo("0.9");
        assertThat(result.acousticFlags().path("pause").asBoolean()).isTrue();
        server.verify();
    }

    @Test
    void kcElectraResponseMapsCognitiveFeatures() {
        ExternalApiProperties properties = properties("", "", "http://kc.test/analyze", "");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        java.util.UUID questionId = java.util.UUID.randomUUID();
        server.expect(requestTo("http://kc.test/analyze"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("앞서 들은 단어를 기억해 보세요.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(questionId.toString())))
                .andRespond(withSuccess("""
                        {"language_reference_score":0.68,"cognitive_flags":{"memory":true},"domain_scores":{"memory":{"correct":1,"total":2}},"model_breakdown":{"provider":"kc"},"model_name":"KcELECTRA","model_version":"kc-v3"}
                        """, MediaType.APPLICATION_JSON));

        HttpKcElectraClient client = new HttpKcElectraClient(
                builder.build(), properties, new ExternalApiExecutor(properties), new ObjectMapper());
        CognitiveAnalysisClient.CognitiveResult result = client.analyze(
                questionId,
                "앞서 들은 단어를 기억해 보세요.", "오늘은 가족을 만났어요.", "memory", "kc-v3");

        assertThat(result.languageReferenceScore()).isEqualByComparingTo("0.68");
        assertThat(result.cognitiveFlags().path("memory").asBoolean()).isTrue();
        assertThat(result.domainScores().path("memory").path("total").asInt()).isEqualTo(2);
        server.verify();
    }

    private ExternalApiProperties properties(String whisperKey, String astUrl, String kcUrl, String geminiKey) {
        return new ExternalApiProperties(
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                0,
                true,
                "auto",
                whisperKey,
                "https://api.openai.com",
                "whisper-1",
                "",
                "",
                "whisper-1",
                "",
                "",
                "us",
                "chirp_3",
                "ko-KR",
                true,
                "https://texttospeech.googleapis.com",
                "neulbom-tts-test",
                "ko-KR",
                "ko-KR-Neural2-A",
                "ko-KR-Neural2-C",
                astUrl,
                "ast-secret",
                "ast-v1",
                kcUrl,
                "kc-secret",
                "kc-v1",
                geminiKey,
                "https://generativelanguage.googleapis.com",
                "");
    }

    private ExternalApiProperties sttProperties(String provider, String localUrl, String googleProjectId) {
        return new ExternalApiProperties(
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                0,
                true,
                provider,
                "",
                "https://api.openai.com",
                "whisper-1",
                localUrl,
                "",
                "local-model",
                "",
                googleProjectId,
                "us",
                "chirp_3",
                "ko-KR",
                true,
                "https://texttospeech.googleapis.com",
                "neulbom-tts-test",
                "ko-KR",
                "ko-KR-Neural2-A",
                "ko-KR-Neural2-C",
                "",
                "",
                "ast-v1",
                "",
                "",
                "kc-v1",
                "",
                "https://generativelanguage.googleapis.com",
                "");
    }
}
