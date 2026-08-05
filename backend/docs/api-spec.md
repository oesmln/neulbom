# 늘봄(NEULBOM) REST API 명세서

> 성장형 캐릭터 기반 치매 조기 스크리닝 서비스
>
> **Version:** v1.2<br>
> **기준 문서:** 기존 REST API 명세서 v1.0 + 2026 중간보고서 수정사항<br>
> **Base URL:** `https://api.dementia-care.com/api/v1`<br>
> **Content-Type:** `application/json`<br>
> **인증:** `Authorization: Bearer {access_token}`

## 0. v1.2 반영 사항

- 회원 유형은 `elder`, `guardian`으로 구분한다. 화면의 “보호자 / 의료진”은 백엔드에서 모두 `guardian` 역할로 처리한다.
- 회원가입 후 최초 검사 전에 학력, 문해 여부, 건강·생활습관, 청력, 스마트폰 사용 수준을 선택적으로 저장한다.
- 보호자 1명이 여러 명의 고령자를 관리할 수 있도록 연결 목록, 접근 범위, 동의 상태를 관리한다.
- 검사 시작 전에 잘 들리는 귀, 안내 음성, 말하기 속도, 자막 표시 설정을 저장한다. 자막은 기본적으로 비활성화한다.
- CIST 검사와 AI 정서 문답을 서로 다른 세션 유형으로 구분한다.
- 음성 답변을 문항 단위로 저장하고, 오프라인에서 녹음한 파일은 재전송할 수 있도록 `client_recording_id`와 처리 상태를 사용한다.
- 기존 Whisper-KcELECTRA 흐름에 AST 음향 분석 결과를 추가한다. AST와 KcELECTRA는 모델별 결과를 보존하며 최종 스크리닝 참고 점수는 서버에서 집계한다.
- 고령자 화면의 결과·일기·달력·지역 캠페인·알림, 보호자 화면의 대시보드·일기 반응·위험 추이 차트에 필요한 API를 추가한다.
- 로그인 화면의 카카오·네이버 로그인과 비밀번호 재설정 흐름을 지원한다.
- 보호자 또는 기관이 발급한 6자리 초대 코드를 검증하고, 고령자가 수락하면 보호자 연결을 생성한다. 초대 코드는 기존 `/guardian/link` 직접 연결 API와 분리한다.
- 결과 화면에 정규화 점수와 별도로 화면 표시 점수(`display_score`, `score_max`, `score_rate`)를 제공한다.
- 캘린더 일기 활동의 감정(`mood`, `mood_level`), 보호자 반응의 `cry` 유형, 알림 전체 읽음 처리를 명세한다.
- 사용자에게 노출되는 결과는 의료적 진단이 아니라 **인지기능 저하 의심 신호**, **추가 확인 권장**, **스크리닝 참고 점수**로 표현한다.

> 검사 및 AI 분석 결과는 의료적 진단을 대신하지 않는다. `screening_reference_score`, `risk_level` 등은 반복 관찰을 위한 참고 정보이며, 의심 결과가 나타나면 치매안심센터 또는 병원에서 추가 검사를 권고한다.

## 1. 공통 규칙

### 1.1 인증 및 권한

| 구분 | 설명 |
| --- | --- |
| 공개 API | `/auth/register`, `/auth/login`, `/auth/refresh` |
| 사용자 본인 | 자신의 프로필, 세션, 일기, 게임, 캐릭터, 알림 조회·수정 |
| `guardian` | 동의가 완료된 연결 대상자의 검사·결과·요약·활동·일기 조회 및 반응 작성 |
| 서버 작업 전용 | AST, KcELECTRA, Gemini 분석 API. 앱에서 직접 호출하지 않고 서버 작업 큐에서 호출하는 것을 권장 |

- 보호자는 연결(`guardian_links`)과 동의(`consents`)가 모두 유효한 대상자만 조회할 수 있다.
- `guardian_id`, `user_id`는 가능하면 JWT의 사용자 정보로 확인하며, 다른 사용자를 지정하는 요청은 서버에서 권한을 검증한다.
- 모든 날짜·시간은 ISO 8601 형식과 타임존을 포함한다. 예: `2026-08-05T10:30:00+09:00`
- ID는 현재 명세에서 `string`으로 표기한다. 실제 구현에서는 UUID 사용을 권장한다.

### 1.2 공통 응답 및 오류

성공 응답은 엔드포인트별 응답 본문을 그대로 반환한다. 오류 응답은 다음 형식을 사용한다.

```json
{
  "error": "요청 필드가 올바르지 않습니다.",
  "code": 400,
  "detail": "password must be at least 8 characters",
  "request_id": "req_01J..."
}
```

| HTTP 상태 | 의미 |
| --- | --- |
| `400` | 잘못된 요청, enum·날짜·필수값 오류 |
| `401` | 액세스 토큰 누락 또는 만료 |
| `403` | 연결·동의·역할 권한 없음 |
| `404` | 리소스 없음 |
| `409` | 중복 요청, 이미 연결된 사용자, 중복 `client_recording_id` |
| `410` | 만료되었거나 이미 사용·폐기된 초대 코드 |
| `413` | 업로드 파일 용량 초과 |
| `422` | 형식은 맞지만 업무 규칙 위반 |
| `429` | 초대 코드 검증 시도 횟수 또는 요청 빈도 제한 초과 |
| `500` | 서버 내부 오류 |
| `503` | 외부 AI 또는 비동기 분석 서비스 일시 중단 |

### 1.3 페이지네이션

목록 API는 다음 쿼리 파라미터를 공통으로 사용한다.

| 파라미터 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `page` | integer | `1` | 1부터 시작하는 페이지 번호 |
| `limit` | integer | `20` | 한 페이지 항목 수, 최대 `100` |
| `from_date` | string | - | 조회 시작일, `YYYY-MM-DD` |
| `to_date` | string | - | 조회 종료일, `YYYY-MM-DD` |

페이지 목록 응답은 필요한 경우 다음 메타 필드를 포함한다.

```json
{
  "items": [],
  "total": 0,
  "page": 1,
  "limit": 20,
  "has_next": false
}
```

### 1.4 오프라인 녹음 동기화 규칙

- 앱은 네트워크가 없어도 질문 표시와 녹음을 진행하고, 기기에 먼저 저장한다.
- 네트워크가 연결되면 `POST /recordings`를 재호출한다.
- `client_recording_id`는 기기에서 생성한 UUID이며, 동일 값으로 재전송해도 중복 녹음이 생성되지 않아야 한다.
- 필요하면 HTTP `Idempotency-Key` 헤더에 `client_recording_id`를 함께 보낸다.
- 녹음 상태는 `device_saved` → `server_pending` → `server_uploaded` → `analysis_completed` 순서로 관리한다. 실패 시 `failed`로 전환한다.

## 2. 전체 엔드포인트 목록

### 2.1 인증·사용자·동의

| Method | Endpoint | 설명 | 인증 | 주요 역할 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/auth/register` | 회원가입 | 불필요 | 전체 | MVP |
| `POST` | `/auth/login` | 로그인 및 JWT 발급 | 불필요 | 전체 | MVP |
| `POST` | `/auth/oauth/{provider}` | 카카오·네이버 소셜 로그인 및 JWT 발급 | 불필요 | 전체 | MVP |
| `POST` | `/auth/password/reset/request` | 비밀번호 재설정 요청 | 불필요 | 전체 | MVP |
| `POST` | `/auth/password/reset/confirm` | 비밀번호 재설정 확정 | 불필요 | 전체 | MVP |
| `POST` | `/auth/refresh` | 액세스 토큰 갱신 | 불필요 | 전체 | MVP |
| `POST` | `/auth/logout` | 로그아웃 및 리프레시 토큰 폐기 | 필요 | 전체 | MVP |
| `GET` | `/users/{user_id}` | 사용자 및 초기 정보 조회 | 필요 | 본인, 권한 보유 보호자 | MVP |
| `PATCH` | `/users/{user_id}` | 사용자 및 초기 정보 수정 | 필요 | 본인, 권한 보유 보호자 | MVP |
| `GET` | `/users/{user_id}/preferences` | 청취·음성·자막 설정 조회 | 필요 | 본인, 권한 보유 보호자 | MVP |
| `PATCH` | `/users/{user_id}/preferences` | 청취·음성·자막 설정 수정 | 필요 | 본인, 권한 보유 보호자 | MVP |
| `GET` | `/voice-profiles` | 선택 가능한 안내 음성 목록 | 필요 | 전체 로그인 사용자 | MVP |
| `POST` | `/consent/{user_id}` | 개인정보·보호자 접근 동의 저장 | 필요 | 본인, 동의 권한자 | MVP |
| `GET` | `/consent/{user_id}` | 동의 상태 조회 | 필요 | 본인, 권한 보유 보호자 | MVP |

### 2.2 보호자 연결

| Method | Endpoint | 설명 | 인증 | 주요 역할 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/guardian/invitations` | 6자리 초대 코드 발급 | 필요 | `guardian` 또는 기관 권한 | MVP |
| `POST` | `/guardian/invitations/verify` | 초대 코드 검증 및 연결 정보 미리보기 | 불필요 | 전체 | MVP |
| `POST` | `/guardian/invitations/accept` | 초대 코드 수락 및 보호자 연결 생성 | 필요 | `elder` | MVP |
| `POST` | `/guardian/link` | 고령자와 보호자 연결 요청 | 필요 | `guardian` | MVP |
| `GET` | `/guardian/{guardian_id}/elders` | 연결된 고령자 목록 및 최신 상태 | 필요 | `guardian` | MVP |
| `PATCH` | `/guardian/link/{link_id}` | 연결 상태·접근 범위 수정 | 필요 | `guardian` | MVP |
| `DELETE` | `/guardian/link/{link_id}` | 연결 해제 | 필요 | `guardian` | MVP |

### 2.3 홈·캘린더·지역 캠페인

| Method | Endpoint | 설명 | 인증 | 주요 역할 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/dashboard/{user_id}` | 고령자 홈 또는 보호자 홈 요약 | 필요 | 사용자 유형별 | MVP |
| `GET` | `/calendar/{user_id}/activities` | 날짜별 일기·검사·게임·캠페인 활동 | 필요 | 본인, 권한 보유 보호자 | MVP |
| `GET` | `/campaigns` | 지역 인지건강 캠페인 목록 | 필요 | 로그인 사용자 | MVP |
| `GET` | `/campaigns/{campaign_id}` | 캠페인 상세 | 필요 | 로그인 사용자 | MVP |
| `POST` | `/campaigns/{campaign_id}/participation` | 캠페인 참여 신청 | 필요 | 본인 또는 권한 보유자 | MVP |
| `GET` | `/campaigns/{campaign_id}/participation` | 캠페인 참여 상태 | 필요 | 본인 또는 권한 보유자 | MVP |

### 2.4 세션·질문·답변

| Method | Endpoint | 설명 | 인증 | 주요 역할 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/sessions` | CIST·AI 정서 문답·게임 세션 시작 | 필요 | `elder`, 권한 보유자 | MVP |
| `GET` | `/sessions/{session_id}` | 세션 상태·진행률 조회 | 필요 | 세션 사용자, 권한 보유자 | MVP |
| `PATCH` | `/sessions/{session_id}/settings` | 청취·음성·자막 설정 적용 | 필요 | 세션 사용자, 권한 보유자 | MVP |
| `PATCH` | `/sessions/{session_id}/end` | 세션 종료 | 필요 | 세션 사용자, 권한 보유자 | MVP |
| `GET` | `/sessions` | 세션 목록 조회 | 필요 | 본인, 권한 보유자 | MVP |
| `POST` | `/sessions/{session_id}/answers` | 문항별 답변 저장 | 필요 | 세션 사용자, 권한 보유자 | MVP |
| `GET` | `/questions/daily` | 오늘의 질문 목록 | 필요 | 세션 사용자 | MVP |
| `GET` | `/questions/{question_id}` | 질문 단건 조회 | 필요 | 세션 사용자 | MVP |

### 2.5 녹음·STT·AI 분석

| Method | Endpoint | 설명 | 인증 | 주요 역할 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/recordings` | 문항별 음성 업로드 및 동기화 | 필요 | 세션 사용자 | MVP |
| `GET` | `/recordings/{recording_id}` | 녹음 업로드·분석 상태 조회 | 필요 | 세션 사용자, 권한 보유자 | MVP |
| `POST` | `/voice/transcribe` | Whisper STT 실행 | 필요/서버 전용 | 서버 작업 큐 | MVP |
| `POST` | `/analysis/acoustic` | AST 음향 특징 분석 | 서버 전용 권장 | 서버 작업 큐 | MVP |
| `POST` | `/analysis/cognitive` | KcELECTRA 텍스트 분석 | 서버 전용 권장 | 서버 작업 큐 | MVP |
| `GET` | `/analysis/cognitive/{user_id}/history` | 인지 분석 이력·추이 조회 | 필요 | 본인, 권한 보유 보호자 | MVP |
| `GET` | `/screenings/{session_id}/result` | 특정 검사 결과 조회 | 필요 | 본인, 권한 보유 보호자 | MVP |
| `POST` | `/summary/session` | Gemini 문답 요약 생성 | 서버 전용 권장 | 서버 작업 큐 | MVP |
| `GET` | `/summary/session/{session_id}` | 문답 요약 조회 | 필요 | 본인, 권한 보유 보호자 | MVP |

### 2.6 일기·반응

| Method | Endpoint | 설명 | 인증 | 주요 역할 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/diaries` | 텍스트·음성 기반 일기 생성 | 필요 | `elder`, 권한 보유자 | MVP |
| `POST` | `/diaries/from-session` | AI 문답 요약으로 일기 생성 | 필요 | `elder`, 권한 보유자 | MVP |
| `GET` | `/diaries/{user_id}` | 사용자 일기 목록·날짜 검색 | 필요 | 본인, 권한 보유 보호자 | MVP |
| `GET` | `/diaries/{diary_id}` | 일기 상세 및 반응 조회 | 필요 | 본인, 권한 보유 보호자 | MVP |
| `PATCH` | `/diaries/{diary_id}` | 일기 수정 | 필요 | 작성자 | MVP |
| `DELETE` | `/diaries/{diary_id}` | 일기 삭제 | 필요 | 작성자 | MVP |
| `POST` | `/diaries/{diary_id}/reactions` | 보호자 반응·메시지 저장 | 필요 | `guardian` | MVP |
| `GET` | `/diaries/{diary_id}/reactions` | 일기 반응 목록 | 필요 | 작성자, 권한 보유자 | MVP |

### 2.7 미니게임·캐릭터·보호자 리포트·알림

| Method | Endpoint | 설명 | 인증 | 주요 역할 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/game/result` | 미니게임 결과 전송 | 필요 | `elder` | MVP |
| `GET` | `/game/{user_id}/history` | 미니게임 이력 조회 | 필요 | 본인, 권한 보유 보호자 | MVP |
| `GET` | `/character/{user_id}` | 캐릭터 레벨·경험치·아이템 조회 | 필요 | 본인, 권한 보유자 | MVP |
| `POST` | `/character/{user_id}/xp` | 출석·방문·대화 경험치 적립 | 필요 | 서버 또는 권한 보유자 | MVP |
| `GET` | `/guardian/{guardian_id}/report` | 선택한 고령자 종합 리포트 | 필요 | `guardian` | MVP |
| `POST` | `/notifications/push` | 서비스 알림 생성·발송 | 서버 전용 권장 | 서버 또는 권한 보유자 | MVP |
| `GET` | `/notifications/{user_id}` | 알림 목록 및 미읽음 수 | 필요 | 본인 | MVP |
| `PATCH` | `/notifications/{id}/read` | 알림 읽음 처리 | 필요 | 수신자 | MVP |
| `PATCH` | `/notifications/read-all` | 현재 사용자의 미읽음 알림 전체 읽음 처리 | 필요 | 수신자 | MVP |

## 3. 인증·사용자·동의 API

### 3.1 `POST /auth/register` - 회원가입

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | string | Y | 로그인 이메일 |
| `password` | string | Y | 8자 이상 |
| `name` | string | Y | 사용자 이름 |
| `role` | enum | Y | `elder`, `guardian` |
| `birth_date` | string | N | `YYYY-MM-DD`, 정확한 생년월일 입력을 원하지 않으면 생략 가능 |
| `age_group` | enum | N | `60s`, `70s`, `80s_plus`, `unknown` |
| `gender` | enum | N | `male`, `female`, `other`, `unknown` |
| `phone` | string | N | 연락처 |

#### Response `201`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `user_id` | string | 생성된 사용자 ID |
| `role` | enum | 가입 역할 |
| `profile_completed` | boolean | 초기 사용자 정보 입력 완료 여부 |
| `created_at` | string | 가입 일시 |

```json
{
  "user_id": "usr_01J...",
  "role": "elder",
  "profile_completed": false,
  "created_at": "2026-08-05T10:30:00+09:00"
}
```

### 3.2 `POST /auth/login` - 로그인

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | string | Y | 이메일 |
| `password` | string | Y | 비밀번호 |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `access_token` | string | JWT 액세스 토큰, 기본 유효기간 1시간 |
| `refresh_token` | string | 리프레시 토큰, 기본 유효기간 30일 |
| `expires_in` | integer | 액세스 토큰 만료까지 남은 초 |
| `user_id` | string | 사용자 ID |
| `role` | enum | `elder`, `guardian` |
| `profile_completed` | boolean | 초기 정보 입력 필요 여부 판단용 |

### 3.2.1 `POST /auth/oauth/{provider}` - 소셜 로그인

현재 지원 provider는 `kakao`, `naver`이다. 앱이 받은 authorization code를 서버가 각 provider에 교환 요청하고, provider access token 또는 사용자 profile 원문은 앱에 그대로 노출하지 않는다.

#### Path Parameters

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `provider` | enum | Y | `kakao`, `naver` |

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `authorization_code` | string | Y | provider에서 발급한 일회성 authorization code |
| `redirect_uri` | string | Y | provider에 등록한 redirect URI |
| `role` | enum | 조건부 | 신규 계정일 때 `elder`, `guardian`; 기존 계정이면 서버의 기존 역할 사용 |

#### Response `200`

기존 로그인 응답 필드에 `is_new_user`를 추가해 반환한다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `access_token` | string | JWT 액세스 토큰 |
| `refresh_token` | string | 리프레시 토큰 |
| `expires_in` | integer | 액세스 토큰 만료까지 남은 초 |
| `user_id` | string | 사용자 ID |
| `role` | enum | `elder`, `guardian` |
| `profile_completed` | boolean | 초기 정보 입력 완료 여부 |
| `is_new_user` | boolean | 이번 소셜 로그인으로 최초 가입했는지 여부 |

지원하지 않는 provider, 만료된 authorization code, provider 계정의 이메일 검증 실패는 `400` 또는 `401`로 반환한다. provider access token과 authorization code는 로그에 기록하지 않는다.

### 3.2.2 `POST /auth/password/reset/request` - 비밀번호 재설정 요청

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | string | Y | 재설정 안내를 받을 이메일 |

#### Response `202`

등록된 이메일인지 여부를 노출하지 않기 위해 항상 동일한 형태로 응답한다.

```json
{
  "request_id": "pwd_01J...",
  "expires_at": "2026-08-05T12:35:00+09:00"
}
```

#### 보안 규칙

- 동일 이메일·IP에 대한 요청 빈도 제한을 적용한다.
- 재설정 token은 일회성·단기 유효값으로 발급하고 원문을 저장하지 않는다.
- 존재하지 않는 이메일에도 `202`를 반환해 계정 존재 여부를 추측할 수 없게 한다.

### 3.2.3 `POST /auth/password/reset/confirm` - 비밀번호 재설정 확정

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reset_token` | string | Y | 이메일 등으로 전달된 일회성 재설정 token |
| `new_password` | string | Y | 8자 이상 정책을 만족하는 새 비밀번호 |

#### Response `204`

응답 본문 없음. 성공 시 기존 refresh token을 모두 폐기하고 다시 로그인하도록 한다.

### 3.3 `POST /auth/refresh` - 액세스 토큰 갱신

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `refresh_token` | string | Y | 리프레시 토큰 |

#### Response `200`

```json
{
  "access_token": "eyJ...",
  "expires_in": 3600
}
```

### 3.4 `POST /auth/logout` - 로그아웃

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `refresh_token` | string | Y | 폐기할 리프레시 토큰 |

#### Response `204`

응답 본문 없음.

### 3.5 `GET /users/{user_id}` - 사용자 및 초기 정보 조회

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `user_id` | string | 사용자 ID |
| `name` | string | 이름 |
| `role` | enum | `elder`, `guardian` |
| `birth_date` | string/null | 생년월일 |
| `age_group` | enum/null | 연령대 |
| `gender` | enum/null | 성별 |
| `phone` | string/null | 연락처 |
| `education_years` | integer/null | 교육 연수 |
| `literacy` | boolean/null | 문해 가능 여부 |
| `health_conditions` | string[] | 주요 질환 또는 건강 참고 정보 |
| `alcohol_use` | enum/null | `none`, `occasional`, `frequent`, `unknown` |
| `smoking_status` | enum/null | `never`, `former`, `current`, `unknown` |
| `hearing_status` | enum/null | `no_difficulty`, `difficulty`, `unknown` |
| `communication_difficulty` | boolean/null | 의사소통 어려움 여부 |
| `smartphone_skill` | enum/null | `low`, `medium`, `high` |
| `profile_completed` | boolean | 초기 정보 입력 완료 여부 |
| `created_at` | string | 가입 일시 |
| `updated_at` | string | 최종 수정 일시 |

### 3.6 `PATCH /users/{user_id}` - 사용자 및 초기 정보 수정

변경할 필드만 전송한다. 초기 검사 전 화면에서 아래 선택형 정보를 함께 저장할 수 있다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `name` | string | N | 이름 |
| `phone` | string | N | 연락처 |
| `birth_date` | string | N | `YYYY-MM-DD` |
| `age_group` | enum | N | `60s`, `70s`, `80s_plus`, `unknown` |
| `gender` | enum | N | `male`, `female`, `other`, `unknown` |
| `education_years` | integer | N | 교육 연수 |
| `literacy` | boolean | N | 문해 가능 여부 |
| `health_conditions` | string[] | N | 주요 질환 목록 |
| `alcohol_use` | enum | N | `none`, `occasional`, `frequent`, `unknown` |
| `smoking_status` | enum | N | `never`, `former`, `current`, `unknown` |
| `hearing_status` | enum | N | `no_difficulty`, `difficulty`, `unknown` |
| `communication_difficulty` | boolean | N | 의사소통 어려움 여부 |
| `smartphone_skill` | enum | N | `low`, `medium`, `high` |

#### Response `200`

```json
{
  "user_id": "usr_01J...",
  "profile_completed": true,
  "updated_at": "2026-08-05T10:35:00+09:00"
}
```

> 위 정보는 모델이 치매 여부를 직접 판단하는 결정 변수가 아니다. 결과 해석과 사용자별 비교 기준을 보완하는 메타데이터로만 사용한다.

### 3.7 `GET /users/{user_id}/preferences` - 사용 환경 설정 조회

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `preferred_hearing_side` | enum | `left`, `right`, `both`, `unknown` |
| `voice_profile_id` | string | 선택한 안내 음성 ID |
| `speech_rate` | float | 말하기 속도, 기본 `0.9`, 허용 범위 `0.75~1.25` |
| `subtitle_enabled` | boolean | 질문 글자 표시 여부, 기본 `false` |
| `sound_effect_enabled` | boolean | 효과음 사용 여부, 기본 `false` 권장 |
| `updated_at` | string | 최종 수정 일시 |

### 3.8 `PATCH /users/{user_id}/preferences` - 사용 환경 설정 수정

변경할 필드만 전송한다. 고령자의 청취 상태를 확인한 뒤 가장 명확하게 들리는 음성을 선택한다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `preferred_hearing_side` | enum | N | `left`, `right`, `both`, `unknown` |
| `voice_profile_id` | string | N | `/voice-profiles`에서 제공한 음성 ID |
| `speech_rate` | float | N | `0.75~1.25` |
| `subtitle_enabled` | boolean | N | 기본 `false`; 청력 문제 시 보호자가 활성화 가능 |
| `sound_effect_enabled` | boolean | N | 배경음·효과음 사용 여부 |

#### Response `200`

```json
{
  "preferred_hearing_side": "right",
  "voice_profile_id": "voice_ko_02",
  "speech_rate": 0.9,
  "subtitle_enabled": false,
  "sound_effect_enabled": false,
  "updated_at": "2026-08-05T10:40:00+09:00"
}
```

### 3.9 `GET /voice-profiles` - 안내 음성 목록

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `language` | string | N | 기본 `ko` |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `voice_profiles[]` | array | 음성 목록 |
| `voice_profile_id` | string | 음성 ID |
| `name` | string | 화면 표시 이름 |
| `pitch_band` | enum | `low`, `middle`, `high` |
| `clarity` | enum | `normal`, `clear` |
| `preview_audio_url` | string | 미리듣기 음성 URL |
| `recommended_for_elder` | boolean | 고령자 권장 여부 |

### 3.10 `POST /consent/{user_id}` - 동의 저장

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `consent_type` | enum | Y | `data_sharing`, `guardian_access`, `analysis`, `voice_collection`, `research_use` |
| `agreed` | boolean | Y | 동의 여부 |
| `agreed_at` | string | Y | 동의 일시, ISO 8601 |
| `version` | string | Y | 동의 문서 버전 |

#### Response `201`

```json
{
  "consent_id": "cst_01J...",
  "consent_type": "guardian_access",
  "agreed": true,
  "created_at": "2026-08-05T10:45:00+09:00"
}
```

### 3.11 `GET /consent/{user_id}` - 동의 상태 조회

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `consents[]` | array | 동의 항목 배열 |
| `consent_type` | enum | 동의 종류 |
| `agreed` | boolean | 현재 동의 여부 |
| `agreed_at` | string/null | 동의 일시 |
| `version` | string | 적용한 문서 버전 |

## 4. 보호자 연결 API

### 4.1 `POST /guardian/invitations` - 6자리 초대 코드 발급

보호자 또는 기관 권한 사용자가 고령자에게 전달할 초대 코드를 발급한다. 기관 발급자는 신규 사용자 역할 enum을 추가하지 않고 기존 `guardian` 역할 또는 별도 운영 권한 정책으로 인증한다. 초대 코드는 기존 `POST /guardian/link` 직접 연결 요청과 별도의 흐름이다.

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `relation` | string | N | 예: 딸, 아들, 사회복지사, 보호자 |
| `access_scope` | string[] | N | `screening`, `summary`, `diary`, `activity`, `campaign`, `all`; 생략 시 프로젝트 기본 범위 적용 |
| `expires_in` | integer | N | 유효 기간(초), 기본 `600`, 서버 허용 최대값 이내 |

#### Response `201`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `invitation_id` | string | 초대 ID |
| `invite_code` | string | 숫자 6자리 코드. 발급 응답에서만 원문을 반환한다 |
| `status` | enum | `issued` |
| `relation` | string/null | 연결 관계 |
| `access_scope` | string[] | 수락 후 생성될 연결의 접근 범위 |
| `expires_at` | string | 만료 일시 |

#### 보안·수명 규칙

- 코드는 숫자 6자리이며 서버에는 해시로만 저장한다.
- 발급 응답 이후 원문 코드를 다시 조회할 수 없고, URL·로그·분석 이벤트에 원문을 남기지 않는다.
- `verify`는 코드를 소비하지 않으며 `accept`가 성공한 시점에만 1회 소비한다.
- 만료·사용·폐기된 코드는 `410`을 반환하고, 반복 검증 실패는 `429`로 제한한다.

### 4.2 `POST /guardian/invitations/verify` - 초대 코드 검증

초대 코드 입력 화면에서 연결 정보를 미리 보여주기 위한 비소비성 검증이다. 코드 자체는 query string이나 path parameter로 보내지 않는다.

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `invite_code` | string | Y | 숫자 6자리 초대 코드 |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `invitation_id` | string | 초대 ID |
| `status` | enum | `issued` |
| `relation` | string/null | 발급자가 설정한 연결 관계 |
| `access_scope` | string[] | 수락 후 적용될 접근 범위 |
| `expires_at` | string | 만료 일시 |
| `requires_consent` | boolean | 수락자 동의가 필요한지 여부 |

성공적인 검증만으로 연결이 생성되거나 코드가 사용 처리되지 않는다.

### 4.3 `POST /guardian/invitations/accept` - 초대 코드 수락

로그인한 고령자가 초대 코드를 수락하면 서버가 `guardian_link`를 생성한다. `verify`를 먼저 호출했더라도 서버는 수락 시 코드를 다시 검증한다.

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `invite_code` | string | Y | 숫자 6자리 초대 코드 |
| `consent_agreed` | boolean | Y | 보호자 접근 동의 여부. 수락하려면 `true`여야 하며 `false`이면 `422`를 반환한다 |

#### Response `201`

`POST /guardian/link`의 연결 객체에 `invitation_id`를 추가해 반환한다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `invitation_id` | string | 사용한 초대 ID |
| `link_id` | string | 생성된 연결 ID |
| `elder_id` | string | 수락한 고령자 ID |
| `guardian_id` | string | 초대 발급자 ID |
| `status` | enum | `pending`, `active` |
| `access_scope` | string[] | 허용된 조회 범위 |
| `consent_required` | boolean | 추가 동의 필요 여부 |
| `created_at` | string | 연결 생성 일시 |

동일 초대 코드의 재수락, 이미 연결된 보호자·고령자 조합, 동의하지 않은 요청은 각각 `410`, `409`, `422`로 처리한다.

### 4.4 `POST /guardian/link` - 고령자 연결 요청

보호자 1명이 여러 고령자를 관리할 수 있다. 연결 생성 후 고령자 동의가 필요한 경우 `status`가 `pending`으로 반환된다.

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `elder_id` | string | Y | 연결할 고령자 ID |
| `guardian_id` | string | N | 일반 사용자는 JWT에서 식별하며, 기관 관리 요청에서만 명시 가능 |
| `relation` | string | N | 예: 딸, 아들, 사회복지사, 보호자 |
| `access_scope` | string[] | N | `screening`, `summary`, `diary`, `activity`, `campaign`, `all`; 생략 시 프로젝트 기본 범위 적용 |

#### Response `201`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `link_id` | string | 연결 ID |
| `elder_id` | string | 고령자 ID |
| `guardian_id` | string | 보호자 ID |
| `status` | enum | `pending`, `active`, `revoked` |
| `access_scope` | string[] | 허용된 조회 범위 |
| `consent_required` | boolean | 대상자 동의 필요 여부 |
| `created_at` | string | 연결 요청 일시 |

### 4.5 `GET /guardian/{guardian_id}/elders` - 연결 대상자 목록

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `status` | enum | N | `pending`, `active`, `revoked` |
| `page` | integer | N | 기본 `1` |
| `limit` | integer | N | 기본 `20` |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `elders[]` | array | 연결된 고령자 목록 |
| `elder_id` | string | 고령자 ID |
| `elder_name` | string | 고령자 이름 |
| `link_id` | string | 연결 ID |
| `status` | enum | 연결 상태 |
| `access_scope` | string[] | 접근 범위 |
| `consent_status` | enum | `required`, `agreed`, `denied` |
| `latest_display_score` | float/null | 최근 화면 표시 점수 |
| `latest_score_max` | float/null | 최근 화면 표시 점수의 만점 |
| `latest_score_rate` | float/null | 최근 화면 표시 점수의 비율, `0.0~1.0` |
| `latest_risk_level` | enum/null | `normal`, `caution`, `warning` |
| `last_session_at` | string/null | 최근 세션 일시 |

### 4.6 `PATCH /guardian/link/{link_id}` - 연결 정보 수정

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `status` | enum | N | `active`, `revoked` |
| `access_scope` | string[] | N | `screening`, `summary`, `diary`, `activity`, `campaign`, `all` |
| `relation` | string | N | 관계 또는 담당자 유형 |

#### Response `200`

```json
{
  "link_id": "lnk_01J...",
  "status": "active",
  "access_scope": ["screening", "summary", "diary", "activity"],
  "updated_at": "2026-08-05T11:00:00+09:00"
}
```

### 4.7 `DELETE /guardian/link/{link_id}` - 연결 해제

#### Response `204`

응답 본문 없음. 연결을 삭제하는 대신 감사 로그에는 해제 이력을 보존한다.

## 5. 홈·캘린더·지역 캠페인 API

### 5.1 `GET /dashboard/{user_id}` - 홈 요약

사용자 역할에 따라 고령자 홈 또는 보호자 홈에 필요한 요약 데이터를 반환한다.

#### Response `200` - 고령자 예시

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `user_id` | string | 사용자 ID |
| `role` | enum | `elder`, `guardian` |
| `character` | object | 캐릭터 상태 요약 |
| `latest_screening` | object/null | 최근 검사 요약 |
| `latest_summary` | object/null | 최근 AI 문답 요약 |
| `today_tasks[]` | array | 오늘 진행할 검사·활동 |
| `unread_notification_count` | integer | 미읽음 알림 수 |
| `upcoming_campaigns[]` | array | 참여 가능한 지역 캠페인 |

`latest_screening` 객체는 다음 필드를 포함한다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `session_id` | string | 검사 세션 ID |
| `screening_reference_score` | float | 스크리닝 참고 점수, `0.0~1.0` |
| `display_score` | float | 화면에 표시할 점수. 예: `27` |
| `score_max` | float | `display_score`의 만점. 예: `30` |
| `score_rate` | float | 화면 표시 점수의 비율, `0.0~1.0` |
| `risk_level` | enum | `normal`, `caution`, `warning` |
| `display_label` | string | 화면 문구, 예: `추가 확인 권장` |
| `completed_at` | string | 검사 완료 일시 |

### 5.2 `GET /calendar/{user_id}/activities` - 캘린더 활동 조회

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `from_date` | string | Y | `YYYY-MM-DD` |
| `to_date` | string | Y | `YYYY-MM-DD` |
| `types` | string | N | `diary`, `screening`, `emotional_qa`, `game`, `campaign`을 쉼표로 구분 |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `activities[]` | array | 날짜별 활동 목록 |
| `activity_id` | string | 활동 ID |
| `activity_type` | enum | `diary`, `screening`, `emotional_qa`, `game`, `campaign` |
| `reference_id` | string | 원본 리소스 ID |
| `activity_date` | string | 활동 날짜 |
| `title` | string | 화면 표시 제목 |
| `status` | enum | `completed`, `in_progress`, `scheduled` |
| `metadata` | object | 활동별 추가 정보 |

`activity_type=diary`인 경우 `metadata`에는 일기에 저장된 `mood`와 `mood_level`을 포함한다. 감정 enum은 `very_sad`, `sad`, `neutral`, `happy`, `very_happy`이고 `mood_level`은 `1~5` 정수다.

### 5.3 `GET /campaigns` - 지역 캠페인 목록

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `region` | string | N | 지역명 또는 행정구역 코드 |
| `status` | enum | N | `upcoming`, `ongoing`, `ended` |
| `from_date` | string | N | 시작일 |
| `to_date` | string | N | 종료일 |
| `page` | integer | N | 기본 `1` |
| `limit` | integer | N | 기본 `20` |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `campaigns[]` | array | 캠페인 목록 |
| `campaign_id` | string | 캠페인 ID |
| `title` | string | 캠페인 제목 |
| `description` | string | 소개 내용 |
| `region` | string | 지역 |
| `start_at` | string | 시작 일시 |
| `end_at` | string | 종료 일시 |
| `reward_xp` | integer | 참여 보상 경험치 |
| `participation_status` | enum | `not_applied`, `applied`, `completed` |
| `thumbnail_url` | string/null | 썸네일 이미지 |

### 5.4 `GET /campaigns/{campaign_id}` - 캠페인 상세

목록 응답의 모든 필드와 `eligibility`, `location`, `contact`, `detail_url`을 포함한다.

### 5.5 `POST /campaigns/{campaign_id}/participation` - 캠페인 참여

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `user_id` | string | N | 본인 JWT 사용 시 생략 가능 |

#### Response `201`

```json
{
  "participation_id": "cp_01J...",
  "campaign_id": "camp_01J...",
  "status": "applied",
  "applied_at": "2026-08-05T11:20:00+09:00",
  "reward_xp": 500
}
```

### 5.6 `GET /campaigns/{campaign_id}/participation` - 참여 상태

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `participation_id` | string/null | 참여 ID |
| `status` | enum | `not_applied`, `applied`, `completed`, `cancelled` |
| `applied_at` | string/null | 신청 일시 |
| `completed_at` | string/null | 완료 일시 |
| `reward_xp` | integer | 보상 경험치 |

## 6. 세션·질문·답변 API

### 6.1 `POST /sessions` - 새 세션 시작

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `user_id` | string | Y | 검사 대상 고령자 ID |
| `session_type` | enum | N | `cist`, `emotional_qa`, `game`, `mixed`; 기본 `cist` |
| `voice_profile_id` | string | N | 세션에서 사용할 안내 음성 |
| `preferred_hearing_side` | enum | N | `left`, `right`, `both`, `unknown` |
| `subtitle_enabled` | boolean | N | 기본 `false`; 청력 보조 목적일 때만 활성화 |
| `offline_mode` | boolean | N | 오프라인 녹음 동기화 사용 여부 |

#### Response `201`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `session_id` | string | 세션 ID |
| `user_id` | string | 고령자 ID |
| `session_type` | enum | 세션 유형 |
| `status` | enum | `active`, `ended` |
| `started_at` | string | 시작 일시 |
| `current_question_order` | integer | 현재 문항 순서, 초기값 `1` |
| `answered_count` | integer | 저장된 답변 수 |
| `total_questions` | integer | 전체 문항 수 |
| `settings` | object | 청취·음성·자막 설정 |

```json
{
  "session_id": "ses_01J...",
  "user_id": "usr_elder_01J...",
  "session_type": "cist",
  "status": "active",
  "started_at": "2026-08-05T11:30:00+09:00",
  "current_question_order": 1,
  "answered_count": 0,
  "total_questions": 5,
  "settings": {
    "voice_profile_id": "voice_ko_02",
    "preferred_hearing_side": "right",
    "subtitle_enabled": false,
    "speech_rate": 0.9
  }
}
```

### 6.2 `GET /sessions/{session_id}` - 세션 상태

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `session_id` | string | 세션 ID |
| `user_id` | string | 고령자 ID |
| `session_type` | enum | `cist`, `emotional_qa`, `game`, `mixed` |
| `status` | enum | `active`, `ended` |
| `current_question_order` | integer | 다음에 진행할 문항 순서 |
| `answered_count` | integer | 완료된 답변 수 |
| `total_questions` | integer | 전체 문항 수 |
| `recording_sync_status` | enum | `device_saved`, `server_pending`, `server_uploaded`, `analysis_completed`, `failed` |
| `started_at` | string | 시작 일시 |
| `ended_at` | string/null | 종료 일시 |

### 6.3 `PATCH /sessions/{session_id}/settings` - 세션별 설정

검사 화면에서 자막을 켜거나, 시작 전에 선택한 음성과 말하기 속도를 적용한다.

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `preferred_hearing_side` | enum | N | `left`, `right`, `both`, `unknown` |
| `voice_profile_id` | string | N | 안내 음성 ID |
| `speech_rate` | float | N | `0.75~1.25` |
| `subtitle_enabled` | boolean | N | 기본 `false` |
| `sound_effect_enabled` | boolean | N | 기본 `false` 권장 |

#### Response `200`

설정 객체를 반환한다.

### 6.4 `PATCH /sessions/{session_id}/end` - 세션 종료

#### Response `200`

```json
{
  "session_id": "ses_01J...",
  "status": "ended",
  "ended_at": "2026-08-05T11:42:00+09:00",
  "answered_count": 5,
  "analysis_status": "pending"
}
```

`analysis_status`는 `pending`, `processing`, `completed`, `failed` 중 하나다.

### 6.5 `GET /sessions` - 세션 목록

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `user_id` | string | Y | 조회 대상 고령자 ID |
| `session_type` | enum | N | `cist`, `emotional_qa`, `game`, `mixed` |
| `date` | string | N | 특정 날짜, `YYYY-MM-DD` |
| `page` | integer | N | 기본 `1` |
| `limit` | integer | N | 기본 `20` |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `sessions[]` | array | 세션 목록 |
| `session_id` | string | 세션 ID |
| `session_type` | enum | 세션 유형 |
| `status` | enum | `active`, `ended` |
| `started_at` | string | 시작 일시 |
| `ended_at` | string/null | 종료 일시 |
| `total` | integer | 전체 건수 |
| `page` | integer | 현재 페이지 |

### 6.6 `POST /sessions/{session_id}/answers` - 문항별 답변 저장

음성 답변은 `recording_id` 또는 `transcript_id`를 연결한다. 네트워크가 끊긴 상태에서 저장한 답변은 `client_answer_id`로 재전송한다.

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `client_answer_id` | string | Y | 기기에서 생성한 중복 방지 ID |
| `question_id` | string | Y | 질문 ID |
| `answer_text` | string | N | 텍스트 답변, STT 완료 후 저장 가능 |
| `recording_id` | string | N | 음성 녹음 ID |
| `transcript_id` | string | N | STT 결과 ID |
| `response_time_ms` | integer | N | 질문 종료 후 답변까지 걸린 시간 |
| `answered_at` | string | Y | 답변 일시 |

`answer_text`, `recording_id`, `transcript_id` 중 하나 이상은 입력해야 한다.

#### Response `201`

```json
{
  "answer_id": "ans_01J...",
  "question_id": "q_01J...",
  "saved": true,
  "next_question_order": 2,
  "sync_status": "server_uploaded"
}
```

### 6.7 `GET /questions/daily` - 오늘의 질문 목록

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `user_id` | string | Y | 질문 대상 사용자 |
| `session_type` | enum | N | `cist`, `emotional_qa` |
| `type` | enum | N | `orientation`, `memory`, `attention`, `language`, `emotion` |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `questions[]` | array | 질문 배열 |
| `question_id` | string | 질문 ID |
| `content` | string | 질문 내용 |
| `type` | enum | 질문 유형 |
| `order` | integer | 진행 순서 |
| `subtitle_available` | boolean | 자막 표시 가능 여부 |
| `hint` | string/null | 힌트 텍스트 |

### 6.8 `GET /questions/{question_id}` - 질문 단건 조회

#### Response `200`

```json
{
  "question_id": "q_01J...",
  "content": "오늘이 무슨 요일인지 말씀해 주세요.",
  "type": "orientation",
  "order": 1,
  "hint": null,
  "subtitle_available": true
}
```

> 화면은 한 번에 하나의 질문만 표시하며, `다음`, `다시 듣기`, `처음으로` 동작은 프론트엔드에서 처리한다. 서버는 세션 진행 상태와 답변 저장을 담당한다.

## 7. 녹음·STT·AI 분석 API

### 7.1 `POST /recordings` - 문항별 음성 업로드 및 동기화

`Content-Type: multipart/form-data`를 사용한다. 파일은 `wav`, `m4a`, `mp3`, 최대 25MB를 허용한다.

#### Form Data

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `audio_file` | file | Y | 고령자 답변 음성 |
| `client_recording_id` | string | Y | 기기 생성 UUID, 재전송 중복 방지 |
| `user_id` | string | Y | 고령자 ID |
| `session_id` | string | Y | 세션 ID |
| `question_id` | string | Y | 문항 ID |
| `recorded_at` | string | Y | 기기 녹음 일시 |
| `device_status` | enum | N | `device_saved`, `server_pending` |

#### Response `201`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `recording_id` | string | 서버 녹음 ID |
| `client_recording_id` | string | 기기 녹음 ID |
| `sync_status` | enum | `server_uploaded`, `analysis_completed`, `failed` |
| `transcript_status` | enum | `pending`, `processing`, `completed`, `failed` |
| `analysis_status` | enum | `pending`, `processing`, `completed`, `failed` |
| `deduplicated` | boolean | 기존 동일 녹음 재전송 여부 |

```json
{
  "recording_id": "rec_01J...",
  "client_recording_id": "client-rec-7f2...",
  "sync_status": "server_uploaded",
  "transcript_status": "pending",
  "analysis_status": "pending",
  "deduplicated": false
}
```

### 7.2 `GET /recordings/{recording_id}` - 녹음 처리 상태

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `recording_id` | string | 녹음 ID |
| `session_id` | string | 세션 ID |
| `question_id` | string | 문항 ID |
| `sync_status` | enum | `device_saved`, `server_pending`, `server_uploaded`, `analysis_completed`, `failed` |
| `transcript_id` | string/null | STT 결과 ID |
| `acoustic_analysis_id` | string/null | AST 분석 결과 ID |
| `cognitive_analysis_id` | string/null | KcELECTRA 분석 결과 ID |
| `error_message` | string/null | 실패 시 오류 내용 |
| `updated_at` | string | 최종 처리 일시 |

### 7.3 `POST /voice/transcribe` - Whisper STT

서버 작업 큐에서 `recording_id`를 기준으로 호출하는 것을 권장한다. 기존 클라이언트 직접 호출이 필요한 경우에도 동일한 메타데이터를 전송한다.

#### Form Data

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `audio_file` | file | 조건부 | 직접 업로드 시 필수 |
| `recording_id` | string | 조건부 | 서버 녹음이 있으면 파일 대신 사용 |
| `user_id` | string | Y | 고령자 ID |
| `session_id` | string | Y | 세션 ID |
| `question_id` | string | Y | 질문 ID |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `transcript_id` | string | 저장된 전사 ID |
| `recording_id` | string | 원본 녹음 ID |
| `transcript` | string | 변환된 텍스트 |
| `duration_sec` | float | 발화 길이(초) |
| `confidence` | float | 인식 신뢰도, `0.0~1.0` |
| `language` | string | 감지 언어, 기본 `ko` |
| `model` | string | 사용한 STT 모델명 |

### 7.4 `POST /analysis/acoustic` - AST 음향 분석

AST는 고령자가 **어떻게 말했는지**를 분석한다. 발화 속도, 침묵·망설임, 음성 에너지, 주파수 패턴, 발화 안정성 등의 음향 특징을 저장한다.

> 이 API는 앱이 직접 호출하기보다 음성 업로드 이후 서버 작업 큐에서 호출한다.

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `recording_id` | string | Y | 분석할 녹음 ID |
| `user_id` | string | Y | 고령자 ID |
| `session_id` | string | Y | 세션 ID |
| `segment_length_sec` | integer | N | 기본 `8`; AST 입력 길이 |
| `model_version` | string | N | AST 모델 버전 |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `acoustic_analysis_id` | string | AST 분석 ID |
| `acoustic_reference_score` | float | 음향 기반 참고 점수, `0.0~1.0` |
| `acoustic_flags` | object | 음향 특징별 플래그 |
| `speech_rate` | float | 발화 속도 |
| `pause_ratio` | float | 침묵 구간 비율 |
| `energy_variability` | float | 음성 에너지 변동성 |
| `speech_stability` | float | 발화 안정성 |
| `analyzed_at` | string | 분석 일시 |

### 7.5 `POST /analysis/cognitive` - KcELECTRA 텍스트 분석

KcELECTRA는 고령자가 **무슨 말을 했는지**를 분석한다. 질문과 답변의 적절성, 지남력, 기억 관련 표현, 문장 길이, 어휘 다양성, 의미 일관성 등을 분석한다.

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `transcript_id` | string | Y | Whisper 전사 ID |
| `transcript` | string | Y | 분석할 텍스트 |
| `user_id` | string | Y | 고령자 ID |
| `session_id` | string | Y | 세션 ID |
| `question_id` | string | N | 문항 ID |
| `question_type` | enum | Y | `orientation`, `memory`, `attention`, `language`, `emotion` |
| `acoustic_analysis_id` | string | N | AST 분석 결과 ID |
| `fusion_mode` | enum | N | `none`, `average`, `weighted_average`; 실험 설정용 |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `analysis_id` | string | 분석 결과 ID |
| `language_reference_score` | float | 텍스트 기반 참고 점수, `0.0~1.0` |
| `screening_reference_score` | float/null | 모델 결합 후 참고 점수 |
| `label` | enum | `normal`, `attention_required`; 진단명이 아님 |
| `risk_level` | enum | `normal`, `caution`, `warning` |
| `cognitive_flags` | object | 인지 영역별 플래그 |
| `domain_scores` | object | 영역별 정답 수·문항 수·비율 |
| `model_breakdown` | object | AST·KcELECTRA 개별 결과 |
| `analyzed_at` | string | 분석 일시 |

`cognitive_flags` 예시:

```json
{
  "orientation": false,
  "memory": true,
  "attention": false,
  "language": false
}
```

`domain_scores` 예시:

```json
{
  "orientation": {"correct": 4, "total": 4, "score_rate": 1.0},
  "memory": {"correct": 4, "total": 4, "score_rate": 1.0},
  "attention": {"correct": 4, "total": 5, "score_rate": 0.8},
  "language": {"correct": 2, "total": 3, "score_rate": 0.67}
}
```

### 7.6 `GET /analysis/cognitive/{user_id}/history` - 분석 이력

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `limit` | integer | N | 기본 `30` |
| `from_date` | string | N | 시작일 |
| `to_date` | string | N | 종료일 |
| `aggregation` | enum | N | `answer`, `session`, `user`; 기본 `session` |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `records[]` | array | 분석 이력 |
| `analysis_id` | string | 분석 ID |
| `session_id` | string | 세션 ID |
| `screening_reference_score` | float | 스크리닝 참고 점수 |
| `display_score` | float | 화면 표시 점수. 검사 유형별 환산 기준을 적용한 값 |
| `score_max` | float | 화면 표시 점수의 만점. 예: `30` |
| `score_rate` | float | `display_score / score_max`, `0.0~1.0` |
| `label` | enum | `normal`, `attention_required` |
| `risk_level` | enum | `normal`, `caution`, `warning` |
| `domain_scores` | object | 영역별 점수 |
| `trend` | enum | `improving`, `declining`, `stable` |
| `avg_score_30d` | float | 최근 30일 평균 |
| `score_delta` | float/null | 직전 동일 집계 결과 대비 `display_score` 차이. 첫 기록은 `null` |
| `analyzed_at` | string | 분석 일시 |

### 7.7 `GET /screenings/{session_id}/result` - 검사 결과

고령자 결과 화면과 보호자 리포트에서 사용하는 사용자 노출용 응답이다.

#### Response `200`

```json
{
  "session_id": "ses_01J...",
  "user_id": "usr_elder_01J...",
  "screening_reference_score": 0.72,
  "display_score": 27,
  "score_max": 30,
  "score_rate": 0.9,
  "risk_level": "caution",
  "display_label": "인지기능 저하 의심 신호",
  "recommendation": "반복 검사 결과를 확인하고 필요하면 전문기관 상담을 권장합니다.",
  "domain_scores": {
    "orientation": {"correct": 4, "total": 4, "score_rate": 1.0},
    "memory": {"correct": 4, "total": 4, "score_rate": 1.0},
    "attention": {"correct": 4, "total": 5, "score_rate": 0.8},
    "language": {"correct": 2, "total": 3, "score_rate": 0.67}
  },
  "completed_at": "2026-08-05T11:42:00+09:00"
}
```

### 7.8 `POST /summary/session` - Gemini 문답 요약 생성

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `session_id` | string | Y | 세션 ID |
| `user_id` | string | Y | 고령자 ID |
| `qa_pairs[]` | array | Y | 질문·답변 쌍 |
| `question_id` | string | Y | 질문 ID |
| `question` | string | Y | 질문 내용 |
| `answer` | string | Y | 답변 전사문 |
| `question_type` | enum | N | 질문 유형 |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `summary_id` | string | 저장된 요약 ID |
| `session_id` | string | 세션 ID |
| `summary` | string | 문답 요약 |
| `vocabulary_score` | float | 어휘 다양성 참고 점수, `0~100` |
| `keyword_flags[]` | string[] | 주목 키워드 목록 |
| `created_at` | string | 생성 일시 |

### 7.9 `GET /summary/session/{session_id}` - 세션 요약 조회

#### Response `200`

7.8의 응답 필드에 `qa_count`, `source_status`를 추가해 반환한다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `qa_count` | integer | 요약에 포함된 문답 수 |
| `source_status` | enum | `pending`, `completed`, `failed` |

## 8. 일기·보호자 반응 API

### 8.1 `POST /diaries` - 일기 생성

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `user_id` | string | Y | 작성자 ID |
| `source_type` | enum | Y | `manual`, `voice`, `session` |
| `title` | string | N | 제목 |
| `content` | string | Y | 일기 본문 또는 STT 결과 |
| `recording_id` | string | N | 음성 일기 원본 녹음 |
| `session_id` | string | N | 문답 세션에서 작성한 경우 |
| `mood` | enum | N | `very_sad`, `sad`, `neutral`, `happy`, `very_happy` |
| `mood_level` | integer | N | 감정 단계 `1~5`. `mood`와 함께 보내면 값이 일치해야 함 |
| `written_at` | string | Y | 작성 일시 |

#### Response `201`

```json
{
  "diary_id": "dry_01J...",
  "user_id": "usr_elder_01J...",
  "source_type": "session",
  "title": "오늘의 이야기",
  "content": "오늘은 산책을 하고 이웃을 만났다.",
  "mood": "happy",
  "mood_level": 4,
  "written_at": "2026-08-05T12:00:00+09:00",
  "created_at": "2026-08-05T12:00:03+09:00"
}
```

### 8.2 `POST /diaries/from-session` - 문답 요약으로 일기 생성

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `session_id` | string | Y | 문답 세션 ID |
| `user_id` | string | Y | 고령자 ID |
| `summary_id` | string | N | 기존 Gemini 요약 ID |
| `title` | string | N | 일기 제목 |
| `content` | string | N | 사용자가 수정한 내용. 생략 시 요약 본문 사용 |
| `mood` | enum | N | `very_sad`, `sad`, `neutral`, `happy`, `very_happy` |
| `mood_level` | integer | N | 감정 단계 `1~5` |

#### Response `201`

`POST /diaries`와 동일한 일기 객체를 반환한다.

### 8.3 `GET /diaries/{user_id}` - 일기 목록

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `date` | string | N | 특정 날짜 |
| `from_date` | string | N | 시작일 |
| `to_date` | string | N | 종료일 |
| `page` | integer | N | 기본 `1` |
| `limit` | integer | N | 기본 `20` |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `diaries[]` | array | 일기 목록 |
| `diary_id` | string | 일기 ID |
| `title` | string | 제목 |
| `preview` | string | 미리보기 |
| `source_type` | enum | `manual`, `voice`, `session` |
| `mood` | enum/null | `very_sad`, `sad`, `neutral`, `happy`, `very_happy` |
| `mood_level` | integer/null | 감정 단계 `1~5` |
| `written_at` | string | 작성 일시 |
| `reaction_count` | integer | 반응 수 |
| `total` | integer | 전체 건수 |
| `page` | integer | 현재 페이지 |

### 8.4 `GET /diaries/{diary_id}` - 일기 상세

#### Response `200`

`diary_id`, `user_id`, `source_type`, `title`, `content`, `session_id`, `mood`, `mood_level`, `written_at`, `created_at`, `updated_at`, `reactions[]`를 반환한다.

### 8.5 `PATCH /diaries/{diary_id}` - 일기 수정

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `title` | string | N | 제목 |
| `content` | string | N | 본문 |

#### Response `200`

`diary_id`, `title`, `content`, `updated_at`을 반환한다.

### 8.6 `DELETE /diaries/{diary_id}` - 일기 삭제

#### Response `204`

응답 본문 없음.

### 8.7 `POST /diaries/{diary_id}/reactions` - 보호자 반응 저장

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reaction_type` | enum | Y | `heart`, `smile`, `cheer`, `pray`, `cry`, `message` |
| `message` | string | 조건부 | `reaction_type=message`일 때 메시지 |

#### Response `201`

```json
{
  "reaction_id": "rct_01J...",
  "diary_id": "dry_01J...",
  "reactor_id": "usr_guardian_01J...",
  "reaction_type": "heart",
  "message": null,
  "created_at": "2026-08-05T12:05:00+09:00"
}
```

### 8.8 `GET /diaries/{diary_id}/reactions` - 반응 목록

일기 작성자 또는 연결된 보호자만 조회할 수 있다. `reactions[]`에 `reaction_id`, `reactor_id`, `reactor_name`, `reaction_type`, `message`, `created_at`을 포함한다.

## 9. 미니게임·캐릭터 API

### 9.1 `POST /game/result` - 미니게임 결과 전송

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `user_id` | string | Y | 고령자 ID |
| `session_id` | string | Y | 게임 세션 ID |
| `game_type` | enum | Y | `image_match`, `consonant`, `word_match` |
| `score` | integer | Y | 획득 점수 |
| `response_times[]` | float[] | Y | 문항별 응답 시간(초) |
| `error_count` | integer | Y | 오답 횟수 |
| `total_questions` | integer | Y | 전체 문항 수 |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `game_result_id` | string | 결과 ID |
| `cognitive_index` | float | 게임 기반 인지기능 참고 지표, `0~100` |
| `xp_earned` | integer | 획득 경험치 |
| `character_level` | integer | 현재 캐릭터 레벨 |
| `level_up` | boolean | 레벨업 여부 |

### 9.2 `GET /game/{user_id}/history` - 게임 이력

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `game_type` | enum | N | 게임 종류 |
| `limit` | integer | N | 기본 `20` |

#### Response `200`

`records[]`에 `game_result_id`, `game_type`, `score`, `cognitive_index`, `played_at`을 포함한다.

### 9.3 `GET /character/{user_id}` - 캐릭터 상태

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `user_id` | string | 사용자 ID |
| `level` | integer | 현재 레벨 |
| `xp_current` | integer | 현재 경험치 |
| `xp_next` | integer | 다음 레벨까지 필요한 경험치 |
| `skin_id` | string | 현재 스킨 ID |
| `unlocked[]` | string[] | 해금 아이템 ID 목록 |

### 9.4 `POST /character/{user_id}/xp` - 경험치 적립

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `amount` | integer | Y | 지급 경험치 |
| `reason` | enum | Y | `attendance`, `visit`, `chat`, `campaign`, `game` |

#### Response `200`

```json
{
  "xp_current": 320,
  "level": 3,
  "level_up": false
}
```

## 10. 보호자 리포트 API

### 10.1 `GET /guardian/{guardian_id}/report` - 고령자 종합 리포트

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `elder_id` | string | Y | 연결된 고령자 ID |
| `from_date` | string | N | 추이 시작일 |
| `to_date` | string | N | 추이 종료일 |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `elder_id` | string | 고령자 ID |
| `elder_name` | string | 고령자 이름 |
| `latest_summary` | string/null | 최근 AI 문답 요약 |
| `latest_screening_score` | float/null | 최근 스크리닝 참고 점수, `0.0~1.0` 정규화 값 |
| `latest_display_score` | float/null | 화면에 표시할 최근 점수 |
| `latest_score_max` | float/null | `latest_display_score`의 만점 |
| `latest_score_rate` | float/null | 최근 화면 표시 점수의 비율, `0.0~1.0` |
| `latest_risk_level` | enum/null | `normal`, `caution`, `warning` |
| `vocabulary_score` | float/null | 어휘 다양성 참고 점수 |
| `game_cognitive_index` | float/null | 게임 기반 참고 지표 |
| `alert_level` | enum | `none`, `caution`, `warning` |
| `trend_30d` | enum | `improving`, `declining`, `stable` |
| `last_session_at` | string/null | 최근 세션 일시 |
| `activity_summary_7d` | object | 최근 7일 활동 지표 |
| `trend_points[]` | array | 차트용 날짜별 추이 |
| `recent_alerts[]` | array | 보호자 알림 목록 |

`trend_points[]` 예시:

```json
[
  {
    "date": "2026-08-01",
    "screening_reference_score": 0.70,
    "display_score": 21,
    "score_max": 30,
    "score_rate": 0.70,
    "score_delta": null,
    "risk_level": "caution"
  },
  {
    "date": "2026-08-05",
    "screening_reference_score": 0.72,
    "display_score": 24.1,
    "score_max": 30,
    "score_rate": 0.8033,
    "score_delta": 3.1,
    "risk_level": "caution"
  }
]
```

> 보호자 화면의 “위험 추이 차트”는 반복 검사 결과를 시각화하는 기능이다. 단일 점수로 확정적인 진단 문구를 만들지 않는다.

## 11. 알림 API

### 11.1 `POST /notifications/push` - 푸시 알림 생성

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `target_user_id` | string | Y | 수신자 ID |
| `title` | string | Y | 알림 제목 |
| `body` | string | Y | 알림 내용 |
| `type` | enum | Y | `screening_alert`, `session_complete`, `summary`, `reminder`, `campaign`, `weekly_report`, `guardian_reaction` |
| `data` | object | N | 화면 이동용 추가 페이로드 |

#### Response `201`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `notification_id` | string | 알림 ID |
| `sent_at` | string | 발송 일시 |

### 11.2 `GET /notifications/{user_id}` - 알림 목록

#### Query Parameters

| 파라미터 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `limit` | integer | `20` | 조회 개수 |
| `unread_only` | boolean | `false` | 미읽음만 조회 |
| `type` | enum | - | 특정 알림 유형만 조회 |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `notifications[]` | array | 알림 배열 |
| `notification_id` | string | 알림 ID |
| `title` | string | 제목 |
| `body` | string | 내용 |
| `type` | enum | 알림 유형 |
| `data` | object/null | 화면 이동 데이터 |
| `is_read` | boolean | 읽음 여부 |
| `created_at` | string | 생성 일시 |
| `unread_count` | integer | 미읽음 건수 |

### 11.3 `PATCH /notifications/{id}/read` - 읽음 처리

#### Response `200`

```json
{
  "notification_id": "noti_01J...",
  "is_read": true,
  "read_at": "2026-08-05T12:20:00+09:00"
}
```

### 11.4 `PATCH /notifications/read-all` - 전체 읽음 처리

현재 인증 사용자의 미읽음 알림을 한 번에 읽음 처리한다. 다른 사용자의 알림을 대상으로 하는 `user_id` 입력은 받지 않는다.

#### Response `200`

```json
{
  "updated_count": 5,
  "read_at": "2026-08-05T12:25:00+09:00"
}
```

## 12. 립싱크·TTS 후속 API

중간보고서에서는 립싱크 적용 가능성을 검토 중이므로, 아래 API는 현재 MVP 필수 구현이 아닌 후속 확장 항목으로 분리한다.

### 12.1 `POST /voice/synthesize` - 안내 음성 생성 (Phase 2)

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `text` | string | Y | 음성으로 읽을 문장 |
| `voice_profile_id` | string | Y | 안내 음성 ID |
| `speech_rate` | float | N | 말하기 속도 |
| `session_id` | string | N | 세션 ID |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `audio_url` | string | 생성된 음성 URL |
| `duration_sec` | float | 음성 길이 |
| `viseme_timeline` | object[] | 립싱크용 입 모양 타임라인 |
| `expires_at` | string | URL 만료 시각 |

## 13. 화면별 API 연결표

| 화면 | 사용 API | 핵심 데이터 |
| --- | --- | --- |
| 시작·로그인 화면 | `POST /auth/login`, `POST /auth/register`, `POST /auth/oauth/{provider}`, `POST /auth/password/reset/request`, `POST /auth/password/reset/confirm` | 이메일·카카오·네이버 로그인, 비밀번호 재설정 |
| 사용자 유형 선택 | `POST /auth/register` | `elder`, `guardian` |
| 초기 사용자 정보 입력 | `PATCH /users/{user_id}`, `POST /consent/{user_id}` | 학력, 문해, 건강·생활습관, 청력, 스마트폰 사용 수준, 동의 |
| 청취 환경·음성 선택 | `GET /voice-profiles`, `PATCH /users/{user_id}/preferences` | 잘 들리는 귀, 음성, 말하기 속도 |
| 초대 코드 입력 | `POST /guardian/invitations/verify`, `POST /guardian/invitations/accept` | 6자리 코드 검증, 동의 후 보호자 연결 생성 |
| CIST 검사 | `POST /sessions`, `GET /questions/daily`, `POST /recordings`, `POST /sessions/{session_id}/answers`, `PATCH /sessions/{session_id}/end` | 문항 1개씩 진행, 음성 답변, 오프라인 재전송 |
| CIST 결과·일기 | `GET /screenings/{session_id}/result`, `GET /summary/session/{session_id}`, `POST /diaries/from-session` | 영역별 점수, 참고 점수, 요약, 일기 저장 |
| AI 정서 문답 | `POST /sessions` with `session_type=emotional_qa`, `GET /questions/daily`, `POST /sessions/{session_id}/answers`, `POST /summary/session` | 캐릭터 문답과 요약 |
| 고령자 홈 | `GET /dashboard/{user_id}`, `GET /character/{user_id}`, `GET /notifications/{user_id}` | 캐릭터, 최근 검사, 오늘 할 일, 알림 |
| 달력·일기 | `GET /calendar/{user_id}/activities`, `GET /diaries/{user_id}`, `GET /diaries/{diary_id}`, `POST /diaries` | 날짜별 일기·활동·감정 |
| 지역 캠페인 | `GET /campaigns`, `GET /campaigns/{campaign_id}`, `POST /campaigns/{campaign_id}/participation` | 캠페인 목록·상세·참여 |
| 보호자 대시보드 | `GET /guardian/{guardian_id}/elders`, `GET /guardian/{guardian_id}/report` | 여러 고령자 카드, 점수, 위험 상태, 활동 지표 |
| 보호자 일기·반응 | `GET /diaries/{user_id}`, `POST /diaries/{diary_id}/reactions` | 일기 열람, 하트·감정·메시지 반응 |
| 보호자 위험 추이 | `GET /guardian/{guardian_id}/report`, `GET /analysis/cognitive/{user_id}/history` | 기간별 참고 점수와 추이 |
| 알림 | `GET /notifications/{user_id}`, `PATCH /notifications/{id}/read`, `PATCH /notifications/read-all` | 검사 결과, AI 대화 완료, 캠페인, 주간 리포트 |

### 13.1 Figma 화면에서 확인했지만 MVP 확정 전인 항목

아래 기능은 화면에 버튼 또는 영역이 보이지만 실제 서비스 범위·외부 연동 여부가 확정되지 않았으므로 v1.2 MVP 엔드포인트 목록에는 포함하지 않는다. 제품 결정 후 별도 Issue에서 계약을 확정한다.

| 화면 기능 | 후보 API | 확정 전 확인 사항 |
| --- | --- | --- |
| 지역 기준선 비교 | `GET /analysis/cognitive/{user_id}/benchmark?region=...` | 지역별 기준 데이터의 출처, 개인정보·표본 기준, 차트 표시 여부 |
| 리포트 내보내기 | `GET /guardian/{guardian_id}/report/export?elder_id=...&format=pdf\|csv` | 파일 형식, 비동기 생성 여부, 다운로드 권한·보존 기간 |
| 전문의 상담 예약 | `POST /consultations`, `GET /consultations`, `PATCH /consultations/{consultation_id}` | 예약 대상 기관·외부 서비스 연동, 개인정보 제공 동의, MVP 포함 여부 |

## 14. 백엔드 구현 우선순위

1. 인증·사용자·동의: 회원가입, 로그인, 초기 사용자 정보, 보호자 접근 동의
2. CIST 핵심 흐름: 세션, 질문, 답변, 녹음 업로드, STT, 결과 조회
3. AI 분석 파이프라인: AST·KcELECTRA 결과 저장 및 세션 단위 집계
4. 고령자 화면: 홈, 캐릭터, 알림, 결과·일기, 캘린더
5. 보호자 화면: 다중 고령자 연결, 대시보드, 리포트, 일기 반응
6. 지역 캠페인 및 참여 보상
7. TTS·립싱크 연동은 핵심 검사 흐름 안정화 이후 확장

## 15. 구현 시 확인할 사항

- 실제 배포 전 Base URL을 개발·스테이징·운영 환경별로 분리한다.
- 음성 원본 파일과 식별 정보는 분리 보관하고 파일명에 이름·생년월일을 포함하지 않는다.
- 녹음 파일은 `P001_Q01_01.wav`와 같이 가명화된 식별자를 사용하거나 서버 UUID로 저장한다.
- 화자 분리, 겹침 발화, 주변 소음, 전처리 버전 등 음성 메타데이터를 분석 이력과 함께 저장한다.
- 분석 결과가 준비되지 않은 경우 `pending` 상태를 반환하고 앱은 결과 화면에서 재조회한다.
- 기존 v1.0의 `dementia_score`는 사용자 노출 응답에서 사용하지 않는다. 기존 클라이언트 호환이 필요하면 서버 내부에서만 deprecated alias로 유지하고, 신규 API 응답은 `screening_reference_score`를 사용한다.
- `display_score`, `score_max`, `score_rate`는 화면 표시용 환산값이며 `screening_reference_score`와 의미·계산 기준을 혼용하지 않는다.
- `label=attention_required`, `risk_level=caution|warning`은 의료적 진단명이 아니며 화면 문구도 동일한 원칙을 따른다.
- 보호자 화면에서는 연결된 대상자의 동의 상태와 접근 범위를 항상 확인한 뒤 데이터를 반환한다.
- 초대 코드 원문은 발급 응답에서만 반환하고 저장·로그·URL에 남기지 않으며, 검증 시도 제한과 1회성 소비를 적용한다.
