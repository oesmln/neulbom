# 늘봄(NEULBOM) 백엔드 개발 체크리스트

> API v1.3 명세서를 실제 Spring Boot 백엔드로 구현하기 위한 순서형 체크리스트
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

- [x] `POST /auth/register` - 회원가입
- [x] `POST /auth/login` - 로그인 및 access token·refresh token 발급
- [x] `POST /auth/oauth/{provider}` - 카카오·네이버 소셜 로그인 및 access token·refresh token 발급
- [x] `POST /auth/password/reset/request` - 비밀번호 재설정 요청
- [x] `POST /auth/password/reset/confirm` - 비밀번호 재설정 확정
- [x] `POST /auth/refresh` - access token 갱신
- [x] `POST /auth/logout` - refresh token 폐기
- [ ] `PATCH /users/me/password` - 현재 비밀번호 확인 후 비밀번호 변경
- [x] `DELETE /users/me` - 회원탈퇴 및 계정 비활성화

비밀번호 재설정의 `PasswordResetNotifier` 전달 경계와 token 저장·폐기 로직은 구현했다. 실제 이메일·SMS provider 연결과 email/IP rate limit은 운영 준비 작업으로 남아 있다.

완료 조건: 회원가입 → 로그인 → 인증 API 호출 → 토큰 갱신 → 로그아웃 흐름이 동작한다.

### 2차. 사용자 온보딩·동의·설정 API

기반: 인증 API와 `users`, `user_preferences`, `consents`, `voice_profiles` 테이블

- [x] `GET /users/{user_id}` - 프로필·초기 사용자 정보 조회
- [x] `PATCH /users/{user_id}` - 프로필·학력·건강·생활습관 정보 수정
- [x] `GET /users/{user_id}/preferences` - 청취·음성·자막 설정 조회
- [x] `PATCH /users/{user_id}/preferences` - 잘 들리는 귀·음성·속도·자막·알림 설정 저장
- [x] `GET /voice-profiles` - 선택 가능한 안내 음성 목록 조회
- [x] `POST /consent/{user_id}` - 개인정보·음성·분석·보호자 접근 동의 저장
- [x] `GET /consent/{user_id}` - 동의 상태 조회

완료 조건: 신규 고령자가 최초 검사 전에 필요한 정보를 저장하고, 본인 외 접근을 차단하며, 동의하지 않은 기능의 실제 활성화 차단은 보호자 연결·기능 이벤트 단계에서 추가 검증한다.

구현 근거: `UserOnboardingController`·`UserOnboardingService`와 각 Repository, 기본 설정·enum·범위 검증, owner 권한·음성 profile·동의 버전 중복 테스트를 반영했다. 보호자 연결에 따른 대리 조회 권한은 3차 구현에서 연결한다.

### 3차. 보호자 연결 API

기반: `guardian_links` 테이블, 역할·동의·access scope 검사

- [x] `POST /guardian/invitations` - 6자리 초대 코드 발급
- [x] `POST /guardian/invitations/verify` - 초대 코드 검증 및 연결 정보 미리보기
- [x] `POST /guardian/invitations/accept` - 초대 코드 수락 및 보호자 연결 생성
- [x] `POST /guardian/link` - 고령자 연결 요청
- [x] `GET /guardian/{guardian_id}/elders` - 연결된 고령자 목록 조회
- [x] `PATCH /guardian/link/{link_id}` - 연결 상태·접근 범위 수정
- [x] `DELETE /guardian/link/{link_id}` - 연결 해제

완료 조건: 초대 코드 입력부터 연결 생성까지 동작하고, 한 보호자가 여러 고령자를 관리하며, 연결·동의·접근 범위에 따라 데이터가 제한된다.

구현 근거: `GuardianController`·`GuardianService`·`GuardianAccessService`와 초대/연결 scope Repository를 추가했다. 원문 코드 비저장, 만료·1회성 소비·IP 실패 제한, 역할·소유권·동의·scope 테스트를 반영했으며, 분석/리포트 API에서 `GuardianAccessService`를 재사용한다.

### 4차. CIST·AI 정서 문답 세션 API

기반: `questions`, `sessions`, `answers` 테이블과 질문 seed 데이터

- [x] `POST /sessions` - `cist`, `emotional_qa`, `game`, `mixed` 세션 시작
- [x] `GET /sessions/{session_id}` - 세션 상태·현재 문항·진행률 조회
- [x] `PATCH /sessions/{session_id}/settings` - 세션별 음성·청취·자막 설정 적용
- [x] `PATCH /sessions/{session_id}/end` - 세션 종료·정성 결과·경험치 적립 상태 반환
- [x] `GET /sessions` - 사용자별 세션 목록 조회
- [x] `GET /sessions/{session_id}/answers` - 대화 질문·답변·전사 내역 조회
- [x] `GET /questions/daily` - 세션 유형별 질문 목록 조회
- [x] `GET /questions/{question_id}` - 질문 단건 조회
- [x] `POST /sessions/{session_id}/answers` - 문항별 답변 저장

완료 조건: CIST 5문항을 중단 후 이어서 진행할 수 있고, AI 정서 문답은 별도 세션으로 동작한다.

구현 근거: `SessionController`·`SessionService`와 세션/질문/답변 Repository를 추가했다. 본인 쓰기, 보호자 `screening`/`summary` scope 읽기, 세션 설정 검증, 답변 콘텐츠·순서·시간 검증, `client_answer_id` 멱등 처리, 종료 후 변경 차단 테스트를 반영했다. STT 전사 ID의 실제 처리와 분석 결과·경험치 연동은 후속 녹음/분석/게임 단계에서 연결한다.

### 5차. 음성 업로드·오프라인 동기화 API

기반: `recordings` 테이블, 파일 저장소, `client_recording_id` 중복 방지

- [ ] `POST /recordings` - 문항 답변 또는 독립 음성 일기 파일 업로드
- [ ] `GET /recordings/{recording_id}` - 업로드·STT·분석 처리 상태 조회

완료 조건: 오프라인에서 저장한 음성을 재전송할 수 있고, 같은 `client_recording_id`가 중복 저장되지 않는다.

### 6차. STT·AI 분석·요약 API

기반: 외부 서비스 adapter, 분석 결과 테이블, 비동기 작업 상태

- [ ] `POST /voice/transcribe` - Whisper STT 실행
- [ ] `POST /analysis/acoustic` - AST 음향 특징 분석
- [ ] `POST /analysis/cognitive` - KcELECTRA 텍스트 분석 및 선택적 결과 결합
- [ ] `POST /summary/session` - Gemini 문답 요약 생성
- [ ] `GET /summary/session/{session_id}` - 문답 요약 조회
- [ ] `POST /summary/daily` - 하루 대화 분석 결과 집계
- [ ] `GET /summary/daily/{user_id}` - 날짜별 대화 집계 요약 조회

완료 조건: 음성 업로드 후 STT → AST/KcELECTRA → 점수 집계 → Gemini 요약 순서로 처리되고, 실패 시 재시도 가능하다.

### 7차. 검사 결과·추이·홈·보호자 리포트 API

기반: 분석 결과 집계와 보호자 권한 검증

- [ ] `GET /screenings/{session_id}/result` - 고령자용 검사 결과 조회
- [ ] `GET /analysis/cognitive/{user_id}/history` - 분석 이력·30일 추이 조회
- [ ] `GET /analysis/cognitive/{user_id}/benchmark` - 보호자용 지역 기준선 비교 조회
- [ ] `GET /dashboard/{user_id}` - 고령자 홈 요약 조회
- [ ] `GET /guardian/{guardian_id}/report` - 선택한 고령자 종합 리포트 및 날짜별 집계 조회
- [ ] `GET /guardian/{guardian_id}/report/export` - 보호자 리포트 PDF·CSV 내보내기

완료 조건: 고령자에게는 정성 결과와 안전한 문구가 반환되고, 보호자에게만 `screening_reference_score`, `risk_level`, `domain_scores`가 권한 검증 후 반환되며 진단 표현이 없다.

### 8차. 일기·캘린더·보호자 반응 API

기반: `diaries`, `diary_reactions` 테이블과 세션 요약 연결

- [ ] `POST /diaries` - 텍스트·음성 일기 생성
- [ ] `POST /diaries/from-session` - AI 문답 요약으로 일기 생성
- [ ] `POST /diaries/from-daily-summary` - 하루 대화 집계 요약으로 일기 생성
- [ ] `GET /diaries/{user_id}/generation-status` - 날짜별 0시 일기 생성 상태 조회
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
- [ ] `GET /character/{user_id}/xp-history` - 경험치 획득 내역 조회
- [ ] `POST /character/{user_id}/xp` - 정서 문답·게임 완료 이벤트 기반 경험치 자동 적립
- [ ] `GET /campaigns` - 지역 캠페인 목록 조회 (Phase 2)
- [ ] `GET /campaigns/{campaign_id}` - 지역 캠페인 상세 조회 (Phase 2)
- [ ] `POST /campaigns/{campaign_id}/participation` - 캠페인 참여 (Phase 2)
- [ ] `GET /campaigns/{campaign_id}/participation` - 캠페인 참여 상태 조회 (Phase 2)

완료 조건: 게임 결과와 경험치가 중복 없이 반영되고, 캠페인 참여·완료·보상이 동작한다.

### 10차. 상담 센터 API

- [ ] `GET /counseling/centers` - 지역별 상담 센터 목록·지도·기관 사이트 링크 조회
- [ ] `GET /counseling/centers/{center_id}/availability` - 상담 가능 시간 조회 (Phase 2)
- [ ] `POST /counseling/appointments` - 상담 예약 생성 (Phase 2)
- [ ] `GET /counseling/appointments` - 본인 상담 예약 목록 조회 (Phase 2)
- [ ] `DELETE /counseling/appointments/{appointment_id}` - 상담 예약 취소 (Phase 2)

완료 조건: 지역 선택 후 센터 목록과 외부 연결이 동작하고, Phase 2에서는 동의·기관 연동 조건 아래 예약·취소까지 동작한다.

### 11차. 알림 API

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

- [x] 팀 저장소의 [API 명세서](api-spec.md)를 기준 버전 `v1.3`으로 확정한다.
- [ ] Base URL을 `local`, `dev`, `prod` 환경별로 분리한다.
- [ ] API 경로, HTTP method, 상태 코드, 필드명, enum을 프론트엔드와 함께 확인한다.
- [x] `elder`, `guardian` 역할을 확정한다.
- [x] `session_type`을 `cist`, `emotional_qa`, `game`, `mixed`로 확정한다.
- [x] 분석 결과의 정규화 필드와 화면 표시 필드를 구분한다: `screening_reference_score`, `display_score`, `score_max`, `score_rate`, `risk_level`, `display_label`.
- [ ] `display_score`와 `score_max`로 `27/30`, `21/30`, `24.1` 형태의 화면 표시를 지원한다.
- [x] 초대 코드는 `invite_code` 6자리, 만료·1회성 소비·검증 시도 제한 규칙을 따른다.
- [x] 기존 `dementia_score`는 신규 응답에서 사용하지 않고 deprecated alias 유지 여부를 결정한다.
- [x] 날짜·시간은 타임존을 포함한 ISO 8601 문자열로 통일한다.
- [ ] AI 정서 문답 세션 종료 시 고령자에게 `result_type`, `display_label`, `message`, `recommendation`만 제공하고 정확한 점수는 보호자에게만 제공한다.
- [ ] 하루 집계 기준을 `Asia/Seoul`의 `00:00~다음 날 00:00`으로 고정한다.
- [ ] 세션별 분석은 종료 후 생성하고, 일일 집계·보호자 리포트·일기 생성은 하루 종료 후 실행한다.
- [x] Figma에 노출된 모든 화면 기능을 구현 범위로 확정하고 화면별 API 연결표와 체크리스트를 1:1로 유지한다.
- [x] 상담 센터 MVP는 지역별 목록과 외부 지도·기관 사이트 연결로 구현하고, 실시간 예약은 Phase 2 구현 범위로 유지한다.
- [x] 지역 지정 캠페인은 초기 MVP에서 제외하고 Phase 2로 관리한다.
- [x] ID 생성 규칙을 UUID 또는 프로젝트 공통 ID 규칙으로 확정한다.

### 0.2 작업 방식

- [ ] 각 단계별 Issue를 생성한다.
- [x] 작업 브랜치를 작업 유형에 따라 `feature/be/#이슈번호-작업명` 또는 `docs/be/#이슈번호-작업명` 형식으로 만든다.
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

- [x] Spring Boot와 Java 버전을 팀에서 확정한다.
- [x] Gradle 프로젝트를 초기화한다.
- [x] 패키지 구조를 정한다.
  - [x] `config`
  - [x] `common`
  - [x] `auth`
  - [x] `user`
  - [x] `guardian`
  - [x] `session`
  - [x] `recording`
  - [x] `analysis`
  - [x] `diary`
  - [x] `game`
  - [x] `campaign`
  - [x] `notification`
- [x] 로컬에서 `./gradlew bootRun`이 실행되는지 확인한다.
- [x] 기본 `/actuator/health` 또는 `/health` 응답을 추가한다.

### 1.2 환경 설정

- [x] `application.yml`의 공통 설정을 작성한다.
- [x] `application-local.yml`을 작성한다.
- [x] `application-dev.yml`을 작성한다.
- [x] `application-prod.yml`을 작성한다.
- [x] `.env.example`에 필요한 변수명만 작성한다.
- [x] JWT secret, DB password, AI API key를 Git에 커밋하지 않는다.
- [x] PostgreSQL 접속 정보와 connection pool 설정을 추가한다.
- [x] 파일 저장소 설정을 추가한다. 로컬은 파일 시스템 또는 MinIO, 운영은 object storage를 사용한다.
- [x] 외부 API timeout, retry 횟수, 최대 업로드 크기를 환경변수로 분리한다.

### 1.3 공통 응답·오류 처리

- [x] 요청 검증용 `@Valid`와 Bean Validation을 설정한다.
- [x] 공통 오류 응답을 `{ error, code, detail, request_id }` 형식으로 통일한다.
- [x] `GlobalExceptionHandler`를 구현한다.
- [x] `400`, `401`, `403`, `404`, `409`, `413`, `422`, `500`, `503` 예외 매핑을 추가한다.
- [x] 존재하지 않는 리소스 오류를 일관된 예외로 처리한다.
- [x] enum·날짜·파일 형식 오류를 명확하게 반환한다.
- [x] 요청마다 `request_id`를 생성하고 로그와 응답에 연결한다.
- [x] 사용자 음성·건강 정보·토큰을 애플리케이션 로그에 남기지 않는다.

### 1.4 공통 기술 요소

- [x] UUID 생성 및 ID 직렬화 규칙을 구현한다.
- [x] 서버 시간과 사용자 표시 시간을 분리한다.
- [x] 공통 `PageResponse`를 구현한다.
- [x] `page`, `limit`, `from_date`, `to_date` 파라미터 검증을 공통화한다.
- [x] CORS 허용 origin을 환경별로 설정한다.
- [x] 파일 업로드 확장자·MIME type·용량 검증을 추가한다.
- [x] API 문서 자동 생성을 위해 OpenAPI 또는 springdoc을 연결한다.
- [x] 개발용 SQL 로그와 운영용 로그 수준을 분리한다.

### 1단계 완료 조건

- [x] 로컬 DB에 연결된 상태로 서버가 실행된다.
- [x] 잘못된 요청이 공통 오류 JSON으로 반환된다.
- [x] `/health` 또는 `/actuator/health`가 정상 응답한다.
- [x] 테스트 코드가 최소 1개 이상 실행된다.

---

## 2. 데이터베이스 설계 및 migration

> 엔티티를 먼저 만들고 컨트롤러를 만드는 순서로 진행한다. 테이블명과 컬럼명은 팀 합의 후 migration 파일로 고정한다.

### 2.1 사용자·보안 테이블

- [x] `users` 테이블을 만든다.
  - [x] `id`
  - [x] `email` unique
  - [x] `password_hash`
  - [x] `name`
  - [x] `role`
  - [x] `birth_date`
  - [x] `age_group`
  - [x] `gender`
  - [x] `phone`
  - [x] `profile_completed`
  - [x] `created_at`, `updated_at`
- [x] `user_profiles` 또는 `users` 확장 컬럼에 초기 건강·생활 정보를 저장한다.
  - [x] `education_years`
  - [x] `literacy`
  - [x] `health_conditions`
  - [x] `alcohol_use`
  - [x] `smoking_status`
  - [x] `hearing_status`
  - [x] `communication_difficulty`
  - [x] `smartphone_skill`
- [x] `user_preferences` 테이블을 만든다.
  - [x] `preferred_hearing_side`
  - [x] `voice_profile_id`
  - [x] `speech_rate`
  - [x] `subtitle_enabled`
  - [x] `sound_effect_enabled`
- [x] `refresh_tokens` 테이블을 만든다.
- [x] `consents` 테이블을 만든다.
  - [x] `consent_type`
  - [x] `agreed`
  - [x] `agreed_at`
  - [x] `version`

### 2.2 보호자 테이블

- [x] `guardian_links` 테이블을 만든다.
- [x] `guardian_id`, `elder_id` 중복 연결을 unique로 차단하고 `status` 값을 제한한다.
- [x] `access_scope`는 `guardian_link_scopes`와 `guardian_invitation_scopes` 별도 권한 테이블로 관리한다.
- [x] `guardian_invitations` 테이블에 코드 hash·만료·사용·시도 횟수를 저장한다.
- [ ] `pending`, `active`, `revoked` 상태 전환 규칙을 정한다.
- [x] 연결 해제 시 감사 로그를 남길 수 있도록 `audit_logs` 테이블을 만든다.
- [x] `guardian_id`, `elder_id`, `status`에 인덱스를 추가한다.

### 2.3 질문·세션·답변 테이블

- [x] `voice_profiles` 테이블과 seed 데이터를 만든다.
- [x] `questions` 테이블을 만든다.
  - [x] `question_type`: `orientation`, `memory`, `attention`, `language`, `emotion`
  - [x] `session_type`
  - [x] `content`
  - [x] `hint`
  - [x] `display_order`
  - [x] `subtitle_available`
- [x] CIST 기본 질문 seed 데이터를 등록한다.
- [x] AI 정서 문답 질문 seed 데이터를 등록한다.
- [x] `sessions` 테이블을 만든다.
  - [x] `user_id`
  - [x] `session_type`
  - [x] `status`
  - [x] `current_question_order`
  - [x] `answered_count`
  - [x] `total_questions`
  - [x] `settings`
  - [x] `started_at`, `ended_at`
- [x] `answers` 테이블을 만든다.
- [x] `client_answer_id`를 세션 단위 unique로 설정한다.
- [x] 답변의 `recording_id`, `transcript_id` nullable 관계를 설계한다.

### 2.4 녹음·AI 분석 테이블

- [x] `recordings` 테이블을 만든다.
  - [x] `client_recording_id` unique
  - [x] `user_id`, `session_id`, `question_id`
  - [x] `purpose=answer|diary`와 목적별 세션·질문 nullable 제약
  - [x] 저장 위치와 파일 metadata
  - [x] `sync_status`
  - [x] `transcript_status`
  - [x] `analysis_status`
  - [x] `recorded_at`
- [x] `transcripts` 테이블을 만든다.
- [x] `acoustic_analyses` 테이블을 만든다.
- [x] `cognitive_analyses` 테이블을 만든다.
- [x] `screening_results` 또는 세션 집계 결과 테이블을 만든다.
- [x] `session_summaries` 테이블을 만든다.
- [x] 모든 분석 결과에 `model_name`, `model_version`, `analyzed_at`을 저장한다.
- [x] 원본 모델 출력과 사용자 노출 결과를 분리한다.
- [x] `screening_reference_score`, `risk_level`, `domain_scores` 저장 구조를 결정한다.
- [x] AST·KcELECTRA 개별 결과를 `model_breakdown`으로 조회할 수 있게 한다.

### 2.5 일기·게임·캐릭터·캠페인·알림 테이블

- [x] `diaries` 테이블을 만든다.
- [x] `diary_reactions` 테이블을 만든다.
- [x] `game_results` 테이블을 만든다.
- [x] `characters` 테이블을 만든다.
- [x] 경험치 중복 적립 방지를 위한 `xp_ledger`와 이벤트 ID를 설계한다.
- [x] `campaigns` 테이블을 만든다.
- [x] `campaign_participations` 테이블에 사용자·캠페인 unique 제약을 추가한다.
- [x] `notifications` 테이블을 만든다.
- [x] `audit_logs` 테이블과 보호자 접근·동의 변경 기록 구조를 만든다.
- [x] `user_preferences`에 전체·보호자 반응·검사·일기·주간 리포트 알림 설정 컬럼을 추가한다.
- [x] `game_results`에 `client_game_result_id` unique와 짝 수·시도·시간·재시작·완료 지표를 추가한다.
- [x] 날짜별 일기 생성 작업 상태·실패·재시도를 저장할 `diary_generation_jobs` 구조를 추가한다.
- [x] `daily_summaries`에 사용자·기준일 unique와 일일 분석 상태를 추가한다.
- [x] `report_exports`에 요청 멱등키·작업 상태·파일 만료 구조를 추가한다.
- [ ] 상담 기관 기준정보와 Phase 2 예약·상태 이력을 저장할 테이블을 설계한다.

### 2.6 migration·무결성 검증

- [x] Flyway를 선택한다.
- [x] 초기 schema migration을 작성한다.
- [x] seed migration을 schema migration과 분리한다.
- [x] 외래키와 삭제 정책을 설정한다.
- [ ] 개인정보 테이블의 접근 권한을 DB 계정별로 검토한다.
- [x] 자주 조회하는 컬럼에 인덱스를 추가한다.
  - [x] `users.email`
  - [x] `sessions.user_id, started_at`
  - [x] `recordings.client_recording_id`
  - [x] `cognitive_analyses.user_id, analyzed_at`
  - [x] `diaries.user_id, written_at`
  - [x] `notifications.recipient_user_id, is_read, created_at`
  - [x] `daily_summaries.user_id, local_date`
  - [x] `diary_generation_jobs.user_id, status, target_date`
  - [x] `report_exports.guardian_id, created_at`
- [x] migration을 빈 DB에서 처음부터 재현한다.
- [ ] migration rollback 또는 복구 절차를 문서화한다.

### 2단계 완료 조건

- [x] 빈 PostgreSQL에 migration만 실행해 전체 스키마가 생성된다.
- [x] 중복 이메일, 중복 연결, 중복 오프라인 녹음이 DB 레벨에서도 차단된다.
- [x] 주요 조회 쿼리에 필요한 인덱스가 존재한다.
- [x] 테스트용 seed 질문과 음성 profile을 조회할 수 있다.

---

## 3. 인증·보안 구현

### 3.1 회원가입·로그인

- [x] `POST /auth/register`를 구현한다.
- [x] 이메일 형식과 중복 이메일을 검증한다.
- [x] 비밀번호 8자 이상 및 정책을 검증한다.
- [x] 비밀번호를 BCrypt 등 단방향 해시로 저장한다.
- [x] `role`을 허용 목록으로 제한한다.
- [ ] Figma 가입 순서에서 사용자 유형 선택 전 입력값을 임시 보관하고 최종 `role` 확정 후 회원가입을 호출하는 클라이언트 계약을 검증한다.
- [x] 회원가입 응답에서 비밀번호를 절대 반환하지 않는다.
- [x] `POST /auth/login`을 구현한다.
- [x] 로그인 성공 시 access token과 refresh token을 발급한다.
- [x] 로그인 실패 시 이메일 존재 여부를 노출하지 않는다.
- [x] `POST /auth/oauth/{provider}`를 구현하고 `kakao`, `naver` provider만 허용한다.
- [x] provider authorization code를 서버에서 교환하고 code·provider token을 로그에 남기지 않는다.
- [x] `POST /auth/password/reset/request`를 구현하고 등록 이메일 여부를 동일한 응답으로 처리한다.
- [x] `POST /auth/password/reset/confirm`를 구현하고 reset token을 일회성으로 폐기한다.
- [x] 비밀번호 재설정 성공 시 기존 refresh token을 폐기한다.
- [ ] `PATCH /users/me/password`를 구현하고 현재 비밀번호·새 비밀번호 정책을 검증한다.
- [ ] 비밀번호 변경 성공 시 현재 세션을 제외한 refresh token을 폐기하고 보안 이벤트를 기록한다.
- [x] `PasswordResetNotifier` adapter를 통해 provider 연결 지점을 분리한다.
- [ ] 운영 이메일 provider credential과 발신 주소를 secret manager로 연결한다.
- [ ] 인증된 전화번호를 보유한 사용자에 대한 SMS provider와 발송 채널 정책을 연결한다.
- [ ] 동일 이메일·IP 기준 rate limit을 구현하고 환경변수로 조정 가능하게 한다.
- [ ] rate limit 초과 시 `429`와 `Retry-After`를 반환하고 계정 존재 여부를 노출하지 않는다.
- [ ] provider 장애 시 재시도·실패 모니터링을 연결하고 token 원문을 응답·로그에 남기지 않는다.
- [x] `DELETE /users/me` 회원탈퇴를 구현한다.
- [x] 회원탈퇴 시 계정 상태를 `withdrawn`으로 변경하고 로그인 개인정보를 비식별화한다.
- [x] 회원탈퇴 시 refresh token·비밀번호 재설정 token·OAuth 계정 연결을 폐기한다.
- [x] 회원탈퇴 API는 URL의 `user_id`를 받지 않고 JWT subject로 본인 계정을 식별한다.

### 3.2 JWT·세션 보안

- [x] JWT access token 만료 시간을 설정한다.
- [x] refresh token 저장·폐기 정책을 구현한다.
- [x] refresh token rotation 여부를 결정한다.
- [x] `POST /auth/refresh`를 구현한다.
- [x] `POST /auth/logout`에서 refresh token을 폐기한다.
- [x] access token 검증 필터를 등록한다.
- [x] 만료 토큰과 잘못된 토큰을 `401`로 반환한다.
- [x] `ROLE_ELDER`, `ROLE_GUARDIAN` 권한 매핑을 구현한다.
- [ ] 서버 작업 전용 API에 앱 사용자 토큰으로 접근하지 못하게 한다.

구현 시 refresh token은 rotation 방식으로 새 token을 발급하고 기존 token을 즉시 폐기한다. 비밀번호 재설정·회원탈퇴에서도 해당 사용자의 refresh token을 모두 폐기한다.

### 3.3 소유권·IDOR 방지

- [ ] 모든 `/{user_id}` API에서 요청 사용자와 대상 사용자의 관계를 확인한다.
- [ ] URL의 `user_id`만 바꿔 다른 고령자의 데이터를 조회할 수 없는지 테스트한다.
- [ ] 보호자가 연결되지 않은 고령자의 데이터를 조회할 수 없는지 테스트한다.
- [ ] 동의하지 않은 대상자의 결과·일기를 조회할 수 없는지 테스트한다.
- [ ] 일기·녹음·세션 ID만 알아도 접근할 수 없는지 테스트한다.
- [ ] 관리자 권한이 필요한 기능을 일반 보호자 권한과 분리한다.
- [x] 회원탈퇴는 본인 JWT subject만 대상으로 처리해 `DELETE /users/me`의 IDOR 경로를 제거한다.

### 3단계 완료 조건

- [x] 회원가입 → 로그인 → 인증 API 호출 → 토큰 갱신 → 로그아웃 흐름이 동작한다.
- [x] 인증 없는 보호 API는 `401`을 반환한다.
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
- [ ] `push_notification_enabled`와 유형별 알림 설정 4종을 저장·조회한다.
- [ ] 알림 설정을 꺼도 앱 내부 알림 저장과 OS push 발송을 구분해 처리한다.

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
- [ ] `emotional_qa` 종료 시 `result_type`, `display_label`, `message`, `recommendation`을 반환한다.
- [ ] 고령자 응답에서 정확한 점수·원본 모델 출력·상세 영역 점수를 제외한다.
- [ ] 보호자 응답에서만 연결·동의·access scope 확인 후 정확한 점수와 상세 분석을 반환한다.
- [ ] 분석이 비동기이면 `GET /screenings/{session_id}/result` 재조회로 결과를 확인한다.
- [ ] 정서 문답 완료 이벤트를 `event_id=session_id`로 경험치 적립과 연결한다.
- [ ] `GET /sessions`를 구현한다.
- [ ] 날짜·세션 유형·페이지네이션 필터를 구현한다.
- [ ] `GET /sessions/{session_id}/answers`를 구현해 질문·답변·전사문·녹음 연결을 순서대로 반환한다.
- [ ] 대화 내역 조회 시 본인 또는 연결·동의·access scope를 검증한다.

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
- [ ] 하루에 여러 정서 문답 세션을 생성할 수 있다.

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
- [ ] `purpose=answer|diary`를 검증하고, `diary`이면 세션·질문 없이 업로드할 수 있게 한다.
- [ ] 음성 일기의 `recording_id`를 `POST /diaries`의 `source_type=voice`와 연결한다.
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

### 8.8 일일 대화 집계

- [ ] `POST /summary/daily`를 서버 작업 큐 전용으로 구현한다.
- [ ] `GET /summary/daily/{user_id}`를 구현한다.
- [ ] `Asia/Seoul` 기준 `00:00~다음 날 00:00`의 여러 세션을 집계한다.
- [ ] `local_date`, `timezone`, `session_count`, `analyzed_session_count`, `status`를 반환한다.
- [ ] 일일 집계 결과에 포함된 세션별 결과와 일일 종합 결과를 구분한다.
- [ ] `daily_summary_id`와 사용자·기준일을 unique로 관리한다.
- [ ] 재시도·재집계 시 동일 일일 결과와 경험치·알림이 중복 생성되지 않게 한다.
- [ ] 집계 실패 시 `failed` 상태와 재처리 가능 상태를 제공한다.
- [ ] 일일 집계 스케줄러의 시간대, 실행 시각, 재시도 정책을 문서화한다.

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
- [ ] 고령자에게는 `result_type`, `display_label`, `message`, `recommendation`만 반환한다.
- [ ] 보호자에게만 `screening_reference_score`, `display_score`, `score_max`, `score_rate`, `domain_scores`를 반환한다.
- [ ] `display_label`과 `recommendation`을 안전한 문구로 반환한다.
- [ ] 분석이 끝나지 않았으면 `pending` 상태를 구분한다.
- [ ] 결과 화면에서 일기 생성에 사용할 `summary_id`를 연결한다.

### 9.2 이력·추이

- [ ] `GET /analysis/cognitive/{user_id}/history`를 구현한다.
- [ ] `answer`, `session`, `day`, `user` 집계 범위를 지원한다.
- [ ] 고령자 이력 응답에서 정확한 점수·상세 모델 결과를 제외한다.
- [ ] 30일 평균과 `improving`, `declining`, `stable` 추이를 계산한다.
- [ ] 분석 이력에 정규화 점수와 표시 점수(`display_score`, `score_max`, `score_rate`)를 함께 반환한다.
- [ ] 직전 결과 대비 `score_delta`의 기준을 동일한 집계 단위로 고정한다.
- [ ] 동일 날짜에 여러 결과가 있을 때 집계 규칙을 정한다.
- [ ] 표본 부족 시 추이를 `stable`로 단정하지 않고 상태를 별도 반환할지 결정한다.
- [ ] `GET /analysis/cognitive/{user_id}/benchmark`를 구현한다.
- [ ] 시·도·시군구별 집계 데이터의 공신력 있는 출처·갱신 주기를 기록한다.
- [ ] 최소 표본 수 미달 시 지역값을 억제하고 `suppressed=true`로 반환한다.
- [ ] 연결·동의·`screening` access scope가 있는 보호자만 지역 기준선을 조회하게 한다.

### 9.3 고령자 홈

- [ ] `GET /dashboard/{user_id}`를 구현한다.
- [ ] 캐릭터 상태, 최근 검사, 최근 요약, 오늘 할 일, 미읽음 알림 수를 조합한다.
- [ ] 월간 AI 문답 횟수·게임 완료 횟수·활동 일수·연속 출석을 반환한다.
- [ ] 일기 카드에 생성 예정·처리·완료·미완료 상태와 확인 가능 시각을 반환한다.
- [ ] 고령자용 인지 활동 상태를 `stable`, `observe`, `attention_required` 안전 문구로 매핑한다.
- [ ] 여러 API를 호출하는 대신 서버 aggregation으로 제공할지 결정한다.
- [ ] 데이터가 없는 신규 사용자의 빈 상태 응답을 정의한다.

### 9.4 보호자 리포트

- [ ] `GET /guardian/{guardian_id}/report`를 구현하고 `elder_id`를 필수 query parameter로 받는다.
- [ ] `date` query parameter로 `Asia/Seoul` 기준 일일 리포트를 조회한다.
- [ ] 최근 요약, 참고 점수, 위험 상태, 게임 지표, 30일 추이를 반환한다.
- [ ] 하루에 여러 번 진행한 세션의 개별 결과와 일일 집계 결과를 함께 반환한다.
- [ ] `session_count`, `analyzed_session_count`, `analysis_status`, `diary_id`를 일일 리포트에 포함한다.
- [ ] 일일 집계 저장을 위해 `daily_summaries` 모델과 사용자·기준일 unique를 설계한다.
- [ ] 보호자 리포트에서 정규화 점수와 표시 점수(`latest_display_score`, `latest_score_max`, `latest_score_rate`)를 구분한다.
- [ ] `trend_points[]`를 차트가 바로 사용할 수 있는 형식으로 제공한다.
- [ ] `activity_summary_7d`를 세션·게임·일기 활동으로 구성한다.
- [ ] `recent_alerts[]`에 보호자가 확인해야 할 이벤트만 포함한다.
- [ ] 리포트 조회 전 연결·동의·access scope를 검증한다.
- [ ] `GET /guardian/{guardian_id}/report/export`를 구현해 PDF·CSV 작업 상태와 서명 URL을 반환한다.
- [ ] 내보내기 기간·형식 중복 요청을 멱등 처리하고 다운로드 접근 audit log를 남긴다.
- [ ] 내보내기 파일의 만료·보존·삭제 정책을 구현한다.

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
- [ ] `POST /diaries/from-daily-summary`를 구현한다.
- [ ] 생성 요청은 `202`와 `processing|completed|failed|conversation_incomplete` 작업 상태를 반환한다.
- [ ] `GET /diaries/{user_id}/generation-status`를 구현한다.
- [ ] 0시 생성 예정·처리 중·완료·실패 상태와 재시도 가능 여부를 홈·대화 완료 화면에 제공한다.
- [ ] 생성 완료·실패 시 설정을 확인해 알림 이벤트를 생성한다.
- [ ] `GET /diaries/{user_id}`를 구현한다.
- [ ] `GET /diaries/{diary_id}`를 구현한다.
- [ ] `PATCH /diaries/{diary_id}`를 구현한다.
- [ ] `DELETE /diaries/{diary_id}`를 구현한다.
- [ ] `source_type`을 `manual`, `voice`, `session`, `daily_summary`로 관리한다.
- [ ] `daily_summary_id`를 일기와 nullable 관계로 연결한다.
- [ ] `Asia/Seoul` 기준 하루 대화 집계를 0시 이후 일기로 생성한다.
- [ ] 같은 `daily_summary_id`로 일기가 중복 생성되지 않게 한다.
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
- [ ] 여러 대화 세션과 일일 집계·일기를 같은 `local_date` 기준으로 표시한다.

### 10단계 완료 조건

- [ ] AI 문답 결과를 일기로 저장할 수 있다.
- [ ] 캘린더에서 일기와 검사·게임 활동을 날짜별로 확인할 수 있다.
- [ ] 보호자가 일기를 열람하고 반응을 남길 수 있다.

---

## 11. 게임·캐릭터·지역 캠페인·상담 센터

### 11.1 미니게임

- [ ] `POST /game/result`를 구현한다.
- [ ] `image_match`, `consonant`, `word_match`를 허용한다.
- [ ] 점수, 응답 시간 배열, 오답 수, 전체 문항 수를 검증한다.
- [ ] 기억력 게임의 `matched_pairs`, `attempt_count`, `duration_sec`, `restarted_count`, `completed`를 저장·검증한다.
- [ ] `client_game_result_id` unique로 결과 재전송을 멱등 처리하고 `deduplicated`를 반환한다.
- [ ] `cognitive_index` 계산 규칙을 문서화한다.
- [ ] `GET /game/{user_id}/history`를 구현한다.
- [ ] 보호자에게 게임 이력을 노출할 때 access scope를 검증한다.

### 11.2 캐릭터·경험치

- [ ] `GET /character/{user_id}`를 구현한다.
- [ ] 캐릭터 응답에 이름·레벨·5단계 성장 상태·XP 목표·남은 XP를 포함한다.
- [ ] `GET /character/{user_id}/xp-history`를 구현해 마이페이지 획득 내역을 cursor 페이지네이션으로 반환한다.
- [ ] `POST /character/{user_id}/xp`를 구현한다.
- [ ] 정서 문답·게임 완료 시 서버 이벤트로 경험치를 자동 적립한다.
- [ ] `event_id=session_id` 또는 `event_id=game_result_id`로 중복 적립을 차단한다.
- [ ] 클라이언트가 임의의 경험치 `amount`를 직접 적립하지 못하게 한다.
- [ ] 출석·방문·정서 문답·게임별 경험치 정책을 정한다. 캠페인 보상은 Phase 2로 분리한다.
- [ ] 동일 이벤트가 재처리돼도 경험치가 중복 적립되지 않게 한다.
- [ ] 레벨업 transaction과 응답 필드를 구현한다.
- [ ] 캐릭터 상태가 고령자 홈에 표시되도록 dashboard와 연결한다.

### 11.3 지역 캠페인

- [ ] 지역 캠페인은 Phase 2로 관리한다.
- [ ] 캠페인 관리용 초기 데이터를 등록한다. (Phase 2)
- [ ] `GET /campaigns`를 구현한다. (Phase 2)
- [ ] `GET /campaigns/{campaign_id}`를 구현한다. (Phase 2)
- [ ] `POST /campaigns/{campaign_id}/participation`을 구현한다. (Phase 2)
- [ ] `GET /campaigns/{campaign_id}/participation`을 구현한다. (Phase 2)
- [ ] 지역·기간·상태 필터를 지원한다. (Phase 2)
- [ ] 동일 사용자의 중복 참여를 차단한다. (Phase 2)
- [ ] 캠페인 완료 시 경험치와 알림을 연결한다. (Phase 2)

### 11.4 상담 센터

- [ ] `GET /counseling/centers`를 구현한다.
- [ ] `province_code`, `district_code`, `facility_type` 필터를 지원한다.
- [ ] 센터명, 기관 유형, 행정구역 코드, 주소, 좌표, 연락처, 네이버 지도 URL, 기관 홈페이지 URL을 반환한다.
- [ ] MVP에서는 `reservation_mode=external_link`만 제공한다.
- [ ] 기관 기준정보의 출처·갱신 주기와 깨진 외부 링크 점검 방식을 정한다.
- [ ] `GET /counseling/centers/{center_id}/availability`를 구현한다. (Phase 2)
- [ ] `POST /counseling/appointments`, `GET /counseling/appointments`를 구현한다. (Phase 2)
- [ ] `DELETE /counseling/appointments/{appointment_id}`를 멱등 취소로 구현한다. (Phase 2)
- [ ] 예약 전에 연결 관계·개인정보 제공 동의·취소 마감·중복 요청을 검증한다. (Phase 2)

### 11단계 완료 조건

- [ ] 게임 결과가 이력과 캐릭터 경험치에 반영된다.
- [ ] 동일 게임 결과 재전송으로 경험치가 중복되지 않는다.
- [ ] 캠페인 목록 조회·참여·완료 상태가 동작한다. (Phase 2)
- [ ] 지역 선택 후 상담 센터 목록과 외부 연결이 동작한다.

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
- [ ] `type`, `severity`, `status_label`, `read_at`을 저장·반환한다.
- [ ] `data`의 `target_route`, `reference_type`, `reference_id`, `elder_id` 계약을 고정하고 이동 대상 권한을 재검증한다.
- [ ] 고령자·보호자별 알림 유형과 화면 배지 문구를 서버 매핑으로 관리한다.

### 12.2 이벤트 연결

- [ ] 검사 분석 완료 시 고령자 알림을 생성한다.
- [ ] 보호자에게 위험 신호 알림을 보낼 조건을 정의한다.
- [ ] AI 정서 문답 요약 완료 시 알림을 생성한다.
- [ ] 일기 생성 완료와 생성 실패 알림을 구분해 생성한다.
- [ ] 검사 결과 업데이트와 인지 점수 하락 경보를 서로 다른 유형·심각도로 생성한다.
- [ ] 보호자 반응 등록 시 고령자 알림을 생성한다.
- [ ] 캠페인 참여 완료 시 알림을 생성한다.
- [ ] 주간 리포트 알림의 생성 시점을 정의한다.
- [ ] 동일 이벤트에 알림이 중복 생성되지 않게 한다.
- [ ] 전체·유형별 사용자 알림 설정을 확인해 OS push 발송을 제어한다.

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
- [ ] 하루에 두 번 이상 정서 문답 세션 진행
- [ ] 질문·답변 저장
- [ ] 세션 종료 후 고령자에게 정성 결과·격려 메시지 안내
- [ ] 고령자 응답에 정확한 점수·상세 영역 결과가 포함되지 않는지 확인
- [ ] 보호자 응답에 연결·동의·access scope 확인 후 수치 결과가 포함되는지 확인
- [ ] Gemini 요약 생성
- [ ] 요약으로 일기 생성
- [ ] 0시 이후 하루 대화 집계로 일일 일기 생성
- [ ] 보호자 일기 조회
- [ ] 보호자 반응 저장
- [ ] 고령자 알림 생성·조회

#### 시나리오 E. 게임 → 캐릭터 → 캠페인

- [ ] 게임 결과 저장
- [ ] 기억력 게임 6쌍 완료 시 짝 수·시도·경과 시간·재시작 횟수 저장
- [ ] 동일 `client_game_result_id` 재전송 시 기존 결과 반환과 XP 중복 방지 확인
- [ ] 게임 이력 조회
- [ ] 게임 완료 이벤트로 경험치 자동 적립
- [ ] 레벨업 여부 확인
- [ ] 캐릭터 성장 단계·남은 XP·경험치 획득 내역 확인
- [ ] 캠페인 목록 조회 (Phase 2)
- [ ] 캠페인 참여 (Phase 2)
- [ ] 완료 보상과 알림 확인 (Phase 2)

#### 시나리오 F. 지역 선택 → 상담 센터 외부 연결

- [ ] 지역 선택
- [ ] `GET /counseling/centers` 호출
- [ ] 상담 센터 목록·주소·연락처 조회
- [ ] 지도 또는 기관 홈페이지 외부 링크 이동

#### 시나리오 G. 마이페이지·계정 설정

- [ ] 프로필·레벨·월간 활동·연속 출석·경험치 내역 조회
- [ ] 전체·유형별 알림 설정 변경 후 재조회
- [ ] 현재 비밀번호 검증 후 비밀번호 변경
- [ ] 변경 후 다른 refresh token 폐기 확인
- [ ] 로그아웃과 회원탈퇴 권한·상태 전이 확인

#### 시나리오 H. 대화 완료 → 다음 날 일기·알림

- [ ] 세션 대화 질문·답변·전사 내역 조회
- [ ] 대화 완료 직후 다음 날 0시 일기 생성 예정 상태 확인
- [ ] 생성 작업의 처리·완료·실패·대화 미완료 상태 확인
- [ ] 생성 완료 또는 실패 알림의 유형·화면 이동 확인
- [ ] 별도 음성 일기 업로드 후 일기와 녹음 연결 확인

#### 시나리오 I. 보호자 추이·내보내기·상담

- [ ] 최소 표본 수를 충족한 지역 기준선과 사용자 추이 비교
- [ ] 표본 부족 지역의 집계값 억제 확인
- [ ] PDF·CSV 리포트 생성, 서명 URL 만료, 다운로드 audit log 확인
- [ ] 시·도·시군구 선택 후 기관 유형·외부 지도 링크 확인
- [ ] 예약 가능 시간·예약·목록·취소 흐름 확인 (Phase 2)

### 13.3 API 계약 검증

- [ ] OpenAPI 문서와 `docs/api-spec.md`의 endpoint 목록을 대조한다.
- [ ] 모든 endpoint의 method와 path가 일치하는지 확인한다.
- [ ] request field명과 response field명이 프론트엔드 타입과 일치하는지 확인한다.
- [ ] enum 값이 프론트엔드 상수와 일치하는지 확인한다.
- [ ] `null` 가능 여부를 명세에 맞춘다.
- [ ] 기본값과 페이지네이션 동작을 확인한다.
- [ ] 고령자·보호자 역할별 결과 필드 노출 차이를 계약 테스트한다.
- [ ] `Asia/Seoul` 기준 `local_date` 일일 집계와 `date` 리포트 query를 계약 테스트한다.
- [ ] `POST /summary/daily`와 `POST /diaries/from-daily-summary`의 중복 실행 방지를 계약 테스트한다.
- [ ] `GET /counseling/centers`의 지역 필터와 외부 URL 응답을 계약 테스트한다.
- [ ] 게임 결과 상세 지표와 `client_game_result_id` 멱등 응답을 계약 테스트한다.
- [ ] 날짜별 일기 생성 작업 상태와 알림 유형·deep link payload를 계약 테스트한다.
- [ ] 지역 기준선의 최소 표본 억제와 리포트 내보내기 권한·만료를 계약 테스트한다.
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
- [ ] 이메일·SMS provider credential, 발신 정보, 재설정 링크 Base URL을 secret manager로 관리한다.
- [ ] 비밀번호 재설정 email/IP rate limit과 `429`·`Retry-After` 동작을 운영 환경에서 검증한다.
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

### 14.1 Figma 전체 화면·기존 계획 기능 구현

Figma에 노출된 기능은 모두 구현한다. Figma에 현재 노출되지 않은 기존 계획 기능도 삭제하지 않고 Phase 2 구현 범위로 유지한다.

- [ ] Figma 화면별 API 연결표의 모든 행을 E2E 시나리오 또는 계약 테스트와 연결한다.
- [ ] 지역 기준선 비교의 데이터 출처·지역 코드·최소 표본·개인정보 기준을 확정하고 MVP로 구현한다.
- [ ] 보호자 리포트 내보내기의 PDF·CSV·비동기 생성·다운로드 권한·보존 기간을 확정하고 MVP로 구현한다.
- [ ] 전문 상담 기관 검색·외부 지도 연결을 MVP로 구현한다.
- [ ] 전문 상담 예약의 기관 연동·개인정보 제공 동의·예약 상태를 확정하고 Phase 2로 구현한다.
- [ ] 지역 캠페인·TTS·립싱크를 Figma 노출 여부와 무관하게 Phase 2로 구현한다.

## 최종 완료 체크

- [ ] API 명세와 실제 구현이 일치한다.
- [ ] 모든 MVP endpoint에 controller, service, repository, validation, test가 있다.
- [ ] 모든 보호 API에 인증·소유권·연결·동의 검증이 있다.
- [ ] 음성·건강·보호자 데이터가 최소 권한으로 보호된다.
- [ ] 분석 실패와 재시도 상태를 사용자가 확인할 수 있다.
- [ ] 오프라인 녹음 재전송과 중복 방지가 동작한다.
- [ ] 결과 문구가 의료적 진단으로 오해되지 않는다.
- [ ] 고령자 화면·보호자 화면의 주요 API 흐름이 E2E 테스트를 통과한다.
- [ ] Figma 화면별 API 연결표의 모든 기능과 비화면 Phase 2 계획이 Issue로 추적된다.
- [ ] 배포·migration·백업·장애 대응 방법이 문서화되어 있다.
