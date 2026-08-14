# 프론트엔드·백엔드 통합 점검 체크리스트

> 관련 Issue: [#54 통합 점검 체크리스트](https://github.com/oesmln/neulbom/issues/54), [#56 프론트엔드·백엔드 실 API 통합](https://github.com/oesmln/neulbom/issues/56), [#59 보호자 초대·연결](https://github.com/oesmln/neulbom/issues/59)
>
> 기준 브랜치: `develop`
>
> 목적: Expo 앱과 Spring Boot API가 실제 환경에서 같은 계약으로 동작하는지 확인하고, 한쪽에만 구현된 기능의 후속 작업 범위를 확정한다.

## 0. 점검 원칙

- [ ] `EXPO_PUBLIC_API_BASE_URL`을 설정해 `USE_MOCK_API=false`인 상태에서 확인한다.
- [ ] 화면이 열리는 것만으로 완료 처리하지 않고 실제 요청, 응답, DB 반영까지 확인한다.
- [ ] API 경로, HTTP method, query/path parameter, request body, response body, 상태 코드를 함께 확인한다.
- [ ] 정상 흐름뿐 아니라 `400`, `401`, `403`, `404`, `409`, `422`, `500`, `503` 처리도 필요한 범위에서 확인한다.
- [ ] 완료한 항목에는 확인한 화면, API, 테스트 또는 재현 절차를 근거로 남긴다.
- [ ] 한쪽에만 있는 기능은 `구현 누락`, `의도적인 로컬 기능`, `의도적인 서버 전용 기능`, `범위 제외` 중 하나로 판정한다.

## 1. 프론트엔드와 백엔드가 제대로 연결된 기능

### 1.1 실행 환경과 공통 통신

- [x] PostgreSQL migration v11 적용 후 백엔드가 `local` profile로 실행된다. (`2026-08-13` 로컬 PostgreSQL 17.10)
- [x] `GET /health`와 `GET /actuator/health`가 정상 응답한다. (`2026-08-13`, 모두 `UP`)
- [ ] iOS simulator에서 `http://localhost:8080`으로 연결된다.
- [ ] Android emulator에서 `http://10.0.2.2:8080`으로 연결된다.
- [ ] 실제 기기에서 개발 PC의 LAN IP로 연결된다.
- [x] Expo Web이 `EXPO_PUBLIC_API_BASE_URL=http://localhost:8080` 설정으로 렌더링되고 콘솔 오류 없이 로그인 화면에 진입한다. (`2026-08-13`)
- [x] Expo Web origin `http://localhost:8081`의 CORS preflight가 정상 응답한다. (`2026-08-13`, HTTP 200)
- [x] 프론트엔드 공통 client가 업무 API에 `/api/v1` prefix를 한 번만 적용한다.
- [ ] JSON 요청과 multipart 요청이 각각 올바른 `Content-Type`으로 전송된다.
- [ ] 백엔드 오류 JSON의 `error`, `code`, `detail`, `request_id`를 프론트가 사용자 메시지로 안전하게 처리한다.
- [ ] 연결 실패와 timeout이 무한 로딩이 아닌 재시도 가능한 오류 화면으로 표시된다.

### 1.2 인증과 세션

- [ ] 회원가입 후 반환된 `user_id`, `role`, `profile_completed`가 프론트 타입과 일치한다.
- [x] `profile_completed=false`인 신규·기존 사용자를 프로필·동의 온보딩으로 분기한다. (`2026-08-13`, Issue #58)
- [x] 프로필과 필수 동의를 저장한 뒤 완료 상태를 세션에 반영하고 재로그인 시 온보딩을 건너뛴다. (`2026-08-13`, Expo Web 실 API)
- [ ] 이메일 로그인 후 access token과 refresh token을 SecureStore에 저장한다.
- [ ] 보호 API 요청에 `Authorization: Bearer {access_token}`을 첨부한다.
- [ ] access token 만료 시 refresh 요청을 한 번만 수행하고 원래 요청을 재시도한다.
- [ ] refresh 실패 시 저장된 세션을 제거하고 로그인 화면으로 이동한다.
- [ ] 로그아웃 시 서버 refresh token과 프론트 저장 세션을 모두 무효화한다.
- [ ] 비밀번호 변경과 회원 탈퇴가 현재 로그인 사용자 기준으로 동작한다.
- [ ] 카카오·네이버 OAuth의 redirect URI와 앱 scheme이 실제 환경 설정과 일치한다.

### 1.3 기능별 API 연결

| 영역 | 프론트 동작 | 백엔드 API | 확인 |
| --- | --- | --- | --- |
| 사용자 | 프로필 조회·수정 | `GET/PATCH /users/{userId}` | [x] |
| 사용자 | 환경설정 조회·수정 | `GET/PATCH /users/{userId}/preferences` | [ ] |
| 사용자 | 음성 프로필 조회 | `GET /voice-profiles` | [ ] |
| 동의 | 동의 저장·조회 | `POST/GET /consent/{userId}` | [ ] |
| 세션 | 검사·정서 문답 시작, 조회, 종료 | `POST /sessions`, `GET/PATCH /sessions/{sessionId}` | [ ] |
| 세션 | 세션 목록·일일 질문 조회 | `GET /sessions`, `GET /questions/daily` | [ ] |
| 답변 | 문항 답변 저장 및 멱등성 | `POST /sessions/{sessionId}/answers` | [ ] |
| 녹음 | 음성 multipart 업로드·상태 조회 | `POST/GET /recordings` | [x] (#61) |
| 음성 출력 | Google TTS 합성·실제 재생·켜기/끄기 | `POST /speech/synthesize` | [x] (#91) |
| 홈 | 고령자 대시보드 조회 | `GET /dashboard/{userId}` | [ ] |
| 검사 결과 | 결과와 인지 추이 조회 | `GET /screenings/{sessionId}/result`, `GET /analysis/cognitive/{userId}/history` | [ ] |
| 일기 | 생성·목록·상세·생성 상태 | `/diaries/**` | [ ] |
| 일기 | 보호자 반응 조회·등록 | `GET/POST /diaries/{diaryId}/reactions` | [x] |
| 캘린더 | 기간별 활동 조회 | `GET /calendar/{userId}/activities` | [ ] |
| 게임 | 게임 결과 저장·기록 조회 | `POST /game/result`, `GET /game/{userId}/history` | [ ] |
| 캐릭터 | 캐릭터·경험치 기록 조회 | `GET /character/{userId}`, `GET /character/{userId}/xp-history` | [ ] |
| 알림 | 목록·개별 읽음·전체 읽음 | `/notifications/**` | [ ] |
| 보호자 | 초대 발급·확인·수락·고령자 목록·연결 관리 | `/guardian/**` | [x] |
| 보호자 | 고령자 리포트 조회 | `GET /guardian/{guardianId}/report` | [x] |
| 상담 | 지역·시설 유형별 센터 조회 | `GET /counseling/centers` | [ ] |

### 1.4 계약 세부 확인

- [ ] 프론트 TypeScript 타입과 백엔드 DTO의 필수값, nullable 여부, enum 값이 일치한다.
- [ ] query parameter의 snake_case와 camelCase 사용이 Controller 선언과 일치한다.
- [ ] 날짜는 `YYYY-MM-DD`, timestamp는 timezone을 포함한 ISO 8601 형식으로 교환한다.
- [ ] 목록 응답의 `page`, `limit`, `total`, `has_next` 구조가 화면 pagination과 일치한다.
- [x] `client_answer_id`, `client_recording_id`, `client_game_result_id` 재전송 시 중복 생성되지 않는다. (#61 녹음 queue 포함)
- [ ] 고령자와 보호자 역할별 접근 권한이 프론트 내비게이션과 백엔드 권한 검사에서 모두 일치한다.
- [ ] 일기 목록과 상세가 같은 `GET /diaries/{id}` 경로에서 의도한 기준으로 구분된다.

### 1.5 이번 통합에서 해소한 계약 불일치

- [x] 인증 전 초대 코드 확인을 위해 `POST /guardian/invitations/verify`를 공개 경로로 허용했다.
- [x] 녹음 목적 enum을 백엔드 계약인 `answer`, `diary`로 정렬했다.
- [x] Expo Web이 생성하는 `audio/webm`·`.webm` 업로드를 백엔드에서 허용하고 테스트했다.
- [x] CIST·정서 문답의 실제 녹음 업로드 결과를 답변의 `recording_id`로 연결했다.
- [x] 색상 맞추기 결과의 `color_match`를 프론트 타입, API 명세, 서비스 검증, DB 제약조건에 추가했다.
- [x] multipart 업로드도 access token 만료 시 refresh 후 한 번 재시도한다.
- [x] 보호자 초대 원문은 발급 응답과 메모리 상태에서만 다루고 공유 sheet에 코드만 전달한다.
- [x] 보호자 연결 목록은 활성 연결만 대시보드에 노출하고 복수 어르신 선택, 범위 수정, 연결 해제를 지원한다.
- [x] 인지 추이 조회의 잘못된 `monthly` aggregation을 백엔드 계약인 `day`와 기간 조건으로 정렬했다.
- [x] 보호자 `403`을 동의 필요, 연결 비활성, 접근 범위 없음으로 구분해 안내한다.

## 2. 프론트엔드에는 있지만 백엔드에는 없는 기능

### 2.1 조사 체크리스트

- [ ] Elder·Guardian navigator에 등록된 모든 화면과 화면 내 저장/수정 버튼을 목록화한다.
- [ ] 각 사용자 동작이 실제 API, mock API, 로컬 상태 중 어디에 연결되는지 표시한다.
- [ ] mock 응답만 있고 실제 요청 분기가 없는 기능을 찾는다.
- [ ] 화면에서 임시 UUID, 고정 사용자 정보 또는 고정 통계 데이터를 사용하는 부분을 찾는다.
- [ ] 저장 성공처럼 보이지만 앱 재실행 또는 새로고침 후 사라지는 상태를 찾는다.
- [ ] 백엔드 API가 필요한 기능과 기기 로컬에만 저장해도 되는 기능을 구분한다.

### 2.2 우선 확인 후보

| 프론트 기능 | 현재 확인할 내용 | 판정 | 후속 작업 |
| --- | --- | --- | --- |
| 캠페인 화면 | Entity와 정적 화면만 있고 Controller/API가 없음 | 범위 제외 | 이번 통합에서는 API를 연결하지 않음 |
| 앱 다크 모드·글씨 크기 | `SecureStore` 기반 기기 로컬 설정으로 명시되어 있음 | 의도적인 로컬 기능 | 서버 동기화 요구가 생길 때 별도 검토 |
| 캐릭터 표시·상호작용 | 서버 캐릭터 상태와 화면의 로컬 상태가 일치하는지 | [ ] | |
| 녹음 UI·재전송 | Native 파일·Web IndexedDB에 음성과 동일 ID를 보존하고 세션 복원·재연결·앱 활성화 시 순차 재전송 | 연결 완료 | #61 |
| 소셜 로그인 화면 | 서버 OAuth API는 있으나 버튼이 authorization code를 얻지 않고 회원가입 흐름으로 이동 | 프론트 구현 누락 | Kakao·Naver 브라우저 인증 연결 Issue 필요 |
| 차트·리포트 UI | 실제 history/report 응답과 서버 허용 aggregation 사용 | 실 API 연결 | #59 |

### 2.3 발견 항목 기록

| 화면/기능 | 프론트 근거 파일 | 필요한 백엔드 계약 | 분류 | 관련 Issue |
| --- | --- | --- | --- | --- |
| 캠페인 목록·참여 | `frontend/src/screens/elder/ElderCampaign.tsx` | `GET /campaigns`, `POST /campaigns/{id}/participations` 등 | 범위 제외 | - |
| 표시 설정 | `frontend/src/store/settings.ts` | 없음 | 의도적인 로컬 기능 | #56 |
| 소셜 로그인 authorization code 획득 | `frontend/src/screens/auth/LoginScreen.tsx` | `POST /auth/oauth/{provider}` | 프론트 부분 구현 | #56 |
| 녹음 영속 재전송 queue | `frontend/src/recording/recordingQueue.ts`, `frontend/src/hooks/useAnswerRecording.ts` | 기존 `client_recording_id` 멱등 계약 사용 | 연결 완료 | #61 |

## 3. 백엔드에는 있지만 프론트엔드에는 구현되지 않은 기능

### 3.1 프론트 구현 필요 여부 확인

| 백엔드 기능 | API | 판정 | 프론트 후속 작업 |
| --- | --- | --- | --- |
| 세션 설정 변경 | `PATCH /sessions/{sessionId}/settings` | 프론트 구현 누락 | 검사 중 설정 UI 필요 여부 결정 |
| 세션 답변 목록 | `GET /sessions/{sessionId}/answers` | 프론트 구현 누락 | 답변 검토 화면 요구 시 연결 |
| 개별 질문 조회 | `GET /questions/{questionId}` | 현재 화면에 불필요 | 일일 질문 응답으로 충족 |
| 지역 인지 지표 비교 | `GET /analysis/cognitive/{userId}/benchmark` | 프론트 구현 누락 | 보호자 차트 비교 UI 연결 |
| 보호자 리포트 내보내기 | `GET /guardian/{guardianId}/report/export` | 프론트 구현 누락 | 내보내기 버튼·다운로드 처리 |
| 보호자 초대 생성 | `POST /guardian/invitations` | 구현 완료 | 코드 생성·만료 표시·공유 UI 연결 (#59) |
| 보호자 직접 연결 | `POST /guardian/link` | 프론트 구현 누락 | 운영 방식 확정 후 연결 UI 구현 |
| 보호자 연결 범위 수정·삭제 | `PATCH/DELETE /guardian/link/{linkId}` | 구현 완료 | 연결별 scope 수정·해제 확인 UI 연결 (#59) |
| 음성 직접 변환 | `POST /voice/transcribe` | 서버 worker 전용 | 앱에서 직접 호출하지 않음 |
| 캐릭터 안내 음성 | `POST /speech/synthesize` | Google TTS MP3 base64 | 온보딩·CIST·AI 대화 연결 완료 (#91) |
| 음향·인지 분석 요청 | `POST /analysis/acoustic`, `POST /analysis/cognitive` | 서버 worker 전용 | 앱에서 직접 호출하지 않음 |
| 세션 요약 생성·조회 | `POST/GET /summary/session/**` | 생성은 worker 전용, 조회는 프론트 미사용 | 필요 화면에서 조회만 연결 |
| 일일 요약 생성·조회 | `POST/GET /summary/daily/**` | 생성은 worker 전용, 조회는 프론트 미사용 | 일기·리포트 요구에 따라 조회 연결 |

### 3.2 서버 전용 API 확인

- [x] `POST /notifications/push`는 `@ServerWorkerOnly` 서버 worker 전용이다.
- [x] `POST /character/{userId}/xp`는 `@ServerWorkerOnly`이며 앱이 직접 호출하지 않는다.
- [x] 전사·분석·요약 생성 API는 `@ServerWorkerOnly`; 요약 조회 API만 사용자 JWT용이다.
- [x] 서버 전용 API를 프론트 구현 누락 목록에서 제외하고 근거를 남겼다.

### 3.3 발견 항목 기록

| API/기능 | 백엔드 근거 파일 | 필요한 프론트 화면·동작 | 분류 | 관련 Issue |
| --- | --- | --- | --- | --- |
| 보호자 초대 생성 | `GuardianController#createInvitation` | 초대 코드 생성·공유 | 구현 완료 | #59 |
| 인지 benchmark | `ReportController#benchmark` | 보호자 비교 차트 | 프론트 구현 누락 | #56 |
| 리포트 export | `ReportController#exportGuardianReport` | 내보내기·다운로드 | 프론트 구현 누락 | #56 |
| 분석·알림·XP 쓰기 | 각 Controller의 `@ServerWorkerOnly` | 없음 | 의도적인 서버 전용 기능 | #56 |

## 4. 통합 실행 순서

- [x] 최신 `develop`에서 통합 작업 브랜치를 생성한다 (`feature/common/#56-frontend-backend-integration`).
- [x] 프론트·백엔드 endpoint/DTO 정적 대조 결과를 1~3절에 기록한다.
- [x] 로컬 PostgreSQL과 백엔드를 실행하고 migration·health·CORS를 확인한다.
- [x] Expo Web을 실 API 환경변수로 실행한다.
- [x] 회원가입 → 역할 선택 → 자동 로그인 → CIST 세션 시작 흐름을 실 API로 검증한다. (`2026-08-13`)
- [ ] 프로필·동의 → 홈 진입 흐름을 실 API로 검증한다.
- [ ] 고령자 핵심 흐름인 CIST → 답변·녹음 → 종료 → 결과 조회를 검증한다.
- [ ] 일기 → 캘린더 → 게임 → 캐릭터 → 알림 흐름을 검증한다.
- [x] 보호자 초대 발급 → 고령자 검증·수락 → 복수 고령자 선택 → 리포트·일기·추이·알림 흐름을 검증한다. (`2026-08-13`)
- [x] 보호자 연결 scope 수정, scope 부족 403, 연결 해제와 활성 목록 제외를 실 API로 검증한다. (`2026-08-13`)
- [ ] 발견한 계약 불일치와 구현 누락을 별도 Issue로 분리한다.
- [ ] 수정 후 전체 회귀 검증을 수행한다.

## 5. 완료 조건

- [x] `frontend`에서 `npm run typecheck`가 통과한다.
- [x] `frontend`에서 Expo Web 앱이 실 API 모드로 실행된다. (`2026-08-13`)
- [x] `backend`에서 `./gradlew test`가 통과한다.
- [x] `backend`에서 `./gradlew build`가 통과한다. (`2026-08-13`, clean build)
- [ ] mock API 없이 고령자 핵심 흐름이 완료된다.
- [x] mock API 없이 보호자 핵심 흐름이 완료된다. (`2026-08-13`, Expo Web + 로컬 실 API)
- [ ] 한쪽에만 구현된 모든 항목에 분류와 후속 Issue가 기록된다.
- [ ] 실제 secret, token, 개인정보가 코드·문서·로그에 포함되지 않는다.
- [ ] 루트 및 프론트·백엔드 실행 문서가 실제 통합 실행 방법과 일치한다.

## 참고 문서

- [백엔드 API 명세](../backend/docs/api-spec.md)
- [백엔드 개발 체크리스트](../backend/docs/backend-development-checklist.md)
- [프론트엔드 개발 체크리스트](../frontend/frontend-development-checklist.md)
- [브랜치 전략](./branch-strategy.md)
