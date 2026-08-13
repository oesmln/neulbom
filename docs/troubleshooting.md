# 백엔드·프론트엔드 통합 트러블슈팅

이 문서는 늘봄 백엔드를 구현하고 Expo 프론트엔드를 실제 Spring API에 연결하면서 확인한 문제를 **증상 → 원인 → 해결 → 재발 방지** 순서로 정리한 실행 문서다. 단순히 현재 코드가 무엇을 하는지 설명하는 문서가 아니라, 같은 문제가 다시 생겼을 때 어디부터 확인할지 안내하는 것이 목적이다.

관련 작업: [Issue #68](https://github.com/oesmln/neulbom/issues/68)

## 먼저 확인할 기준

- API 경로·필드·enum·상태 코드는 [`backend/docs/api-spec.md`](../backend/docs/api-spec.md)를 기준으로 한다.
- 백엔드 완료 조건과 권한·보안 규칙은 [`backend/AGENTS.md`](../backend/AGENTS.md)와 [`backend/docs/backend-development-checklist.md`](../backend/docs/backend-development-checklist.md)를 기준으로 한다.
- 프론트/백엔드 연결 상태는 [`frontend-backend-integration-checklist.md`](frontend-backend-integration-checklist.md)에서 확인한다.
- 모든 작업은 Issue → `develop` 기반 브랜치 → 작업 단위 커밋 → `develop` 대상 PR 순서로 진행한다.

문서의 근거는 백엔드 PR #2, #6, #10, #17, #18, #20, #22, #24, #26, #28, #30, #32, #34, #38, #42, #46, #47과 통합 PR #57, 후속 프론트 PR #63~#67의 코드·테스트·검증 기록이다.

## 1. 백엔드 구현 중 발생한 문제

### 1.1 API 경로가 `/auth`와 `/api/v1/auth`로 갈라짐

**증상**

- 프론트가 호출하는 로그인·회원가입 URL이 백엔드 Controller의 실제 URL과 달라 `404`가 난다.
- 공개 API로 생각한 로그인 요청이 `401` 또는 인증 필터 응답을 받는다.

**원인**

- 초기 Controller와 Security allowlist가 `/auth/**` 기준으로 작성된 뒤 API v1.3 계약의 공통 prefix `/api/v1`이 반영됐다.
- Controller만 바꾸거나 Security 설정만 바꾸면 둘 사이에 다시 불일치가 생긴다.

**해결**

- Controller의 `@RequestMapping`과 Security `permitAll` 목록을 `/api/v1/...`로 함께 변경했다.
- 프론트는 `EXPO_PUBLIC_API_BASE_URL`을 호스트만 받게 하고 API 계층에서 `/api/v1`을 한 번만 붙인다.
- 인증 통합 테스트도 모든 경로를 `/api/v1` 기준으로 바꿔 계약을 고정했다. ([PR #18](https://github.com/oesmln/neulbom/pull/18))

**재발 방지**

- 새 endpoint를 추가할 때 `api-spec.md`, Controller, Security, 프론트 endpoint, 테스트를 한 작업에서 함께 확인한다.
- `curl` 또는 MockMvc에서 실제 경로를 먼저 확인하고 프론트 화면을 연결한다.

### 1.2 Flyway migration·JPA Entity·빈 DB 사이의 스키마 드리프트

**증상**

- 애플리케이션 시작 시 테이블·컬럼·enum이 없다는 오류가 난다.
- 기존 DB에서는 동작하지만 빈 PostgreSQL에서만 실패한다.
- Entity를 고쳤는데 이미 적용된 migration을 수정하면 환경마다 schema가 달라진다.

**원인**

- API 기능을 먼저 구현하고 migration을 나중에 추가하거나, 이미 적용된 `Vn` 파일을 다시 수정했기 때문이다.
- API v1.3 확장 과정에서 기존 schema와 새 profile·동의·분석·리포트 필드가 함께 바뀌었다.

**해결**

- migration을 `V1`~`V11` 순서로 누적하고, 적용된 migration은 수정하지 않고 새 `Vn`으로 변경한다.
- `DatabaseMigrationTest`에서 빈 schema와 unique·foreign key·check 제약을 확인한다.
- 로컬 PostgreSQL에서 Flyway를 실행한 뒤 `./gradlew test`와 `./gradlew build`를 같이 통과시킨다.

**재발 방지**

- Entity 변경 → migration → repository/integration test 순서를 지킨다.
- `./gradlew clean build`를 빈 DB 또는 재현 가능한 test profile에서 주기적으로 실행한다.

### 1.3 공통 오류 응답이 endpoint마다 달라짐

**증상**

- 프론트가 오류 문구를 표시하지 못하거나, 어떤 API는 JSON이고 어떤 API는 빈 본문을 반환한다.
- `400`, `403`, `404`, `409`, `422`, `503`을 화면에서 구분할 수 없다.

**원인**

- Controller에서 예외를 제각각 응답하거나 검증 예외를 공통 처리하지 않았다.
- 요청 추적용 request ID가 응답과 로그에 연결되지 않았다.

**해결**

- `GlobalExceptionHandler`에서 `{ error, code, detail, request_id }` 형식으로 통일했다.
- 인증·권한·리소스 없음·중복·업무 규칙·외부 서비스 장애를 각각 상태 코드에 매핑했다.
- `RequestIdFilter`가 요청 ID를 생성하고 응답 및 로그에 연결한다. ([PR #2](https://github.com/oesmln/neulbom/pull/2))

**재발 방지**

- Controller에서 임의의 오류 envelope을 만들지 않는다.
- 프론트 `apiErrorMessage`는 상태 코드별 사용자 문구를 유지하고, 서버의 `detail`은 민감정보가 아닌 경우에만 사용한다.

### 1.4 JWT role과 scope 권한이 사라져 `403`이 발생함

**증상**

- 유효한 JWT인데 `ROLE_ELDER`/`ROLE_GUARDIAN` 기반 endpoint가 `403`을 반환한다.
- 보호자가 연결된 고령자의 데이터를 읽을 수 있거나, 반대로 정상 연결인데 읽지 못한다.

**원인**

- JWT의 `role` claim을 Spring authority로 변환하는 과정에서 기존 scope authority를 덮어썼다.
- URL의 `user_id`만 믿고 JWT subject, 역할, 연결, 동의, access scope를 함께 검증하지 않았다.

**해결**

- JWT scope authority를 유지하면서 `role`을 `ROLE_*` authority로 추가한다.
- `GuardianAccessService`를 공통 권한 경계로 두고 `guardian_link`, consent, `screening`/`summary`/`diary`/`activity` scope를 모두 검사한다.
- 본인·보호자·연결되지 않은 사용자·동의하지 않은 사용자를 각각 통합 테스트했다. ([PR #18](https://github.com/oesmln/neulbom/pull/18), [PR #22](https://github.com/oesmln/neulbom/pull/22))

**재발 방지**

- `user_id`·`guardian_id`가 요청에 있어도 JWT subject와 소유권을 서버에서 다시 확인한다.
- 보호자 API를 새로 만들 때 성공 케이스 하나만 테스트하지 말고 scope별 `403`을 함께 테스트한다.

### 1.5 비밀번호 재설정이 계정 존재 여부와 공격 가능성을 노출함

**증상**

- 같은 이메일로 요청을 반복하면 무제한 provider 호출이 가능하다.
- 존재하는 이메일과 존재하지 않는 이메일의 응답 차이로 계정이 추측된다.

**원인**

- 이메일 기준과 IP 기준 rate limit, `Retry-After`가 없었다.
- reset token·비밀번호·provider credential을 로그에 남길 위험이 있었다.

**해결**

- 이메일/IP별 rate limiter를 추가하고 초과 시 `429`와 `Retry-After`를 반환한다.
- 존재 여부와 무관하게 동일한 응답 형식을 사용하고 token 원문은 로그에 남기지 않는다.
- 비밀번호 변경 시 기존 refresh token 폐기 옵션과 audit log를 추가했다. ([PR #18](https://github.com/oesmln/neulbom/pull/18))

**재발 방지**

- rate limit 수치는 환경변수로 조정하고 테스트에서 이메일 기준·IP 기준을 각각 확인한다.
- secret·token·비밀번호가 포함된 로그를 코드 리뷰 체크리스트에서 검색한다.

### 1.6 OAuth provider는 연결됐지만 운영 설정과 코드 교환 경계가 불명확함

**증상**

- provider에서 authorization code를 받았는데 백엔드 토큰 교환이 실패한다.
- redirect URI가 조금만 달라도 `400`이 되거나, provider 장애가 앱 전체 오류처럼 보인다.

**원인**

- Kakao/Naver client secret, token URI, user-info URI, 허용 redirect URI가 환경별로 분리되지 않았다.
- 앱이 provider secret을 알아야 하는 것처럼 설계되거나, 등록되지 않은 redirect URI를 허용할 위험이 있었다.

**해결**

- `OAuthProperties`에 provider별 client ID/secret, token/user URI, exact redirect allowlist를 환경변수로 분리했다.
- 백엔드가 authorization code를 provider token으로 교환하고 profile을 읽는다. 앱에는 secret·access token을 두지 않는다.
- 미등록 redirect URI·지원하지 않는 provider·provider 오류를 각각 `400`/`401`/`503`으로 처리하고 원문 token을 로그에 남기지 않는다. ([PR #46](https://github.com/oesmln/neulbom/pull/46))

**재발 방지**

- `.env.example`에는 빈 secret과 안전한 예시만 둔다.
- provider client는 `MockRestServiceServer` 테스트로 token 요청 파라미터와 profile 매핑을 고정한다.
- 운영 redirect URI는 provider 콘솔과 `*_ALLOWED_REDIRECT_URIS` 양쪽에 같은 문자열로 등록한다.

### 1.7 인증 전 초대 코드 검증이 막힘

**증상**

- 로그인하지 않은 고령자가 초대 코드를 입력하면 `401`이 반환된다.
- 코드 검증만 필요한 화면에서 연결을 미리 보여줄 수 없다.

**원인**

- `POST /guardian/invitations/verify`를 인증이 필요한 보호자 API와 동일하게 처리했다.

**해결**

- 검증 endpoint만 공개하고, 실제 수락 endpoint에서는 로그인한 `elder`의 JWT·동의·중복 연결을 다시 검증한다.
- 초대 원문은 hash만 저장하고 만료·1회성 소비는 `410`, 반복 실패는 `429`로 분리했다. ([커밋 `dc05883`](https://github.com/oesmln/neulbom/commit/dc05883))

**재발 방지**

- 공개 endpoint와 보호 endpoint를 Security 설정 및 integration test에서 명시적으로 구분한다.
- 초대 코드를 URL·로그·분석 이벤트에 넣지 않는다.

### 1.8 CIST·정서 문답 답변의 중복 저장과 순서 오류

**증상**

- 네트워크 재시도나 버튼 중복 입력으로 같은 답변이 두 번 저장된다.
- 다른 세션의 질문을 저장하거나, 종료된 세션에 답변이 추가된다.

**원인**

- 앱 재시도용 `client_answer_id`가 없거나 DB unique 제약이 없었다.
- 세션 소유권·질문 순서·세션 상태를 Controller에서만 처리했다.

**해결**

- 세션 단위 `client_answer_id` 멱등성과 transaction 내 `answered_count`/`current_question_order` 갱신을 적용했다.
- 질문이 세션에 속하는지, 순서가 맞는지, 세션이 종료되지 않았는지 Service에서 검증한다. ([PR #24](https://github.com/oesmln/neulbom/pull/24))

**재발 방지**

- 답변을 재전송하는 테스트에서 같은 ID의 응답과 DB row 수를 함께 확인한다.
- 화면은 서버가 답변을 저장하기 전 다음 문항으로 이동하지 않는다.

### 1.9 녹음 업로드 계약과 실제 파일 형식이 맞지 않음

**증상**

- Native의 `m4a` 업로드는 되는데 Expo Web에서 녹음이 `415`/`400`으로 거부된다.
- 업로드 후 같은 녹음이 중복 생성되거나, 다른 사용자의 recording ID를 조회할 수 있다.

**원인**

- 백엔드 허용 목록이 `wav`/`m4a`/`mp3`만 알고 Web의 `audio/webm`을 몰랐다.
- 파일 확장자만 검사하거나 `client_recording_id`를 DB unique로 보장하지 않았다.

**해결**

- Expo Web의 `audio/webm`·`.webm`을 허용 목록과 테스트에 추가했다. ([커밋 `705e1f7`](https://github.com/oesmln/neulbom/commit/705e1f7))
- 확장자·MIME·실제 파일 내용·25MB 제한을 함께 검증하고 서버 저장 파일명은 서버 UUID 기반으로 생성한다.
- `client_recording_id`를 DB unique로 두고 multipart 업로드와 `Idempotency-Key`에 같은 값을 사용한다.
- recording 조회는 본인 또는 연결·동의·scope가 있는 보호자만 허용한다. ([PR #26](https://github.com/oesmln/neulbom/pull/26))

**재발 방지**

- 플랫폼별 실제 MIME을 계약 표와 integration test에 기록한다.
- 업로드 성공·중복 재전송·잘못된 형식·용량 초과·IDOR를 모두 테스트한다.

### 1.10 STT·AST·KcELECTRA·Gemini provider 장애와 credential 노출

**증상**

- 외부 provider가 느리거나 내려가면 API가 무한 대기하거나 내부 예외를 그대로 노출한다.
- 로컬에서 provider credential이 없어 분석 테스트를 실행할 수 없다.

**원인**

- 앱이 외부 provider를 직접 호출하거나, provider별 응답 schema를 도메인 Service가 직접 해석했다.
- timeout/retry/fallback 정책과 서버 작업 권한이 분리되지 않았다.

**해결**

- provider adapter를 `analysis/integration`에 격리하고 응답을 내부 DTO로 변환한다.
- connect/read timeout과 제한된 retry를 환경변수로 두고 최종 실패는 `503` 및 상태값으로 반환한다.
- local에서는 deterministic fallback으로 계약을 검증할 수 있고, dev/prod에서는 credential 미설정 시 fallback을 끈다.
- 분석 생성 endpoint는 `@ServerWorkerOnly`로 앱이 직접 호출하지 못하게 했다. ([PR #42](https://github.com/oesmln/neulbom/pull/42), [PR #47](https://github.com/oesmln/neulbom/pull/47))

**재발 방지**

- provider API key·ADC JSON·원문 응답을 저장소와 로그에 남기지 않는다.
- 성공·timeout·429·5xx·schema 오류를 mock client로 각각 테스트한다.

### 1.11 결과·리포트의 권한 및 의료적 표현 문제

**증상**

- 고령자 화면에 보호자용 점수·영역별 분석값이 노출된다.
- benchmark 표본이 부족한데 평균값이나 진단처럼 보이는 문구를 표시한다.

**원인**

- audience와 access scope를 응답 생성 후 필터링하거나, 지역 기준선 데이터가 없을 때 임의의 숫자를 채웠다.

**해결**

- 고령자에는 정성 결과와 안전한 표시 문구만, 보호자에게만 `screening_reference_score`, `risk_level`, `domain_scores`를 권한 검증 후 반환한다.
- benchmark 표본이 부족하면 `suppressed=true`로 반환하고 비교 불가 문구를 사용한다.
- 결과는 의료적 진단이 아니라 스크리닝 참고·추가 확인 권장 표현으로 제한한다. ([PR #30](https://github.com/oesmln/neulbom/pull/30))

**재발 방지**

- 역할별 JSON snapshot과 `403` 테스트를 유지한다.
- 화면에 없는 숫자를 mock이나 기본값으로 채우지 않는다.

### 1.12 KST 날짜 경계와 일기 생성 job 중복

**증상**

- 한국 시간 자정과 UTC 날짜가 달라 캘린더·일기 생성 대상 날짜가 어긋난다.
- 같은 세션/일일 요약을 재처리하면 일기가 두 개 생성되거나 알림이 중복된다.

**원인**

- 날짜 aggregation을 서버 시간대와 사용자 표시 시간대로 구분하지 않았다.
- `daily_summary_id`와 job 상태에 멱등 키가 없었다.

**해결**

- 저장 timestamp는 UTC, 일일 집계와 생성 기준은 `Asia/Seoul`로 고정했다.
- `pending`/`processing`/`completed`/`failed`/`conversation_incomplete` 상태와 `daily_summary_id` 중복 방지를 저장한다.
- 완료·실패 알림도 event key로 중복을 막는다. ([PR #32](https://github.com/oesmln/neulbom/pull/32))

**재발 방지**

- UTC 자정 직전·직후와 KST 자정 기준 integration test를 둔다.
- 동일 job 재호출 시 기존 상태와 diary ID를 반환하는지 확인한다.

### 1.13 `color_match` 게임 enum이 API·DB에 없음

**증상**

- 프론트 게임은 동작하지만 결과 저장 시 `game_type` enum 검증 또는 DB check constraint에서 실패한다.

**원인**

- 초기 명세 enum에는 `image_match`, `consonant`, `word_match`만 있었고 프론트 색상 게임 값이 별도로 합의되지 않았다.

**해결**

- `color_match`를 명세·Service 검증·PostgreSQL migration `V11`에 추가하고 통합 테스트를 갱신했다. ([커밋 `43f086b`](https://github.com/oesmln/neulbom/commit/43f086b))

**재발 방지**

- enum 변경은 Java 코드만 고치지 말고 API 명세·프론트 타입·DB constraint·seed/test를 한 번에 갱신한다.

### 1.14 알림 worker 재시도와 전체 읽음 범위

**증상**

- 외부 작업 재시도로 같은 알림이 중복 생성된다.
- `read-all`이 요청 body의 user ID를 믿어 다른 사용자의 알림을 읽음 처리할 위험이 있다.

**원인**

- 이벤트의 멱등 키와 수신자 소유권 검증이 없었다.

**해결**

- 수신자·`event_key` unique 및 `data.event_id`를 사용해 worker 재시도를 멱등 처리한다.
- 전체 읽음은 body의 user ID를 받지 않고 JWT 주체의 알림만 변경한다. ([PR #38](https://github.com/oesmln/neulbom/pull/38))

**재발 방지**

- 동일 event 재전송과 다른 JWT로 read-all을 호출하는 테스트를 유지한다.

## 2. 프론트·백엔드 통합 중 발생한 문제

### 2.1 실 API 환경에서 mock과 real API가 섞임

**증상**

- 화면은 성공했는데 DB에는 요청이 없거나, 반대로 빈 API URL로 `fetch` 오류가 난다.
- Android emulator·실기기·Web에서 서로 다른 localhost를 사용한다.

**원인**

- `EXPO_PUBLIC_API_BASE_URL`이 비어 있으면 mock mode이고, 환경마다 backend host가 다르다.
- 프론트가 `/api/v1`까지 포함한 값을 받으면서 API 계층이 다시 prefix를 붙이는 문제가 생길 수 있다.

**해결**

- API base URL은 호스트만 받는다.
  - Web/iOS simulator: `http://localhost:8080`
  - Android emulator: `http://10.0.2.2:8080`
  - 실제 기기: 개발 PC의 LAN IP
- 로컬 PostgreSQL + Spring `local` profile + Expo Web을 함께 실행하고 CORS preflight와 health endpoint를 먼저 확인했다. ([PR #57](https://github.com/oesmln/neulbom/pull/57))

**재발 방지**

- 브라우저에서 “성공 화면”만 보지 말고 backend 로그·DB row·Network 요청을 함께 확인한다.
- `.env` 변경 후 Metro를 재시작한다.

### 2.2 프론트 타입과 백엔드 JSON 계약이 어긋남

**증상**

- JSON은 `snake_case`인데 TypeScript가 camelCase로 읽어 값이 `undefined`가 된다.
- 날짜·enum·상태값이 맞지 않아 화면 분기가 실패한다.

**원인**

- Java record의 `@JsonProperty`/naming strategy를 프론트 타입에 반영하지 않았다.
- API 명세와 기존 mock 타입을 기준으로만 화면을 만들었다.

**해결**

- 프론트 API 타입을 서버 JSON 그대로 `snake_case`로 정의하고 endpoint별 응답을 대조했다.
- 결과 status/type, diary mood enum, notification status, game type 등을 백엔드 계약에 맞췄다. ([커밋 `b22ed91`](https://github.com/oesmln/neulbom/commit/b22ed91), [PR #64](https://github.com/oesmln/neulbom/pull/64))

**재발 방지**

- DTO/record를 수정하면 TypeScript type, mock fixture, 화면 분기, integration test를 검색한다.
- “컴파일 성공”만으로 JSON 계약이 맞다고 판단하지 않고 실제 응답 body를 캡처한다.

### 2.3 인증 복원과 `profile_completed` 분기가 빠짐

**증상**

- 로그인 직후에는 홈에 들어가지만 앱 재실행 때 온보딩을 반복하거나, 신규 사용자가 빈 프로필로 홈에 진입한다.
- refresh 후 역할은 복원되지만 프로필 완료 상태가 사라진다.

**원인**

- SecureStore session envelope에 `profile_completed`를 저장하지 않았다.
- Splash/Root navigation이 role만 보고 Elder/Guardian으로 이동했다.

**해결**

- 세션에 access/refresh token, user ID, role, profile 완료 상태를 함께 저장한다.
- `profile_completed=false`는 `Onboarding`, true는 역할별 홈으로 보내도록 분기했다. ([커밋 `8c6fd99`](https://github.com/oesmln/neulbom/commit/8c6fd99), [PR #63](https://github.com/oesmln/neulbom/pull/63))

**재발 방지**

- 신규 로그인·기존 로그인·앱 재실행·refresh 네 가지 경우를 모두 확인한다.
- OAuth 신규 계정도 동일한 `profile_completed` 분기를 사용한다. 현재 PR #66은 PR #63 merge 후 rebase 연결이 남아 있다.

### 2.4 인증 전 초대 검증과 초대 수락 흐름이 섞임

**증상**

- 가입 화면에서 초대 코드를 미리 검증하려는데 인증 오류가 난다.
- verify만 했는데 연결이 생성되거나, 수락 후 같은 코드를 다시 쓰면 화면이 애매하게 처리한다.

**원인**

- `verify`(비소비성 공개 미리보기)와 `accept`(로그인·동의 후 연결 생성)를 같은 UI/API 단계로 취급했다.

**해결**

- 프론트는 verify 결과를 미리보기로만 사용하고, 로그인/역할/동의가 끝난 뒤 accept를 호출한다.
- 성공·동의 거절 `422`·재사용/만료 `410`·scope 부족 `403`을 각각 사용자 문구로 나눈다. ([PR #59](https://github.com/oesmln/neulbom/pull/65))

**재발 방지**

- verify 호출만으로 연결 row가 생기지 않는지 확인한다.
- 코드 원문을 로그·URL·analytics에 남기지 않는다.

### 2.5 CIST 이전 이동 경고와 종료 상태의 화면 race

**증상**

- 첫 문항에서 뒤로 가기를 누르면 navigation warning이 뜨거나 화면이 멈춘다.
- 답변 저장/종료 중 사용자가 다시 눌러 중복 요청을 보낸다.

**원인**

- 첫 문항에는 이전 화면이 없는데 `goBack`을 무조건 호출했다.
- API 저장 중 버튼 잠금과 세션 종료 상태 처리가 부족했다.

**해결**

- 첫 문항에서는 navigation stack 상태를 확인하고, 이전 문항이 있을 때만 index를 감소시킨다. ([커밋 `e5f9527`](https://github.com/oesmln/neulbom/commit/e5f9527))
- 저장 중 `submitting`을 사용하고 서버 응답 후 다음 문항/결과로 이동한다.

**재발 방지**

- 첫 문항·중간 문항·마지막 문항을 각각 수동/자동화 테스트한다.
- 동일 answer ID와 recording ID를 요청마다 재사용한다.

### 2.6 실제 녹음은 메모리에만 있어 앱 재실행 시 사라짐

**증상**

- 업로드 실패 후 앱을 닫으면 녹음 URI와 `client_recording_id`가 사라져 재전송할 수 없다.
- 앱이 다시 열려도 화면에는 이전 답변이 완료되지 않은 상태로 남는다.

**원인**

- 초기 `useAnswerRecording`이 recorder URI를 React ref에만 보관하고 즉시 업로드했다.
- 네트워크 복원·앱 활성화 때 queue를 읽고 재전송하는 계층이 없었다.

**해결**

- Native는 앱 문서 디렉터리, Web은 IndexedDB에 오디오와 metadata를 함께 저장한다.
- 세션 복원·네트워크 재연결·앱 활성화 시 현재 사용자 queue만 순차 전송한다.
- 같은 `client_recording_id`와 `Idempotency-Key`를 유지하고 성공하면 파일/metadata를 삭제한다.
- 백그라운드 업로드가 화면보다 먼저 끝나는 경우를 위해 최소 완료 receipt를 저장하고 화면이 소비한다. ([PR #67](https://github.com/oesmln/neulbom/pull/67))

**재발 방지**

- 계정 전환 시 다른 사용자의 queue를 전송하지 않는다.
- 7일 초과 실패 파일을 정리하고 원본 음성·token을 로그에 남기지 않는다.
- 자동화 브라우저에는 마이크 장치가 없어, Draft 해제 전 iOS/Android 실기기에서 오프라인 재실행 E2E를 별도로 수행한다.

### 2.7 Web 녹음 MIME과 multipart 경계 처리

**증상**

- Web에서만 업로드가 실패하거나 backend가 파일을 읽지 못한다.
- multipart 요청을 수동으로 `Content-Type` 지정해 boundary 오류가 난다.

**원인**

- Web `Blob`의 `audio/webm`을 Native `audio/mp4`로 가정했다.
- FormData boundary를 런타임이 붙여야 하는데 직접 덮어썼다.

**해결**

- Web은 `Blob`과 `.webm` filename, Native는 file descriptor와 `.m4a`를 사용한다.
- multipart 요청에서는 `Content-Type`을 직접 지정하지 않고 runtime boundary를 사용한다.
- access token이 만료된 multipart 요청은 refresh 후 한 번만 재시도한다.

**재발 방지**

- Web/Android/iOS별 실제 업로드 파일명·MIME을 각각 확인한다.
- 서버에서 `audio_file` part 이름과 query parameter 이름을 명세와 대조한다.

### 2.8 화면에 endpoint가 있어도 백엔드가 없는 기능을 실제 연결 대상으로 착각함

**증상**

- 캠페인 버튼·지역 비교·리포트 export·초대 발급처럼 화면은 있지만 연결할 API가 없는 기능에서 통합 작업이 멈춘다.
- 반대로 백엔드 API는 있는데 화면이 없어 “연동 완료”로 잘못 집계한다.

**원인**

- 화면 목록과 API 목록을 한 표로 대조하지 않았다.
- MVP/Phase 2와 의도적인 로컬 기능을 구분하지 않았다.

**해결**

- 통합 체크리스트를 `정상 연결`, `프론트 구현 누락`, `백엔드 구현 누락`, `의도적인 로컬`, `범위 제외`로 나눴다.
- 캠페인은 사용자 결정으로 이번 통합 범위에서 제외했다.
- 보호자 초대 관리·OAuth·영속 녹음 queue는 각각 Issue #59, #60, #61로 분리했다. ([PR #57](https://github.com/oesmln/neulbom/pull/57))

**재발 방지**

- “버튼이 있다”를 “API가 연결됐다”로 간주하지 않는다.
- 각 항목에 프론트 파일, 백엔드 Controller/Service, endpoint, 후속 Issue를 함께 기록한다.

### 2.9 OAuth Web callback 제약과 state 검증

**증상**

- provider 로그인 버튼이 회원가입 화면으로만 이동한다.
- Kakao native custom scheme redirect가 provider에서 거부된다.
- authorization code는 받았는데 state mismatch와 신규 사용자 role 처리 방법이 없다.

**원인**

- Expo AuthSession 연결이 없었고 provider 콘솔 redirect 규칙을 확인하지 않았다.
- 백엔드 교환 DTO에는 PKCE `code_verifier` 필드가 없는데 앱이 PKCE를 켜면 서버가 교환할 수 없다.

**해결**

- `expo-auth-session`으로 code flow를 만들고 `maybeCompleteAuthSession`, state 비교, cancel/error/config 누락 처리를 추가했다.
- Kakao REST redirect 제약에 맞춰 명시적인 HTTP/HTTPS redirect URI를 요구하고, Native 운영은 HTTPS universal link/dev build에서 검증하도록 문서화했다.
- 현재 계약에 code_verifier가 없으므로 `usePKCE:false`로 두고, provider secret/token은 백엔드에서만 교환한다.
- 신규 소셜 계정은 provider 인증 전에 elder/guardian role을 선택한다. ([PR #66](https://github.com/oesmln/neulbom/pull/66))

**재발 방지**

- provider 콘솔 redirect URI와 프론트 env, 백엔드 allowlist를 exact match로 관리한다.
- state mismatch, redirect 거부, provider 401/503, cancel을 각각 테스트한다.
- 실제 provider secret은 `.env` 밖과 저장소에 두지 않는다.

### 2.10 보호자 화면의 실제 데이터와 권한 오류

**증상**

- 다중 고령자 연결인데 화면이 한 명만 가리키거나, 연결은 됐는데 차트·일기 API가 빈 화면으로 보인다.
- `403` 권한 부족과 `404` 데이터 없음이 같은 빈 상태로 표시된다.

**원인**

- 선택된 elder ID를 전역 상태로 유지하지 않았거나 report/diary API query를 계약과 다르게 만들었다.
- 권한 오류·분석 대기·데이터 없음의 화면 상태를 구분하지 않았다.

**해결**

- 연결 목록에서 selected elder를 선택하고 차트·일기·알림 요청이 그 ID를 사용하도록 했다.
- 날짜 range, diary mood enum, permission error, analysis empty/pending 상태를 API 계약과 맞췄다. ([PR #65](https://github.com/oesmln/neulbom/pull/65))

**재발 방지**

- 다중 elder 전환 후 각 API 요청 URL/query를 Network에서 확인한다.
- `403`, `404`, `409`, `422`, pending을 별도 화면/문구로 검증한다.

### 2.11 API 오류를 화면에서 삼키거나 잘못된 기본값으로 대체함

**증상**

- 저장 실패인데 다음 화면으로 넘어가거나, 분석 대기·데이터 없음이 0점/가짜 데이터로 보인다.
- 사용자에게 서버 상태를 설명할 수 없다.

**원인**

- `catch`에서 아무 동작도 하지 않거나, mock fixture를 real mode에서도 사용했다.
- API의 `pending`, `insufficient_data`, `403`, `404`, `409`, `422`를 화면 상태로 모델링하지 않았다.

**해결**

- 저장 실패·권한 부족·분석 대기·빈 데이터·중복 요청을 각각 사용자 문구와 재시도 동작으로 연결했다. ([PR #64](https://github.com/oesmln/neulbom/pull/64))
- 서버가 주지 않은 숫자나 score를 프론트에서 만들지 않고 응답이 없으면 빈 상태를 표시한다.

**재발 방지**

- 정상·loading·empty·offline·forbidden·not found·conflict를 화면 체크리스트로 유지한다.
- `apiErrorMessage`와 endpoint type을 공통 계층에서 관리한다.

### 2.12 영속 queue 재시도 경합과 화면 복원

**증상**

- 네트워크 reconnect와 사용자의 수동 재시도가 동시에 일어나 새 queue 항목이 한 번 건너뛴다.
- 백그라운드에서 업로드가 끝났는데 화면이 `recording_id`를 받지 못한다.

**원인**

- queue sync Promise가 이미 실행 중이면 새 user/새 항목 요청을 버렸다.
- 업로드 성공 즉시 파일을 지우면서 화면과 연결할 최소 결과가 없었다.

**해결**

- sync 요청 user ID를 pending 상태로 보관하고 현재 작업 뒤에 최신 요청을 한 번 더 처리한다.
- 성공 후 음성은 즉시 삭제하되 최소 receipt를 저장하고 해당 문항 화면이 소비한다.
- 다른 계정·7일 초과 항목은 전송 전에 정리한다. ([커밋 `badf084`](https://github.com/oesmln/neulbom/commit/badf084), [PR #67](https://github.com/oesmln/neulbom/pull/67))

**재발 방지**

- queue 저장/상태 변경/업로드/삭제 순서를 직렬화한다.
- 앱 종료·재실행은 자동화 브라우저가 아니라 실기기에서 확인한다.

## 3. 실행·검증 중 발견한 운영성 문제

### 3.1 프론트에 `npm run build`가 없음

**증상**

- 일반적인 `npm run build` 명령이 `Missing script: build`로 실패한다.

**해결**

- 타입 검사는 `npm run typecheck`를 사용한다.
- Web 배포 번들 검사는 `npx expo export --platform web`을 사용한다.
- 백엔드는 반드시 `cd backend && ./gradlew test && ./gradlew build`로 실행한다.

### 3.2 `expo install --check`의 버전 권고와 실제 실패를 구분

**증상**

- Expo SDK가 호환 버전 업데이트를 권고하지만 현재 typecheck/export는 통과한다.

**해결**

- 권고를 즉시 대규모 업데이트로 반영하지 않고 별도 dependency 작업으로 분리한다.
- PR 테스트 결과에 “호환 버전 권고”와 실제 기능 실패를 구분해 기록한다.

### 3.3 브라우저 OAuth 성공 뒤 CIST `404`가 발생함

**증상**

- OAuth callback과 `/auth/oauth/{provider}` 요청은 성공했는데 테스트 화면이 CIST에서 `404`를 표시한다.

**원인**

- 로컬 fake provider/backend는 OAuth 응답만 흉내 냈고 CIST endpoint까지 구현하지 않았다.

**해결**

- Network 요청과 backend 수신 body를 분리해 OAuth 성공(`authorization_code`, `redirect_uri`, `role`, `state`)을 먼저 판정했다.
- 이후 CIST `404`는 OAuth 실패가 아니라 테스트 double의 범위 밖 오류로 분류했다.

**재발 방지**

- 통합 테스트 double은 검증 대상 endpoint만 제공한다는 범위를 문서에 적는다.
- 성공 판정은 화면 이동만이 아니라 request/response와 세션 저장까지 확인한다.

### 3.4 stacked branch와 merge 순서 혼선

**증상**

- 한 PR에 이미 다른 미머지 작업의 커밋이 포함되거나, merge 후 되돌림 PR이 필요해진다.
- 문서·migration 충돌을 어느 PR에서 해결해야 할지 불명확해진다.

**원인**

- 후속 작업 브랜치를 최신 `develop`에서 다시 파지 않고 이전 feature branch에서 쌓았다.
- PR 대상과 의존 PR을 본문에 명확히 적지 않았다. 과거 녹음 PR #26에는 보호자·세션 선행 커밋이 함께 보였고, 이를 되돌리는 PR #48이 열렸다가 머지되지 않고 닫혔다.

**해결**

- 기능별로 Issue와 브랜치를 분리하고 PR 대상은 항상 `develop`으로 통일한다.
- 선행 PR이 머지된 뒤 후속 브랜치를 `develop`에서 rebase하고 충돌을 작업자 브랜치에서 해결한다.
- 현재 통합 PR은 #63 → #64 → #65 → #66 → #67 순서로 머지하며, #66은 #63 머지 후 온보딩 분기를 연결한다.

**재발 방지**

- push 전 `git diff origin/develop...HEAD --stat`과 `git log origin/develop..HEAD --oneline`을 확인한다.
- PR 본문에 `close #번호`, 선행 PR, 테스트와 범위 제외를 적는다.

## 4. 반복 가능한 검증 순서

### 백엔드

```bash
cd backend
./gradlew test
./gradlew build
```

추가 확인:

1. local PostgreSQL/Flyway가 빈 DB에서 시작되는지 확인한다.
2. `/health`, `/actuator/health`, `/api-docs`가 응답하는지 확인한다.
3. 인증 없는 공개 API와 보호 API의 `401`/`403`을 각각 확인한다.
4. validation, 중복, 권한, provider 장애, 파일 형식·용량 오류를 확인한다.
5. 로그에 secret·token·password·음성 원문·초대 코드가 없는지 확인한다.

### 프론트

```bash
cd frontend
npm run typecheck
npx expo export --platform web
```

추가 확인:

1. `.env`의 API host와 플랫폼별 localhost 주소를 확인하고 Metro를 재시작한다.
2. mock mode가 아닌 real API mode인지 backend access log와 DB row로 확인한다.
3. 로그인/refresh/로그아웃, 신규 온보딩, CIST/정서 문답, 녹음, 일기/게임/알림, 보호자 다중 대상자 흐름을 순서대로 확인한다.
4. 정상뿐 아니라 `pending`, empty, offline, `403`, `404`, `409`, `410`, `422`, `503`을 확인한다.
5. 실기기에서 마이크 권한·앱 종료·재실행·네트워크 복원 queue를 확인한다.

## 5. 현재 남은 확인 사항

- [PR #63](https://github.com/oesmln/neulbom/pull/63)~[PR #67](https://github.com/oesmln/neulbom/pull/67)은 현재 Draft 상태다. 순서대로 develop에 반영하고 충돌을 정리해야 한다.
- PR #66은 PR #63의 `Onboarding` 라우트가 develop에 들어온 뒤 `profile_completed=false` OAuth 신규 사용자 분기를 연결해야 한다.
- PR #67은 코드/웹 export/백엔드 멱등 테스트까지 끝났지만 iOS 또는 Android 실기기 오프라인 재실행 E2E가 남아 있다.
- 캠페인 API와 화면 연결은 사용자 결정에 따라 이번 통합 범위에서 제외했다.
- 외부 provider 운영 credential, redirect URI, object storage, 실제 worker queue·push provider 연결은 운영 전 확인 항목이다.
