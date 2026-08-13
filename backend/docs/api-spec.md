# 늘봄(NEULBOM) REST API 명세서

> 성장형 캐릭터 기반 치매 조기 스크리닝 서비스
>
> **Version:** v1.3<br>
> **기준 문서:** 기존 REST API 명세서 v1.2 + Figma Make 치매노노 v45 전체 화면<br>
> **Base URL:** `https://api.dementia-care.com/api/v1`<br>
> **Content-Type:** `application/json`<br>
> **인증:** `Authorization: Bearer {access_token}`

## 0. v1.3 반영 사항

- Figma Make v45에 노출된 모든 고령자·보호자 화면을 구현 범위로 확정한다. Figma에 현재 노출되지 않은 기존 계획 기능도 삭제하지 않고 후속 구현 범위로 유지한다.
- 로그인 상태의 비밀번호 변경, 마이페이지 알림 설정, 캐릭터 성장 단계·월간 활동·연속 출석·경험치 내역, 회원탈퇴 계약을 명시한다.
- AI 정서 문답 대화 내역 조회와 `Asia/Seoul` 기준 다음 날 0시 일기 생성 작업의 예약·처리·실패 상태를 명시한다.
- 기억력 게임 화면의 짝 맞춤 수, 시도 횟수, 경과 시간, 재시작 횟수와 중복 결과 방지를 명시한다.
- 알림 유형·심각도·상태 배지와 화면 이동용 `target_route`, `reference_type`, `reference_id` payload를 고정한다.
- 상담 기관 검색을 시·도/시·군·구 행정구역 코드와 병원·치매안심센터·보건소 유형으로 조회하고 네이버 지도·기관 사이트 링크를 제공하도록 구체화한다.
- Figma의 지역 기준선 비교와 보호자 리포트 내보내기를 구현 대상 API로 확정한다. 외부 기관 실시간 예약, 지역 캠페인, TTS·립싱크도 후속 구현 범위로 유지한다.
- 회원가입 화면 입력값은 사용자 유형 선택까지 클라이언트에 임시 보관하고, `role` 확정 후 `POST /auth/register`를 호출하는 단계 순서를 명시한다.

### v1.2까지의 반영 사항

- 회원 유형은 `elder`, `guardian`으로 구분한다. 화면의 “보호자 / 의료진”은 백엔드에서 모두 `guardian` 역할로 처리한다.
- 회원가입 후 최초 검사 전에 학력, 문해 여부, 건강·생활습관, 청력, 스마트폰 사용 수준을 선택적으로 저장한다.
- 보호자 1명이 여러 명의 고령자를 관리할 수 있도록 연결 목록, 접근 범위, 동의 상태를 관리한다.
- 검사 시작 전에 잘 들리는 귀, 안내 음성, 말하기 속도, 자막 표시 설정을 저장한다. 자막은 기본적으로 비활성화한다.
- CIST 검사와 AI 정서 문답을 서로 다른 세션 유형으로 구분한다.
- 음성 답변을 문항 단위로 저장하고, 오프라인에서 녹음한 파일은 재전송할 수 있도록 `client_recording_id`와 처리 상태를 사용한다.
- 기존 Whisper-KcELECTRA 흐름에 AST 음향 분석 결과를 추가한다. AST와 KcELECTRA는 모델별 결과를 보존하며 최종 스크리닝 참고 점수는 서버에서 집계한다.
- 고령자 화면의 결과·일기·달력·알림, 보호자 화면의 대시보드·일기 반응·위험 추이 차트에 필요한 API를 추가한다. 지역 캠페인은 후속 확장 기능으로 분리한다.
- 로그인 화면의 카카오·네이버 로그인과 비밀번호 재설정 흐름을 지원한다.
- 보호자 또는 기관이 발급한 6자리 초대 코드를 검증하고, 고령자가 수락하면 보호자 연결을 생성한다. 초대 코드는 기존 `/guardian/link` 직접 연결 API와 분리한다.
- 결과 화면에 정규화 점수와 별도로 화면 표시 점수(`display_score`, `score_max`, `score_rate`)를 제공한다.
- 캘린더 일기 활동의 감정(`mood`, `mood_level`), 보호자 반응의 `cry` 유형, 알림 전체 읽음 처리를 명세한다.
- AI 정서 문답은 세션 종료 시 캐릭터가 고령자에게 정성적 결과와 격려 메시지를 안내하고, 정확한 점수·상세 분석은 보호자 화면에만 제공한다.
- 하루 여러 번의 대화를 허용하고, 세션별 분석 결과를 `Asia/Seoul` 기준 하루 단위로 집계해 보호자 리포트와 일일 일기 생성에 사용한다.
- 상담 센터는 초기에는 지역별 목록과 지도·기관 사이트 외부 링크만 제공하고, 실시간 예약·일정 연동은 후속 확장 기능으로 분리한다.
- AI 정서 문답과 게임 세션 완료 시 서버가 경험치를 자동 적립하고, 동일 이벤트의 중복 적립을 차단한다.
- 사용자에게 노출되는 결과는 의료적 진단이 아니라 **인지기능 저하 의심 신호**, **추가 확인 권장**, **스크리닝 참고 점수**로 표현한다.

> 검사 및 AI 분석 결과는 의료적 진단을 대신하지 않는다. `screening_reference_score`, `risk_level` 등은 반복 관찰을 위한 참고 정보이며, 의심 결과가 나타나면 치매안심센터 또는 병원에서 추가 검사를 권고한다.

## 1. 공통 규칙

### 1.1 인증 및 권한

| 구분 | 설명 |
| --- | --- |
| 공개 API | `/auth/register`, `/auth/login`, `/auth/oauth/{provider}`, `/auth/password/reset/**`, `/auth/refresh` |
| 사용자 본인 | 자신의 프로필, 세션, 일기, 게임, 캐릭터, 알림 조회·수정 |
| `guardian` | 동의가 완료된 연결 대상자의 검사·결과·요약·활동·일기 조회 및 반응 작성 |
| 서버 작업 전용 | AST, KcELECTRA, Gemini 분석 API. 앱에서 직접 호출하지 않고 서버 작업 큐에서 호출하는 것을 권장 |

- 보호자는 연결(`guardian_links`)과 동의(`consents`)가 모두 유효한 대상자만 조회할 수 있다.
- `guardian_id`, `user_id`는 가능하면 JWT의 사용자 정보로 확인하며, 다른 사용자를 지정하는 요청은 서버에서 권한을 검증한다.
- 모든 날짜·시간은 ISO 8601 형식과 타임존을 포함한다. 예: `2026-08-05T10:30:00+09:00`
- 사용자 활동일(`local_date`)은 기본 `Asia/Seoul` 시간대의 `00:00:00` 이상, 다음 날 `00:00:00` 미만 구간으로 계산한다. 서버 저장 시간은 UTC를 사용할 수 있지만 집계 기준일은 이 규칙을 따른다.
- `elder`가 요청한 결과·이력 응답에는 정확한 점수·원본 모델 출력·상세 영역 점수를 포함하지 않고 `result_type`, `display_label`, `message`, `recommendation`만 제공한다. 정확한 점수와 상세 분석은 연결·동의·access scope가 확인된 `guardian`에게만 제공한다.
- 역할별 필드 노출은 JWT의 인증 역할로 서버가 결정하며, `role` 또는 `audience` query parameter로 변경할 수 없다.
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
| `PATCH` | `/users/me/password` | 로그인 상태 비밀번호 변경 | 필요 | 본인 | MVP |
| `DELETE` | `/users/me` | 회원탈퇴 및 계정 비활성화 | 필요 | 본인 | MVP |
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
| `GET` | `/campaigns` | 지역 인지건강 캠페인 목록 | 필요 | 로그인 사용자 | Phase 2 |
| `GET` | `/campaigns/{campaign_id}` | 캠페인 상세 | 필요 | 로그인 사용자 | Phase 2 |
| `POST` | `/campaigns/{campaign_id}/participation` | 캠페인 참여 신청 | 필요 | 본인 또는 권한 보유자 | Phase 2 |
| `GET` | `/campaigns/{campaign_id}/participation` | 캠페인 참여 상태 | 필요 | 본인 또는 권한 보유자 | Phase 2 |

### 2.4 세션·질문·답변

| Method | Endpoint | 설명 | 인증 | 주요 역할 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/sessions` | CIST·AI 정서 문답·게임 세션 시작 | 필요 | `elder`, 권한 보유자 | MVP |
| `GET` | `/sessions/{session_id}` | 세션 상태·진행률 조회 | 필요 | 세션 사용자, 권한 보유자 | MVP |
| `PATCH` | `/sessions/{session_id}/settings` | 청취·음성·자막 설정 적용 | 필요 | 세션 사용자, 권한 보유자 | MVP |
| `PATCH` | `/sessions/{session_id}/end` | 세션 종료·정성 결과·경험치 적립 상태 반환 | 필요 | 세션 사용자, 권한 보유자 | MVP |
| `GET` | `/sessions` | 세션 목록 조회 | 필요 | 본인, 권한 보유자 | MVP |
| `POST` | `/sessions/{session_id}/answers` | 문항별 답변 저장 | 필요 | 세션 사용자, 권한 보유자 | MVP |
| `GET` | `/sessions/{session_id}/answers` | 세션 대화·답변 내역 조회 | 필요 | 세션 사용자, 권한 보유자 | MVP |
| `GET` | `/questions/daily` | 오늘의 질문 목록 | 필요 | 세션 사용자 | MVP |
| `GET` | `/questions/{question_id}` | 질문 단건 조회 | 필요 | 세션 사용자 | MVP |

### 2.5 녹음·STT·AI 분석

| Method | Endpoint | 설명 | 인증 | 주요 역할 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/recordings` | 문항 답변·음성 일기 녹음 업로드 및 동기화 | 필요 | 본인, 세션 사용자 | MVP |
| `GET` | `/recordings/{recording_id}` | 녹음 업로드·분석 상태 조회 | 필요 | 세션 사용자, 권한 보유자 | MVP |
| `POST` | `/voice/transcribe` | 선택한 STT provider 실행 | 필요/서버 전용 | 서버 작업 큐 | MVP |
| `POST` | `/analysis/acoustic` | AST 음향 특징 분석 | 서버 전용 권장 | 서버 작업 큐 | MVP |
| `POST` | `/analysis/cognitive` | KcELECTRA 텍스트 분석 | 서버 전용 권장 | 서버 작업 큐 | MVP |
| `GET` | `/analysis/cognitive/{user_id}/history` | 인지 분석 이력·추이 조회 | 필요 | 본인, 권한 보유 보호자 | MVP |
| `GET` | `/analysis/cognitive/{user_id}/benchmark` | 지역 기준선 비교 | 필요 | 권한 보유 보호자 | MVP |
| `GET` | `/screenings/{session_id}/result` | 검사·정서 문답 세션 결과 조회 | 필요 | 본인, 권한 보유 보호자 | MVP |
| `POST` | `/summary/session` | Gemini 문답 요약 생성 | 서버 전용 권장 | 서버 작업 큐 | MVP |
| `GET` | `/summary/session/{session_id}` | 문답 요약 조회 | 필요 | 본인, 권한 보유 보호자 | MVP |
| `POST` | `/summary/daily` | 하루 대화 분석 결과 집계 | 서버 전용 권장 | 서버 작업 큐 | MVP |
| `GET` | `/summary/daily/{user_id}` | 날짜별 대화 집계 요약 조회 | 필요 | 본인, 권한 보유 보호자 | MVP |

### 2.6 일기·반응

| Method | Endpoint | 설명 | 인증 | 주요 역할 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/diaries` | 텍스트·음성 기반 일기 생성 | 필요 | `elder`, 권한 보유자 | MVP |
| `POST` | `/diaries/from-session` | AI 문답 요약으로 일기 생성 | 필요 | `elder`, 권한 보유자 | MVP |
| `POST` | `/diaries/from-daily-summary` | 하루 대화 집계 요약으로 일기 생성 | 서버 작업 또는 본인 | 서버 작업 큐, `elder` | MVP |
| `GET` | `/diaries/{user_id}/generation-status` | 날짜별 일기 생성 상태 조회 | 필요 | 본인, 권한 보유 보호자 | MVP |
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
| `GET` | `/character/{user_id}/xp-history` | 경험치 획득 내역 조회 | 필요 | 본인, 권한 보유자 | MVP |
| `POST` | `/character/{user_id}/xp` | 정서 문답·게임 완료 등 서버 이벤트 경험치 적립 | 서버 전용 권장 | 서버 작업 큐 | MVP |
| `GET` | `/guardian/{guardian_id}/report` | 선택한 고령자 종합 리포트 | 필요 | `guardian` | MVP |
| `GET` | `/guardian/{guardian_id}/report/export` | 보호자 리포트 PDF·CSV 내보내기 | 필요 | `guardian` | MVP |
| `POST` | `/notifications/push` | 서비스 알림 생성·발송 | 서버 전용 (`server:write`) | 서버 워커 | MVP |
| `GET` | `/notifications/{user_id}` | 알림 목록 및 미읽음 수 | 필요 | 본인 | MVP |
| `PATCH` | `/notifications/{id}/read` | 알림 읽음 처리 | 필요 | 수신자 | MVP |
| `PATCH` | `/notifications/read-all` | 현재 사용자의 미읽음 알림 전체 읽음 처리 | 필요 | 수신자 | MVP |

### 2.8 상담 센터

| Method | Endpoint | 설명 | 인증 | 주요 역할 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/counseling/centers` | 지역별 상담 센터 목록 및 지도·기관 사이트 외부 링크 | 필요 | 로그인 사용자 | MVP |
| `GET` | `/counseling/centers/{center_id}/availability` | 상담 가능 시간 조회 | 필요 | `guardian` | Phase 2 |
| `POST` | `/counseling/appointments` | 상담 예약 생성 | 필요 | `guardian` | Phase 2 |
| `GET` | `/counseling/appointments` | 본인 상담 예약 목록 조회 | 필요 | `guardian` | Phase 2 |
| `PATCH` | `/counseling/appointments/{appointment_id}` | 본인 상담 예약 변경 | 필요 | `guardian` | Phase 2 |
| `DELETE` | `/counseling/appointments/{appointment_id}` | 상담 예약 취소 | 필요 | 예약자 | Phase 2 |

예약 생성에는 `elder_id`, `center_id`, `appointment_at`, `consultation_type`(`cognitive_screening`, `neurology`, `counseling`, `other`), `privacy_agreed=true`가 필요하다. 예약은 `requested`로 생성되고, 이후 운영자·기관 연동에서 `confirmed`로 변경할 수 있다. 보호자는 본인이 소유한 예약만 변경·취소할 수 있으며 `requested`·`confirmed` 상태에서만 취소가 가능하다. 예약 신청·변경·취소는 보호자 알림으로 전달된다.

## 3. 인증·사용자·동의 API

### 3.1 `POST /auth/register` - 회원가입

Figma의 단계 순서는 `이름·이메일·비밀번호 입력 → 초대 코드 입력 또는 건너뛰기 → 사용자 유형 선택`이다. 첫 화면 입력만으로 계정을 생성하지 않으며, 클라이언트는 사용자 유형이 확정될 때까지 값을 안전하게 임시 보관한 뒤 최종 `role`과 함께 이 API를 호출한다. 초대 코드를 입력한 경우 공개 `verify`를 먼저 호출하고, `elder` 가입·로그인 완료 후 `accept`를 호출한다.

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
| `state` | string | N | provider OAuth 요청과 함께 전달할 state 값 |

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

지원하지 않는 provider, 등록되지 않은 `redirect_uri`, 만료된 authorization code, provider 계정의 이메일 검증 실패는 `400` 또는 `401`로 반환한다. credential 미설정 또는 provider 장애는 `503`으로 반환한다. provider access token, authorization code, client secret은 로그에 기록하지 않는다. `redirect_uri`는 provider별 `*_ALLOWED_REDIRECT_URIS`에 정확히 일치해야 한다.

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

#### 발송 provider·요청 제한 정책

- 현재 요청 계약은 `email`을 기준으로 하며, 서버의 `PasswordResetNotifier` adapter가 재설정 링크 또는 token 전달을 담당한다.
- 운영에서는 이메일 provider를 연결하고, SMS를 지원할 경우 인증된 사용자 전화번호가 있는 계정에 한해 SMS provider를 연결한다. SMS를 앱에서 직접 선택하게 하는 `delivery_channel` 필드는 provider·전화번호 정책을 확정한 뒤 별도 계약으로 추가한다.
- provider credential, 발신 주소·번호, 재설정 링크의 Base URL은 환경변수 또는 secret manager로 주입하며 소스와 로그에 저장하지 않는다.
- 이메일 기준과 IP 기준의 rate limit을 모두 적용한다. 권장 초기값은 동일 이메일 15분당 3회, 동일 IP 1시간당 10회이며 운영 트래픽에 맞춰 환경변수로 조정한다.
- 제한을 초과하면 `429`와 `Retry-After` 헤더를 반환한다. 계정 존재 여부가 드러나지 않도록 제한 전후의 응답 본문은 동일한 오류 형식을 사용한다.
- provider 장애 시 token 원문을 응답·로그에 남기지 않고 전송 작업을 재시도하거나 실패 상태로 기록한다. 로컬·테스트 환경은 provider가 연결되지 않은 상태이므로 실제 메시지는 발송되지 않는다.

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
  "refresh_token": "eyJ...",
  "expires_in": 3600
}
```

응답의 `refresh_token`은 새 token이며, 요청에 사용한 기존 refresh token은 즉시 폐기한다. 모든 access token 응답은 `user_id`, `role`, `profile_completed`, `is_new_user` 필드를 함께 반환한다(`is_new_user`는 일반 로그인·갱신 시 `false`).

### 3.4 `POST /auth/logout` - 로그아웃

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `refresh_token` | string | Y | 폐기할 리프레시 토큰 |

#### Response `204`

응답 본문 없음.

### 3.4.1 `PATCH /users/me/password` - 로그인 상태 비밀번호 변경

마이페이지에서 현재 비밀번호를 확인한 뒤 새 비밀번호로 변경한다. 현재 access token의 사용자만 변경할 수 있으며 URL에 `user_id`를 받지 않는다.

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `current_password` | string | Y | 현재 비밀번호 |
| `new_password` | string | Y | 8자 이상이며 현재 비밀번호와 다른 새 비밀번호 |
| `logout_other_sessions` | boolean | N | 다른 기기의 refresh token 폐기 여부, 기본 `true` |

#### Response `204`

응답 본문 없음. 현재 비밀번호가 일치하지 않으면 계정 존재 여부를 추가로 노출하지 않는 `400`을 반환한다. `logout_other_sessions=true`이면 사용자의 모든 refresh token을 폐기하며 현재 access token만 만료 시각까지 유효하다. `false`이면 기존 refresh token을 유지한다. 두 경우 모두 비밀번호 변경 보안 이벤트를 audit log에 기록한다.

### 3.4.2 `DELETE /users/me` - 회원탈퇴

현재 access token의 사용자만 탈퇴할 수 있으며, URL에 다른 사용자의 `user_id`를 받지 않는다. 탈퇴 처리는 다음 순서로 수행한다.

- 계정 상태를 `withdrawn`으로 변경하고 탈퇴 시각을 저장한다.
- 재가입 시 이메일을 사용할 수 있도록 로그인 이메일·비밀번호·이름·연락처를 비식별화한다.
- 해당 사용자의 모든 refresh token과 비밀번호 재설정 token을 폐기한다.
- 카카오·네이버 OAuth 계정 연결을 해제한다.
- 검사·일기·감사 로그 등 보존 대상 데이터는 별도 보존 정책에 따라 유지한다.

#### Response `204`

응답 본문 없음. 탈퇴 후 refresh token으로는 다시 인증할 수 없다.

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
| `push_notification_enabled` | boolean | OS push 수신 여부, 기본 `true`; 인앱 알림 저장과 별도 |
| `guardian_reaction_notification_enabled` | boolean | 보호자 반응 알림 수신 여부, 기본 `true` |
| `screening_notification_enabled` | boolean | 검사·인지 활동 결과 알림 수신 여부, 기본 `true` |
| `diary_notification_enabled` | boolean | 일기 생성 완료·미완료 알림 수신 여부, 기본 `true` |
| `weekly_report_notification_enabled` | boolean | 주간 리포트 알림 수신 여부, 기본 `true` |
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
| `push_notification_enabled` | boolean | N | OS push 수신 여부; 인앱 알림 저장과 별도 |
| `guardian_reaction_notification_enabled` | boolean | N | 보호자 반응 알림 수신 여부 |
| `screening_notification_enabled` | boolean | N | 검사·인지 활동 결과 알림 수신 여부 |
| `diary_notification_enabled` | boolean | N | 일기 생성 완료·미완료 알림 수신 여부 |
| `weekly_report_notification_enabled` | boolean | N | 주간 리포트 알림 수신 여부 |

#### Response `200`

```json
{
  "preferred_hearing_side": "right",
  "voice_profile_id": "voice_ko_02",
  "speech_rate": 0.9,
  "subtitle_enabled": false,
  "sound_effect_enabled": false,
  "push_notification_enabled": true,
  "guardian_reaction_notification_enabled": true,
  "screening_notification_enabled": true,
  "diary_notification_enabled": true,
  "weekly_report_notification_enabled": true,
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

#### 구현 권한·기본값 규칙

- 현재 MVP 구현은 JWT subject와 `user_id`가 일치하는 본인 요청만 허용한다. 다른 사용자의 프로필·설정·동의 조회·수정은 `403`을 반환한다. 보호자의 연결·동의·access scope 기반 조회는 4장 구현 이후 연결한다.
- 사용 환경 설정 행이 없으면 `preferred_hearing_side=unknown`, `speech_rate=0.90`, `subtitle_enabled=false`, `sound_effect_enabled=false`, 알림 5종은 `true`인 기본값을 생성해 반환한다.
- `voice_profile_id`는 활성 상태의 한국어 안내 음성만 선택할 수 있으며, 존재하지 않거나 비활성인 ID는 `404`로 거부한다. `language`가 없으면 `ko`를 사용한다.
- 동의는 `(user_id, consent_type, version)` 단위로 이력을 보존한다. 동일 버전을 다시 저장하면 `409`를 반환하며, 허용되지 않은 동의 유형·미래 시각은 `400`으로 거부한다.

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

#### 구현 권한·수명 규칙

- 초대 발급은 `guardian`, 초대 수락은 `elder` 역할만 허용한다. 초대 원문은 발급 응답에서만 반환하고 `code_hash`만 저장한다.
- 초대 기본 유효 기간은 600초, 최대 검증 시도는 5회이며, 검증 실패가 반복되면 IP 기준 `429`를 반환한다. 만료·사용·폐기 코드는 `410`이다.
- 초대 수락은 `consent_agreed=true`일 때만 가능하며, 성공 시 `guardian_access` 동의 이력과 `active` 연결을 함께 생성한다. 직접 연결 요청은 동의 전 `pending`으로 저장한다.
- 연결 수정·해제와 고령자 목록 조회는 JWT의 보호자 본인만 수행할 수 있다. 다른 `guardian_id` 또는 `link_id`를 지정한 요청은 `403`이다.
- 보호자 데이터 접근은 `active` 연결, 보호자 접근 동의, 연결 `access_scope`를 모두 확인한다. `all`을 제외한 scope 밖의 데이터는 `403`이다.

## 5. 홈·캘린더·상담 센터·지역 캠페인 API

지역 캠페인 endpoint와 홈의 `upcoming_campaigns[]`는 Phase 2 확장 범위다. 초기 MVP는 홈·캘린더·상담 센터 목록 및 외부 연결을 우선 구현한다.

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
| `conversation_streak_days` | integer | 연속 AI 정서 문답 완료 일수 |
| `monthly_activity` | object | 이번 달 AI 문답·게임 완료 횟수와 출석 일수 |
| `latest_diary` | object/null | 홈 일기 카드 상태와 대상 날짜 |
| `cognitive_activity` | object/null | 인지 활동 상태 카드 |
| `unread_notification_count` | integer | 미읽음 알림 수 |
| `upcoming_campaigns[]` | array | 참여 가능한 지역 캠페인. Phase 2에서만 제공 |

`character`는 `GET /character/{user_id}`의 `level`, `display_name`, `stage`, `xp_current`, `xp_goal`, `xp_remaining`, `skin_id` 요약을 포함한다.

`today_tasks[]`의 공통 필드는 `task_type`, `status`, `title`, `description`, `target_route`다. `task_type`은 `emotional_qa`, `memory_game`, `diary`이며 `status`는 화면별로 다음 값을 사용한다.

- AI 정서 문답: `not_started`, `in_progress`, `completed`
- 기억력 게임: `new`, `in_progress`, `completed`
- 일기: `scheduled`, `processing`, `completed`, `failed`, `conversation_incomplete`

`monthly_activity`는 `year_month`, `emotional_qa_completed_count`, `game_completed_count`, `attendance_days`, `current_attendance_streak_days`를 포함한다.

`latest_diary`는 `target_date`, `generation_status`, `diary_id`, `display_label`, `message`, `available_at`을 포함한다. `generation_status`는 `scheduled`, `processing`, `completed`, `failed`, `conversation_incomplete` 중 하나다.

`cognitive_activity`는 다음 필드를 포함한다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `status` | enum | `stable`, `observe`, `attention_required` |
| `display_label` | string | `안정적`, `꾸준한 관찰`, `확인 필요` 등 화면 문구 |
| `title` | string | 상태 카드 제목 |
| `message` | string | 진단이 아닌 관찰 안내 문구 |
| `reference_date` | string | 상태 산정 기준일, `YYYY-MM-DD` |

`cognitive_activity.status`는 보호자용 `risk_level`을 그대로 노출하지 않는 고령자 화면 전용 표현이다. 서버가 정한 매핑과 안전 문구를 반환하고 프론트엔드가 임의 점수 임계값으로 판단하지 않는다.

`latest_screening` 객체는 다음 필드를 포함한다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `session_id` | string | 검사 세션 ID |
| `result_status` | enum | `pending`, `processing`, `completed`, `failed` |
| `result_type` | enum/null | `positive_feedback`, `follow_up_recommended`, `insufficient_data` |
| `display_label` | string/null | 고령자 화면 문구 |
| `message` | string/null | 캐릭터가 안내할 메시지 |
| `recommendation` | string/null | 추가 확인 권장 문구 |
| `completed_at` | string | 검사 완료 일시 |

보호자 홈 또는 권한이 확인된 보호자 요청에는 위 필드와 함께 `screening_reference_score`, `display_score`, `score_max`, `score_rate`, `risk_level`, `domain_scores`를 추가한다. 고령자 홈에는 해당 수치 필드를 포함하지 않는다.

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
  "analysis_status": "pending",
  "result_status": "pending",
  "result_type": null,
  "display_label": null,
  "message": null,
  "recommendation": null,
  "xp_earned": 0,
  "character_level": 3,
  "level_up": false
}
```

`analysis_status`와 `result_status`는 각각 `pending`, `processing`, `completed`, `failed` 중 하나다. 분석이 완료되면 `result_type`, `display_label`, `message`, `recommendation`을 채운다.

`result_type`은 고령자 화면에 사용할 정성 결과 유형이며 `positive_feedback`, `follow_up_recommended`, `insufficient_data` 중 하나다. 예시 메시지는 다음 원칙을 따른다.

- `positive_feedback`: 오늘 대화가 잘 진행됐다는 격려 메시지
- `follow_up_recommended`: 정확한 점수 대신 전문기관 추가 확인을 권하는 메시지
- `insufficient_data`: 답변 부족·분석 실패 등으로 결과를 확정할 수 없다는 메시지

`session_type=emotional_qa`인 경우 세션 종료 후 캐릭터가 이 결과를 안내한다. `xp_earned`, `character_level`, `level_up`은 정서 문답 완료 이벤트가 처리된 경우에 반환하며, 동일 `session_id`로 재요청해도 경험치가 중복 적립되지 않는다. 정확한 수치와 상세 분석 결과는 이 응답에 포함하지 않는다.

분석이 비동기로 진행되면 앱은 `GET /screenings/{session_id}/result`를 재조회해 결과를 확인한다.

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

### 6.7 `GET /sessions/{session_id}/answers` - 세션 대화·답변 내역

Figma의 `대화 내역` 화면과 중단 세션 복구에 사용한다. 세션 사용자 본인은 자신의 전사문과 질문·답변만 조회할 수 있고, 보호자는 연결·동의·`summary` 또는 `screening` access scope가 있을 때만 조회할 수 있다.

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `session_id` | string | 세션 ID |
| `session_type` | enum | 세션 유형 |
| `answers[]` | array | 문항 순서별 답변 목록 |
| `answers[].answer_id` | string | 답변 ID |
| `answers[].question_id` | string | 질문 ID |
| `answers[].question_order` | integer | 문항 순서 |
| `answers[].question_text` | string | 화면 표시 질문 문구 |
| `answers[].answer_text` | string/null | STT 또는 텍스트 답변 |
| `answers[].recording_id` | string/null | 원본 녹음 ID. 원본 재생 권한은 별도 검증 |
| `answers[].answered_at` | string | 답변 일시 |

검사 정답·채점 기준, 모델 원문 출력, 다른 사용자의 답변은 반환하지 않는다.

### 6.8 `GET /questions/daily` - 오늘의 질문 목록

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

### 6.9 `GET /questions/{question_id}` - 질문 단건 조회

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

#### 구현 권한·진행 규칙

- 세션 시작·설정 변경·종료·답변 저장은 고령자 본인만 수행한다. 보호자는 활성 연결과 동의가 있고 `screening` 또는 `summary` scope가 있을 때 세션·답변을 읽을 수 있다.
- 세션 설정이 생략되면 사용자 환경 설정 또는 `preferred_hearing_side=unknown`, `speech_rate=0.90`, 자막·효과음 `false`를 사용한다. `voice_profile_id`는 활성 한국어 음성만 허용한다.
- 동일 세션의 `client_answer_id`를 재전송하면 기존 답변을 다시 반환하고 답변 수를 증가시키지 않는다. 종료된 세션과 다른 세션 유형의 질문은 `422`로 거부한다.

## 7. 녹음·STT·AI 분석 API

### 7.1 `POST /recordings` - 문항 답변·음성 일기 업로드 및 동기화

`Content-Type: multipart/form-data`를 사용한다. 파일은 `wav`, `m4a`, `mp3`, 최대 25MB를 허용한다.

#### Form Data

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `audio_file` | file | Y | 고령자 답변 음성 |
| `client_recording_id` | string | Y | 기기 생성 UUID, 재전송 중복 방지 |
| `user_id` | string | Y | 고령자 ID |
| `purpose` | enum | Y | `answer`, `diary` |
| `session_id` | string | 조건부 | `purpose=answer`일 때 세션 ID |
| `question_id` | string | 조건부 | `purpose=answer`일 때 문항 ID |
| `recorded_at` | string | Y | 기기 녹음 일시 |
| `device_status` | enum | N | `device_saved`, `server_pending` |

`purpose=diary`이면 `session_id`, `question_id`를 받지 않으며 STT 완료 후 `POST /diaries`의 `source_type=voice`, `recording_id`로 연결한다. 답변 녹음과 음성 일기는 같은 멱등성·파일 검증·보존 정책을 사용하되 분석 파이프라인과 접근 권한은 목적별로 분리한다.

#### Response `201`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `recording_id` | string | 서버 녹음 ID |
| `client_recording_id` | string | 기기 녹음 ID |
| `purpose` | enum | `answer`, `diary` |
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

#### 구현 권한·동기화 규칙

- 녹음 업로드는 고령자 본인만 수행한다. `purpose=answer`는 본인 소유 세션·활성 질문의 `session_id`와 `question_id`를 함께 받아야 하고, `purpose=diary`는 두 필드를 받지 않는다.
- 현재 local 저장소는 `app.storage.local-root/recordings/{recording_id}.{extension}`에 안전한 서버 키로 저장한다. 허용 확장자는 `wav`, `m4a`, `mp3`, 최대 25MB이며 MIME type도 함께 검증한다.
- 동일 `client_recording_id`를 본인이 재전송하면 기존 `recording_id`와 처리 상태를 `deduplicated=true`로 반환한다. 다른 사용자가 해당 ID를 사용하면 `403`이다.
- 상태 조회는 본인 또는 활성 보호자 연결의 `screening`(답변)·`diary`(음성 일기) scope만 허용한다. STT·AST·KcELECTRA 결과 ID는 처리 완료 시 adapter가 채우며 초기 업로드 응답에서는 `pending`이다.

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

### 7.3 `POST /voice/transcribe` - 선택한 STT provider

서버 작업 큐에서 `recording_id`를 기준으로 호출하는 것을 권장한다. 기존 클라이언트 직접 호출이 필요한 경우에도 동일한 메타데이터를 전송한다.

STT provider는 `STT_PROVIDER`로 선택한다. `openai`는 OpenAI 호스팅 Whisper, `local`은 OpenAI 호환 로컬 Whisper 서버, `google`은 Google Cloud Speech-to-Text V2를 사용한다. `auto`는 설정된 OpenAI → 로컬 Whisper → Google Cloud STT 순서로 선택하고, `none`은 외부 STT를 사용하지 않는다.

로컬 Whisper 서버는 `POST /v1/audio/transcriptions` multipart 계약(`file`, `model`, `language`, `response_format`)을 제공해야 한다. Google Cloud STT는 서버의 Application Default Credentials(로컬 `gcloud auth application-default login`, 운영 서비스 계정 또는 workload identity)를 사용하며 앱에 provider credential을 노출하지 않는다.

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
| `aggregation` | enum | N | `answer`, `session`, `day`, `user`; 기본 `session`. `day`는 `Asia/Seoul` 기준 일일 집계 |

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

`aggregation=day`를 사용하면 하루에 여러 번 진행한 세션을 `local_date`별로 합산한다. 고령자 본인 요청에서는 수치·상세 영역 필드를 제외하고 정성 결과 필드만 반환한다.

### 7.6.1 `GET /analysis/cognitive/{user_id}/benchmark` - 지역 기준선 비교

보호자 위험 추이 화면의 `지역 전산 비교`와 그래프 생성에 사용한다. 연결·동의·`screening` access scope가 확인된 보호자만 조회할 수 있다. 개인을 식별할 수 없도록 최소 표본 수를 충족한 지역 집계만 반환한다.

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `province_code` | string | Y | 시·도 행정구역 코드 |
| `district_code` | string | N | 시·군·구 행정구역 코드 |
| `from_date` | string | N | 비교 시작일 |
| `to_date` | string | N | 비교 종료일 |
| `aggregation` | enum | N | `month`, `quarter`; 기본 `month` |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `user_series[]` | array | 대상 고령자의 날짜별 표시 점수 |
| `regional_series[]` | array | 비식별 지역 기준선 |
| `regional_series[].region_code` | string | 행정구역 코드 |
| `regional_series[].region_name` | string | 화면 표시 지역명 |
| `regional_series[].period` | string | 집계 기간 |
| `regional_series[].average_display_score` | float | 지역 평균 표시 점수 |
| `regional_series[].sample_size` | integer | 집계 표본 수 |
| `regional_series[].suppressed` | boolean | 최소 표본 미달로 값이 숨겨졌는지 여부 |
| `source_name` | string | 기준 데이터 출처 |
| `source_updated_at` | string | 데이터 갱신 일시 |

최소 표본 수, 데이터 출처, 갱신 주기와 지역 비교 문구는 운영 전 확정한다. `suppressed=true`이면 평균 점수 대신 비교 불가 상태를 표시한다.

### 7.7 `GET /screenings/{session_id}/result` - 검사 결과

검사 또는 AI 정서 문답 세션 종료 후 결과를 조회한다. 응답은 JWT 역할과 연결·동의·access scope에 따라 서버가 필터링한다. `audience`는 응답 설명을 위한 값이며 요청으로 지정할 수 없다.

#### 고령자 응답 `200`

정확한 점수, 원본 모델 출력, 상세 `domain_scores`는 포함하지 않는다.

```json
{
  "audience": "elder",
  "session_id": "ses_01J...",
  "user_id": "usr_elder_01J...",
  "session_type": "emotional_qa",
  "result_status": "completed",
  "result_type": "positive_feedback",
  "display_label": "오늘 대화 결과가 좋아요",
  "message": "오늘도 잘 대화하셨어요. 다음 대화에서 만나요.",
  "recommendation": null,
  "completed_at": "2026-08-05T11:42:00+09:00"
}
```

#### 보호자 응답 `200`

연결·동의·access scope가 확인된 보호자에게만 아래 수치·상세 분석 필드를 추가한다. `screening_label`은 수치 결과에 대한 보호자용 상태 문구이며, 고령자용 `display_label`과 구분한다.

```json
{
  "audience": "guardian",
  "session_id": "ses_01J...",
  "user_id": "usr_elder_01J...",
  "session_type": "emotional_qa",
  "result_status": "completed",
  "result_type": "follow_up_recommended",
  "display_label": "추가 확인을 권장해요",
  "message": "오늘 대화가 끝났어요. 보호자와 결과를 함께 확인해 주세요.",
  "recommendation": "반복 관찰 결과를 확인하고 필요하면 전문기관 상담을 권장합니다.",
  "screening_reference_score": 0.72,
  "display_score": 27,
  "score_max": 30,
  "score_rate": 0.9,
  "risk_level": "caution",
  "screening_label": "인지기능 저하 의심 신호",
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

### 7.10 `POST /summary/daily` - 하루 대화 분석 집계

세션 종료 후 생성된 개별 분석 결과를 `Asia/Seoul` 기준 하루 단위로 합산한다. 앱 사용자가 직접 호출하지 않고 서버 작업 큐 또는 스케줄러에서 호출한다.

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `user_id` | string | Y | 집계 대상 고령자 ID |
| `local_date` | string | Y | 집계 기준일, `YYYY-MM-DD` |
| `timezone` | string | N | 기본 `Asia/Seoul` |

#### Response `202`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `daily_summary_id` | string | 일일 집계 ID |
| `user_id` | string | 고령자 ID |
| `local_date` | string | 집계 기준일 |
| `timezone` | string | 집계 시간대 |
| `session_count` | integer | 해당 날짜의 전체 세션 수 |
| `analyzed_session_count` | integer | 분석 완료 세션 수 |
| `status` | enum | `pending`, `processing`, `completed`, `failed` |

### 7.11 `GET /summary/daily/{user_id}` - 일일 대화 집계 조회

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `date` | string | N | 특정 기준일, `YYYY-MM-DD`; 생략 시 최신 집계 |
| `from_date` | string | N | 조회 시작일 |
| `to_date` | string | N | 조회 종료일 |
| `page` | integer | N | 기본 `1` |
| `limit` | integer | N | 기본 `20` |

#### Response `200`

`daily_summaries[]`에는 `daily_summary_id`, `user_id`, `local_date`, `timezone`, `session_count`, `analyzed_session_count`, `status`, `display_label`, `message`, `recommendation`, `diary_id`를 포함한다. 보호자에게는 `screening_reference_score`, `domain_scores`, `trend` 등 수치·상세 집계 필드를 추가하고, 고령자 본인에게는 정성 결과 필드만 반환한다.

### 7.12 외부 STT·AI provider 실행 계약

분석 endpoint는 `SCOPE_server:write`를 가진 서버 작업만 호출한다. 앱은 외부 provider URL이나 API key를 직접 알 수 없으며, provider 응답은 내부 adapter DTO로 검증한 뒤 저장 모델로 변환한다.

| 작업 | 기본 provider | 설정값 | 요청 계약 |
| --- | --- | --- | --- |
| STT | OpenAI 호스팅 Whisper | `STT_PROVIDER=openai`, `WHISPER_API_KEY`, `WHISPER_API_BASE_URL`, `WHISPER_MODEL` | `POST {base_url}/v1/audio/transcriptions` multipart `file`, `model`, `language=ko`, `response_format=verbose_json` |
| STT | 로컬 Whisper | `STT_PROVIDER=local`, `LOCAL_WHISPER_API_BASE_URL`, `LOCAL_WHISPER_API_KEY`(선택), `LOCAL_WHISPER_MODEL` | `POST {base_url}/v1/audio/transcriptions` multipart `file`, `model`, `language=ko`, `response_format=verbose_json` |
| STT | Google Cloud Speech-to-Text V2 | `STT_PROVIDER=google`, `GOOGLE_STT_PROJECT_ID`, `GOOGLE_STT_LOCATION`, `GOOGLE_STT_MODEL`, `GOOGLE_STT_LANGUAGE_CODE`, ADC credential | `POST /v2/projects/{project}/locations/{location}/recognizers/_:recognize` JSON `config.autoDecodingConfig`, `languageCodes`, `model`, base64 `content` |
| 음향 분석 | AST HTTP service | `AST_API_URL`, `AST_API_KEY`, `AST_MODEL` | multipart `audio_file`, `recording_id`, `segment_length_sec`, `model_version` |
| 텍스트 분석 | KcELECTRA HTTP service | `KCELECTRA_API_URL`, `KCELECTRA_API_KEY`, `KCELECTRA_MODEL` | JSON `transcript`, `question_type`, `model_version` |
| 세션 요약 | Gemini API | `GEMINI_API_KEY`, `GEMINI_API_BASE_URL`, `GEMINI_MODEL` | `POST {base_url}/v1beta/models/{model}:generateContent` JSON `contents`와 구조화 응답 지시 |

모든 외부 호출은 `EXTERNAL_API_CONNECT_TIMEOUT`, `EXTERNAL_API_READ_TIMEOUT`, `EXTERNAL_API_RETRY_COUNT`를 사용한다. `429`와 `5xx`는 제한된 횟수만 재시도하고, 최종 실패·timeout·응답 schema 오류는 `503`으로 반환한다. API key와 provider 응답 원문은 로그에 남기지 않는다.

로컬 기본값은 `EXTERNAL_API_ALLOW_FALLBACK=true`일 때 deterministic fallback으로 계약·화면 연동을 검증할 수 있다. `dev`·`prod` 프로필은 fallback을 끄며, provider 설정이 없으면 `503`을 반환한다. 실제 운영 연결 전에는 각 provider의 endpoint, 모델 버전, 보관·전송 정책을 환경별 secret manager에서 설정한다. Google Cloud STT의 `GOOGLE_APPLICATION_CREDENTIALS`는 서비스 계정 JSON 경로를 가리키거나 실행 환경의 ADC를 사용하며, JSON 원문은 저장소에 두지 않는다.

## 8. 일기·보호자 반응 API

### 8.1 `POST /diaries` - 일기 생성

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `user_id` | string | Y | 작성자 ID |
| `source_type` | enum | Y | `manual`, `voice`, `session`, `daily_summary` |
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

### 8.3 `POST /diaries/from-daily-summary` - 하루 집계 요약으로 일기 생성

`Asia/Seoul` 기준 하루가 종료된 뒤 서버 스케줄러가 호출한다. 사용자가 직접 본문을 수정해야 하는 경우에도 동일한 endpoint를 본인 권한으로 호출할 수 있다.

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `daily_summary_id` | string | Y | 완료된 일일 집계 요약 ID |
| `user_id` | string | Y | 고령자 ID |
| `title` | string | N | 일기 제목 |
| `content` | string | N | 사용자가 수정한 내용. 생략 시 일일 요약으로 생성 |
| `mood` | enum | N | `very_sad`, `sad`, `neutral`, `happy`, `very_happy` |
| `mood_level` | integer | N | 감정 단계 `1~5` |

#### Response `202`

```json
{
  "generation_job_id": "dgj_01J...",
  "user_id": "usr_elder_01J...",
  "target_date": "2026-08-05",
  "status": "processing",
  "scheduled_at": "2026-08-06T00:00:00+09:00",
  "available_at": null,
  "diary_id": null,
  "retryable": true
}
```

동일한 `daily_summary_id`로 재요청해도 생성 작업과 일기가 중복되지 않아야 한다. 생성 완료 시 `status=completed`, `diary_id`, `available_at`을 저장하고 `diary_generated` 알림을 생성한다. 실패 시 `status=failed`와 안전한 `failure_reason`을 저장하고 `diary_generation_failed` 알림을 생성한다. 해당 날짜에 완료된 대화가 없으면 `conversation_incomplete`로 종료한다.

### 8.3.1 `GET /diaries/{user_id}/generation-status` - 날짜별 일기 생성 상태

홈 일기 카드와 대화 완료 화면에서 다음 날 0시 생성 예정·처리·완료·미완료 상태를 표시하는 데 사용한다.

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `date` | string | Y | 대화가 진행된 기준일, `YYYY-MM-DD` |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `generation_job_id` | string/null | 생성 작업 ID |
| `target_date` | string | 일기 대상 날짜 |
| `status` | enum | `scheduled`, `processing`, `completed`, `failed`, `conversation_incomplete` |
| `scheduled_at` | string/null | 생성 예약 시각 |
| `available_at` | string/null | 일기 확인 가능 시각 |
| `diary_id` | string/null | 완료된 일기 ID |
| `failure_reason` | enum/null | `summary_failed`, `generation_failed`, `insufficient_conversation`, `unknown` |
| `retryable` | boolean | 서버 재시도 가능 여부 |
| `display_label` | string | 홈 카드 상태 문구 |
| `message` | string | 사용자 안내 문구 |

### 8.4 `GET /diaries/{user_id}` - 일기 목록

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
| `source_type` | enum | `manual`, `voice`, `session`, `daily_summary` |
| `mood` | enum/null | `very_sad`, `sad`, `neutral`, `happy`, `very_happy` |
| `mood_level` | integer/null | 감정 단계 `1~5` |
| `written_at` | string | 작성 일시 |
| `reaction_count` | integer | 반응 수 |
| `total` | integer | 전체 건수 |
| `page` | integer | 현재 페이지 |

### 8.5 `GET /diaries/{diary_id}` - 일기 상세

#### Response `200`

`diary_id`, `user_id`, `source_type`, `title`, `content`, `session_id`, `daily_summary_id`, `mood`, `mood_level`, `written_at`, `created_at`, `updated_at`, `reactions[]`를 반환한다.

### 8.6 `PATCH /diaries/{diary_id}` - 일기 수정

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `title` | string | N | 제목 |
| `content` | string | N | 본문 |

#### Response `200`

`diary_id`, `title`, `content`, `updated_at`을 반환한다.

### 8.7 `DELETE /diaries/{diary_id}` - 일기 삭제

#### Response `204`

응답 본문 없음.

### 8.8 `POST /diaries/{diary_id}/reactions` - 보호자 반응 저장

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

### 8.9 `GET /diaries/{diary_id}/reactions` - 반응 목록

일기 작성자 또는 연결된 보호자만 조회할 수 있다. `reactions[]`에 `reaction_id`, `reactor_id`, `reactor_name`, `reaction_type`, `message`, `created_at`을 포함한다.

#### 백엔드 구현 메모 (v1.3)

- `POST /diaries/from-session`은 저장된 세션 요약을 재사용하고, 본문을 함께 보내면 사용자가 수정한 본문을 우선 저장한다.
- `POST /diaries/from-daily-summary`는 `daily_summary_id`를 작업 멱등 키로 사용하며 `202`와 `scheduled|processing|completed|failed|conversation_incomplete` 상태를 반환한다. 동일 요약 재요청은 기존 job/일기를 반환한다.
- `GET /diaries/{user_id}`와 `GET /diaries/{diary_id}`가 동일한 경로 템플릿을 공유하므로 서버는 먼저 diary ID 존재 여부를 확인하고 없으면 사용자 목록 응답을 반환한다.
- 반응은 `(diary_id, reactor_id, reaction_type)` unique 정책으로 중복 저장을 막고, 보호자 연결의 `diary` access scope를 확인한다.
- 캘린더는 `from_date`·`to_date`와 `types` 필터를 서버에서 적용하고, 일기 감정값은 `metadata.mood`, `metadata.mood_level`로 반환한다.

## 9. 미니게임·캐릭터 API

### 9.1 `POST /game/result` - 미니게임 결과 전송

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `user_id` | string | Y | 고령자 ID |
| `session_id` | string | Y | 게임 세션 ID |
| `client_game_result_id` | string | Y | 기기에서 생성한 재전송 중복 방지 ID |
| `game_type` | enum | Y | `image_match`, `consonant`, `word_match`, `color_match` |
| `score` | integer | Y | 획득 점수 |
| `response_times[]` | float[] | Y | 문항별 응답 시간(초) |
| `error_count` | integer | Y | 오답 횟수 |
| `total_questions` | integer | Y | 전체 문항 수 |
| `matched_pairs` | integer | 조건부 | `image_match`에서 맞춘 짝 수, 완료 시 `6` |
| `attempt_count` | integer | 조건부 | `image_match`에서 두 장을 선택한 총 시도 횟수 |
| `duration_sec` | integer | Y | 시작부터 완료까지 경과 시간(초) |
| `restarted_count` | integer | N | 다시 시작 횟수, 기본 `0` |
| `completed` | boolean | Y | 정상 완료 여부 |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `game_result_id` | string | 결과 ID |
| `cognitive_index` | float | 게임 기반 인지기능 참고 지표, `0~100` |
| `xp_earned` | integer | 획득 경험치 |
| `character_level` | integer | 현재 캐릭터 레벨 |
| `level_up` | boolean | 레벨업 여부 |
| `deduplicated` | boolean | 동일 `client_game_result_id` 재전송 여부. 재전송이면 기존 결과를 반환하고 XP를 다시 적립하지 않음 |

### 9.2 `GET /game/{user_id}/history` - 게임 이력

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `game_type` | enum | N | 게임 종류 |
| `limit` | integer | N | 기본 `20` |

#### Response `200`

`records[]`에 `game_result_id`, `game_type`, `score`, `matched_pairs`, `attempt_count`, `duration_sec`, `restarted_count`, `completed`, `cognitive_index`, `xp_earned`, `played_at`을 포함한다.

게임 결과 저장이 완료되면 서버가 `event_id=game_result_id`로 경험치를 자동 적립한다. 앱이 별도로 XP 적립 API를 호출하지 않는다.

### 9.3 `GET /character/{user_id}` - 캐릭터 상태

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `user_id` | string | 사용자 ID |
| `display_name` | string | 캐릭터 화면 표시 이름 |
| `level` | integer | 현재 레벨 |
| `stage` | enum | `egg`, `puppy`, `sprout`, `flower`, `star` |
| `stage_index` | integer | 현재 성장 단계의 1부터 시작하는 순번 |
| `stage_count` | integer | 전체 성장 단계 수 |
| `xp_current` | integer | 현재 경험치 |
| `xp_goal` | integer | 다음 레벨 도달에 필요한 누적 경험치 |
| `xp_remaining` | integer | 다음 레벨까지 남은 경험치 |
| `skin_id` | string | 현재 스킨 ID |
| `unlocked[]` | string[] | 해금 아이템 ID 목록 |

### 9.4 `GET /character/{user_id}/xp-history` - 경험치 획득 내역

마이페이지의 경험치 획득 내역 펼침 영역에 사용한다. 본인 또는 연결·동의·access scope가 확인된 보호자만 조회할 수 있다.

#### Query Parameters

| 파라미터 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `limit` | integer | `20` | 조회 개수 |
| `cursor` | string | - | 다음 페이지 cursor |

#### Response `200`

`records[]`에 `xp_ledger_id`, `reason`, `display_title`, `amount`, `event_id`, `earned_at`을 포함하고, 최상위에 `next_cursor`를 반환한다. `reason`은 `attendance`, `visit`, `emotional_qa`, `game`, `campaign` 중 하나다.

### 9.5 `POST /character/{user_id}/xp` - 경험치 적립

정서 문답·게임 완료 등 서버 이벤트에서만 호출하는 내부 API다. 클라이언트가 임의의 `amount`를 보내는 방식으로 사용하지 않는다.

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `amount` | integer | Y | 지급 경험치 |
| `reason` | enum | Y | `attendance`, `visit`, `emotional_qa`, `game`, `campaign`; `campaign`은 Phase 2 |
| `event_id` | string | Y | 원본 이벤트 ID. 동일 이벤트 재처리 시 중복 적립 방지 |

#### Response `200`

```json
{
  "xp_current": 320,
  "level": 3,
  "level_up": false
}
```

#### 백엔드 구현 메모 (v1.3)

- `client_game_result_id`와 `xp_ledger.event_id=game_result_id`를 함께 unique로 관리해 오프라인 재전송에서도 게임 결과와 XP를 한 번만 반영한다.
- `cognitive_index`는 `score / total_questions * 100`으로 계산하며, `image_match`는 `matched_pairs`, `attempt_count`, `duration_sec`, `restarted_count`, `completed`를 함께 검증한다.
- 캐릭터 XP 적립 endpoint는 `server:write` scope 전용이다. 보호자·고령자 앱 토큰은 직접 호출할 수 없고, 이력 조회는 본인 또는 연결된 `activity` scope 보호자만 가능하다.
- 캠페인 목록·참여·보상은 Phase 2 범위로 유지한다.

## 10. 보호자 리포트 API

### 10.1 `GET /guardian/{guardian_id}/report` - 고령자 종합 리포트

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `elder_id` | string | Y | 연결된 고령자 ID |
| `date` | string | 조건부 | 특정 일일 리포트 기준일, `YYYY-MM-DD`; `from_date`, `to_date`와 함께 사용할 수 없음 |
| `from_date` | string | N | 추이 시작일 |
| `to_date` | string | N | 추이 종료일 |
| `timezone` | string | N | 일일 집계 시간대. 기본 `Asia/Seoul` |

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
| `daily_summary` | object/null | `date`를 요청한 경우 해당 날짜의 다회 대화 집계 |

`daily_summary`에는 `local_date`, `timezone`, `session_count`, `analyzed_session_count`, `analysis_status`, `diary_id`, `conversation_results[]`를 포함한다. `conversation_results[]`에는 날짜 안에 종료된 각 세션의 `session_id`, `session_type`, `result_type`, `display_label`, `screening_reference_score`, `domain_scores`를 포함한다. `screening_reference_score`와 `domain_scores`는 보호자 리포트에서만 반환한다.

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

### 10.2 `GET /guardian/{guardian_id}/report/export` - 보호자 리포트 내보내기

Figma의 리포트 내보내기 동작에 사용한다. 연결·동의·`screening`, `summary` access scope를 모두 확인하며, 생성된 파일 URL은 짧은 만료 시간을 가진 서명 URL이어야 한다.

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `elder_id` | string | Y | 연결된 고령자 ID |
| `from_date` | string | Y | 시작일, `YYYY-MM-DD` |
| `to_date` | string | Y | 종료일, `YYYY-MM-DD` |
| `format` | enum | Y | `pdf`, `csv` |
| `timezone` | string | N | 기본 `Asia/Seoul` |

#### Response `202`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `export_id` | string | 내보내기 작업 ID |
| `status` | enum | `processing`, `completed`, `failed` |
| `download_url` | string/null | 완료된 경우 서명 다운로드 URL |
| `expires_at` | string/null | URL 만료 시각 |
| `failure_reason` | string/null | 실패 사유 코드 |

동일 사용자·대상자·기간·형식 요청은 진행 중 작업을 재사용한다. 다운로드와 재생성 접근도 audit log에 남기고, 원본 파일 보존 기간은 운영 정책으로 제한한다.

## 11. 알림 API

### 11.1 `POST /notifications/push` - 푸시 알림 생성

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `target_user_id` | string | Y | 수신자 ID |
| `title` | string | Y | 알림 제목 |
| `body` | string | Y | 알림 내용 |
| `type` | enum | Y | `screening_alert`, `screening_updated`, `session_complete`, `summary`, `diary_generated`, `diary_generation_failed`, `reminder`, `campaign`, `weekly_report`, `guardian_reaction` |
| `severity` | enum | N | `info`, `success`, `caution`, `danger`; 기본 `info` |
| `status_label` | string | N | `완료`, `주의`, `위험` 등 서버가 결정한 화면 배지 문구 |
| `data` | object | N | 화면 이동용 추가 페이로드. 재시도 멱등 키가 필요하면 `event_id`를 포함한다. |

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
| `severity` | enum | 알림 심각도·색상 의미 |
| `status_label` | string/null | 화면 상태 배지 문구 |
| `data` | object/null | 화면 이동 데이터 |
| `is_read` | boolean | 읽음 여부 |
| `read_at` | string/null | 읽은 시각 |
| `created_at` | string | 생성 일시 |
| `unread_count` | integer | 미읽음 건수 |

`data`는 가능한 경우 `target_route`, `reference_type`, `reference_id`, `elder_id`를 포함한다. `reference_type`은 `session`, `screening`, `diary`, `report`, `campaign` 중 하나며, 수신자가 해당 resource를 조회할 권한이 없으면 이동 전에 `403`을 반환한다. 알림 생성기는 수신자의 `push_notification_enabled`와 유형별 알림 설정을 확인하되, 앱 내부 알림 저장 여부와 OS push 발송 여부를 구분한다.

`POST /notifications/push`는 `SCOPE_server:write` 권한을 가진 서버 작업만 호출할 수 있다. `data.event_id`가 같은 수신자에게 다시 전달되면 기존 알림을 반환해 분석 워커 재시도와 이벤트 중복 전달을 멱등 처리한다. 목록·읽음 endpoint는 JWT 주체가 `{user_id}` 또는 알림 수신자와 같은지 다시 확인하며, 전체 읽음은 요청 본문에 `user_id`를 받지 않고 현재 인증 사용자에게만 적용한다. 현재 구현은 매주 월요일 09:00(Asia/Seoul) 주간 리포트 워커가 `weekly_report` 알림 생성 경계를 호출하도록 정의했으며, 실제 OS push provider·스케줄러 연결은 운영 단계에서 추가한다.

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

## 12. 상담 센터·립싱크·TTS API

### 12.1 `GET /counseling/centers` - 지역별 상담 센터 목록

MVP에서는 지역을 선택하면 상담 센터 목록과 네이버 지도·기관 홈페이지 등 외부 연결 URL을 제공한다. Phase 2에서는 동일 센터 식별자를 사용해 예약 가능 시간 조회와 예약·취소를 제공한다.

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `province_code` | string | Y | 시·도 코드 |
| `district_code` | string | N | 시·군·구 코드. 선택 전에는 생략 가능 |
| `facility_type` | enum | N | `hospital`, `dementia_center`, `public_health_center` |
| `page` | integer | N | 기본 `1` |
| `limit` | integer | N | 기본 `20` |

#### Response `200`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `centers[]` | array | 상담 센터 목록 |
| `center_id` | string | 센터 ID |
| `name` | string | 센터명 |
| `province_code` | string | 시·도 코드 |
| `province_name` | string | 시·도 표시명 |
| `district_code` | string | 시·군·구 코드 |
| `district_name` | string | 시·군·구 표시명 |
| `facility_type` | enum | `hospital`, `dementia_center`, `public_health_center` |
| `address` | string | 주소 |
| `phone` | string/null | 대표 연락처 |
| `latitude` | float/null | 지도 표시용 위도 |
| `longitude` | float/null | 지도 표시용 경도 |
| `naver_map_url` | string/null | 네이버 지도 외부 링크 |
| `homepage_url` | string/null | 기관 홈페이지 외부 링크 |
| `reservation_mode` | enum | `external_link` 고정(MVP), 후속으로 `integrated` 검토 |

시·도와 시·군·구 선택지는 행정구역 기준정보를 사용하며, 프론트엔드는 자유 입력 지역명을 API 식별자로 사용하지 않는다.

#### 백엔드 구현 메모 (v1.3)

- `counseling_centers` 기준 테이블을 migration으로 생성하고 부산광역시 해운대구 MVP seed를 등록한다.
- `province_code`는 필수이며 `district_code`, `facility_type`, `page`, `limit`을 서버에서 필터·페이지네이션한다. 응답의 `homepage_url`과 `naver_map_url`은 외부 연결용 URL이다.
- MVP의 `reservation_mode`는 `external_link`로 고정한다. availability·appointment endpoint는 Phase 2로 유지한다.

### 12.2 `GET /counseling/centers/{center_id}/availability` - 상담 가능 시간 (Phase 2)

`from_date`, `to_date`를 받아 `slots[]`의 `slot_id`, `starts_at`, `ends_at`, `status=available|unavailable`을 반환한다. 외부 기관이 실시간 연동을 지원하지 않으면 `501 COUNSELING_INTEGRATION_UNAVAILABLE`을 반환하고 센터의 외부 예약 URL을 안내한다.

### 12.3 `POST /counseling/appointments` - 상담 예약 생성 (Phase 2)

요청은 `center_id`, `slot_id`, `elder_id`, `contact_name`, `contact_phone`, `privacy_consent_version`을 포함한다. 응답 `201`은 `appointment_id`, `status=confirmed|requested`, `starts_at`, `center`, `cancellation_deadline`을 반환한다. 보호자는 연결 관계와 상담용 개인정보 제공 동의를 충족해야 하며, idempotency key로 중복 예약을 막는다.

### 12.4 `GET /counseling/appointments` - 상담 예약 목록 (Phase 2)

현재 인증 보호자의 예약만 반환한다. `status`, `from_date`, `to_date` 필터를 지원하며 `appointments[]`에 기관·대상자·예약 일시·상태를 포함한다.

### 12.5 `DELETE /counseling/appointments/{appointment_id}` - 상담 예약 취소 (Phase 2)

예약자만 취소할 수 있다. 취소 마감 이후에는 `409 APPOINTMENT_CANCELLATION_CLOSED`, 이미 취소된 요청은 기존 상태와 함께 멱등 응답을 반환한다.

### 12.6 립싱크·TTS 후속 API

중간보고서에서는 립싱크 적용 가능성을 검토 중이므로, 아래 API는 현재 MVP 필수 구현이 아닌 후속 확장 항목으로 분리한다.

#### 12.6.1 `POST /voice/synthesize` - 안내 음성 생성 (Phase 2)

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
| 마이페이지·설정 | `GET/PATCH /users/{user_id}`, `GET/PATCH /users/{user_id}/preferences`, `PATCH /users/me/password`, `POST /auth/logout`, `DELETE /users/me`, `GET /character/{user_id}`, `GET /character/{user_id}/xp-history`, `GET /dashboard/{user_id}` | 프로필, 알림 설정, 비밀번호, 로그아웃·탈퇴, 레벨·경험치 내역, 월간 활동·연속 출석 |
| 초대 코드 입력 | `POST /guardian/invitations/verify`, `POST /guardian/invitations/accept` | 6자리 코드 검증, 동의 후 보호자 연결 생성 |
| CIST 검사 | `POST /sessions`, `GET /questions/daily`, `POST /recordings`, `POST /sessions/{session_id}/answers`, `PATCH /sessions/{session_id}/end` | 문항 1개씩 진행, 음성 답변, 오프라인 재전송 |
| CIST 결과·일기 | `GET /screenings/{session_id}/result`, `GET /summary/session/{session_id}`, `POST /diaries/from-session` | 고령자 정성 결과, 보호자용 점수·영역별 결과, 요약, 일기 저장 |
| AI 정서 문답 | `POST /sessions` with `session_type=emotional_qa`, `GET /questions/daily`, `POST /sessions/{session_id}/answers`, `PATCH /sessions/{session_id}/end`, `GET /screenings/{session_id}/result` | 세션 종료 후 캐릭터 결과 안내, 정성 결과, XP 적립 |
| 대화 내역 | `GET /sessions`, `GET /sessions/{session_id}`, `GET /sessions/{session_id}/answers` | 세션 목록, 질문·답변·전사문, 중단 세션 복구 |
| 하루 대화 리포트·일기 | `POST /summary/daily`, `GET /summary/daily/{user_id}`, `GET /guardian/{guardian_id}/report?date=...`, `POST /diaries/from-daily-summary`, `GET /diaries/{user_id}/generation-status` | KST 기준 다회 대화 집계, 보호자 리포트, 0시 일기 생성 작업과 상태 |
| 고령자 홈 | `GET /dashboard/{user_id}`, `GET /character/{user_id}`, `GET /notifications/{user_id}` | 캐릭터, 최근 검사, 오늘 할 일, 알림 |
| 달력·일기 | `GET /calendar/{user_id}/activities`, `GET /diaries/{user_id}`, `GET /diaries/{diary_id}`, `POST /recordings` with `purpose=diary`, `POST /diaries` | 날짜별 일기·활동·감정, 음성 일기 녹음 |
| 기억력 게임 | `POST /game/result`, `GET /game/{user_id}/history`, `GET /character/{user_id}` | 짝 맞춤 수, 시도·시간·재시작, XP와 성장 단계 |
| 상담 센터 | `GET /counseling/centers?province_code=...&district_code=...` | 시·도·시군구별 기관 유형, 주소, 네이버 지도·기관 사이트 외부 연결 |
| 지역 캠페인 (Phase 2) | `GET /campaigns`, `GET /campaigns/{campaign_id}`, `POST /campaigns/{campaign_id}/participation` | 초기 범위에서 제외한 확장 기능 |
| 보호자 대시보드 | `GET /guardian/{guardian_id}/elders`, `GET /guardian/{guardian_id}/report` | 여러 고령자 카드, 점수, 위험 상태, 활동 지표 |
| 보호자 일기·반응 | `GET /diaries/{user_id}`, `POST /diaries/{diary_id}/reactions` | 일기 열람, 하트·감정·메시지 반응 |
| 보호자 위험 추이 | `GET /guardian/{guardian_id}/report`, `GET /analysis/cognitive/{user_id}/history`, `GET /analysis/cognitive/{user_id}/benchmark`, `GET /guardian/{guardian_id}/report/export` | 기간별 참고 점수·지역 기준선·리포트 내보내기 |
| 고령자 알림 | `GET /notifications/{user_id}`, `PATCH /notifications/{id}/read`, `PATCH /notifications/read-all` | 일기 생성·미완료, 보호자 반응, 검사 결과, 읽음 상태·화면 이동 |
| 보호자 알림 | `GET /notifications/{user_id}`, `PATCH /notifications/{id}/read`, `PATCH /notifications/read-all` | 인지 점수 하락 경보, AI 대화 완료, 주간 리포트, 캠페인 참여 |

### 13.1 전체 구현 범위와 단계 구분

Figma에 노출된 기능은 모두 구현 대상으로 확정한다. 외부 데이터·기관 연동이 필요한 기능은 계약과 개인정보 기준을 먼저 확정하되, 범위에서 삭제하지 않는다. 현재 Figma에 보이지 않는 기존 계획 기능도 Phase 2 구현 대상으로 유지한다.

| 기능 | 구현 단계 | 구현 전 확정 사항 |
| --- | --- | --- |
| 지역 기준선 비교 | MVP | 공신력 있는 집계 데이터 출처, 최소 표본 수, 갱신 주기 |
| 리포트 내보내기 | MVP | PDF·CSV 양식, 서명 URL 만료, 파일 보존·감사 로그 |
| 전문 상담 기관 검색·외부 지도 | MVP | 행정구역 코드, 기관 기준정보 출처, 링크 갱신 방식 |
| 전문 상담 예약 | Phase 2 | 외부 기관 API, 개인정보 제공 동의, 예약·취소 정책 |
| 지역 캠페인·참여 보상 | Phase 2 | 지역·기간·완료 기준, 중복 참여·XP 정책 |
| 안내 음성 TTS·립싱크 | Phase 2 | provider, 비용·cache, viseme 형식, 장애 대체 동작 |

## 14. 백엔드 구현 우선순위

1. 인증·사용자·동의: 회원가입, 로그인, 초기 사용자 정보, 보호자 접근 동의
2. CIST 핵심 흐름: 세션, 질문, 답변, 녹음 업로드, STT, 결과 조회
3. AI 분석 파이프라인: AST·KcELECTRA 결과 저장 및 세션 단위 집계
4. 고령자 화면: 홈, 캐릭터, 알림, 결과·일기, 캘린더, 마이페이지·설정
5. 보호자 화면: 다중 고령자 연결, 대시보드, 일일 리포트, 일기 반응
6. Figma 보완 화면: 비밀번호 변경, 대화 내역, 음성 일기, 게임 상세 지표, XP 내역, 알림 유형·이동
7. 보호자 분석 확장: 지역 기준선 비교와 리포트 내보내기
8. 상담 센터 목록과 외부 지도·기관 사이트 연결
9. 상담 예약, 지역 캠페인·참여 보상은 Phase 2로 분리하되 구현 범위에서 유지
10. TTS·립싱크 연동은 핵심 검사 흐름 안정화 이후 확장

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
