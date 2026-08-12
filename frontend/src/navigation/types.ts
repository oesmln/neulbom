import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import type { NavigatorScreenParams } from "@react-navigation/native";

/** Root onboarding + role stacks */
export type RootStackParamList = {
  Splash: undefined;
  Login: undefined;
  /**
   * Sign-up only. api-spec 3.1 creates the account once, at the end of
   * 이름·이메일·비밀번호 → 초대 코드 → 유형 선택, so the form values travel here
   * in memory and `POST /auth/register` is sent with the chosen role.
   */
  UserType: { signup?: PendingSignup } | undefined;
  Elder: undefined;
  Guardian: undefined;
};

/** Values held between the sign-up form and the role choice. Never persisted. */
export type PendingSignup = {
  name: string;
  email: string;
  password: string;
  inviteCode?: string;
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
  ElderResult: { sessionId: string } | undefined;
  ElderNotifications: undefined;
  ElderCampaign: undefined;
};

/** Bottom tabs: 홈 sits in the middle as a raised button. */
export type ElderTabParamList = {
  ElderAiChat: undefined;
  ElderCalendar: undefined;
  ElderHome: undefined;
  ElderGameHub: undefined;
  ElderMyPage: undefined;
};

/** Guardian area */
export type GuardianTabParamList = {
  GuardianDashboard: undefined;
  GuardianDiary: undefined;
  GuardianChart: undefined;
  GuardianNotifications: undefined;
};

export type RootNav = NativeStackNavigationProp<RootStackParamList>;
export type ElderNav = NativeStackNavigationProp<ElderStackParamList>;
