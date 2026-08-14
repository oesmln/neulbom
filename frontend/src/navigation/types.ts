import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import type { NavigatorScreenParams } from "@react-navigation/native";

/** Root onboarding + role stacks */
export type RootStackParamList = {
  Splash: undefined;
  Login: { mode?: "login" | "signup" } | undefined;
  /**
   * Sign-up only. api-spec 3.1 creates the account once, at the end of
   * 이름·이메일·비밀번호 → 초대 코드 → 유형 선택, so the form values travel here
   * in memory and `POST /auth/register` is sent with the chosen role.
   */
  UserType: { signup?: PendingSignup } | undefined;
  EmailVerification: { signup: PendingSignup };
  SignupComplete: SignupCompleteParams;
  ElderProfile: { inviteCode?: string } | undefined;
  Onboarding: undefined;
  Elder: undefined;
  Guardian: undefined;
};

/** Values held between the sign-up form and the role choice. Never persisted. */
export type PendingSignup = {
  name: string;
  email: string;
  password: string;
  inviteCode?: string;
  requiredConsentsAccepted?: boolean;
};

export type SignupCompleteParams = {
  role: "elder" | "guardian";
  inviteCode?: string;
  invitationError?: string;
};

/** Elder area (bottom tabs live inside this stack so detail screens can be pushed) */
export type ElderStackParamList = {
  ElderTabs: NavigatorScreenParams<ElderTabParamList>;
  /**
   * CIST is the one-off initial screening, not a tab — it is reached from
   * onboarding and from the home card, and it pushes over the tabs.
   */
  ElderCist: undefined;
  /**
   * Carries the session it belongs to so the screen can read
   * `GET /screenings/{session_id}/result` instead of guessing.
   */
  ElderResult: { sessionId: string; mode?: "daily" | "baseline" } | undefined;
  ElderNotifications: undefined;
  ElderCampaign: undefined;
  /** 비밀번호 변경 — reached from 마이페이지. */
  ElderPasswordChange: undefined;
  /** 앱 설정 — reached from 마이페이지. */
  ElderAppSettings: undefined;
  /** 두뇌 게임 3종 — pushed over the tabs from the game hub. */
  ElderGameCardMatch: undefined;
  ElderGameColor: undefined;
  ElderGameConsonant: undefined;
};

/** Bottom tabs: 홈 sits in the middle as a raised button. */
export type ElderTabParamList = {
  ElderAiChat: undefined;
  ElderCalendar: undefined;
  ElderHome: undefined;
  ElderGameHub: undefined;
  ElderMyPage: undefined;
};

/**
 * Guardian area.
 *
 * Tabs sit inside a stack for the same reason the elder side does: 상담 예약 and
 * 앱 설정 are reached from the dashboard and push over the tabs rather than
 * becoming tabs of their own.
 */
export type GuardianStackParamList = {
  GuardianTabs: NavigatorScreenParams<GuardianTabParamList>;
  GuardianNotifications: undefined;
  /** 전문의 상담 예약 — reached from the score-drop alert on the dashboard. */
  GuardianCounselingCenters: undefined;
  /** 앱 설정 — reached from the gear button in the dashboard header. */
  GuardianAppSettings: undefined;
  /** 보호자 초대 발급과 연결별 접근 범위 관리. */
  GuardianConnections: undefined;
};

export type GuardianTabParamList = {
  GuardianDashboard: undefined;
  GuardianRecord: undefined;
  GuardianChart: undefined;
  GuardianAppointments: undefined;
  GuardianSettings: undefined;
};

export type RootNav = NativeStackNavigationProp<RootStackParamList>;
export type ElderNav = NativeStackNavigationProp<ElderStackParamList>;
export type GuardianNav = NativeStackNavigationProp<GuardianStackParamList>;
