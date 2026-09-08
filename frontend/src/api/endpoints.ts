/**
 * Every backend call the app makes, one function per endpoint.
 *
 * Paths and query parameter names are copied from the Spring controllers, not
 * from prose: `@RequestParam(name = "user_id")` means the query key is
 * `user_id`, `@PathVariable UUID userId` means it goes in the path. Anything the
 * backend does not expose is absent here rather than approximated.
 *
 * In mock mode (`USE_MOCK_API`) each function answers from `apiMock.ts` with the
 * same response shape, so screens never branch on which mode they are in.
 */

import * as Crypto from "expo-crypto";
import { Platform } from "react-native";

import { USE_MOCK_API, APP_TIMEZONE } from "./config";
import { request, uploadMultipart } from "./client";
import { ApiError } from "./errors";
import * as mock from "./mock";
import type {
  AnswerRequest,
  AnswerResponse,
  AuthTokenResponse,
  CalendarActivitiesResponse,
  CharacterResponse,
  CistAiAnalysisResponse,
  CistRecognitionPlanResponse,
  ConsentRequest,
  ConsentResponse,
  ConsentsResponse,
  CounselingCentersResponse,
  DashboardResponse,
  EmailAvailabilityResponse,
  DiariesResponse,
  DiaryCreateRequest,
  DiaryDetailResponse,
  EldersResponse,
  GameHistoryResponse,
  GameResultRequest,
  GameResultResponse,
  GenerationStatusResponse,
  GuardianLinkResponse,
  GuardianLinkUpdateRequest,
  GuardianReportResponse,
  HistoryResponse,
  InvitationCreateRequest,
  InvitationCreateResponse,
  InvitationVerifyResponse,
  IsoDate,
  LoginRequest,
  NotificationReadResponse,
  NotificationsReadAllResponse,
  NotificationsResponse,
  OAuthLoginRequest,
  OAuthCompleteRequest,
  OAuthPrepareResponse,
  PasswordChangeRequest,
  PasswordResetRequestResponse,
  QuestionsResponse,
  ReactionResponse,
  ReactionsResponse,
  RecordingPurpose,
  RecordingStatusResponse,
  RecordingUploadResponse,
  RegisterRequest,
  RegisterResponse,
  EmailVerificationRequestResponse,
  Role,
  ScreeningResultResponse,
  SpeechSynthesizeRequest,
  SpeechSynthesizeResponse,
  SessionEndResponse,
  SessionResponse,
  SessionStartRequest,
  SessionType,
  SessionsResponse,
  TranscribeResponse,
  UserPreferenceResponse,
  UserPreferenceUpdateRequest,
  UserProfileResponse,
  UserProfileUpdateRequest,
  UserProfileUpdateResponse,
  Uuid,
  VoiceProfilesResponse,
  XpHistoryResponse,
  NearbyCentersResponse,
} from "./types";

/**
 * Client-generated id for the idempotency contract: `client_recording_id`,
 * `client_answer_id` and `client_game_result_id` all exist so a retry after a
 * dropped response cannot create a second row.
 */
export function newClientId(): Uuid {
  return Crypto.randomUUID();
}

/* ── auth ───────────────────────────────────────────────────────────────── */

export const auth = {
  checkEmailAvailability(email: string): Promise<EmailAvailabilityResponse> {
    const normalizedEmail = email.trim().toLowerCase();
    if (USE_MOCK_API) {
      return Promise.resolve({ email: normalizedEmail, available: true });
    }
    return request("/auth/email/availability", {
      query: { email: normalizedEmail },
      anonymous: true,
    });
  },

  register(body: RegisterRequest): Promise<RegisterResponse> {
    if (USE_MOCK_API) {
      // Remember the role so the sign-in that follows returns the same one.
      mock.rememberMockRole(body.role);
      return Promise.resolve({
        user_id: body.role === "guardian" ? mock.MOCK_GUARDIAN_ID : mock.MOCK_ELDER_ID,
        role: body.role,
        profile_completed: false,
        email_verified: true,
        created_at: new Date().toISOString(),
      });
    }
    return request("/auth/register", { method: "POST", body, anonymous: true });
  },

  requestEmailVerification(email: string): Promise<EmailVerificationRequestResponse> {
    if (USE_MOCK_API) {
      return Promise.resolve({
        request_id: mock.MOCK_ELDER_ID,
        expires_at: new Date(Date.now() + 86_400_000).toISOString(),
      });
    }
    return request("/auth/email/verify/request", {
      method: "POST",
      body: { email },
      anonymous: true,
    });
  },

  confirmEmailVerification(verificationToken: string): Promise<void> {
    if (USE_MOCK_API) return Promise.resolve();
    return request("/auth/email/verify/confirm", {
      method: "POST",
      body: { verification_token: verificationToken },
      anonymous: true,
    });
  },

  login(body: LoginRequest): Promise<AuthTokenResponse> {
    if (USE_MOCK_API) {
      const role = body.email.trim().toLowerCase().startsWith("guardian")
        ? "guardian"
        : mock.currentMockRole();
      return Promise.resolve(mock.mockAuthToken(role));
    }
    return request("/auth/login", { method: "POST", body, anonymous: true });
  },

  oauthLogin(provider: "kakao" | "naver", body: OAuthLoginRequest): Promise<AuthTokenResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockAuthToken(body.role));
    return request(`/auth/oauth/${provider}`, { method: "POST", body, anonymous: true });
  },

  prepareOAuthLogin(
    provider: "kakao" | "naver",
    body: OAuthLoginRequest,
  ): Promise<OAuthPrepareResponse> {
    if (USE_MOCK_API) {
      return Promise.resolve({
        status: "role_required",
        tokens: null,
        pending_token: "mock-oauth-pending-token",
        email: "social@example.com",
        display_name: "소셜 사용자",
      });
    }
    return request(`/auth/oauth/${provider}/prepare`, { method: "POST", body, anonymous: true });
  },

  completeOAuthLogin(provider: "kakao" | "naver", body: OAuthCompleteRequest): Promise<AuthTokenResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockAuthToken(body.role));
    return request(`/auth/oauth/${provider}/complete`, { method: "POST", body, anonymous: true });
  },

  requestPasswordReset(email: string): Promise<PasswordResetRequestResponse> {
    if (USE_MOCK_API) {
      return Promise.resolve({
        request_id: mock.MOCK_ELDER_ID,
        expires_at: new Date(Date.now() + 600_000).toISOString(),
      });
    }
    return request("/auth/password/reset/request", {
      method: "POST",
      body: { email },
      anonymous: true,
    });
  },

  confirmPasswordReset(resetToken: string, newPassword: string): Promise<void> {
    if (USE_MOCK_API) return Promise.resolve();
    return request("/auth/password/reset/confirm", {
      method: "POST",
      body: { reset_token: resetToken, new_password: newPassword },
      anonymous: true,
    });
  },

  logout(refreshToken: string): Promise<void> {
    if (USE_MOCK_API) return Promise.resolve();
    return request("/auth/logout", { method: "POST", body: { refresh_token: refreshToken } });
  },

  changePassword(body: PasswordChangeRequest): Promise<void> {
    if (USE_MOCK_API) return Promise.resolve();
    return request("/users/me/password", { method: "PATCH", body });
  },

  withdraw(): Promise<void> {
    if (USE_MOCK_API) return Promise.resolve();
    return request("/users/me", { method: "DELETE" });
  },
};

/* ── users ──────────────────────────────────────────────────────────────── */

export const users = {
  profile(userId: Uuid, role: Role = "elder"): Promise<UserProfileResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockProfile(userId, role));
    return request(`/users/${userId}`);
  },

  updateProfile(userId: Uuid, body: UserProfileUpdateRequest): Promise<UserProfileUpdateResponse> {
    if (USE_MOCK_API) {
      if (body.character_name?.trim()) {
        mock.setMockCharacterDisplayName(body.character_name.trim());
      }
      return Promise.resolve({
        user_id: userId,
        profile_completed: true,
        onboarding_step: body.onboarding_step ?? "completed",
        onboarding_completed: body.onboarding_completed ?? true,
        baseline_completed: body.baseline_completed ?? false,
        character_name: body.character_name ?? mock.mockCharacterDisplayNameValue(),
        updated_at: new Date().toISOString(),
      });
    }
    return request(`/users/${userId}`, { method: "PATCH", body });
  },

  preferences(userId: Uuid): Promise<UserPreferenceResponse> {
    if (USE_MOCK_API) {
      return Promise.resolve({
        preferred_hearing_side: "both",
        voice_profile_id: null,
        speech_rate: 1,
        subtitle_enabled: false,
        sound_effect_enabled: true,
        push_notification_enabled: true,
        guardian_reaction_notification_enabled: true,
        screening_notification_enabled: true,
        diary_notification_enabled: true,
        weekly_report_notification_enabled: true,
        updated_at: new Date().toISOString(),
      });
    }
    return request(`/users/${userId}/preferences`);
  },

  updatePreferences(
    userId: Uuid,
    body: UserPreferenceUpdateRequest,
  ): Promise<UserPreferenceResponse> {
    if (USE_MOCK_API) return users.preferences(userId);
    return request(`/users/${userId}/preferences`, { method: "PATCH", body });
  },

  voiceProfiles(language = "ko"): Promise<VoiceProfilesResponse> {
    if (USE_MOCK_API) return Promise.resolve({ voice_profiles: [] });
    return request("/voice-profiles", { query: { language } });
  },

  saveConsent(userId: Uuid, body: ConsentRequest): Promise<ConsentResponse> {
    if (USE_MOCK_API) {
      return Promise.resolve({
        consent_id: newClientId(),
        consent_type: body.consent_type,
        agreed: body.agreed,
        agreed_at: body.agreed_at,
        version: body.version,
        created_at: new Date().toISOString(),
      });
    }
    return request(`/consent/${userId}`, { method: "POST", body });
  },

  consents(userId: Uuid): Promise<ConsentsResponse> {
    if (USE_MOCK_API) return Promise.resolve({ consents: [] });
    return request(`/consent/${userId}`);
  },
};

/* ── speech ─────────────────────────────────────────────────────────────── */

const MOCK_SILENT_WAV =
  "UklGRmQBAABXQVZFZm10IBAAAAABAAEAQB8AAIA+AAACABAAZGF0YUABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";

export const speech = {
  synthesize(body: SpeechSynthesizeRequest): Promise<SpeechSynthesizeResponse> {
    if (USE_MOCK_API) {
      return Promise.resolve({
        audio_content_base64: MOCK_SILENT_WAV,
        content_type: "audio/wav",
        voice_profile_id: body.voice_profile_id ?? "voice_ko_01",
        voice_name: "ko-KR-Neural2-A",
        speech_rate: body.speech_rate ?? 0.9,
      });
    }
    return request("/speech/synthesize", { method: "POST", body });
  },
};

/* ── sessions ───────────────────────────────────────────────────────────── */

export const sessions = {
  start(body: SessionStartRequest): Promise<SessionResponse> {
    if (USE_MOCK_API) {
      return Promise.resolve(mock.mockSession(body.user_id, body.session_type ?? "cist"));
    }
    return request("/sessions", { method: "POST", body });
  },

  get(sessionId: Uuid): Promise<SessionResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockSession(mock.MOCK_ELDER_ID, "cist"));
    return request(`/sessions/${sessionId}`);
  },

  end(sessionId: Uuid): Promise<SessionEndResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockSessionEnd(sessionId));
    return request(`/sessions/${sessionId}/end`, { method: "PATCH" });
  },

  list(userId: Uuid, params: { sessionType?: string; page?: number; limit?: number } = {}): Promise<SessionsResponse> {
    if (USE_MOCK_API) return Promise.resolve({ sessions: [], total: 0, page: 1, limit: 20 });
    return request("/sessions", {
      query: {
        user_id: userId,
        sessionType: params.sessionType,
        page: params.page,
        limit: params.limit,
      },
    });
  },

  saveAnswer(sessionId: Uuid, body: AnswerRequest): Promise<AnswerResponse> {
    if (USE_MOCK_API) {
      return Promise.resolve({
        answer_id: newClientId(),
        question_id: body.question_id,
        saved: true,
        next_question_order: 0,
        sync_status: "synced",
      });
    }
    return request(`/sessions/${sessionId}/answers`, {
      method: "POST",
      body,
      idempotencyKey: body.client_answer_id,
    });
  },

  dailyQuestions(userId: Uuid, sessionType: SessionType): Promise<QuestionsResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockDailyQuestions(sessionType));
    return request("/questions/daily", { query: { user_id: userId, session_type: sessionType } });
  },
};

/* ── integrated CIST AI analysis ───────────────────────────────────────── */

export const cistAi = {
  createRecognitionPlan(sessionId: Uuid): Promise<CistRecognitionPlanResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockCistRecognitionPlan(sessionId));
    return request(`/sessions/${sessionId}/cist-ai/recognition-plan`, { method: "POST" });
  },

  createAnalysis(sessionId: Uuid): Promise<CistAiAnalysisResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockCreateCistAiAnalysis(sessionId));
    return request(`/sessions/${sessionId}/cist-ai/analyses`, { method: "POST" });
  },

  getAnalysis(sessionId: Uuid): Promise<CistAiAnalysisResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockGetCistAiAnalysis(sessionId));
    return request(`/sessions/${sessionId}/cist-ai/analyses`);
  },

  retryAnalysis(sessionId: Uuid): Promise<CistAiAnalysisResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockRetryCistAiAnalysis(sessionId));
    return request(`/sessions/${sessionId}/cist-ai/analyses/retry`, { method: "POST" });
  },
};

/* ── recordings ─────────────────────────────────────────────────────────── */

export interface RecordingUpload {
  uri: string;
  clientRecordingId: Uuid;
  userId: Uuid;
  purpose: RecordingPurpose;
  sessionId?: Uuid;
  questionId?: Uuid;
  recordedAt: string;
  durationMs: number;
  deviceStatus?: string;
  mimeType?: string;
  fileName?: string;
}

export const recordings = {
  /**
   * `RecordingController` takes the audio as a `audio_file` part and everything
   * else as query parameters, so the payload is split accordingly.
   */
  async upload(input: RecordingUpload): Promise<RecordingUploadResponse> {
    if (USE_MOCK_API) {
      return {
        recording_id: newClientId(),
        client_recording_id: input.clientRecordingId,
        purpose: input.purpose,
        sync_status: "server_uploaded",
        transcript_status: "pending",
        analysis_status: "pending",
        deduplicated: false,
      };
    }
    const form = new FormData();
    const fileName = input.fileName ?? "answer.m4a";
    const mimeType = input.mimeType ?? "audio/mp4";
    if (Platform.OS === "web") {
      const source = await fetch(input.uri);
      const bytes = await source.arrayBuffer();
      form.append("audio_file", new Blob([bytes], { type: mimeType }), fileName);
    } else {
      form.append("audio_file", {
        uri: input.uri,
        name: fileName,
        type: mimeType,
        // React Native's FormData accepts this descriptor on native platforms.
      } as unknown as Blob);
    }
    return uploadMultipart(
      "/recordings",
      form,
      {
        client_recording_id: input.clientRecordingId,
        user_id: input.userId,
        purpose: input.purpose,
        session_id: input.sessionId,
        question_id: input.questionId,
        recorded_at: input.recordedAt,
        duration_ms: input.durationMs,
        device_status: input.deviceStatus,
      },
      input.clientRecordingId,
    );
  },

  status(recordingId: Uuid): Promise<RecordingStatusResponse> {
    return request(`/recordings/${recordingId}`);
  },

  transcribe(recordingId: Uuid): Promise<TranscribeResponse> {
    if (USE_MOCK_API) {
      return Promise.resolve({
        transcript_id: newClientId(),
        recording_id: recordingId,
        transcript: "음성 답변이 텍스트로 변환됐어요.",
        duration_sec: null,
        confidence: null,
        language: "ko",
        model: "mock",
      });
    }
    return request(`/recordings/${recordingId}/transcribe`, { method: "POST" });
  },
};

/* ── dashboard, screening result, guardian report ───────────────────────── */

export const reports = {
  dashboard(userId: Uuid): Promise<DashboardResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockDashboard(userId));
    return request(`/dashboard/${userId}`);
  },

  screeningResult(sessionId: Uuid, audience: "elder" | "guardian" = "elder"): Promise<ScreeningResultResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockScreeningResult(sessionId, audience));
    return request(`/screenings/${sessionId}/result`);
  },

  cognitiveHistory(
    userId: Uuid,
    params: { limit?: number; fromDate?: IsoDate; toDate?: IsoDate; aggregation?: string } = {},
  ): Promise<HistoryResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockHistory());
    return request(`/analysis/cognitive/${userId}/history`, {
      query: {
        limit: params.limit,
        from_date: params.fromDate,
        to_date: params.toDate,
        aggregation: params.aggregation,
      },
    });
  },

  guardianReport(
    guardianId: Uuid,
    elderId: Uuid,
    params: { date?: IsoDate; fromDate?: IsoDate; toDate?: IsoDate } = {},
  ): Promise<GuardianReportResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockGuardianReport());
    return request(`/guardian/${guardianId}/report`, {
      query: {
        elder_id: elderId,
        date: params.date,
        from_date: params.fromDate,
        to_date: params.toDate,
        timezone: APP_TIMEZONE,
      },
    });
  },
};

/* ── diaries & calendar ─────────────────────────────────────────────────── */

export const diaries = {
  /**
   * `GET /diaries/{id}` is overloaded on the backend: with a date range it lists
   * a user's diaries, without one it returns a single diary. Two functions here
   * so call sites cannot mix them up.
   */
  listForUser(
    userId: Uuid,
    params: { fromDate?: IsoDate; toDate?: IsoDate; page?: number; limit?: number } = {},
  ): Promise<DiariesResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockDiaries());
    return request(`/diaries/${userId}`, {
      query: {
        from_date: params.fromDate,
        to_date: params.toDate,
        page: params.page,
        limit: params.limit,
      },
    });
  },

  detail(diaryId: Uuid): Promise<DiaryDetailResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockDiaryDetail(diaryId));
    return request(`/diaries/${diaryId}`);
  },

  create(body: DiaryCreateRequest) {
    if (USE_MOCK_API) return Promise.resolve(mock.mockDiaryDetail(mock.MOCK_ELDER_ID));
    return request<DiaryDetailResponse>("/diaries", { method: "POST", body });
  },

  generationStatus(userId: Uuid, date: IsoDate): Promise<GenerationStatusResponse> {
    if (USE_MOCK_API) {
      return Promise.resolve({
        generation_job_id: null,
        target_date: date,
        status: "completed",
        scheduled_at: null,
        available_at: new Date().toISOString(),
        diary_id: mock.mockDiaries().diaries[0]?.diary_id ?? null,
        failure_reason: null,
        retryable: false,
        display_label: "일기 생성 완료",
        message: "오늘 일기가 준비됐어요.",
      });
    }
    return request(`/diaries/${userId}/generation-status`, { query: { date } });
  },

  reactions(diaryId: Uuid): Promise<ReactionsResponse> {
    if (USE_MOCK_API) return Promise.resolve({ reactions: [] });
    return request(`/diaries/${diaryId}/reactions`);
  },

  react(diaryId: Uuid, reactionType: string, message?: string): Promise<ReactionResponse> {
    if (USE_MOCK_API) {
      return Promise.resolve({
        reaction_id: newClientId(),
        diary_id: diaryId,
        reactor_id: mock.MOCK_GUARDIAN_ID,
        reactor_name: "김철수",
        reaction_type: reactionType,
        message: message ?? null,
        created_at: new Date().toISOString(),
      });
    }
    return request(`/diaries/${diaryId}/reactions`, {
      method: "POST",
      body: { reaction_type: reactionType, message },
    });
  },

  calendar(userId: Uuid, fromDate: IsoDate, toDate: IsoDate): Promise<CalendarActivitiesResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockCalendarActivities());
    return request(`/calendar/${userId}/activities`, {
      query: { from_date: fromDate, to_date: toDate },
    });
  },
};

/* ── game & character ───────────────────────────────────────────────────── */

export const game = {
  /**
   * XP is never posted from the client — `POST /character/{id}/xp` exists for
   * server-side use and the checklist forbids calling it here. Submitting the
   * game result is what earns XP.
   */
  submitResult(body: GameResultRequest): Promise<GameResultResponse> {
    if (USE_MOCK_API) {
      return Promise.resolve(mock.mockSubmitGameResult(body));
    }
    return request("/game/result", {
      method: "POST",
      body,
      idempotencyKey: body.client_game_result_id,
    });
  },

  history(userId: Uuid, limit = 20): Promise<GameHistoryResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockGameHistory());
    return request(`/game/${userId}/history`, { query: { limit } });
  },

  character(userId: Uuid): Promise<CharacterResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockCharacter(userId));
    return request(`/character/${userId}`);
  },

  xpHistory(userId: Uuid, limit = 20): Promise<XpHistoryResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockXpHistory());
    return request(`/character/${userId}/xp-history`, { query: { limit } });
  },
};

/* ── notifications ──────────────────────────────────────────────────────── */

export const notifications = {
  list(userId: Uuid, role: Role, params: { unreadOnly?: boolean; limit?: number } = {}): Promise<NotificationsResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockNotifications(role));
    return request(`/notifications/${userId}`, {
      query: { unread_only: params.unreadOnly, limit: params.limit },
    });
  },

  markRead(notificationId: Uuid): Promise<NotificationReadResponse> {
    if (USE_MOCK_API) {
      mock.mockMarkRead(notificationId);
      return Promise.resolve({
        notification_id: notificationId,
        is_read: true,
        read_at: new Date().toISOString(),
      });
    }
    return request(`/notifications/${notificationId}/read`, { method: "PATCH" });
  },

  markAllRead(role: Role): Promise<NotificationsReadAllResponse> {
    if (USE_MOCK_API) {
      return Promise.resolve({
        updated_count: mock.mockMarkAllRead(role),
        read_at: new Date().toISOString(),
      });
    }
    return request("/notifications/read-all", { method: "PATCH" });
  },
};

/* ── guardian link ──────────────────────────────────────────────────────── */

let mockInvitation: InvitationCreateResponse | null = null;
let mockInvitationUsed = false;

function requireUsableMockInvitation(inviteCode: string): InvitationCreateResponse {
  if (!mockInvitation || mockInvitation.invite_code !== inviteCode) {
    throw new ApiError(404, "초대 코드를 찾을 수 없습니다.");
  }
  if (mockInvitationUsed || new Date(mockInvitation.expires_at).getTime() <= Date.now()) {
    throw new ApiError(410, "초대 코드가 만료되었거나 이미 사용되었습니다.");
  }
  return mockInvitation;
}

export const guardian = {
  createInvitation(body: InvitationCreateRequest): Promise<InvitationCreateResponse> {
    if (USE_MOCK_API) {
      mockInvitation = {
        invitation_id: newClientId(),
        invite_code: "123456",
        status: "issued",
        relation: body.relation ?? null,
        access_scope: body.access_scope ?? ["screening", "summary", "diary", "activity"],
        expires_at: new Date(Date.now() + (body.expires_in ?? 600) * 1000).toISOString(),
      };
      mockInvitationUsed = false;
      return Promise.resolve(mockInvitation);
    }
    return request("/guardian/invitations", { method: "POST", body });
  },

  verifyInvitation(inviteCode: string): Promise<InvitationVerifyResponse> {
    if (USE_MOCK_API) {
      const invitation = requireUsableMockInvitation(inviteCode);
      return Promise.resolve({
        invitation_id: invitation.invitation_id,
        status: "pending",
        relation: invitation.relation,
        access_scope: invitation.access_scope,
        expires_at: invitation.expires_at,
        requires_consent: true,
      });
    }
    return request("/guardian/invitations/verify", {
      method: "POST",
      body: { invite_code: inviteCode },
      anonymous: true,
    });
  },

  acceptInvitation(inviteCode: string, consentAgreed: boolean): Promise<GuardianLinkResponse> {
    if (USE_MOCK_API) {
      const invitation = requireUsableMockInvitation(inviteCode);
      if (!consentAgreed) throw new ApiError(422, "보호자 접근 동의가 필요합니다.");
      mockInvitationUsed = true;
      return Promise.resolve({
        invitation_id: invitation.invitation_id,
        link_id: newClientId(),
        elder_id: mock.MOCK_ELDER_ID,
        guardian_id: mock.MOCK_GUARDIAN_ID,
        status: "active",
        access_scope: invitation.access_scope,
        consent_required: false,
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      });
    }
    return request("/guardian/invitations/accept", {
      method: "POST",
      body: { invite_code: inviteCode, consent_agreed: consentAgreed },
    });
  },

  elders(guardianId: Uuid, status?: string): Promise<EldersResponse> {
    if (USE_MOCK_API) return Promise.resolve(mock.mockElders());
    return request(`/guardian/${guardianId}/elders`, { query: { status } });
  },

  updateLink(linkId: Uuid, body: GuardianLinkUpdateRequest): Promise<GuardianLinkResponse> {
    if (USE_MOCK_API) {
      return Promise.resolve({
        invitation_id: null,
        link_id: linkId,
        elder_id: mock.MOCK_ELDER_ID,
        guardian_id: mock.MOCK_GUARDIAN_ID,
        status: body.status ?? "active",
        access_scope: body.access_scope ?? ["screening", "summary", "diary", "activity"],
        consent_required: false,
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      });
    }
    return request(`/guardian/link/${linkId}`, { method: "PATCH", body });
  },

  revokeLink(linkId: Uuid): Promise<void> {
    if (USE_MOCK_API) return Promise.resolve();
    return request(`/guardian/link/${linkId}`, { method: "DELETE" });
  },
};

/* ── counseling ─────────────────────────────────────────────────────────── */

export const counseling = {
  centers(provinceCode: string, districtCode?: string, facilityType?: string): Promise<CounselingCentersResponse> {
    if (USE_MOCK_API) {
      return Promise.resolve(mock.mockCounselingCenters(provinceCode, districtCode, facilityType));
    }
    return request("/counseling/centers", {
      query: {
        province_code: provinceCode,
        district_code: districtCode,
        facility_type: facilityType,
      },
    });
  },

  /** 시·도/시·군·구 표시명으로 서버가 카카오 로컬 검색을 대신 호출한다 (api-spec 12.1a). */
  nearby(provinceName: string, districtName?: string, facilityType?: string): Promise<NearbyCentersResponse> {
    if (USE_MOCK_API) {
      return Promise.resolve(mock.mockNearbyCenters(provinceName, districtName, facilityType));
    }
    return request("/counseling/nearby", {
      query: {
        province_name: provinceName,
        district_name: districtName,
        facility_type: facilityType,
      },
    });
  },
};
