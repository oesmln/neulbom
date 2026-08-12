# 늘봄(NEULBOM) 프론트엔드 개발 체크리스트 (초안)

> **상태:** 초안 · 미확정 · 팀 리뷰 대기
> **기준 문서:** `backend/docs/api-spec.md` v1.3, Figma Make 「치매노노」(BkFVZ148AZd7XvC89w8llK)
> **대상 디렉터리:** `neulbom/frontend/`
>
> API의 경로·필드·상태값·HTTP 상태 코드는 `api-spec.md`를 기준으로 한다. 명세와 화면이
> 어긋나면 임의로 해석하지 말고 이슈에 차이를 기록한다.

---

## 0. 확정이 필요한 선행 결정

체크리스트의 나머지 단계는 이 결정들에 의존한다. 먼저 팀 합의가 필요하다.

- [ ] **런타임**: Expo(권장) vs bare React Native
      3D 캐릭터(`expo-gl`)와 문항 녹음(`expo-audio`)이 핵심 기능이라 Expo 유지를 권장한다.
      저장소 README의 `npm run ios / android`는 Expo에서도 그대로 동작한다.
- [ ] **상태 관리**: 서버 상태 캐싱·폴링 계층 선정 (분석 결과 `pending` 재조회가 명세 요구사항)
- [ ] **3D 에셋 커밋 범위**: `optimized/` 6MB만 커밋, Meshy 원본 88MB는 제외
- [ ] **Base URL 주입 방식**: dev / staging / prod 분리 (`app.config.ts` extra vs `.env`)
- [ ] **디자인 기준**: Figma Make 최신본 기준 폰트 스케일(13~20px)과
      기존 접근성 규칙(본문 18px 이상)이 충돌한다. 어느 쪽을 따를지 확정한다.

---

## 1단계 · 프로젝트 기반

- [ ] `frontend/` 프로젝트 초기화 (TypeScript, 절대경로 alias)
- [ ] 디렉터리 구조 확정
      `api/` `screens/` `components/` `navigation/` `hooks/` `store/` `assets/` `theme/`
- [ ] `theme.ts` 디자인 토큰 이식 — Figma `theme.css` + 화면별 상수와 일치시킬 것
      `P #5A8F68` `PD #3D6B4A` `PL #EAF3EC` `T #2D3132` `TS #697268` `MU #F0EDE6`
      `TAN #D4A373` `TANL #FAF0E4` `D #C0392B` `DL #FDEAEA` `OK #4E9566` `CHR #2E5238`
      `BR rgba(45,49,50,0.11)`
- [ ] 공통 프리미티브 (`Screen` `ScreenHeader` `Button` `Card` `Badge` `Pill` `ProgressBar` `SpeechBubble`)
- [ ] 아이콘 세트 통일 (Figma는 lucide, 앱은 `Ionicons` — 매핑표 작성)
- [ ] `npm run typecheck` 통과
- [ ] ESLint / Prettier 설정

## 2단계 · API 클라이언트와 인증

> 명세 1.1 / 1.2 / 3.1~3.4.2

- [ ] HTTP 클라이언트: `baseURL = {ENV}/api/v1`, 공통 timeout
- [ ] 공통 오류 파서 — `{ error, code, detail, request_id }` 단일 형식으로 정규화
- [ ] 상태 코드별 처리: `400 401 403 404 409 410 413 422 429 500 503`
- [ ] `Authorization: Bearer {access_token}` 자동 주입
- [ ] 토큰 보관 — `expo-secure-store` (AsyncStorage 금지)
- [ ] `401` → `POST /auth/refresh` 자동 재시도 (동시 요청 큐잉, 무한 루프 차단)
- [ ] refresh 실패 시 전역 로그아웃 → Splash 복귀
- [ ] `POST /auth/register` — **회원가입 3단계 순서 준수**
      이름·이메일·비밀번호 입력 → 초대 코드 입력/건너뛰기 → 유형 선택 → 그때 `role`과 함께 1회 호출
      (첫 화면 입력만으로 계정을 만들지 않는다. 중간 값은 메모리에만 임시 보관)
- [ ] `POST /auth/login`
- [ ] `POST /auth/oauth/{kakao|naver}` — authorization code 교환, `redirect_uri` 사전 등록값과 정확히 일치
- [ ] `POST /auth/password/reset/request` / `confirm`
- [ ] `POST /auth/logout`, `PATCH /users/me/password`, `DELETE /users/me`
- [ ] `profile_completed` 값으로 초기 정보 입력 화면 분기
- [ ] 로그·크래시 리포트에 토큰·비밀번호·개인정보가 남지 않는지 확인

## 3단계 · 온보딩 화면

> 명세 13장 「시작·로그인」「사용자 유형 선택」「초기 사용자 정보 입력」「청취 환경·음성 선택」

- [ ] 시작 화면 — 캐릭터, 시작하기 / 이미 계정이 있어요
- [ ] 로그인·회원가입 (탭 전환, 비밀번호 표시 토글, 카카오·네이버)
- [ ] 비밀번호 찾기 (이메일 입력 → 전송 완료 안내)
- [ ] 초대 코드 6자리 입력 (커스텀 숫자 키패드, 건너뛰기 가능)
      `POST /guardian/invitations/verify` → 가입·로그인 후 `accept`
- [ ] 사용자 유형 선택 (`elder` / `guardian`)
- [ ] 초기 사용자 정보 입력 — 학력, 문해 여부, 건강·생활습관, 청력, 스마트폰 사용 수준
      `PATCH /users/{user_id}`
- [ ] 개인정보·보호자 접근 동의 `POST /consent/{user_id}`
- [ ] 청취 환경·안내 음성 선택 `GET /voice-profiles` + `PATCH /users/{user_id}/preferences`
      (자막 기본값 `false`)

## 4단계 · CIST 검사 흐름 (음성 기반)

> 명세 6장·7장. **프론트 난이도 최상 — 가장 먼저 프로토타입할 것**

- [ ] `POST /sessions` (`session_type=cist`) → `session_id`
- [ ] `GET /questions/daily` 문항 로드, 1문항씩 진행
- [ ] 문항 유형 분기 — 음성 답변형 / 듣기형(단어 등록)
- [ ] 마이크 권한 요청 및 거부 시 대체 흐름
- [ ] 녹음 UI — 파형, 경과 시간, 녹음/완료 상태 (대형 원형 버튼)
- [ ] `POST /recordings` multipart 업로드 (`purpose`, `session_id`, `question_id`)
- [ ] **오프라인 큐** — 기기 저장 후 재전송
      상태 전이 `device_saved → server_pending → server_uploaded → analysis_completed`, 실패 시 `failed`
- [ ] `client_recording_id` (기기 생성 UUID) 멱등 보장 — 재전송해도 중복 생성 없음
- [ ] `Idempotency-Key` 헤더 지원
- [ ] `POST /sessions/{id}/answers` 문항별 답변 저장
- [ ] `PATCH /sessions/{id}/end` 종료 + 경험치 적립 상태 수신
- [ ] `PATCH /sessions/{id}/settings` 자막·음성·말하기 속도 반영
- [ ] 중단 세션 복구 — `GET /sessions/{id}`의 `current_question_order`부터 재개
- [ ] 업로드 실패·`413`·`503` 사용자 안내 문구

## 5단계 · 결과와 역할별 노출 분리

> **명세 1.1 / 5.1의 핵심 제약. 위반 시 화면이 비어버린다.**

- [ ] `GET /screenings/{session_id}/result`
- [ ] **고령자 화면에는 점수를 표시하지 않는다.**
      `result_type` `display_label` `message` `recommendation`만 사용
- [ ] `screening_reference_score` `display_score` `score_max` `score_rate` `risk_level`
      `domain_scores`는 **보호자 화면 전용**
- [ ] 고령자 결과 화면은 캐릭터 + 말풍선 + 일기 생성 안내로 구성 (Figma 최신본과 동일)
- [ ] `result_status=pending|processing` → 재조회 폴링, 완료 전 점수 자리 표시 금지
- [ ] `insufficient_data` 상태 처리
- [ ] 의료적 확정 진단으로 읽히는 문구를 화면에 쓰지 않는다

## 6단계 · 고령자 메인 화면

- [ ] 홈 `GET /dashboard/{user_id}`
      캐릭터 요약, 인사말, 연속 참여일, 기능 카드, 인지 활동 상태 카드
- [ ] **인지 활동 상태 카드** — `cognitive_activity.status`(`stable|observe|attention_required`)를
      서버가 준 `display_label` `title` `message` 그대로 표시.
      **프론트에서 점수 임계값으로 판단하지 않는다** (명세 5.1 명시)
- [ ] 하단 탭 — AI 대화 / 일기 / **홈(중앙 돌출)** / 게임 / 마이
- [ ] AI 정서 문답 `POST /sessions` (`emotional_qa`) — intro → chat → loading → 결과
- [ ] 대화 내역 `GET /sessions`, `GET /sessions/{id}/answers`
- [ ] 달력·일기 `GET /calendar/{user_id}/activities`, `GET /diaries/{user_id}`
      날짜별 감정 이모지, 일기 상세, 음성 일기 추가
- [ ] 일기 생성 상태 `GET /diaries/{user_id}/generation-status`
      `scheduled|processing|completed|failed|conversation_incomplete` 전부 화면 문구 정의
      (KST 다음 날 0시 생성)
- [ ] 알림 `GET /notifications/{user_id}`, `PATCH /{id}/read`, `PATCH /read-all`
      `target_route` `reference_type` `reference_id`로 화면 이동
- [ ] 마이페이지 — 프로필, 레벨·경험치, 캐릭터 이름 변경, 알림 설정, 비밀번호 변경, 로그아웃, 탈퇴
      `GET /character/{user_id}`, `GET /character/{user_id}/xp-history`
- [ ] 앱 설정 화면

## 7단계 · 두뇌 게임

- [ ] 게임 허브 (카드 뒤집기 / 색깔 맞추기 / 초성 맞추기)
- [ ] 카드 뒤집기 — 짝 맞춤 수, 시도 횟수, 경과 시간, 재시작 횟수 기록
- [ ] 색깔 맞추기 (스트룹) / 초성 맞추기
- [ ] `POST /game/result` 결과 전송 — **중복 결과 방지**
- [ ] `GET /game/{user_id}/history`
- [ ] 경험치 반영 — 서버가 적립하므로 클라이언트는 `POST /character/{id}/xp`를 직접 호출하지 않는다

## 8단계 · 보호자 화면

보호자 영역은 고령자용 sage green이 아니라 **`@/theme`의 `guardian` 팔레트**를 쓴다
(`blue` `#4A7BC4` / `blueLight` `#EBF2FB` / `blueDark` `#3465A8`). 헤더·탭 활성·차트 선이
모두 이 파랑이고, 보호자 화면에 `colors.primary`를 쓰지 않는다.

- [x] 종합 리포트 `GET /guardian/{guardian_id}/report`
- [x] 위험 추이 차트 `GET /analysis/cognitive/{user_id}/history`
      **완성형 차트 라이브러리 없이 구현** — 꺾은선·점선 임계선처럼 사각형으로 안 되는 것만
      `react-native-svg` 프리미티브를 쓴다 (`components/ScoreTrendChart.tsx`)
- [x] 일기 열람·반응 `GET /diaries/{user_id}`, `POST /diaries/{diary_id}/reactions`
      (이모지 4종 + 응원 메시지)
- [x] 보호자 알림 — 유형별 배지, 미읽음 강조, 모두 읽음
- [x] 연결·동의 미완료 대상자는 데이터 대신 안내 화면 표시 (`403` 처리)
- [x] 앱 설정 화면 배치 (다크 모드·글씨 크기 선택 UI, 앱 버전)
- [ ] 대시보드 `GET /guardian/{guardian_id}/elders` — **다중** 고령자 카드
      (엔드포인트는 연결했고 현재는 첫 어르신을 자동 선택한다)
- [ ] 지역 기준선 비교 `GET /analysis/cognitive/{user_id}/benchmark`
- [ ] 리포트 내보내기 `GET /guardian/{guardian_id}/report/export` (PDF·CSV, 서명 URL 만료 처리)
- [ ] 다크 모드·글씨 크기의 **실제 적용** — `PATCH /users/{id}/preferences`에 해당 필드가
      없어 저장할 곳이 아직 없다

## 9단계 · 상담 센터

- [x] `GET /counseling/centers?province_code=&district_code=`
- [x] 시·도 / 시·군·구 선택 UI, 기관 유형(병원·치매안심센터·보건소) 필터
      시·도는 `@/theme`의 `provinces` 코드표, 시·군·구는 응답의 `district_code`에서 파생시킨다
      (지역 목록 endpoint가 명세에 없다)
- [x] 네이버 지도·기관 사이트 외부 링크 열기

## 10단계 · 캐릭터(메모이) 3D

- [ ] `expo-gl` + `three` + `@react-three/fiber/native` 렌더러 이식
- [ ] `metro.config.js` — `.glb` assetExts 등록, `three` 단일 빌드 고정
- [ ] optimized glb 6종 사용 (25k 삼각형 / 512² / 개당 1MB)
- [ ] 캐릭터 1개만 메모리 상주 — 전환 시 `dispose` + `useLoader.clear`
- [ ] `metalness = 0` 런타임 강제 (환경맵 없이 검게 나오는 문제)
- [ ] 스켈레톤 인식 프레이밍 (`getVertexPosition` 샘플링, bind-pose 박스 사용 금지)
- [ ] `GET /character/{user_id}`의 `level` / `stage` / `skin_id`와 모델 매핑표 확정
- [ ] GL 초기화 실패 시 2D 폴백

## 11단계 · Phase 2 (범위 유지, 후순위)

- [ ] 지역 캠페인 `GET /campaigns` 외
- [ ] 상담 예약 `POST /counseling/appointments` 외
- [ ] 안내 음성 TTS·립싱크

---

## 공통 완료 조건

작업은 코드만 작성한 상태가 아니라 다음을 모두 만족해야 완료로 본다.

- [ ] 명세의 경로·메서드·필드·상태 코드와 구현이 일치한다
- [ ] 로딩 / 빈 상태 / 오류 / 권한 없음 / 오프라인 화면이 모두 있다
- [ ] `pending` 상태는 재조회하고, 값이 없는 자리를 0이나 더미로 채우지 않는다
- [ ] 고령자 화면에 점수·원본 모델 출력·상세 영역 점수가 노출되지 않는다
- [ ] 접근성: 터치 타깃 최소 44dp, `accessibilityRole` / `accessibilityLabel` 부여
- [ ] 로그에 토큰·음성 원문·개인정보가 없다
- [ ] 새 환경변수는 `.env.example`에 기록되어 있다
- [ ] `npm run typecheck` 통과
- [ ] 실기기 또는 에뮬레이터에서 해당 화면을 직접 확인했다

## Git 협업 규칙

- [ ] Issue 생성 후 `develop`에서 작업 브랜치 생성
- [ ] 브랜치 `feature/fe/#{issue}-{short-description}`
- [ ] 커밋 `feature(fe): 작업 요약`
- [ ] PR 대상은 항상 `develop`
- [ ] `main` / `develop` 직접 커밋 금지
- [ ] 사용자 요청 없이 원격 push·PR·merge 금지
