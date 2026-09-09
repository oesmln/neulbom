package com.neulbom.backend.counseling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import com.neulbom.backend.config.ExternalApiExecutor;
import com.neulbom.backend.config.ExternalApiProperties;
import com.neulbom.backend.config.OAuthProperties;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoLocalClientTest {

    private static final String BASE_URL = "https://kakao-local.test";
    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

    @Test
    void keywordSearchMapsPlacesAndSendsTheRestKeyAsKakaoAk() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(Matchers.startsWith(BASE_URL + KakaoLocalClient.KEYWORD_SEARCH_PATH)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK kakao-rest-key"))
                .andRespond(withSuccess("""
                        {"documents":[
                          {"id":"1001","place_name":"강남구치매안심센터","road_address_name":"서울 강남구 선릉로 668",
                           "address_name":"서울 강남구 삼성동 8","phone":"02-3423-7211","x":"127.0492","y":"37.5172",
                           "place_url":"http://place.map.kakao.com/1001"},
                          {"id":"1002","place_name":"","road_address_name":"","address_name":"","phone":"","x":"","y":"","place_url":""}
                        ],"meta":{"total_count":1}}
                        """, MediaType.APPLICATION_JSON));

        KakaoLocalClient client = client(builder, "kakao-rest-key");
        List<KakaoLocalClient.Place> places = client.search("서울특별시 강남구 치매안심센터", 15);

        assertThat(places).hasSize(1);
        KakaoLocalClient.Place place = places.get(0);
        assertThat(place.id()).isEqualTo("1001");
        assertThat(place.name()).isEqualTo("강남구치매안심센터");
        assertThat(place.address()).isEqualTo("서울 강남구 선릉로 668");
        assertThat(place.phone()).isEqualTo("02-3423-7211");
        assertThat(place.latitude()).isEqualByComparingTo("37.5172");
        assertThat(place.longitude()).isEqualByComparingTo("127.0492");
        assertThat(place.placeUrl()).isEqualTo("http://place.map.kakao.com/1001");
        server.verify();
    }

    @Test
    void sameQueryIsServedFromCacheWithinTtl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess("""
                        {"documents":[{"id":"1","place_name":"해운대구 보건소","road_address_name":"부산 해운대구 양운로 100",
                          "address_name":"","phone":"051-746-4000","x":"129.1631","y":"35.1638","place_url":"http://place.map.kakao.com/1"}]}
                        """, MediaType.APPLICATION_JSON));

        KakaoLocalClient client = client(builder, "kakao-rest-key");
        assertThat(client.search("부산광역시 해운대구 보건소", 15)).hasSize(1);
        assertThat(client.search("부산광역시 해운대구 보건소", 15)).hasSize(1);
        server.verify();
    }

    @Test
    void providerErrorsSurfaceAsExternalServiceUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(Matchers.startsWith(BASE_URL)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorType\":\"NotAuthorizedError\",\"message\":\"App disabled OPEN_MAP_AND_LOCAL service.\"}"));

        KakaoLocalClient client = client(builder, "kakao-rest-key");

        assertThatThrownBy(() -> client.search("서울특별시 치매안심센터", 15))
                .isInstanceOf(ExternalServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void isConfiguredRequiresTheKakaoClientId() {
        assertThat(client(RestClient.builder(), "kakao-rest-key").isConfigured()).isTrue();
        assertThat(client(RestClient.builder(), "").isConfigured()).isFalse();
    }

    private static KakaoLocalClient client(RestClient.Builder builder, String clientId) {
        OAuthProperties oauth = new OAuthProperties(
                new OAuthProperties.Provider(clientId, "", "", "", ""),
                new OAuthProperties.Provider("", "", "", "", ""));
        return new KakaoLocalClient(
                builder.build(),
                oauth,
                new ExternalApiExecutor(properties()),
                Clock.fixed(NOW, ZoneOffset.UTC),
                BASE_URL);
    }

    private static ExternalApiProperties properties() {
        return new ExternalApiProperties(
                Duration.ofSeconds(1), Duration.ofSeconds(1), 0, true,
                "google", "", "https://api.openai.com", "whisper-1", "", "", "whisper-1",
                "", "", "us", "chirp_3", "ko-KR", true,
                "", "", "ko-KR", "", "",
                "", "", "", "", "", "",
                "", "https://generativelanguage.googleapis.com", "gemini-3.6-flash");
    }
}
