# 늘봄(NEULBOM) 백엔드 개발 체크리스트

> API v1.2 명세서를 실제 Spring Boot 백엔드로 구현하기 위한 순서형 체크리스트
>
> 기준 문서: [REST API 명세서](api-spec.md)
> 기술 스택: Spring Boot(Java), PostgreSQL, 외부 AI/STT 연동

## 사용 방법

- 각 항목은 위에서 아래 순서대로 진행한다.
- 같은 단계 안에서는 체크 순서를 지킨다.
- 기능 구현이 끝나도 테스트와 권한 검증까지 완료해야 체크한다.
- `MVP`는 현재 화면과 API 연결에 필요한 범위이고, `Phase 2`는 핵심 검사 흐름 이후 진행한다.

## 전체 개발 순서

| 순서 | 단계 | 완료 기준 |
| --- | --- | --- |
| 0 | 개발 기준 확정 | API, 역할, 상태값, 환경변수가 팀에서 합의됨 |
| 1 | 프로젝트·공통 기반 | 애플리케이션이 실행되고 공통 오류 응답이 동작함 |
| 2 | 데이터베이스 설계 | 핵심 테이블·관계·인덱스가 migration으로 생성됨 |
| 3 | 인증·보안 | 회원가입, 로그인, JWT, 역할·소유권 검증이 동작함 |
| 4 | 사용자 온보딩 | 초기 사용자 정보, 동의, 청취·음성 설정이 저장됨 |
| 5 | 보호자 권한 | 다중 고령자 연결과 접근 범위 검증이 동작함 |
| 6 | CIST·AI 문답 핵심 흐름 | 세션·질문·답변을 시작하고 이어서 진행할 수 있음 |
| 7 | 음성 업로드·오프라인 동기화 | 문항별 녹음 업로드와 재전송·중복 방지가 동작함 |
| 8 | AI 분석 파이프라인 | STT·AST·KcELECTRA·요약 결과가 비동기로 저장됨 |
| 9 | 결과·추이·홈 | 고령자 결과와 보호자 리포트를 안전한 문구로 조회함 |
| 10 | 일기·캘린더 | 문답 요약을 일기로 저장하고 날짜별 활동을 조회함 |
| 11 | 게임·캐릭터·캠페인 | 게임 결과, 경험치, 지역 캠페인 참여가 동작함 |
| 12 | 알림 | 검사·요약·반응·캠페인 관련 알림이 생성·조회됨 |
| 13 | 통합 테스트·운영 | 주요 사용자 시나리오와 배포·모니터링 준비가 끝남 |
| 14 | 후속 기능 | TTS·립싱크 등 Phase 2 기능을 별도로 진행함 |

## 단계별 작성 API 목록

아래 목록은 실제 Controller와 Service를 만드는 순서다. 각 API는 **Controller → Service → Repository → Validation → 권한 테스트**까지 끝난 뒤 체크한다.

### 1차. 인증·계정 API

기반: `users`, `refresh_tokens` 테이블과 Spring Security 설정

- [ ] `POST /auth/register` - 회원가입
- [ ] `POST /auth/login` - 로그인 및 access token·refresh token 발급
- [ ] `POST /auth/oauth/{provider}` - 카카오·네이버 소셜 로그인 및 access token·refresh token 발급
- [ ] `POST /auth/password/reset/request` - 비밀번호 재설정 요청
- [ ] `POST /auth/password/reset/confirm` - 비밀번호 재설정 확정
- [ ] `POST /auth/refresh` - access token 갱신
- [ ] `POST /auth/logout` - refresh token 폐기

완료 조건: 회원가입 → 로그인 → 인증 API 호출 → 토큰 갱신 → 로그아웃 흐름이 동작한다.

### 2차. 사용자 온보딩·동의·설정 API

기반: 인증 API와 `users`, `user_preferences`, `consents`, `voice_profiles` 테이블

- [ ] `GET /users/{user_id}` - 프로필·초기 사용자 정보 조회
- [ ] `PATCH /users/{user_id}` - 프로필·학력·건강·생활습관 정보 수정
- [ ] `GET /users/{user_id}/preferences` - 청취·음성·자막 설정 조회
- [ ] `PATCH /users/{user_id}/preferences` - 잘 들리는 귀·음성·속도·자막 설정 저장
- [ ] `GET /voice-profiles` - 선택 가능한 안내 음성 목록 조회
- [ ] `POST /consent/{user_id}` - 개인정보·음성·분석·보호자 접근 동의 저장
- [ ] `GET /consent/{user_id}` - 동의 상태 조회

완료 조건: 신규 고령자가 최초 검사 전에 필요한 정보를 저장하고, 동의하지 않은 기능은 활성화되지 않는다.

### 3차. 보호자 연결 API

기반: `guardian_links` 테이블, 역할·동의·access scope 검사

- [ ] `POST /guardian/invitations` - 6자리 초대 코드 발급
- [ ] `POST /guardian/invitations/verify` - 초대 코드 검증 및 연결 정보 미리보기
- [ ] `POST /guardian/invitations/accept` - 초대 코드 수락 및 보호자 연결 생성
- [ ] `POST /guardian/link` - 고령자 연결 요청
- [ ] `GET /guardian/{guardian_id}/elders` - 연결된 고령자 목록 조회
- [ ] `PATCH /guardian/link/{link_id}` - 연결 상태·접근 범위 수정
- [ ] `DELETE /guardian/link/{link_id}` - 연결 해제

완료 조건: 초대 코드 입력부터 연결 생성까지 동작하고, 한 보호자가 여러 고령자를 관리하며, 연결·동의·접근 범위에 따라 데이터가 제한된다.

### 4차. CIST·AI 정서 문답 세션 API

기반: `questions`, `sessions`, `answers` 테이블과 질문 seed 데이터

- [ ] `POST /sessions` - `cist`, `emotional_qa`, `game`, `mixed` 세션 시작
- [ ] `GET /sessions/{session_id}` - 세션 상태·현재 문항·진행률 조회
- [ ] `PATCH /sessions/{session_id}/settings` - 세션별 음성·청취·자막 설정 적용
- [ ] `PATCH /sessions/{session_id}/end` - 세션 종료 및 분석 예약
- [ ] `GET /sessions` - 사용자별 세션 목록 조회
- [ ] `GET /questions/daily` - 세션 유형별 질문 목록 조회
- [ ] `GET /questions/{question_id}` - 질문 단건 조회
- [ ] `POST /sessions/{session_id}/answers` - 문항별 답변 저장

완료 조건: CIST 5문항을 중단 후 이어서 진행할 수 있고, AI 정서 문답은 별도 세션으로 동작한다.

### 5차. 음성 업로드·오프라인 동기화 API

기반: `recordings` 테이블, 파일 저장소, `client_recording_id` 중복 방지

- [ ] `POST /recordings` - 문항별 음성 파일 업로드
- [ ] `GET /recordings/{recording_id}` - 업로드·STT·분석 처리 상태 조회

완료 조건: 오프라인에서 저장한 음성을 재전송할 수 있고, 같은 `client_recording_id`가 중복 저장되지 않는다.

### 6차. STT·AI 분석·요약 API

기반: 외부 서비스 adapter, 분석 결과 테이블, 비동기 작업 상태

- [ ] `POST /voice/transcribe` - Whisper STT 실행
- [ ] `POST /analysis/acoustic` - AST 음향 특징 분석
- [ ] `POST /analysis/cognitive` - KcELECTRA 텍스트 분석 및 선택적 결과 결합
- [ ] `POST /summary/session` - Gemini 문답 요약 생성
- [ ] `GET /summary/session/{session_id}` - 문답 요약 조회

완료 조건: 음성 업로드 후 STT → AST/KcELECTRA → 점수 집계 → Gemini 요약 순서로 처리되고, 실패 시 재시도 가능하다.

### 7차. 검사 결과·추이·홈·보호자 리포트 API

기반: 분석 결과 집계와 보호자 권한 검증

- [ ] `GET /screenings/{session_id}/result` - 고령자용 검사 결과 조회
- [ ] `GET /analysis/cognitive/{user_id}/history` - 분석 이력·30일 추이 조회
- [ ] `GET /dashboard/{user_id}` - 고령자 홈 요약 조회
- [ ] `GET /guardian/{guardian_id}/report` - 선택한 고령자 종합 리포트 조회

완료 조건: `screening_reference_score`, `risk_level`, `domain_scores`가 화면에서 사용할 수 있는 형태로 반환되고 진단 표현이 없다.

### 8차. 일기·캘린더·보호자 반응 API

기반: `diaries`, `diary_reactions` 테이블과 세션 요약 연결

- [ ] `POST /diaries` - 텍스트·음성 일기 생성
- [ ] `POST /diaries/from-session` - AI 문답 요약으로 일기 생성
- [ ] `GET /diaries/{user_id}` - 날짜별 일기 목록 조회
- [ ] `GET /diaries/{diary_id}` - 일기 상세 조회
- [ ] `PATCH /diaries/{diary_id}` - 일기 수정
- [ ] `DELETE /diaries/{diary_id}` - 일기 삭제
- [ ] `POST /diaries/{diary_id}/reactions` - 보호자 반응·메시지 저장
- [ ] `GET /diaries/{diary_id}/reactions` - 일기 반응 조회
- [ ] `GET /calendar/{user_id}/activities` - 일기·검사·게임·캠페인 활동 조회

완료 조건: 문답 결과를 일기로 저장하고, 캘린더에서 활동을 확인하며, 보호자가 일기에 반응할 수 있다.

### 9차. 게임·캐릭터·캠페인 API

기반: `game_results`, `characters`, `xp_ledger`, `campaigns`, `campaign_participations` 테이블

- [ ] `POST /game/result` - 미니게임 결과 저장
- [ ] `GET /game/{user_id}/history` - 게임 이력 조회
- [ ] `GET /character/{user_id}` - 캐릭터 레벨·경험치·아이템 조회
- [ ] `POST /character/{user_id}/xp` - 출석·방문·대화·게임·캠페인 경험치 적립
- [ ] `GET /campaigns` - 지역 캠페인 목록 조회
- [ ] `GET /campaigns/{campaign_id}` - 지역 캠페인 상세 조회
- [ ] `POST /campaigns/{campaign_id}/participation` - 캠페인 참여
- [ ] `GET /campaigns/{campaign_id}/participation` - 캠페인 참여 상태 조회

완료 조건: 게임 결과와 경험치가 중복 없이 반영되고, 캠페인 참여·완료·보상이 동작한다.

### 10차. 알림 API

기반: `notifications` 테이블과 검사·요약·반응·캠페인 이벤트

- [ ] `POST /notifications/push` - 서비스 내부 알림 생성·발송
- [ ] `GET /notifications/{user_id}` - 알림 목록·미읽음 수 조회
- [ ] `PATCH /notifications/{id}/read` - 알림 읽음 처리
- [ ] `PATCH /notifications/read-all` - 현재 사용자의 미읽음 알림 전체 읽음 처리

완료 조건: 검사 완료, 요약 완료, 보호자 반응, 캠페인 완료 이벤트가 알림으로 연결된다.

### Phase 2. TTS·립싱크 API

- [ ] `POST /voice/synthesize` - 선택한 안내 음성으로 TTS 생성
- [ ] `viseme_timeline` 응답을 캐릭터 립싱크와 연결

> TTS·립싱크는 CIST 핵심 흐름과 보호자 화면이 안정화된 이후 진행한다.

---

## 0. 개발 기준 확정

### 0.1 API 계약 고정

- [ ] 팀 저장소의 [API 명세서](api-spec.md)를 기준 버전 `v1.2`로 확정한다.
- [ ] Base URL을 `local`, `dev`, `prod` 환경별로 분리한다.
- [ ] API 경로, HTTP method, 상태 코드, 필드명, enum을 프론트엔드와 함께 확인한다.
- [ ] `elder`, `guardian` 역할을 확정한다.
- [ ] `session_type`을 `cist`, `emotional_qa`, `game`, `mixed`로 확정한다.
- [ ] 분석 결과의 정규화 필드와 화면 표시 필드를 구분한다: `screening_reference_score`, `display_score`, `score_max`, `score_rate`, `risk_level`, `display_label`.
- [ ] `display_score`와 `score_max`로 `27/30`, `21/30`, `24.1` 형태의 화면 표시를 지원한다.
- [ ] 초대 코드는 `invite_code` 6자리, 만료·1회성 소비·검증 시도 제한 규칙을 따른다.
- [ ] 기존 `dementia_score`는 신규 응답에서 사용하지 않고 deprecated alias 유지 여부를 결정한다.
- [ ] 날짜·시간은 타임존을 포함한 ISO 8601 문자열로 통일한다.
- [ ] ID 생성 규칙을 UUID 또는 프로젝트 공통 ID 규칙으로 확정한다.

### 0.2 작업 방식

- [ ] 각 단계별 Issue를 생성한다.
- [ ] 작업 브랜치를 작업 유형에 따라 `feature/be/#이슈번호-작업명` 또는 `docs/be/#이슈번호-작업명` 형식으로 만든다.
- [ ] API 변경이 생기면 명세서와 프론트엔드 계약을 함께 수정한다.
- [ ] 하나의 PR에는 하나의 기능 흐름만 포함한다.
- [ ] PR마다 테스트 방법과 미완료 항목을 기록한다.

### 0.3 개인정보·의료적 표현 기준

- [ ] 검사 결과가 의료적 진단으로 해석되지 않도록 응답·로그·알림 문구를 검토한다.
- [ ] 화면과 API에서 `치매 확률`, `치매 진단` 같은 확정 표현을 사용하지 않는다.
- [ ] 음성 원본, 전사문, 건강 정보, 보호자 접근 기록의 보관 범위를 정한다.
- [ ] 이름·생년월일·연락처와 음성 학습 데이터를 분리 보관하는 정책을 정한다.

---

## 1. 프로젝트·공통 기반 구축

### 1.1 Spring Boot 프로젝트 초기화

- [ ] Spring Boot와 Java 버전을 팀에서 확정한다.
- [ ] Gradle 프로젝트를 초기화한다.
- [ ] 패키지 구조를 정한다.
  - [ ] `config`
  - [ ] `common`
  - [ ] `auth`
  - [ ] `user`
  - [ ] `guardian`
  - [ ] `session`
  - [ ] `recording`
  - [ ] `analysis`
  - [ ] `diary`
  - [ ] `game`
  - [ ] `campaign`
  - [ ] `notification`
- [ ] 로컬에서 `./gradlew bootRun`이 실행되는지 확인한다.
- [ ] 기본 `/actuator/health` 또는 `/health` 응답을 추가한다.

### 1.2 환경 설정

- [ ] `application.yml`의 공통 설정을 작성한다.
- [ ] `application-local.yml`을 작성한다.
- [ ] `application-dev.yml`을 작성한다.
- [ ] `application-prod.yml`을 작성한다.
- [ ] `.env.example`에 필요한 변수명만 작성한다.
- [ ] JWT secret, DB password, AI API key를 Git에 커밋하지 않는다.
- [ ] PostgreSQL 접속 정보와 connection pool 설정을 추가한다.
- [ ] 파일 저장소 설정을 추가한다. 로컬은 파일 시스템 또는 MinIO, 운영은 object storage를 사용한다.
- [ ] 외부 API timeout, retry 횟수, 최대 업로드 크기를 환경변수로 분리한다.

### 1.3 공통 응답·오류 처리

- [ ] 요청 검증용 `@Valid`와 Bean Validation을 설정한다.
- [ ] 공통 오류 응답을 `{ error, code, detail, request_id }` 형식으로 통일한다.
- [ ] `GlobalExceptionHandler`를 구현한다.
- [ ] `400`, `401`, `403`, `404`, `409`, `413`, `422`, `500`, `503` 예외 매핑을 추가한다.
- [ ] 존재하지 않는 리소스 오류를 일관된 예외로 처리한다.
- [ ] enum·날짜·파일 형식 오류를 명확하게 반환한다.
- [ ] 요청마다 `request_id`를 생성하고 로그와 응답에 연결한다.
- [ ] 사용자 음성·건강 정보·토큰을 애플리케이션 로그에 남기지 않는다.

### 1.4 공통 기술 요소

- [ ] UUID 생성 및 ID 직렬화 규칙을 구현한다.
- [ ] 서버 시간과 사용자 표시 시간을 분리한다.
- [ ] 공통 `PageResponse`를 구현한다.
- [ ] `page`, `limit`, `from_date`, `to_date` 파라미터 검증을 공통화한다.
- [ ] CORS 허용 origin을 환경별로 설정한다.
- [ ] 파일 업로드 확장자·MIME type·용량 검증을 추가한다.
- [ ] API 문서 자동 생성을 위해 OpenAPI 또는 springdoc을 연결한다.
- [ ] 개발용 SQL 로그와 운영용 로그 수준을 분리한다.

### 1단계 완료 조건

- [ ] 로컬 DB에 연결된 상태로 서버가 실행된다.
- [ ] 잘못된 요청이 공통 오류 JSON으로 반환된다.
- [ ] `/health` 또는 `/actuator/health`가 정상 응답한다.
- [ ] 테스트 코드가 최소 1개 이상 실행된다.

---

## 2. 데이터베이스 설계 및 migration

> 엔티티를 먼저 만들고 컨트롤러를 만드는 순서로 진행한다. 테이블명과 컬럼명은 팀 합의 후 migration 파일로 고정한다.

### 2.1 사용자·보안 테이블

- [ ] `users` 테이블을 만든다.
  - [ ] `id`
  - [ ] `email` unique
  - [ ] `password_hash`
  - [ ] `name`
  - [ ] `role`
  - [ ] `birth_date`
  - [ ] `age_group`
  - [ ] `gender`
  - [ ] `phone`
  - [ ] `profile_completed`
  - [ ] `created_at`, `updated_at`
- [ ] `user_profiles` 또는 `users` 확장 컬럼에 초기 건강·생활 정보를 저장한다.
  - [ ] `education_years`
  - [ ] `literacy`
  - [ ] `health_conditions`
  - [ ] `alcohol_use`
  - [ ] `smoking_status`
  - [ ] `hearing_status`
  - [ ] `communication_difficulty`
  - [ ] `smartphone_skill`
- [ ] `user_preferences` 테이블을 만든다.
  - [ ] `preferred_hearing_side`
  - [ ] `voice_profile_id`
  - [ ] `speech_rate`
  - [ ] `subtitle_enabled`
  - [ ] `sound_effect_enabled`
- [ ] `refresh_tokens` 테이블을 만든다.
- [ ] `consents` 테이블을 만든다.
  - [ ] `consent_type`
  - [ ] `agreed`
  - [ ] `agreed_at`
  - [ ] `version`

### 2.2 보호자 테이블

- [ ] `guardian_links` 테이블을 만든다.
- [ ] `guardian_id`, `elder_id`, `status` 조합을 검증한다.
- [ ] `access_scope`를 JSONB 또는 별도 권한 테이블 중 하나로 결정한다.
- [ ] `pending`, `active`, `revoked` 상태 전환 규칙을 정한다.
- [ ] 연결 해제 시 감사 로그를 남길 수 있도록 한다.
- [ ] `guardian_id`, `elder_id`, `status`에 인덱스를 추가한다.

### 2.3 질문·세션·답변 테이블

- [ ] `voice_profiles` 테이블 또는 seed 데이터를 만든다.
- [ ] `questions` 테이블을 만든다.
  - [ ] `question_type`: `orientation`, `memory`, `attention`, `language`, `emotion`
  - [ ] `session_type`
  - [ ] `content`
  - [ ] `hint`
  - [ ] `display_order`
  - [ ] `subtitle_available`
- [ ] CIST 기본 질문 seed 데이터를 등록한다.
- [ ] AI 정서 문답 질문 seed 데이터를 등록한다.
- [ ] `sessions` 테이블을 만든다.
  - [ ] `user_id`
  - [ ] `session_type`
  - [ ] `status`
  - [ ] `current_question_order`
  - [ ] `answered_count`
  - [ ] `total_questions`
  - [ ] `settings`
  - [ ] `started_at`, `ended_at`
- [ ] `answers` 테이블을 만든다.
- [ ] `client_answer_id`를 세션 단위 unique로 설정한다.
- [ ] 답변의 `recording_id`, `transcript_id` nullable 관계를 설계한다.

### 2.4 녹음·AI 분석 테이블

- [ ] `recordings` 테이블을 만든다.
  - [ ] `client_recording_id` unique
  - [ ] `user_id`, `session_id`, `question_id`
  - [ ] 저장 위치와 파일 metadata
  - [ ] `sync_status`
  - [ ] `transcript_status`
  - [ ] `analysis_status`
  - [ ] `recorded_at`
- [ ] `transcripts` 테이블을 만든다.
- [ ] `acoustic_analyses` 테이블을 만든다.
- [ ] `cognitive_analyses` 테이블을 만든다.
- [ ] `screening_results` 또는 세션 집계 결과 테이블을 만든다.
- [ ] `session_summaries` 테이블을 만든다.
- [ ] 모든 분석 결과에 `model_name`, `model_version`, `analyzed_at`을 저장한다.
- [ ] 원본 모델 출력과 사용자 노출 결과를 분리한다.
- [ ] `screening_reference_score`, `risk_level`, `domain_scores` 저장 구조를 결정한다.
- [ ] AST·KcELECTRA 개별 결과를 `model_breakdown`으로 조회할 수 있게 한다.

### 2.5 일기·게임·캐릭터·캠페인·알림 테이블

- [ ] `diaries` 테이블을 만든다.
- [ ] `diary_reactions` 테이블을 만든다.
- [ ] `game_results` 테이블을 만든다.
- [ ] `characters` 테이블을 만든다.
- [ ] 경험치 중복 적립 방지를 위한 `xp_ledger` 또는 이벤트 ID를 설계한다.
- [ ] `campaigns` 테이블을 만든다.
- [ ] `campaign_participations` 테이블에 사용자·캠페인 unique 제약을 추가한다.
- [ ] `notifications` 테이블을 만든다.
- [ ] `audit_logs` 테이블 필요 여부를 결정하고 보호자 접근·동의 변경을 기록한다.

### 2.6 migration·무결성 검증

- [ ] Flyway 또는 Liquibase를 선택한다.
- [ ] 초기 schema migration을 작성한다.
- [ ] seed migration과 운영 데이터 migration을 분리한다.
- [ ] 외래키와 삭제 정책을 설정한다.
- [ ] 개인정보 테이블의 접근 권한을 DB 계정별로 검토한다.
- [ ] 자주 조회하는 컬럼에 인덱스를 추가한다.
  - [ ] `users.email`
  - [ ] `sessions.user_id, started_at`
  - [ ] `recordings.client_recording_id`
  - [ ] `analysis_results.user_id, analyzed_at`
  - [ ] `diaries.user_id, written_at`
  - [ ] `notifications.user_id, is_read, created_at`
- [ ] migration을 빈 DB에서 처음부터 재현한다.
- [ ] migration rollback 또는 복구 절차를 문서화한다.

### 2단계 완료 조건

- [ ] 빈 PostgreSQL에 migration만 실행해 전체 스키마가 생성된다.
- [ ] 중복 이메일, 중복 연결, 중복 오프라인 녹음이 DB 레벨에서도 차단된다.
- [ ] 주요 조회 쿼리에 필요한 인덱스가 존재한다.
- [ ] 테스트용 seed 질문과 음성 profile을 조회할 수 있다.

---

## 3. 인증·보안 구현

### 3.1 회원가입·로그인

- [ ] `POST /auth/register`를 구현한다.
- [ ] 이메일 형식과 중복 이메일을 검증한다.
- [ ] 비밀번호 8자 이상 및 정책을 검증한다.
- [ ] 비밀번호를 BCrypt 등 단방향 해시로 저장한다.
- [ ] `role`을 허용 목록으로 제한한다.
- [ ] 회원가입 응답에서 비밀번호를 절대 반환하지 않는다.
- [ ] `POST /auth/login`을 구현한다.
- [ ] 로그인 성공 시 access token과 refresh token을 발급한다.
- [ ] 로그인 실패 시 이메일 존재 여부를 노출하지 않는다.
- [ ] `POST /auth/oauth/{provider}`를 구현하고 `kakao`, `naver` provider만 허용한다.
- [ ] provider authorization code를 서버에서 교환하고 code·provider token을 로그에 남기지 않는다.
- [ ] `POST /auth/password/reset/request`를 구현하고 등록 이메일 여부를 동일한 응답으로 처리한다.
- [ ] `POST /auth/password/reset/confirm`를 구현하고 reset token을 일회성으로 폐기한다.
- [ ] 비밀번호 재설정 성공 시 기존 refresh token을 폐기한다.

### 3.2 JWT·세션 보안

- [ ] JWT access token 만료 시간을 설정한다.
- [ ] refresh token 저장·폐기 정책을 구현한다.
- [ ] refresh token rotation 여부를 결정한다.
- [ ] `POST /auth/refresh`를 구현한다.
- [ ] `POST /auth/logout`에서 refresh token을 폐기한다.
- [ ] access token 검증 필터를 등록한다.
- [ ] 만료 토큰과 잘못된 토큰을 `401`로 반환한다.
- [ ] `ROLE_ELDER`, `ROLE_GUARDIAN` 권한 매핑을 구현한다.
- [ ] 서버 작업 전용 API에 앱 사용자 토큰으로 접근하지 못하게 한다.

### 3.3 소유권·IDOR 방지

- [ ] 모든 `/{user_id}` API에서 요청 사용자와 대상 사용자의 관계를 확인한다.
- [ ] URL의 `user_id`만 바꿔 다른 고령자의 데이터를 조회할 수 없는지 테스트한다.
- [ ] 보호자가 연결되지 않은 고령자의 데이터를 조회할 수 없는지 테스트한다.
- [ ] 동의하지 않은 대상자의 결과·일기를 조회할 수 없는지 테스트한다.
- [ ] 일기·녹음·세션 ID만 알아도 접근할 수 없는지 테스트한다.
- [ ] 관리자 권한이 필요한 기능을 일반 보호자 권한과 분리한다.

### 3단계 완료 조건

- [ ] 회원가입 → 로그인 → 인증 API 호출 → 토큰 갱신 → 로그아웃 흐름이 동작한다.
- [ ] 인증 없는 보호 API는 `401`을 반환한다.
- [ ] 역할이 맞지 않는 API는 `403`을 반환한다.
- [ ] 주요 리소스의 소유권·연결·동의 검증 테스트가 통과한다.

---

## 4. 사용자 온보딩·동의·청취 설정

### 4.1 사용자 프로필

- [ ] `GET /users/{user_id}`를 구현한다.
- [ ] `PATCH /users/{user_id}`를 구현한다.
- [ ] 이름, 연락처, 연령대, 성별을 저장한다.
- [ ] 학력·교육 연수, 문해 여부를 저장한다.
- [ ] 주요 질환, 음주, 흡연 정보를 선택형으로 저장한다.
- [ ] 청력 상태와 의사소통 어려움을 저장한다.
- [ ] 스마트폰 사용 수준을 저장한다.
- [ ] 초기 정보 입력 완료 여부를 계산한다.
- [ ] 민감 정보는 보호자에게 접근 범위가 있을 때만 반환한다.

### 4.2 동의

- [ ] `POST /consent/{user_id}`를 구현한다.
- [ ] `GET /consent/{user_id}`를 구현한다.
- [ ] `data_sharing`, `guardian_access`, `analysis`, `voice_collection`, `research_use`를 관리한다.
- [ ] 동의 문서 `version`과 동의 시각을 저장한다.
- [ ] 동의 철회 시 이후 데이터 접근·분석 정책을 정의한다.
- [ ] 보호자 연결 활성화 전에 `guardian_access` 동의 상태를 확인한다.

### 4.3 안내 음성·청취 설정

- [ ] `voice_profiles` seed 데이터를 등록한다.
- [ ] `GET /voice-profiles`를 구현한다.
- [ ] `GET /users/{user_id}/preferences`를 구현한다.
- [ ] `PATCH /users/{user_id}/preferences`를 구현한다.
- [ ] `preferred_hearing_side`를 `left`, `right`, `both`, `unknown`으로 제한한다.
- [ ] `speech_rate` 허용 범위를 검증한다.
- [ ] `subtitle_enabled` 기본값을 `false`로 설정한다.
- [ ] 보호자가 청력 보조 목적으로 자막을 켤 수 있는 조건을 구현한다.
- [ ] 효과음 기본값을 꺼진 상태로 설정한다.

### 4단계 완료 조건

- [ ] 회원가입 직후 초기 정보 입력 화면의 API 흐름이 완료된다.
- [ ] 동의하지 않은 보호자 연결이 활성화되지 않는다.
- [ ] 음성 profile 조회와 설정 저장이 정상 동작한다.
- [ ] 설정 응답이 다음 세션 시작에 반영된다.

---

## 5. 보호자 연결과 권한 범위

### 5.1 연결 API

- [ ] `POST /guardian/invitations`로 숫자 6자리 초대 코드를 발급한다.
- [ ] 초대 코드는 기본 만료 시간과 최대 만료 시간을 검증한다.
- [ ] `POST /guardian/invitations/verify`는 연결을 생성하지 않는 미리보기 검증으로 구현한다.
- [ ] `POST /guardian/invitations/accept` 성공 시에만 코드를 소비하고 `guardian_links`를 생성한다.
- [ ] 사용·만료 초대 코드에 `410`, 반복 실패에 `429`를 반환한다.
- [ ] 초대 코드 원문을 DB·URL·로그에 저장하지 않고 단방향 해시로 관리한다.
- [ ] `POST /guardian/link`를 구현한다.
- [ ] `GET /guardian/{guardian_id}/elders`를 구현한다.
- [ ] `PATCH /guardian/link/{link_id}`를 구현한다.
- [ ] `DELETE /guardian/link/{link_id}`를 구현한다.
- [ ] 연결 생성 시 중복 연결을 차단한다.
- [ ] 연결 상태를 `pending`, `active`, `revoked`로 관리한다.
- [ ] 보호자 1명이 여러 고령자를 연결할 수 있는지 확인한다.
- [ ] 한 고령자에게 여러 보호자를 연결할 수 있는지 정책을 반영한다.

### 5.2 접근 범위

- [ ] `screening`, `summary`, `diary`, `activity`, `campaign`, `all` 범위를 구현한다.
- [ ] 요청마다 연결 상태와 access scope를 확인한다.
- [ ] `screening` 권한이 없으면 검사 결과·추이를 반환하지 않는다.
- [ ] `diary` 권한이 없으면 일기와 반응을 반환하지 않는다.
- [ ] `activity` 권한이 없으면 캘린더와 게임 활동을 반환하지 않는다.
- [ ] 권한 변경 시 기존 토큰만으로 우회할 수 없는지 확인한다.
- [ ] 연결 해제 후 기존 URL로 리소스 조회가 불가능한지 테스트한다.

### 5단계 완료 조건

- [ ] 보호자 대시보드에서 연결된 여러 고령자 목록을 조회할 수 있다.
- [ ] `pending` 연결은 동의 전 데이터 조회가 불가능하다.
- [ ] 접근 범위별 허용·거부 테스트가 모두 통과한다.

---

## 6. CIST·AI 정서 문답 핵심 흐름

### 6.1 질문 데이터

- [ ] CIST 질문을 문항 ID와 순서로 seed한다.
- [ ] 질문 유형을 `orientation`, `memory`, `attention`, `language`로 관리한다.
- [ ] AI 정서 문답 질문을 `emotion` 유형으로 seed한다.
- [ ] 질문별 자막 가능 여부를 저장한다.
- [ ] 질문 내용과 정답·채점 기준의 접근 권한을 분리한다.
- [ ] 외부로 공개하면 안 되는 검사 원문·해설지 보관 범위를 검토한다.

### 6.2 세션 시작·조회·종료

- [ ] `POST /sessions`를 구현한다.
- [ ] `session_type`별 질문 세트를 선택한다.
- [ ] 세션 시작 시 음성·청취·자막 설정을 snapshot으로 저장한다.
- [ ] `GET /sessions/{session_id}`를 구현한다.
- [ ] 현재 문항 순서, 답변 수, 전체 문항 수를 반환한다.
- [ ] `PATCH /sessions/{session_id}/settings`를 구현한다.
- [ ] 세션별 자막 설정 변경 권한을 검증한다.
- [ ] `PATCH /sessions/{session_id}/end`를 구현한다.
- [ ] 종료 시 분석 작업을 예약한다.
- [ ] `GET /sessions`를 구현한다.
- [ ] 날짜·세션 유형·페이지네이션 필터를 구현한다.

### 6.3 문항별 답변

- [ ] `POST /sessions/{session_id}/answers`를 구현한다.
- [ ] `client_answer_id`로 오프라인 재전송 중복을 방지한다.
- [ ] 답변에 `recording_id`, `transcript_id`, `answer_text`를 연결한다.
- [ ] 세션에 속하지 않은 질문 ID를 거부한다.
- [ ] 종료된 세션에 새 답변을 저장하지 않는다.
- [ ] 답변 저장과 `answered_count`, `current_question_order` 갱신을 하나의 transaction으로 처리한다.
- [ ] 답변 순서가 뒤섞여도 데이터가 깨지지 않도록 정책을 정한다.
- [ ] `GET /questions/daily`를 구현한다.
- [ ] `GET /questions/{question_id}`를 구현한다.

### 6.4 화면 흐름 검증

- [ ] CIST 화면에서 한 번에 한 문항만 진행된다.
- [ ] `다음`, `다시 듣기`, `처음으로` 동작에 필요한 상태를 프론트엔드가 복구할 수 있다.
- [ ] 앱을 종료했다가 다시 열어도 세션 진행 상태를 조회할 수 있다.
- [ ] AI 정서 문답은 CIST와 다른 질문 세트·세션 유형으로 동작한다.

### 6단계 완료 조건

- [ ] 테스트 계정으로 CIST 5문항 세션을 시작할 수 있다.
- [ ] 각 답변이 문항과 세션에 정확히 연결된다.
- [ ] 세션 중단 후 재진입하면 다음 문항부터 이어진다.
- [ ] 세션 종료 시 분석 대기 상태가 생성된다.

---

## 7. 음성 업로드·오프라인 동기화

### 7.1 파일 저장

- [ ] `POST /recordings` multipart 업로드를 구현한다.
- [ ] `wav`, `m4a`, `mp3`만 허용한다.
- [ ] 최대 25MB 파일 제한을 적용한다.
- [ ] 파일 확장자만 믿지 말고 MIME type과 실제 파일 형식을 함께 검증한다.
- [ ] 저장 파일명에 이름·생년월일을 사용하지 않는다.
- [ ] 서버 UUID 또는 가명화된 식별자로 파일명을 생성한다.
- [ ] 파일 metadata에 사용자·세션·질문·녹음 시각을 저장한다.
- [ ] 로컬 저장소와 운영 object storage를 adapter로 분리한다.

### 7.2 재전송·중복 방지

- [ ] `client_recording_id`를 필수로 받는다.
- [ ] 동일한 `client_recording_id` 재요청 시 기존 recording을 반환한다.
- [ ] `Idempotency-Key` 지원 여부를 결정한다.
- [ ] 네트워크 재연결 후 여러 파일을 순차 전송할 수 있게 한다.
- [ ] 업로드 실패 시 원인을 `error_message`로 저장한다.
- [ ] `GET /recordings/{recording_id}`를 구현한다.
- [ ] `device_saved`, `server_pending`, `server_uploaded`, `analysis_completed`, `failed` 상태 전환을 구현한다.
- [ ] 업로드 완료와 분석 완료를 별도 상태로 관리한다.

### 7.3 개인정보·보존

- [ ] 음성 파일 접근 URL을 공개하지 않는다.
- [ ] 다운로드 URL은 짧은 만료 시간을 가진 signed URL로 제공한다.
- [ ] 원본 보존 기간과 삭제 정책을 정한다.
- [ ] 동의 철회 또는 계정 삭제 시 음성 데이터 처리 정책을 구현한다.
- [ ] 음성 파일과 사용자 식별 정보의 저장 위치를 분리한다.

### 7단계 완료 조건

- [ ] 정상 업로드, 용량 초과, 잘못된 형식, 네트워크 재전송 테스트가 통과한다.
- [ ] 같은 파일을 2번 보내도 recording이 1개만 생성된다.
- [ ] 앱이 오프라인이었다가 연결된 뒤 업로드 상태가 최종적으로 분석 대기까지 전환된다.

---

## 8. STT·AST·KcELECTRA·Gemini 분석 파이프라인

### 8.1 외부 서비스 추상화

- [ ] `SpeechToTextClient` 인터페이스를 만든다.
- [ ] `AcousticAnalysisClient` 또는 AST 실행 adapter를 만든다.
- [ ] `CognitiveAnalysisClient` 또는 KcELECTRA 실행 adapter를 만든다.
- [ ] `SessionSummaryClient` 또는 Gemini 실행 adapter를 만든다.
- [ ] 실제 외부 서비스와 mock 구현을 분리한다.
- [ ] API key, model name, timeout, retry를 설정값으로 관리한다.
- [ ] 외부 서비스 응답 스키마를 내부 DTO로 변환한다.

### 8.2 비동기 작업 흐름

- [ ] recording 업로드 완료 이벤트를 만든다.
- [ ] STT 작업을 예약한다.
- [ ] AST 작업을 예약한다.
- [ ] STT 완료 후 KcELECTRA 작업을 예약한다.
- [ ] AST·KcELECTRA 결과가 모두 준비되면 세션 집계 작업을 예약한다.
- [ ] 문답 세션 종료 후 Gemini 요약 작업을 예약한다.
- [ ] 작업 상태를 `pending`, `processing`, `completed`, `failed`로 관리한다.
- [ ] 작업 실패 시 재시도 횟수와 마지막 오류를 저장한다.
- [ ] 외부 서비스가 지연돼도 API 요청이 오래 붙잡히지 않게 한다.
- [ ] 중복 작업이 실행돼도 결과가 중복 저장되지 않게 한다.

### 8.3 Whisper STT

- [ ] `POST /voice/transcribe`를 구현한다.
- [ ] `recording_id` 기반 서버 작업 호출을 우선 지원한다.
- [ ] `transcript`, `duration_sec`, `confidence`, `language`, `model`을 저장한다.
- [ ] 한국어 `ko` 결과를 기본값으로 처리한다.
- [ ] STT 실패·빈 전사·낮은 confidence 처리 정책을 정한다.
- [ ] 원본 전사문을 수정하지 않고 전처리본을 별도 저장한다.

### 8.4 AST 음향 분석

- [ ] `POST /analysis/acoustic`를 구현한다.
- [ ] 8초 segment 입력 규칙을 반영한다.
- [ ] 짧은 음성의 padding 정책을 정한다.
- [ ] 화자 분리·겹침 발화·소음 metadata 저장 구조를 구현한다.
- [ ] `speech_rate`, `pause_ratio`, `energy_variability`, `speech_stability`를 저장한다.
- [ ] `acoustic_reference_score`와 모델 버전을 저장한다.
- [ ] AST 결과를 KcELECTRA 결과와 분리 보관한다.

### 8.5 KcELECTRA 분석

- [ ] `POST /analysis/cognitive`를 구현한다.
- [ ] 전사문과 질문 유형을 함께 분석한다.
- [ ] 지남력·기억·주의·언어 플래그를 저장한다.
- [ ] 문장 길이·어휘 다양성·의미 일관성 feature 저장 여부를 결정한다.
- [ ] `language_reference_score`와 모델 버전을 저장한다.
- [ ] AST 결과 ID를 선택적으로 연결한다.

### 8.6 점수 집계·사용자 노출 결과

- [ ] 모델별 점수와 결합 점수를 구분한다.
- [ ] `fusion_mode`를 `none`, `average`, `weighted_average`로 관리한다.
- [ ] 결합 가중치는 설정값 또는 모델 버전별 configuration으로 분리한다.
- [ ] `screening_reference_score`를 `0.0~1.0` 범위로 검증한다.
- [ ] `risk_level`을 `normal`, `caution`, `warning`으로 변환한다.
- [ ] `label`을 `normal`, `attention_required`로 반환한다.
- [ ] 영역별 `correct`, `total`, `score_rate`를 저장한다.
- [ ] 사용자 화면용 `display_score`, `score_max`, `score_rate`를 검사 유형별 환산 규칙으로 계산한다.
- [ ] 분석 이력에 직전 동일 집계 결과 대비 `score_delta`를 제공하고 첫 기록은 `null`로 반환한다.
- [ ] 사용자 노출 응답에 내부 모델 raw output을 포함하지 않는다.

### 8.7 Gemini 문답 요약

- [ ] `POST /summary/session`을 구현한다.
- [ ] `GET /summary/session/{session_id}`를 구현한다.
- [ ] 질문·답변 쌍을 문항 순서대로 전달한다.
- [ ] `summary`, `vocabulary_score`, `keyword_flags`를 저장한다.
- [ ] 요약 결과가 없을 때 `source_status=pending`을 반환한다.
- [ ] 모델 응답에 의료적 진단 표현이 포함되지 않도록 후처리·검수 정책을 정한다.

### 8단계 완료 조건

- [ ] mock 외부 서비스로 업로드 → STT → AST/KcELECTRA → 집계 → 요약 전체 흐름이 통과한다.
- [ ] 외부 서비스 실패 시 녹음·세션 데이터가 유실되지 않는다.
- [ ] 동일 recording에 대한 중복 분석 결과가 생성되지 않는다.
- [ ] 결과 API가 `screening_reference_score`와 안전한 화면 문구를 반환한다.

---

## 9. 검사 결과·추이·홈·보호자 리포트

### 9.1 고령자 결과

- [ ] `GET /screenings/{session_id}/result`를 구현한다.
- [ ] 최근 검사 결과와 영역별 점수를 반환한다.
- [ ] `display_score`, `score_max`, `score_rate`를 반환해 화면 점수 형식을 구성한다.
- [ ] `display_label`과 `recommendation`을 안전한 문구로 반환한다.
- [ ] 분석이 끝나지 않았으면 `pending` 상태를 구분한다.
- [ ] 결과 화면에서 일기 생성에 사용할 `summary_id`를 연결한다.

### 9.2 이력·추이

- [ ] `GET /analysis/cognitive/{user_id}/history`를 구현한다.
- [ ] `answer`, `session`, `user` 집계 범위를 지원한다.
- [ ] 30일 평균과 `improving`, `declining`, `stable` 추이를 계산한다.
- [ ] 분석 이력에 정규화 점수와 표시 점수(`display_score`, `score_max`, `score_rate`)를 함께 반환한다.
- [ ] 직전 결과 대비 `score_delta`의 기준을 동일한 집계 단위로 고정한다.
- [ ] 동일 날짜에 여러 결과가 있을 때 집계 규칙을 정한다.
- [ ] 표본 부족 시 추이를 `stable`로 단정하지 않고 상태를 별도 반환할지 결정한다.

### 9.3 고령자 홈

- [ ] `GET /dashboard/{user_id}`를 구현한다.
- [ ] 캐릭터 상태, 최근 검사, 최근 요약, 오늘 할 일, 미읽음 알림 수를 조합한다.
- [ ] 여러 API를 호출하는 대신 서버 aggregation으로 제공할지 결정한다.
- [ ] 데이터가 없는 신규 사용자의 빈 상태 응답을 정의한다.

### 9.4 보호자 리포트

- [ ] `GET /guardian/{guardian_id}/report`를 구현하고 `elder_id`를 필수 query parameter로 받는다.
- [ ] 최근 요약, 참고 점수, 위험 상태, 게임 지표, 30일 추이를 반환한다.
- [ ] 보호자 리포트에서 정규화 점수와 표시 점수(`latest_display_score`, `latest_score_max`, `latest_score_rate`)를 구분한다.
- [ ] `trend_points[]`를 차트가 바로 사용할 수 있는 형식으로 제공한다.
- [ ] `activity_summary_7d`를 세션·게임·일기 활동으로 구성한다.
- [ ] `recent_alerts[]`에 보호자가 확인해야 할 이벤트만 포함한다.
- [ ] 리포트 조회 전 연결·동의·access scope를 검증한다.

### 9단계 완료 조건

- [ ] 고령자 결과 화면이 실제 API 응답만으로 구성된다.
- [ ] 보호자 계정으로 여러 고령자 카드를 조회할 수 있다.
- [ ] 연결되지 않은 고령자의 리포트가 절대 노출되지 않는다.
- [ ] 결과·리포트 문구에 진단으로 오해할 표현이 없다.

---

## 10. 일기·캘린더·보호자 반응

### 10.1 일기

- [ ] `POST /diaries`를 구현한다.
- [ ] `POST /diaries/from-session`을 구현한다.
- [ ] `GET /diaries/{user_id}`를 구현한다.
- [ ] `GET /diaries/{diary_id}`를 구현한다.
- [ ] `PATCH /diaries/{diary_id}`를 구현한다.
- [ ] `DELETE /diaries/{diary_id}`를 구현한다.
- [ ] `source_type`을 `manual`, `voice`, `session`으로 관리한다.
- [ ] 일기에 `mood`와 `mood_level`을 저장하고 캘린더 활동의 `metadata`에 포함한다.
- [ ] `mood`를 `very_sad`, `sad`, `neutral`, `happy`, `very_happy`로 제한하고 `mood_level`을 `1~5`로 검증한다.
- [ ] 문답 요약에서 일기를 만들 때 사용자가 본문을 수정할 수 있게 한다.
- [ ] 작성자만 일기를 수정·삭제할 수 있도록 한다.

### 10.2 보호자 반응

- [ ] `POST /diaries/{diary_id}/reactions`를 구현한다.
- [ ] `GET /diaries/{diary_id}/reactions`를 구현한다.
- [ ] `heart`, `smile`, `cheer`, `pray`, `cry`, `message`를 허용한다.
- [ ] `message` 반응일 때 메시지를 필수로 한다.
- [ ] 반응 작성자와 대상 일기의 연결 권한을 확인한다.
- [ ] 같은 사용자가 동일 일기에 같은 반응을 여러 번 남길 수 있는지 정책을 정한다.

### 10.3 캘린더

- [ ] `GET /calendar/{user_id}/activities`를 구현한다.
- [ ] `diary`, `screening`, `emotional_qa`, `game`, `campaign` 활동을 통합한다.
- [ ] 날짜 범위와 활동 유형 필터를 지원한다.
- [ ] 각 활동의 `reference_id`로 상세 화면 이동이 가능하게 한다.
- [ ] 일기 활동의 감정 아이콘을 `metadata.mood`와 `metadata.mood_level`로 표시한다.
- [ ] 활동이 없는 날짜의 빈 응답을 정의한다.

### 10단계 완료 조건

- [ ] AI 문답 결과를 일기로 저장할 수 있다.
- [ ] 캘린더에서 일기와 검사·게임 활동을 날짜별로 확인할 수 있다.
- [ ] 보호자가 일기를 열람하고 반응을 남길 수 있다.

---

## 11. 게임·캐릭터·지역 캠페인

### 11.1 미니게임

- [ ] `POST /game/result`를 구현한다.
- [ ] `image_match`, `consonant`, `word_match`를 허용한다.
- [ ] 점수, 응답 시간 배열, 오답 수, 전체 문항 수를 검증한다.
- [ ] `cognitive_index` 계산 규칙을 문서화한다.
- [ ] `GET /game/{user_id}/history`를 구현한다.
- [ ] 보호자에게 게임 이력을 노출할 때 access scope를 검증한다.

### 11.2 캐릭터·경험치

- [ ] `GET /character/{user_id}`를 구현한다.
- [ ] `POST /character/{user_id}/xp`를 구현한다.
- [ ] 출석·방문·대화·캠페인·게임별 경험치 정책을 정한다.
- [ ] 동일 이벤트가 재처리돼도 경험치가 중복 적립되지 않게 한다.
- [ ] 레벨업 transaction과 응답 필드를 구현한다.
- [ ] 캐릭터 상태가 고령자 홈에 표시되도록 dashboard와 연결한다.

### 11.3 지역 캠페인

- [ ] 캠페인 관리용 초기 데이터를 등록한다.
- [ ] `GET /campaigns`를 구현한다.
- [ ] `GET /campaigns/{campaign_id}`를 구현한다.
- [ ] `POST /campaigns/{campaign_id}/participation`을 구현한다.
- [ ] `GET /campaigns/{campaign_id}/participation`을 구현한다.
- [ ] 지역·기간·상태 필터를 지원한다.
- [ ] 동일 사용자의 중복 참여를 차단한다.
- [ ] 캠페인 완료 시 경험치와 알림을 연결한다.

### 11단계 완료 조건

- [ ] 게임 결과가 이력과 캐릭터 경험치에 반영된다.
- [ ] 동일 게임 결과 재전송으로 경험치가 중복되지 않는다.
- [ ] 캠페인 목록 조회·참여·완료 상태가 동작한다.

---

## 12. 알림 구현

### 12.1 알림 저장·조회

- [ ] `notifications` 저장 로직을 구현한다.
- [ ] `POST /notifications/push`를 서버 내부 호출용으로 구현한다.
- [ ] `GET /notifications/{user_id}`를 구현한다.
- [ ] `PATCH /notifications/{id}/read`를 구현한다.
- [ ] `PATCH /notifications/read-all`을 구현한다.
- [ ] 미읽음 수를 정확하게 계산한다.
- [ ] `unread_only`, `type`, `limit` 필터를 구현한다.
- [ ] 알림의 `data`에 화면 이동용 reference ID를 저장한다.

### 12.2 이벤트 연결

- [ ] 검사 분석 완료 시 고령자 알림을 생성한다.
- [ ] 보호자에게 위험 신호 알림을 보낼 조건을 정의한다.
- [ ] AI 정서 문답 요약 완료 시 알림을 생성한다.
- [ ] 보호자 반응 등록 시 고령자 알림을 생성한다.
- [ ] 캠페인 참여 완료 시 알림을 생성한다.
- [ ] 주간 리포트 알림의 생성 시점을 정의한다.
- [ ] 동일 이벤트에 알림이 중복 생성되지 않게 한다.

### 12단계 완료 조건

- [ ] 알림 목록과 읽음 처리가 동작한다.
- [ ] 알림 화면의 모두 읽음 동작이 현재 인증 사용자에게만 적용된다.
- [ ] 검사 완료·반응·캠페인 이벤트가 알림으로 연결된다.
- [ ] 수신자 외 사용자가 알림을 읽거나 수정할 수 없다.

---

## 13. 통합 테스트·계약 테스트·운영 준비

### 13.1 단위·통합 테스트

- [ ] 서비스 계층 단위 테스트를 작성한다.
- [ ] Repository와 migration 통합 테스트를 작성한다.
- [ ] Controller validation 테스트를 작성한다.
- [ ] 공통 오류 응답 테스트를 작성한다.
- [ ] JWT 만료·역할·소유권 테스트를 작성한다.
- [ ] 연결·동의·access scope 테스트를 작성한다.
- [ ] 페이지네이션·날짜 필터 테스트를 작성한다.
- [ ] 파일 업로드 형식·용량 제한 테스트를 작성한다.
- [ ] 오프라인 recording 중복 전송 테스트를 작성한다.
- [ ] 외부 AI는 mock으로 성공·실패·timeout 테스트를 작성한다.

### 13.2 핵심 E2E 시나리오

#### 시나리오 A. 고령자 온보딩 → CIST → 결과

- [ ] 회원가입
- [ ] 초기 사용자 정보 저장
- [ ] 개인정보·분석 동의 저장
- [ ] 음성 profile과 잘 들리는 귀 선택
- [ ] CIST 세션 시작
- [ ] 질문 목록 조회
- [ ] 문항별 음성 업로드
- [ ] 답변 저장
- [ ] 세션 종료
- [ ] STT·AST·KcELECTRA 분석 완료 확인
- [ ] 검사 결과 조회
- [ ] 문답 요약 조회

#### 시나리오 B. 오프라인 녹음 → 재연결 동기화

- [ ] 네트워크가 없는 상태에서 기기 녹음 metadata 생성
- [ ] `device_saved` 상태 유지
- [ ] 네트워크 복구 후 `POST /recordings` 재전송
- [ ] `server_uploaded` 상태 확인
- [ ] 동일 recording 재전송 시 `deduplicated=true` 확인
- [ ] 분석 완료 후 `analysis_completed` 확인

#### 시나리오 C. 보호자 다중 대상자

- [ ] 보호자 회원가입
- [ ] 고령자 2명 이상 연결 요청
- [ ] 대상자 동의 후 연결 활성화
- [ ] 연결된 고령자 목록 조회
- [ ] 대상자별 리포트 조회
- [ ] 연결되지 않은 고령자 조회 시 `403` 확인
- [ ] access scope를 줄인 뒤 제한 데이터가 반환되지 않는지 확인

#### 시나리오 D. AI 정서 문답 → 일기 → 보호자 반응

- [ ] `emotional_qa` 세션 시작
- [ ] 질문·답변 저장
- [ ] Gemini 요약 생성
- [ ] 요약으로 일기 생성
- [ ] 보호자 일기 조회
- [ ] 보호자 반응 저장
- [ ] 고령자 알림 생성·조회

#### 시나리오 E. 게임 → 캐릭터 → 캠페인

- [ ] 게임 결과 저장
- [ ] 게임 이력 조회
- [ ] 경험치 적립
- [ ] 레벨업 여부 확인
- [ ] 캠페인 목록 조회
- [ ] 캠페인 참여
- [ ] 완료 보상과 알림 확인

### 13.3 API 계약 검증

- [ ] OpenAPI 문서와 `docs/api-spec.md`의 endpoint 목록을 대조한다.
- [ ] 모든 endpoint의 method와 path가 일치하는지 확인한다.
- [ ] request field명과 response field명이 프론트엔드 타입과 일치하는지 확인한다.
- [ ] enum 값이 프론트엔드 상수와 일치하는지 확인한다.
- [ ] `null` 가능 여부를 명세에 맞춘다.
- [ ] 기본값과 페이지네이션 동작을 확인한다.
- [ ] 초대 코드 입력 API에서 code가 path/query로 노출되지 않고, 발급·검증·수락 endpoint의 상태 전이가 일치하는지 확인한다.
- [ ] 사용·만료 초대 코드의 `410`, 반복 검증 실패의 `429`, 동의 거부의 `422` 응답을 계약 테스트한다.
- [ ] `PATCH /notifications/read-all`이 개별 읽음 endpoint와 충돌하지 않고 현재 사용자 알림만 변경하는지 확인한다.
- [ ] Postman 또는 Bruno collection을 만든다.
- [ ] 개발 서버에서 프론트엔드가 사용하는 API를 실제로 호출해 본다.

### 13.4 성능·장애 테스트

- [ ] 보호자 대시보드의 다중 대상자 조회 성능을 측정한다.
- [ ] 분석 이력·캘린더 목록의 페이지네이션을 확인한다.
- [ ] 동시 음성 업로드 처리량을 측정한다.
- [ ] 외부 AI timeout 시 API가 즉시 `pending` 또는 `503`을 반환하는지 확인한다.
- [ ] 분석 작업이 재시작돼도 중복 결과가 생성되지 않는지 확인한다.
- [ ] PostgreSQL connection pool과 파일 저장소 용량을 모니터링한다.

### 13.5 배포·운영

- [ ] Dockerfile 또는 배포 실행 방법을 작성한다.
- [ ] 운영 DB migration 실행 순서를 정한다.
- [ ] 환경변수와 secret 주입 방식을 정한다.
- [ ] `/health`와 DB·파일 저장소·외부 AI 상태 점검을 분리한다.
- [ ] 에러 로그와 request ID로 요청을 추적할 수 있게 한다.
- [ ] 음성 파일 보관 용량과 삭제 작업을 모니터링한다.
- [ ] DB 백업·복구 절차를 문서화한다.
- [ ] 장애 시 분석 작업 재처리 방법을 문서화한다.
- [ ] 개인정보 접근·동의 변경·파일 삭제 audit log를 확인한다.

### 13단계 완료 조건

- [ ] 핵심 E2E 시나리오 A~E가 모두 통과한다.
- [ ] API 계약 불일치가 없다.
- [ ] 인증·권한·개인정보·중복 처리 테스트가 통과한다.
- [ ] 운영 환경에서 migration·health check·로그 추적이 가능하다.

---

## 14. Phase 2 후속 기능

핵심 CIST와 보호자 흐름이 안정화된 후 진행한다.

- [ ] `POST /voice/synthesize`를 구현한다.
- [ ] 사용자 선택 음성·말하기 속도를 TTS에 연결한다.
- [ ] `audio_url` 만료 정책을 구현한다.
- [ ] `viseme_timeline` 응답 형식을 프론트엔드 캐릭터 애니메이션과 합의한다.
- [ ] GLB 캐릭터의 음성 재생·입 모양·표정 이벤트 연동을 검증한다.
- [ ] AST·KcELECTRA fusion weight를 실제 검증 데이터로 재평가한다.
- [ ] 독립 화자 데이터 기준의 모델 평가 결과를 저장한다.
- [ ] 직접 수집 음성의 익명화·연구 활용 동의 흐름을 별도로 검토한다.

### 14.1 Figma 화면 추가 검토

화면에 보이지만 MVP 범위와 외부 연동 여부가 확정되지 않은 기능은 별도 Issue에서 API 계약을 확정한 뒤 구현한다.

- [ ] 지역 기준선 비교에 필요한 데이터 출처·지역 query·개인정보 기준을 확정한다.
- [ ] 보호자 리포트 내보내기의 파일 형식·비동기 생성·다운로드 권한·보존 기간을 확정한다.
- [ ] 전문의 상담 예약의 기관 연동·개인정보 제공 동의·예약 상태를 확정한다.
- [ ] 위 기능을 v1.2 MVP endpoint에 포함할지 결정하고, 미확정이면 후속 Issue로 분리한다.

## 최종 완료 체크

- [ ] API 명세와 실제 구현이 일치한다.
- [ ] 모든 MVP endpoint에 controller, service, repository, validation, test가 있다.
- [ ] 모든 보호 API에 인증·소유권·연결·동의 검증이 있다.
- [ ] 음성·건강·보호자 데이터가 최소 권한으로 보호된다.
- [ ] 분석 실패와 재시도 상태를 사용자가 확인할 수 있다.
- [ ] 오프라인 녹음 재전송과 중복 방지가 동작한다.
- [ ] 결과 문구가 의료적 진단으로 오해되지 않는다.
- [ ] 고령자 화면·보호자 화면의 주요 API 흐름이 E2E 테스트를 통과한다.
- [ ] 배포·migration·백업·장애 대응 방법이 문서화되어 있다.
