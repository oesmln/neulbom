# Backend

늘봄(NEULBOM)의 Spring Boot 기반 서버/API입니다.

## 기술 스택

- Java 21
- Spring Boot 3.5 계열
- Gradle Wrapper
- PostgreSQL
- Spring Data JPA / Hibernate
- Spring Security
- springdoc OpenAPI
- Whisper, AST, KcELECTRA, Gemini 외부 연동

## 로컬 실행

로컬 PostgreSQL에 `neulbom` 데이터베이스가 준비되어 있어야 합니다.

```bash
createdb neulbom
cd backend
cp .env.example .env
./gradlew bootRun --args='--spring.profiles.active=local'
```

로컬 기본 연결값은 다음과 같습니다.

- URL: `jdbc:postgresql://localhost:5432/neulbom`
- 사용자: 현재 OS 사용자명
- 비밀번호: 환경변수 `DB_PASSWORD`

## STT provider 선택

`STT_PROVIDER`로 `openai`, `local`, `google`, `auto`, `none` 중 하나를 선택한다. 로컬 Whisper는 OpenAI 호환 `POST /v1/audio/transcriptions` 서버 주소를 `LOCAL_WHISPER_API_BASE_URL`에 넣는다. Google Cloud STT V2는 `GOOGLE_STT_PROJECT_ID`와 `GOOGLE_STT_LOCATION`을 설정하고, 로컬에서는 다음 명령으로 Application Default Credentials를 준비한다.

```bash
gcloud auth application-default login
```

실제 secret과 서비스 계정 JSON은 저장소에 커밋하지 않는다. 자세한 provider 계약은 [`docs/api-spec.md`](docs/api-spec.md)의 7.12절과 [`AGENTS.md`](AGENTS.md)를 따른다.

## 카카오·네이버 OAuth

provider client secret은 백엔드 `.env`에만 둡니다. 프론트가 authorization code를 받을 때 사용한 redirect URI를 provider 콘솔과 백엔드 allowlist에 동일하게 등록합니다. 여러 환경은 쉼표로 구분합니다.

```dotenv
KAKAO_ALLOWED_REDIRECT_URIS=http://localhost:8081/auth/callback/kakao,https://app.example.com/auth/callback/kakao
NAVER_ALLOWED_REDIRECT_URIS=http://localhost:8081/auth/callback/naver,https://app.example.com/auth/callback/naver
```

등록되지 않은 URI는 provider code 교환 전에 거부됩니다. authorization code, provider token과 client secret은 로그에 출력하지 않습니다.

실행 확인:

```bash
curl http://localhost:8080/health
curl http://localhost:8080/actuator/health
```

OpenAPI 문서는 다음 주소에서 확인합니다.

- Swagger UI: <http://localhost:8080/docs>
- OpenAPI JSON: <http://localhost:8080/api-docs>

## 검사 명령

```bash
cd backend
./gradlew test
./gradlew build
```

## 디렉터리

```text
backend/
  AGENTS.md
  docs/
  src/main/java/com/neulbom/backend/
    config/
    common/
    auth/
    user/
    guardian/
    session/
    recording/
    analysis/
    diary/
    game/
    campaign/
    notification/
```

전체 개발 규칙은 [`AGENTS.md`](AGENTS.md), API 정의는 [`docs/api-spec.md`](docs/api-spec.md), 개발 순서는 [`docs/backend-development-checklist.md`](docs/backend-development-checklist.md)를 확인합니다.

## 주요 역할

- 사용자 및 보호자 연결 관리
- CIST 검사와 AI 정서 문답 세션 관리
- 문항별 음성 녹음과 오프라인 재전송 처리
- STT·음향·인지 분석 결과 관리
- 일기, 게임, 캐릭터, 지역 캠페인, 알림 제공
- PostgreSQL 데이터 저장
- 외부 AI/STT 서비스 연동
