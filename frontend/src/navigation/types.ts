import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import type { NavigatorScreenParams } from "@react-navigation/native";
import type {
  ConsentType,
  UserPreferenceUpdateRequest,
  UserProfileUpdateRequest,
} from "@/api/types";

/** Root onboarding + role stacks */
export type RootStackParamList = {
  Splash: undefined;
  Login:
    | {
        mode?: "login" | "signup";
        socialProvider?: "kakao" | "naver";
        socialPendingToken?: string;
        socialDisplayName?: string | null;
      }
    | undefined;
  OAuthCallback: {
    provider: "kakao" | "naver";
    code?: string;
    state?: string;
    error?: string;
    error_description?: string;
  };
  /**
   * Sign-up only. api-spec 3.1 creates the account once, at the end of
   * 이름·이메일·비밀번호 → 유형 선택 → (고령자라면) 초대 코드, so the form
   * values travel here in memory and `POST /auth/register` is sent with the chosen role.
   */
  UserType: { signup?: PendingSignup } | undefined;
  SignupInvite: { signup?: PendingSignup } | undefined;
  EmailVerification: { signup: PendingSignup };
  EmailVerificationLink: { token?: string } | undefined;
  SignupComplete: SignupCompleteParams;
  PasswordReset: { token?: string } | undefined;
  ElderProfile: { inviteCode?: string; signup?: PendingSignup } | undefined;
  Onboarding: undefined;
  Elder: NavigatorScreenParams<ElderStackParamList> | undefined;
  Guardian: undefined;
};

/** Values held between the sign-up form and the role choice. Never persisted. */
export type PendingSignup = {
  name: string;
  email: string;
  password: string;
  inviteCode?: string;
  requiredConsentsAccepted?: boolean;
  elderSetup?: PendingElderSetup;
};

/** Elder profile and feature consents held in memory until email verification. */
export type PendingElderSetup = {
  profile: UserProfileUpdateRequest;
  preferences: UserPreferenceUpdateRequest;
  consents: ConsentType[];
  guardianConsentAccepted: boolean;
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
  ElderCist: { sessionId: string; retryQuestionCodes: string[] } | undefined;
  /**
   * Carries the session it belongs to so the screen can read
   * `GET /screenings/{session_id}/result` instead of guessing.
   */
  ElderResult: { sessionId: string; mode?: "daily" | "baseline" } | undefined;
  ElderNotifications: undefined;
  ElderCampaign: undefined;
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
  ElderMyPage: NavigatorScreenParams<ElderMyPageStackParamList> | undefined;
};

/**
 * 마이 탭 안의 스택. 앱 설정·비밀번호 변경은 탭 위로 push하지 않고 이 스택
 * 안에서 열려 하단 탭바가 계속 보인다.
 */
export type ElderMyPageStackParamList = {
  ElderMyPageMain: undefined;
  ElderPasswordChange: undefined;
  ElderAppSettings: undefined;
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
  /** 비밀번호 변경 — reached from 보호자 설정. */
  GuardianPasswordChange: undefined;
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
export type ElderMyPageNav = NativeStackNavigationProp<ElderMyPageStackParamList>;
export type GuardianNav = NativeStackNavigationProp<GuardianStackParamList>;
