# CIST AI 로컬 통합 테스트 (macOS)

이 문서는 Expo 앱 → Spring Boot → AI 서버 → signed audio URL까지 실제로 연결하는 로컬 절차다. 기본 경로는 Cloudflare 없이 Docker AI 서버가 호스트 백엔드에 직접 접근한다. 토큰, JWT, 음성 원문, Google 서비스 계정 키는 저장소에 커밋하거나 채팅으로 전달하지 않는다.

## 1. 준비물

- Java 21, Node.js 20, PostgreSQL
- Docker Desktop (`docker compose version`이 성공해야 함)
- AI 모델 아티팩트
- Google Cloud STT를 사용할 수 있는 ADC 또는 서비스 계정 JSON

```bash
gcloud auth application-default login
```

다른 팀원의 컴퓨터에서는 개인 ADC 대신 전용 서비스 계정을 사용하고, JSON 파일은 저장소 밖에 둔다. `GOOGLE_APPLICATION_CREDENTIALS`에는 그 절대경로만 설정한다.

## 2. 로컬 백엔드 접근 주소 준비

Docker로 실행하는 AI 서버는 호스트 백엔드에 `host.docker.internal`로 접근한다. Cloudflare tunnel은 기본 로컬 실행에 필요하지 않다. AI 서버를 호스트 프로세스로 직접 실행하는 경우에는 아래 환경변수의 호스트를 `localhost`로 맞춘다.

```dotenv
# backend/.env
AI_AUDIO_PUBLIC_BASE_URL=http://host.docker.internal:8080

# ai-server/.env
AI_SERVER_APP_ENV=local
AI_SERVER_AUDIO_DOWNLOAD_ALLOWED_HOSTS=host.docker.internal
```

호스트에서 두 서버를 직접 실행하면 `AI_AUDIO_PUBLIC_BASE_URL=http://localhost:8080`과 `AI_SERVER_AUDIO_DOWNLOAD_ALLOWED_HOSTS=localhost`를 사용한다.

## 3. 양쪽 환경변수 맞추기

저장소 루트에서 다음 명령을 실행하면 Docker 로컬 기본값을 구성한다. 서비스 토큰은 AI 서버와 백엔드에 동일하게, signed URL 서명 키는 백엔드에만 새로 생성된다. 실제 값은 화면에 출력하지 않는다.

```bash
./scripts/prepare-cist-ai-local-env.sh
```

HTTPS tunnel을 사용하는 운영 유사 테스트가 필요할 때만 `./scripts/prepare-cist-ai-local-env.sh https://발급된주소.trycloudflare.com`처럼 주소를 인자로 전달한다. `ai-server/.env`의 `AI_SERVER_MODEL_ARTIFACTS_HOST_PATH`와 `backend/.env`의 DB·Google 설정은 각 컴퓨터 경로에 맞게 별도로 확인한다.

토큰이 같은지만 값 노출 없이 검사할 수 있다.

```bash
test "$(sed -n 's/^AI_SERVER_SERVICE_TOKEN=//p' backend/.env)" = \
  "$(sed -n 's/^AI_SERVER_SERVICE_TOKEN=//p' ai-server/.env)" && echo "service token: matched"
```

## 4. 서버와 앱 실행

두 번째 터미널에서 AI 서버를 실행한다.

```bash
cd ai-server
docker compose up -d --build
curl http://localhost:8000/health/ready
```

세 번째 터미널에서 백엔드를 실행한다. local profile은 `backend/.env`를 자동으로 읽고 Flyway `V21`도 적용한다.

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

```bash
curl http://localhost:8080/actuator/health
```

네 번째 터미널에서 앱을 실제 API 모드로 실행한다.

```bash
cd frontend
EXPO_PUBLIC_API_BASE_URL=http://localhost:8080 npm run web
```

실기기에서는 `localhost` 대신 Mac의 LAN IP를 사용한다. AI 서버가 받는 음성 URL은 앱의 API 주소와 무관하며, Docker 로컬 실행에서는 `host.docker.internal` 백엔드 주소를 사용한다.

## 5. 테스트 계정과 실행 순서

앱에서 고령자 테스트 계정을 가입·로그인하고 분석 및 음성 수집 동의를 완료한다. JWT는 로그인 응답으로 앱에 자동 저장되므로 별도 발급 사이트가 필요 없다.

1. CIST를 시작해 Q1~Q11의 음성을 녹음한다.
2. Q11 저장 후 앱이 recognition plan을 호출하는지 확인한다.
3. `next_question_codes`에 선택된 Q12~Q16만 화면에 나오는지 확인한다.
4. 마지막 시행 문항까지 저장하면 세션 종료와 분석 생성이 차례로 호출되는지 확인한다.
5. 결과 화면에서 `pending → processing → completed`로 조회되는지 확인한다.
6. `risk_level`별 문구가 다음 의미로 표시되는지 확인한다.
   - `stable`: 안정적
   - `monitoring_needed`: 꾸준한 관찰 필요
   - `review_needed`: 확인 필요
7. `needs_retry`가 `REISSUE_AUDIO_URL`이면 바로 재분석하고, `REPLACE_RESPONSE`이면 지정된 문항만 다시 녹음한 후 재분석하는지 확인한다.

백엔드 저장 결과는 다음처럼 확인한다.

```sql
SELECT analysis_id,
       session_id,
       status,
       retry_count,
       model_score,
       decision_threshold,
       review_threshold,
       threshold_version,
       risk_flag,
       risk_level
FROM cist_ai_analyses
ORDER BY created_at DESC
LIMIT 5;
```

## 6. 자동 계약 테스트

라이브 서버와 별개로, 저장소의 mock 계약 테스트는 다음 명령으로 실행한다.

```bash
cd backend
./gradlew test --tests 'com.neulbom.backend.analysis.CistAiAnalysisIntegrationTest'
```

```bash
cd frontend
npm run typecheck
```

라이브 테스트에서 AI가 `completed`를 반환하지 않으면 AI 서버 `/health/ready`, 모델 아티팩트 경로, Google STT 권한, 로컬 접근 주소와 allowlist, 서비스 토큰 순서로 확인한다.
