/**
 * Offline fixtures, shaped exactly like the backend responses.
 *
 * The point is not to fake a server — it is that every screen reads the same
 * field names whether the data came from `GET /dashboard/{user_id}` or from
 * here. When `EXPO_PUBLIC_API_BASE_URL` is set these are never touched, and no
 * screen changes.
 *
 * Values reproduce what the screens showed before the API layer existed, so the
 * app looks identical while running on fixtures.
 */

import type {
  AuthTokenResponse,
  CalendarActivitiesResponse,
  CharacterResponse,
  CounselingCentersResponse,
  DashboardResponse,
  DiariesResponse,
  DiaryDetailResponse,
  EldersResponse,
  GameHistoryResponse,
  GuardianReportResponse,
  HistoryResponse,
  NotificationResponse,
  NotificationsResponse,
  QuestionsResponse,
  Role,
  ScreeningResultResponse,
  SessionEndResponse,
  SessionResponse,
  SessionType,
  UserProfileResponse,
  Uuid,
  XpHistoryResponse,
} from "./types";

export const MOCK_ELDER_ID = "11111111-1111-4111-8111-111111111111";
export const MOCK_GUARDIAN_ID = "22222222-2222-4222-8222-222222222222";
const MOCK_SESSION_ID = "33333333-3333-4333-8333-333333333333";

/** Deterministic id so repeated mock calls keep referring to the same row. */
function fixedId(prefix: string, n: number): Uuid {
  const tail = String(n).padStart(12, "0");
  return `${prefix}-0000-4000-8000-${tail}`;
}

function isoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(
    d.getDate(),
  ).padStart(2, "0")}`;
}

function daysAgo(n: number): Date {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d;
}

/* ── auth ───────────────────────────────────────────────────────────────── */

/**
 * Which demo user the next sign-in belongs to.
 *
 * A real `POST /auth/login` reads the role off the account; without a server the
 * role chosen during sign-up has to be remembered here, or a guardian would be
 * handed an elder session on the very next call.
 */
let lastRole: Role = "elder";

export function rememberMockRole(role: Role) {
  lastRole = role;
}

export function currentMockRole(): Role {
  return lastRole;
}

export function mockAuthToken(role: Role = lastRole): AuthTokenResponse {
  lastRole = role;
  return {
    access_token: "mock.access.token",
    refresh_token: "mock.refresh.token",
    expires_in: 3600,
    user_id: role === "guardian" ? MOCK_GUARDIAN_ID : MOCK_ELDER_ID,
    role,
    profile_completed: true,
    is_new_user: false,
  };
}

export function mockProfile(userId: Uuid, role: Role): UserProfileResponse {
  const now = new Date().toISOString();
  return {
    user_id: userId,
    name: role === "guardian" ? "김철수" : "김영자",
    role,
    birth_date: role === "guardian" ? "1975-04-02" : "1948-03-11",
    age_group: role === "guardian" ? "unknown" : "70s",
    gender: role === "guardian" ? "male" : "female",
    phone: null,
    education_years: 6,
    literacy: true,
    health_conditions: [],
    alcohol_use: "none",
    smoking_status: "never",
    hearing_status: "normal",
    communication_difficulty: false,
    smartphone_skill: "basic",
    profile_completed: true,
    created_at: now,
    updated_at: now,
  };
}

/* ── dashboard ──────────────────────────────────────────────────────────── */

export function mockDashboard(userId: Uuid): DashboardResponse {
  const today = new Date();
  return {
    user_id: userId,
    role: "elder",
    character: {
      level: 2,
      display_name: "늘봄",
      stage: "puppy",
      xp_current: 210,
      xp_goal: 250,
      xp_remaining: 40,
      skin_id: "memoi-1",
    },
    latest_screening: {
      session_id: MOCK_SESSION_ID,
      result_status: "completed",
      result_type: "stable",
      display_label: "안정적",
      message: "오늘도 인지 기능이 안정적이에요.",
      recommendation: "지금처럼만 꾸준히 이어가 주세요!",
      completed_at: daysAgo(0).toISOString(),
    },
    latest_summary: null,
    today_tasks: [
      {
        task_type: "emotional_qa",
        status: "pending",
        title: "AI 정서 문답",
        description: "오늘의 기억을 AI와 함께 이야기해요",
        target_route: "ElderAiChat",
      },
      {
        task_type: "game",
        status: "available",
        title: "두뇌 게임",
        description: "재미있는 게임으로 두뇌를 자극해요",
        target_route: "ElderGameHub",
      },
    ],
    conversation_streak_days: 7,
    monthly_activity: {
      year_month: `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`,
      emotional_qa_completed_count: 12,
      game_completed_count: 8,
      attendance_days: 7,
      current_attendance_streak_days: 7,
    },
    latest_diary: {
      target_date: isoDate(daysAgo(0)),
      generation_status: "completed",
      diary_id: fixedId("aaaaaaaa", 1),
      display_label: "일기 생성 완료",
      message: "어제 대화를 바탕으로 오늘 일기가 업데이트되었어요.",
      available_at: daysAgo(0).toISOString(),
    },
    cognitive_activity: {
      status: "stable",
      display_label: "안정적",
      title: "안정적인 상태예요",
      message: "현재 인지 활동이 안정적으로 유지되고 있어요",
      reference_date: isoDate(daysAgo(1)),
    },
    unread_notification_count: 2,
    recent_alerts: [],
  };
}

/* ── character & xp ─────────────────────────────────────────────────────── */

export function mockCharacter(userId: Uuid): CharacterResponse {
  return {
    user_id: userId,
    display_name: "늘봄",
    level: 2,
    stage: "puppy",
    stage_index: 1,
    stage_count: 5,
    xp_current: 210,
    xp_goal: 250,
    xp_remaining: 40,
    skin_id: "memoi-1",
    unlocked: ["chick", "puppy"],
  };
}

export function mockXpHistory(): XpHistoryResponse {
  const entries: [string, string, number, number][] = [
    ["emotional_qa_completed", "AI 정서 문답 완료", 30, 0],
    ["game_completed", "기억력 게임 ★★★", 30, 0],
    ["streak_bonus", "7일 연속 보너스", 50, 1],
    ["emotional_qa_completed", "AI 정서 문답 완료", 30, 1],
    ["game_completed", "기억력 게임 ★★☆", 20, 2],
  ];
  return {
    records: entries.map(([reason, title, amount, ago], i) => ({
      xp_ledger_id: fixedId("bbbbbbbb", i + 1),
      reason,
      display_title: title,
      amount,
      event_id: null,
      earned_at: daysAgo(ago).toISOString(),
    })),
    next_cursor: null,
  };
}

export function mockGameHistory(): GameHistoryResponse {
  return { records: [], total: 0, limit: 20 };
}

/* ── notifications ──────────────────────────────────────────────────────── */

const elderNotifications: NotificationResponse[] = [
  {
    notification_id: fixedId("cccccccc", 1),
    title: "일기 생성 완료",
    body: "어제 대화를 바탕으로 오늘 일기가 업데이트되었어요.",
    type: "diary_generated",
    severity: "info",
    status_label: null,
    data: null,
    is_read: false,
    read_at: null,
    created_at: daysAgo(0).toISOString(),
  },
  {
    notification_id: fixedId("cccccccc", 2),
    title: "보호자 반응",
    body: "아드님(김철수)이 어제 일기에 반응을 남겼어요.",
    type: "guardian_reaction",
    severity: "info",
    status_label: null,
    data: null,
    is_read: false,
    read_at: null,
    created_at: daysAgo(1).toISOString(),
  },
  {
    notification_id: fixedId("cccccccc", 3),
    title: "검사 결과 업데이트",
    body: "이번 주 인지 활동 결과가 업데이트되었어요.",
    type: "screening_result",
    severity: "info",
    status_label: null,
    data: null,
    is_read: true,
    read_at: daysAgo(5).toISOString(),
    created_at: daysAgo(6).toISOString(),
  },
  {
    notification_id: fixedId("cccccccc", 4),
    title: "일기 생성 미완료",
    body: "어제 일기 생성이 완료되지 않았어요. 잠시 후 다시 확인해 주세요.",
    type: "diary_incomplete",
    severity: "warning",
    status_label: null,
    data: null,
    is_read: true,
    read_at: daysAgo(7).toISOString(),
    created_at: daysAgo(8).toISOString(),
  },
];

const guardianNotifications: NotificationResponse[] = [
  {
    notification_id: fixedId("dddddddd", 1),
    title: "주의력 점수 하락",
    body: "이번 주 주의력이 지난주 대비 2% 낮아졌어요.",
    type: "score_drop",
    severity: "warning",
    status_label: null,
    data: null,
    is_read: false,
    read_at: null,
    created_at: new Date(Date.now() - 3600_000).toISOString(),
  },
  {
    notification_id: fixedId("dddddddd", 2),
    title: "CIST 검사 완료",
    body: "어머니가 오늘 검사를 마쳤어요 (24점).",
    type: "screening_completed",
    severity: "info",
    status_label: null,
    data: null,
    is_read: true,
    read_at: daysAgo(0).toISOString(),
    created_at: daysAgo(0).toISOString(),
  },
  {
    notification_id: fixedId("dddddddd", 3),
    title: "새 일기가 등록되었어요",
    body: "따뜻한 반응을 남겨보세요 💚",
    type: "diary_created",
    severity: "info",
    status_label: null,
    data: null,
    is_read: true,
    read_at: daysAgo(1).toISOString(),
    created_at: daysAgo(1).toISOString(),
  },
  {
    notification_id: fixedId("dddddddd", 4),
    title: "정기 검진 D-3",
    body: "신경과 진료 예약이 있어요.",
    type: "appointment_reminder",
    severity: "info",
    status_label: null,
    data: null,
    is_read: true,
    read_at: daysAgo(2).toISOString(),
    created_at: daysAgo(2).toISOString(),
  },
];

/** Read flags live here so mark-as-read behaves the same in mock mode. */
const readState = new Set<Uuid>(
  [...elderNotifications, ...guardianNotifications].filter((n) => n.is_read).map((n) => n.notification_id),
);

export function mockNotifications(role: Role): NotificationsResponse {
  const source = role === "guardian" ? guardianNotifications : elderNotifications;
  const notifications = source.map((n) => ({
    ...n,
    is_read: readState.has(n.notification_id),
    read_at: readState.has(n.notification_id) ? n.read_at ?? new Date().toISOString() : null,
  }));
  return {
    notifications,
    unread_count: notifications.filter((n) => !n.is_read).length,
  };
}

export function mockMarkRead(notificationId: Uuid) {
  readState.add(notificationId);
}

export function mockMarkAllRead(role: Role): number {
  const source = role === "guardian" ? guardianNotifications : elderNotifications;
  let updated = 0;
  for (const n of source) {
    if (!readState.has(n.notification_id)) {
      readState.add(n.notification_id);
      updated += 1;
    }
  }
  return updated;
}

/* ── diaries & calendar ─────────────────────────────────────────────────── */

const DIARY_SEED: { ago: number; mood: string; level: number; content: string }[] = [
  { ago: 0, mood: "good", level: 4, content: "손주가 놀러 와서 즐거웠다. 기억력 게임도 같이 했다." },
  { ago: 1, mood: "neutral", level: 3, content: "비가 와서 산책은 못 했지만 AI 문답을 했다." },
  { ago: 2, mood: "good", level: 5, content: "경로당에서 친구들과 이야기를 많이 나눴다." },
  { ago: 3, mood: "good", level: 4, content: "아침에 텃밭을 돌봤다. 상추가 잘 자라고 있다." },
  { ago: 4, mood: "neutral", level: 3, content: "병원에 다녀왔다. 별다른 이상은 없다고 했다." },
  { ago: 5, mood: "good", level: 5, content: "며느리가 반찬을 가져다줬다. 고마웠다." },
  { ago: 6, mood: "good", level: 4, content: "오랜만에 옛날 사진을 꺼내 보았다." },
];

export function mockDiaries(): DiariesResponse {
  const diaries = DIARY_SEED.map((d, i) => ({
    diary_id: fixedId("aaaaaaaa", i + 1),
    title: null,
    preview: d.content,
    source_type: "daily_summary",
    mood: d.mood,
    mood_level: d.level,
    written_at: daysAgo(d.ago).toISOString(),
    reaction_count: i === 0 ? 2 : i === 1 ? 1 : i === 2 ? 3 : 0,
  }));
  return { diaries, total: diaries.length, page: 1, limit: 20 };
}

export function mockDiaryDetail(diaryId: Uuid): DiaryDetailResponse {
  const list = mockDiaries().diaries;
  const item = list.find((d) => d.diary_id === diaryId) ?? list[0];
  return {
    diary_id: item.diary_id,
    user_id: MOCK_ELDER_ID,
    source_type: item.source_type,
    title: item.title,
    content: item.preview ?? "",
    session_id: null,
    daily_summary_id: null,
    mood: item.mood,
    mood_level: item.mood_level,
    written_at: item.written_at,
    created_at: item.written_at,
    updated_at: item.written_at,
    reactions: [],
  };
}

export function mockCalendarActivities(): CalendarActivitiesResponse {
  return {
    activities: DIARY_SEED.map((d, i) => ({
      activity_id: fixedId("eeeeeeee", i + 1),
      activity_type: "diary",
      reference_id: fixedId("aaaaaaaa", i + 1),
      activity_date: isoDate(daysAgo(d.ago)),
      title: d.content,
      status: "completed",
      metadata: { mood: d.mood, mood_level: d.level },
    })),
  };
}

/* ── sessions & questions ───────────────────────────────────────────────── */

const CIST_QUESTIONS: { content: string; hint: string; type: string }[] = [
  {
    content: "오늘이 몇 년 몇 월 며칠 무슨 요일인지 말씀해 주세요.",
    hint: "연도, 월, 일, 요일을 순서대로 말씀해 주세요.",
    type: "voice",
  },
  {
    content: "지금 계신 이곳이 어디인지 말씀해 주세요.\n(시/도 → 구/군 → 동/읍)",
    hint: "현재 계신 장소를 최대한 자세히 말씀해 주세요.",
    type: "voice",
  },
  {
    content: "제가 말하는 세 가지 단어를 잘 기억해 주세요.\n\n비행기 · 연필 · 사과",
    hint: "잠시 후 다시 물어볼게요.",
    type: "listen",
  },
  {
    content: "100에서 7을 빼면 얼마인가요?\n그 숫자에서 또 7을 빼면 얼마인가요? (5번 반복)",
    hint: "천천히 계산해서 말씀해 주세요.",
    type: "voice",
  },
  {
    content: "아까 기억하신 세 가지 단어가 무엇인지 말씀해 주세요.",
    hint: "아까 들으신 단어들을 떠올려보세요.",
    type: "voice",
  },
];

const EMOTIONAL_QUESTIONS: string[] = [
  "안녕하세요, 어르신! 오늘 기분이 어떠세요?",
  "오늘 아침에 드신 식사는 어떠셨나요?",
  "오늘 가장 기억에 남는 일이 있으셨나요?",
  "오늘 하루 중 가장 기뻤던 순간이 언제였나요?",
  "내일 기대되는 일이나 하고 싶은 것이 있으신가요?",
];

export function mockDailyQuestions(sessionType: SessionType): QuestionsResponse {
  if (sessionType === "emotional_qa") {
    return {
      questions: EMOTIONAL_QUESTIONS.map((content, i) => ({
        question_id: fixedId("ffffffff", i + 1),
        content,
        type: "voice",
        order: i + 1,
        hint: null,
        subtitle_available: true,
      })),
    };
  }
  return {
    questions: CIST_QUESTIONS.map((q, i) => ({
      question_id: fixedId("99999999", i + 1),
      content: q.content,
      type: q.type,
      order: i + 1,
      hint: q.hint,
      subtitle_available: true,
    })),
  };
}

export function mockSession(userId: Uuid, sessionType: SessionType): SessionResponse {
  return {
    session_id: MOCK_SESSION_ID,
    user_id: userId,
    session_type: sessionType,
    status: "in_progress",
    current_question_order: 1,
    answered_count: 0,
    total_questions: mockDailyQuestions(sessionType).questions.length,
    recording_sync_status: null,
    settings: null,
    started_at: new Date().toISOString(),
    ended_at: null,
  };
}

export function mockSessionEnd(sessionId: Uuid): SessionEndResponse {
  return {
    session_id: sessionId,
    status: "completed",
    ended_at: new Date().toISOString(),
    answered_count: 5,
    analysis_status: "completed",
    result_status: "completed",
    result_type: "stable",
    display_label: "안정적",
    message: "오늘도 인지 기능이 안정적이에요. 지금처럼만 꾸준히 이어가 주세요!",
    recommendation: "내일도 같은 시간에 대화해요.",
    xp_earned: 30,
    character_level: 2,
    level_up: false,
  };
}

/**
 * Elder-audience result: no score fields at all, mirroring the backend's
 * `@JsonInclude(NON_NULL)` behaviour for `audience=elder`.
 */
export function mockScreeningResult(
  sessionId: Uuid,
  audience: "elder" | "guardian",
): ScreeningResultResponse {
  const base: ScreeningResultResponse = {
    audience,
    session_id: sessionId,
    user_id: MOCK_ELDER_ID,
    session_type: "cist",
    result_status: "completed",
    result_type: "stable",
    display_label: "안정적",
    message: "오늘도 인지 기능이 안정적이에요. 지금처럼만 꾸준히 이어가 주세요!",
    recommendation: "내일도 같은 시간에 대화해요.",
    completed_at: new Date().toISOString(),
  };
  if (audience === "elder") return base;
  return {
    ...base,
    screening_reference_score: 24,
    display_score: 24,
    score_max: 30,
    score_rate: 0.8,
    risk_level: "low",
    screening_label: "정상",
  };
}

/* ── guardian ───────────────────────────────────────────────────────────── */

export function mockElders(): EldersResponse {
  return {
    elders: [
      {
        elder_id: MOCK_ELDER_ID,
        elder_name: "김영자",
        link_id: fixedId("77777777", 1),
        status: "active",
        access_scope: ["screening", "diary", "report"],
        consent_status: "granted",
        latest_display_score: 24,
        latest_score_max: 30,
        latest_score_rate: 0.8,
        latest_risk_level: "low",
        last_session_at: daysAgo(0).toISOString(),
      },
    ],
  };
}

export function mockGuardianReport(): GuardianReportResponse {
  const weekly = [22, 23, 21, 24, 24, 25];
  return {
    elder_id: MOCK_ELDER_ID,
    elder_name: "김영자",
    latest_summary: "전반적으로 안정적이며 기억력이 꾸준히 향상되고 있어요. 주의력은 소폭 하락했으니 규칙적인 수면을 권장합니다.",
    latest_screening_score: 24,
    latest_display_score: 24,
    latest_score_max: 30,
    latest_score_rate: 0.8,
    latest_risk_level: "low",
    vocabulary_score: null,
    game_cognitive_index: null,
    alert_level: "low",
    trend_30d: "improving",
    last_session_at: daysAgo(0).toISOString(),
    activity_summary7d: { session_count: 6, game_count: 8, diary_count: 7 },
    trend_points: weekly.map((score, i) => ({
      date: isoDate(daysAgo((weekly.length - 1 - i) * 7)),
      screening_reference_score: score,
      display_score: score,
      score_max: 30,
      score_rate: score / 30,
      score_delta: i === 0 ? null : score - weekly[i - 1],
      risk_level: "low",
    })),
    recent_alerts: guardianNotifications.slice(0, 2).map((n) => ({
      notification_id: n.notification_id,
      title: n.title,
      body: n.body,
      severity: n.severity,
      created_at: n.created_at,
    })),
    daily_summary: null,
  };
}

/**
 * Counselling centres, mirroring the `V9__create_counseling_centers.sql` seed.
 *
 * The MVP seed is 부산광역시 해운대구 only, so the fixture is too — inventing
 * institutions for other regions would make the offline app look like it has
 * nationwide coverage the backend does not have.
 */
const counselingCenters = [
  {
    center_id: "00000000-0000-0000-0000-000000009001",
    name: "부산대학교병원 신경과",
    facility_type: "hospital",
    address: "부산광역시 해운대구 APEC로 170",
    latitude: 35.1712,
    longitude: 129.1284,
    phone: "051-240-7000",
    naver_map_url: "https://map.naver.com/p/search/부산대학교병원",
    homepage_url: "https://www.pnuh.or.kr",
  },
  {
    center_id: "00000000-0000-0000-0000-000000009002",
    name: "해운대구 치매안심센터",
    facility_type: "dementia_center",
    address: "부산광역시 해운대구 반여로 30",
    latitude: 35.2074,
    longitude: 129.1262,
    phone: "051-749-7575",
    naver_map_url: "https://map.naver.com/p/search/해운대구%20치매안심센터",
    homepage_url: "https://www.haeundae.go.kr",
  },
  {
    center_id: "00000000-0000-0000-0000-000000009003",
    name: "해운대구 보건소",
    facility_type: "public_health_center",
    address: "부산광역시 해운대구 양운로 100",
    latitude: 35.1638,
    longitude: 129.1631,
    phone: "051-746-4000",
    naver_map_url: "https://map.naver.com/p/search/해운대구%20보건소",
    homepage_url: "https://www.haeundae.go.kr/health",
  },
];

export function mockCounselingCenters(
  provinceCode: string,
  districtCode?: string,
  facilityType?: string,
): CounselingCentersResponse {
  // Filtered the way the server filters, so selecting a province with no seeded
  // centres correctly shows the empty state instead of Busan's list.
  const centers = counselingCenters
    .filter(() => provinceCode === "26")
    .filter(() => !districtCode || districtCode === "26350")
    .filter((c) => !facilityType || c.facility_type === facilityType)
    .map((c) => ({
      ...c,
      province_code: "26",
      district_code: "26350",
      province_name: "부산광역시",
      district_name: "해운대구",
      reservation_mode: "external_link",
      source_name: "공공기관 기준정보",
      source_updated_at: daysAgo(30).toISOString(),
    }));
  return { centers, total: centers.length };
}

export function mockHistory(): HistoryResponse {
  const weekly = [22, 23, 21, 24, 24, 25];
  return {
    records: weekly.map((score, i) => ({
      analysis_id: fixedId("88888888", i + 1),
      session_id: MOCK_SESSION_ID,
      screening_reference_score: score,
      display_score: score,
      score_max: 30,
      score_rate: score / 30,
      label: "stable",
      risk_level: "low",
      domain_scores: { memory: 4, attention: -2, language: 1, visuospatial: 3 },
      trend: "improving",
      score_delta: i === 0 ? undefined : score - weekly[i - 1],
      analyzed_at: daysAgo((weekly.length - 1 - i) * 7).toISOString(),
    })),
    total: weekly.length,
    aggregation: "weekly",
    sample_sufficient: true,
  };
}
