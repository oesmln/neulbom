package com.neulbom.backend.counseling;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.neulbom.backend.config.ExternalApiExecutor;
import com.neulbom.backend.config.OAuthProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 로컬 키워드 검색(`GET /v2/local/search/keyword.json`) 어댑터.
 *
 * REST 키는 카카오 OAuth에 이미 쓰는 {@code KAKAO_CLIENT_ID}를 재사용한다 — 카카오는 같은 앱의
 * REST API 키 하나로 로그인과 로컬 API를 모두 인증한다. 키는 서버에만 있고 앱으로 나가지 않는다.
 * 같은 검색어는 {@link #CACHE_TTL} 동안 메모리에 캐시해 무료 쿼터(일 10만 건)를 아낀다.
 */
@Component
public class KakaoLocalClient {

    static final String KEYWORD_SEARCH_PATH = "/v2/local/search/keyword.json";
    static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final int MAX_CACHE_ENTRIES = 2_000;

    /** 카카오 로컬 검색 결과 한 건. 앱 카드에 필요한 필드만 옮긴다. */
    public record Place(
            String id,
            String name,
            String address,
            String phone,
            BigDecimal latitude,
            BigDecimal longitude,
            String placeUrl
    ) {
    }

    private record CacheEntry(List<Place> places, Instant expiresAt) {
    }

    private final RestClient restClient;
    private final OAuthProperties oauthProperties;
    private final ExternalApiExecutor executor;
    private final Clock clock;
    private final String baseUrl;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Autowired
    public KakaoLocalClient(
            @Qualifier("externalRestClient") RestClient restClient,
            OAuthProperties oauthProperties,
            ExternalApiExecutor executor,
            Clock clock
    ) {
        this(restClient, oauthProperties, executor, clock, "https://dapi.kakao.com");
    }

    KakaoLocalClient(
            RestClient restClient,
            OAuthProperties oauthProperties,
            ExternalApiExecutor executor,
            Clock clock,
            String baseUrl
    ) {
        this.restClient = restClient;
        this.oauthProperties = oauthProperties;
        this.executor = executor;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    public boolean isConfigured() {
        return oauthProperties.kakao() != null && StringUtils.hasText(oauthProperties.kakao().clientId());
    }

    /**
     * 키워드로 장소를 검색한다. provider 실패는 {@link ExternalApiExecutor}가
     * {@code ExternalServiceUnavailableException}으로 바꿔 던지며, 호출자가 빈 목록 폴백을 결정한다.
     */
    public List<Place> search(String query, int size) {
        String key = query.trim() + "|" + size;
        Instant now = clock.instant();
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.places();
        }
        List<Place> places = executor.execute("Kakao Local", () -> fetch(query.trim(), size));
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(key, new CacheEntry(places, now.plus(CACHE_TTL)));
        return places;
    }

    private List<Place> fetch(String query, int size) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path(KEYWORD_SEARCH_PATH)
                .queryParam("query", query)
                .queryParam("size", Math.max(1, Math.min(size, 15)))
                .build()
                .encode()
                .toUri();
        JsonNode body = restClient.get()
                .uri(uri)
                .header("Authorization", "KakaoAK " + oauthProperties.kakao().clientId())
                .retrieve()
                .body(JsonNode.class);
        if (body == null || !body.path("documents").isArray()) {
            return List.of();
        }
        List<Place> places = new ArrayList<>();
        for (JsonNode doc : body.path("documents")) {
            Place place = toPlace(doc);
            if (StringUtils.hasText(place.name())) {
                places.add(place);
            }
        }
        return List.copyOf(places);
    }

    private static Place toPlace(JsonNode doc) {
        String road = doc.path("road_address_name").asText("");
        String lot = doc.path("address_name").asText("");
        return new Place(
                doc.path("id").asText(null),
                doc.path("place_name").asText(""),
                StringUtils.hasText(road) ? road : lot,
                blankToNull(doc.path("phone").asText("")),
                decimal(doc.path("y").asText(null)),
                decimal(doc.path("x").asText(null)),
                blankToNull(doc.path("place_url").asText("")));
    }

    private static BigDecimal decimal(String value) {
        try {
            return StringUtils.hasText(value) ? new BigDecimal(value) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
