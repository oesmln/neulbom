/**
 * Wire types mirroring the backend records under
 * `backend/src/main/java/com/neulbom/backend/**\/api/`.
 *
 * Every response record carries `@JsonNaming(SnakeCaseStrategy)` (or explicit
 * `@JsonProperty`), so the field names below are snake_case on purpose — they
 * are the JSON as it arrives, not a camelCase adaptation. Keeping them verbatim
 * means a mismatch shows up as a type error instead of an undefined at runtime.
 *
 * Java → TypeScript: `UUID` and `Instant` are strings (ISO-8601 for Instant),
 * `LocalDate` is a "YYYY-MM-DD" string, `BigDecimal` is a number, and
 * `JsonNode` is left as `unknown` because the backend does not fix its shape.
 */

export type Uuid = string;
/** ISO-8601 instant, e.g. "2026-08-12T09:12:00Z". */
export type IsoInstant = string;
/** Calendar date, e.g. "2026-08-12". */
export type IsoDate = string;

export type Role = "elder" | "guardian";

/* ── common ─────────────────────────────────────────────────────────────── */

/** `common/api/ApiErrorResponse` */
export interface ApiErrorBody {
  error: string;
  code: number;
  detail: string | null;
  request_id: string | null;
}

/* ── auth ───────────────────────────────────────────────────────────────── */

export interface RegisterRequest {
  email: string;
  password: string;
  name: string;
  role: Role;
  birth_date?: IsoDate;
  age_group?: "60s" | "70s" | "80s_plus" | "unknown";
  gender?: "male" | "female" | "other" | "unknown";
  phone?: string;
}

export interface RegisterResponse {
  user_id: Uuid;
  role: Role;
  profile_completed: boolean;
  email_verified: boolean;
  created_at: IsoInstant;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface OAuthLoginRequest {
  authorization_code: string;
  redirect_uri: string;
  role?: Role;
  state?: string;
}

export interface AuthTokenResponse {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  user_id: Uuid;
  role: Role;
  profile_completed: boolean;
  email_verified: boolean;
  is_new_user: boolean;
  onboarding_step: OnboardingStep;
  onboarding_completed: boolean;
  baseline_completed: boolean;
  character_name: string | null;
}

export interface PasswordResetRequestResponse {
  request_id: Uuid;
  expires_at: IsoInstant;
}

export interface EmailVerificationRequestResponse {
  request_id: Uuid;
  expires_at: IsoInstant;
}

export interface PasswordChangeRequest {
  current_password: string;
  new_password: string;
  logout_other_sessions?: boolean;
}

/* ── users ──────────────────────────────────────────────────────────────── */

export interface UserProfileResponse {
  user_id: Uuid;
  name: string;
  role: Role;
  birth_date: IsoDate | null;
  age_group: string | null;
  gender: string | null;
  phone: string | null;
  education_years: number | null;
  literacy: boolean | null;
  health_conditions: string[] | null;
  alcohol_use: string | null;
  smoking_status: string | null;
  hearing_status: string | null;
  communication_difficulty: boolean | null;
  smartphone_skill: string | null;
  profile_completed: boolean;
  onboarding_step: OnboardingStep;
  onboarding_completed: boolean;
  baseline_completed: boolean;
  character_name: string | null;
  created_at: IsoInstant;
  updated_at: IsoInstant;
}

export interface UserProfileUpdateRequest {
  name?: string;
  phone?: string;
  birth_date?: IsoDate;
  age_group?: string;
  gender?: string;
  education_years?: number;
  literacy?: boolean;
  health_conditions?: string[];
  alcohol_use?: string;
  smoking_status?: string;
  hearing_status?: string;
  communication_difficulty?: boolean;
  smartphone_skill?: string;
  onboarding_step?: OnboardingStep;
  onboarding_completed?: boolean;
  baseline_completed?: boolean;
  character_name?: string;
}

export interface UserProfileUpdateResponse {
  user_id: Uuid;
  profile_completed: boolean;
  onboarding_step: OnboardingStep;
  onboarding_completed: boolean;
  baseline_completed: boolean;
  character_name: string | null;
  updated_at: IsoInstant;
}

export interface UserPreferenceResponse {
  preferred_hearing_side: string | null;
  voice_profile_id: string | null;
  speech_rate: number | null;
  subtitle_enabled: boolean;
  sound_effect_enabled: boolean;
  push_notification_enabled: boolean;
  guardian_reaction_notification_enabled: boolean;
  screening_notification_enabled: boolean;
  diary_notification_enabled: boolean;
  weekly_report_notification_enabled: boolean;
  updated_at: IsoInstant;
}

export type UserPreferenceUpdateRequest = Partial<
  Omit<UserPreferenceResponse, "updated_at">
>;

export interface VoiceProfileResponse {
  voice_profile_id: string;
  name: string;
  pitch_band: string | null;
  clarity: string | null;
  preview_audio_url: string | null;
  recommended_for_elder: boolean;
}

export interface VoiceProfilesResponse {
  voice_profiles: VoiceProfileResponse[];
}

export type ConsentType =
  | "terms_of_service"
  | "privacy_collection"
  | "sensitive_health"
  | "report_sharing"
  | "data_sharing"
  | "guardian_access"
  | "analysis"
  | "voice_collection"
  | "research_use";

export interface ConsentRequest {
  consent_type: ConsentType;
  agreed: boolean;
  agreed_at: IsoInstant;
  version: string;
}

export interface ConsentResponse {
  consent_id: Uuid;
  consent_type: ConsentType;
  agreed: boolean;
  agreed_at: IsoInstant;
  version: string;
  created_at: IsoInstant;
}

export interface ConsentsResponse {
  consents: ConsentResponse[];
}

/* ── sessions & questions ───────────────────────────────────────────────── */

export type OnboardingStep =
  | "not_started"
  | "intro"
  | "character_name"
  | "consent"
  | "baseline"
  | "completed";

export type SessionType = "cist" | "baseline" | "onboarding" | "emotional_qa" | "game" | "mixed";

export interface SessionStartRequest {
  user_id: Uuid;
  session_type?: SessionType;
  voice_profile_id?: string;
  preferred_hearing_side?: string;
  subtitle_enabled?: boolean;
  offline_mode?: boolean;
}

export interface SessionSettings {
  voice_profile_id: string | null;
  preferred_hearing_side: string | null;
  speech_rate: number | null;
  subtitle_enabled: boolean;
  sound_effect_enabled: boolean;
}

export interface SessionResponse {
  session_id: Uuid;
  user_id: Uuid;
  session_type: string;
  status: string;
  current_question_order: number;
  answered_count: number;
  total_questions: number;
  recording_sync_status: string | null;
  settings: SessionSettings | null;
  started_at: IsoInstant;
  ended_at: IsoInstant | null;
}

export interface SessionEndResponse {
  session_id: Uuid;
  status: string;
  ended_at: IsoInstant;
  answered_count: number;
  analysis_status: string | null;
  result_status: string | null;
  result_type: string | null;
  display_label: string | null;
  message: string | null;
  recommendation: string | null;
  xp_earned: number;
  character_level: number | null;
  level_up: boolean;
}

export interface SessionListItem {
  session_id: Uuid;
  session_type: string;
  status: string;
  started_at: IsoInstant;
  ended_at: IsoInstant | null;
}

export interface SessionsResponse {
  sessions: SessionListItem[];
  total: number;
  page: number;
  limit: number;
}

export interface QuestionResponse {
  question_id: Uuid;
  content: string;
  type: string;
  order: number;
  hint: string | null;
  subtitle_available: boolean;
}

export interface QuestionsResponse {
  questions: QuestionResponse[];
}

export interface AnswerRequest {
  client_answer_id: Uuid;
  question_id: Uuid;
  answer_text?: string;
  recording_id?: Uuid;
  transcript_id?: Uuid;
  response_time_ms?: number;
  answered_at: IsoInstant;
}

export interface AnswerResponse {
  answer_id: Uuid;
  question_id: Uuid;
  saved: boolean;
  next_question_order: number;
  sync_status: string;
}

export interface SessionAnswerItem {
  answer_id: Uuid;
  question_id: Uuid;
  question_order: number;
  question_text: string;
  answer_text: string | null;
  recording_id: Uuid | null;
  answered_at: IsoInstant;
}

export interface SessionAnswersResponse {
  session_id: Uuid;
  session_type: string;
  answers: SessionAnswerItem[];
}

/* ── recordings ─────────────────────────────────────────────────────────── */

export type RecordingPurpose = "answer" | "diary";

export interface RecordingUploadResponse {
  recording_id: Uuid;
  client_recording_id: Uuid;
  purpose: string;
  sync_status: string;
  transcript_status: string | null;
  analysis_status: string | null;
  deduplicated: boolean;
}

export interface RecordingStatusResponse {
  recording_id: Uuid;
  client_recording_id: Uuid;
  purpose: string;
  session_id: Uuid | null;
  question_id: Uuid | null;
  sync_status: string;
  transcript_status: string | null;
  transcript_id: Uuid | null;
  acoustic_analysis_id: Uuid | null;
  cognitive_analysis_id: Uuid | null;
  error_message: string | null;
  updated_at: IsoInstant;
}

/* ── dashboard & screening result ───────────────────────────────────────── */

/** `stable | observe | attention_required` — the server decides, never the app. */
export type CognitiveStatus = "stable" | "observe" | "attention_required";
export type AnalysisStatus = "pending" | "processing" | "completed" | "failed";
export type ScreeningResultType =
  | "positive_feedback"
  | "follow_up_recommended"
  | "insufficient_data";

export interface DashboardCharacterSummary {
  level: number;
  display_name: string;
  stage: string;
  xp_current: number;
  xp_goal: number;
  xp_remaining: number;
  skin_id: string | null;
}

export interface DashboardScreeningSummary {
  session_id: Uuid;
  result_status: AnalysisStatus;
  result_type: ScreeningResultType | null;
  display_label: string | null;
  message: string | null;
  recommendation: string | null;
  completed_at: IsoInstant | null;
}

export interface DashboardSummary {
  summary_id: Uuid;
  session_id: Uuid;
  summary: string;
  created_at: IsoInstant;
}

export interface DashboardTask {
  task_type: string;
  status: string;
  title: string;
  description: string | null;
  target_route: string | null;
}

export interface DashboardActivitySummary {
  year_month: string;
  emotional_qa_completed_count: number;
  game_completed_count: number;
  attendance_days: number;
  current_attendance_streak_days: number;
}

export interface DashboardDiarySummary {
  target_date: IsoDate;
  generation_status: string;
  diary_id: Uuid | null;
  display_label: string | null;
  message: string | null;
  available_at: IsoInstant | null;
}

export interface DashboardCognitiveActivity {
  status: CognitiveStatus;
  display_label: string;
  title: string;
  message: string;
  reference_date: IsoDate | null;
}

export interface DashboardNotificationSummary {
  notification_id: Uuid;
  title: string;
  body: string;
  type: string;
  severity: string | null;
  status_label: string | null;
  created_at: IsoInstant;
}

export interface DashboardResponse {
  user_id: Uuid;
  role: Role;
  character: DashboardCharacterSummary | null;
  latest_screening: DashboardScreeningSummary | null;
  latest_summary: DashboardSummary | null;
  today_tasks: DashboardTask[];
  conversation_streak_days: number;
  monthly_activity: DashboardActivitySummary | null;
  latest_diary: DashboardDiarySummary | null;
  cognitive_activity: DashboardCognitiveActivity | null;
  unread_notification_count: number;
  recent_alerts: DashboardNotificationSummary[];
}

/**
 * `report/api/ScreeningResultResponse` — annotated `@JsonInclude(NON_NULL)`, so
 * the score fields are simply absent from an elder-audience response rather than
 * null. That is the whole point of `audience`: never read a score on an elder
 * screen, it will not be there.
 */
export interface ScreeningResultResponse {
  audience: "elder" | "guardian";
  session_id: Uuid;
  user_id: Uuid;
  session_type: string;
  result_status: AnalysisStatus;
  result_type?: ScreeningResultType;
  display_label?: string;
  message?: string;
  recommendation?: string;
  screening_reference_score?: number;
  display_score?: number;
  score_max?: number;
  score_rate?: number;
  risk_level?: string;
  screening_label?: string;
  domain_scores?: unknown;
  completed_at?: IsoInstant;
  summary_id?: Uuid;
}

/* ── guardian report & history ──────────────────────────────────────────── */

export interface GuardianReportActivitySummary {
  session_count: number;
  game_count: number;
  diary_count: number;
}

export interface GuardianReportTrendPoint {
  date: IsoDate;
  screening_reference_score: number | null;
  display_score: number | null;
  score_max: number | null;
  score_rate: number | null;
  score_delta: number | null;
  risk_level: string | null;
}

export interface GuardianReportAlert {
  notification_id: Uuid;
  title: string;
  body: string;
  severity: string | null;
  created_at: IsoInstant;
}

export interface GuardianReportConversationResult {
  session_id: Uuid;
  session_type: string;
  result_type: string | null;
  display_label: string | null;
  screening_reference_score: number | null;
  domain_scores: unknown;
}

export interface GuardianReportDaily {
  local_date: IsoDate;
  timezone: string;
  session_count: number;
  analyzed_session_count: number;
  analysis_status: string;
  diary_id: Uuid | null;
  conversation_results: GuardianReportConversationResult[];
}

export interface GuardianReportResponse {
  elder_id: Uuid;
  elder_name: string;
  latest_summary: string | null;
  latest_screening_score: number | null;
  latest_display_score: number | null;
  latest_score_max: number | null;
  latest_score_rate: number | null;
  latest_risk_level: string | null;
  vocabulary_score: number | null;
  game_cognitive_index: number | null;
  alert_level: string | null;
  trend_30d: string | null;
  last_session_at: IsoInstant | null;
  activity_summary7d: GuardianReportActivitySummary | null;
  trend_points: GuardianReportTrendPoint[];
  recent_alerts: GuardianReportAlert[];
  daily_summary: GuardianReportDaily | null;
}

export interface HistoryRecordResponse {
  analysis_id: Uuid;
  session_id?: Uuid;
  screening_reference_score?: number;
  display_score?: number;
  score_max?: number;
  score_rate?: number;
  label?: string;
  risk_level?: string;
  domain_scores?: unknown;
  trend?: string;
  average_score30d?: number;
  score_delta?: number;
  analyzed_at?: IsoInstant;
}

export interface HistoryResponse {
  records: HistoryRecordResponse[];
  total: number;
  aggregation: string | null;
  sample_sufficient: boolean;
}

export interface ReportExportResponse {
  export_id: Uuid;
  status: string;
  download_url: string | null;
  expires_at: IsoInstant | null;
  failure_reason: string | null;
}

/* ── diaries & calendar ─────────────────────────────────────────────────── */

export interface ReactionResponse {
  reaction_id: Uuid;
  diary_id: Uuid;
  reactor_id: Uuid;
  reactor_name: string | null;
  reaction_type: string;
  message: string | null;
  created_at: IsoInstant;
}

export interface ReactionsResponse {
  reactions: ReactionResponse[];
}

export interface DiaryListItem {
  diary_id: Uuid;
  title: string | null;
  preview: string | null;
  source_type: string;
  mood: string | null;
  mood_level: number | null;
  written_at: IsoInstant;
  reaction_count: number;
}

export interface DiariesResponse {
  diaries: DiaryListItem[];
  total: number;
  page: number;
  limit: number;
}

export interface DiaryDetailResponse {
  diary_id: Uuid;
  user_id: Uuid;
  source_type: string;
  title: string | null;
  content: string;
  session_id: Uuid | null;
  daily_summary_id: Uuid | null;
  mood: string | null;
  mood_level: number | null;
  written_at: IsoInstant;
  created_at: IsoInstant;
  updated_at: IsoInstant;
  reactions: ReactionResponse[];
}

export interface DiaryCreateRequest {
  user_id: Uuid;
  source_type: string;
  title?: string;
  content: string;
  recording_id?: Uuid;
  session_id?: Uuid;
  mood?: string;
  mood_level?: number;
  written_at: IsoInstant;
}

export interface GenerationStatusResponse {
  generation_job_id: Uuid | null;
  target_date: IsoDate;
  status: "scheduled" | "processing" | "completed" | "failed" | "conversation_incomplete";
  scheduled_at: IsoInstant | null;
  available_at: IsoInstant | null;
  diary_id: Uuid | null;
  failure_reason: string | null;
  retryable: boolean;
  display_label: string | null;
  message: string | null;
}

export interface CalendarActivity {
  activity_id: Uuid;
  activity_type: string;
  reference_id: Uuid | null;
  activity_date: IsoDate;
  title: string | null;
  status: string | null;
  metadata: unknown;
}

export interface CalendarActivitiesResponse {
  activities: CalendarActivity[];
}

/* ── game & character ───────────────────────────────────────────────────── */

export type GameType = "image_match" | "consonant" | "word_match" | "color_match";

export interface CharacterResponse {
  user_id: Uuid;
  display_name: string;
  level: number;
  stage: string;
  stage_index: number;
  stage_count: number;
  xp_current: number;
  xp_goal: number;
  xp_remaining: number;
  skin_id: string | null;
  unlocked: string[];
}

export interface GameResultRequest {
  user_id: Uuid;
  session_id: Uuid;
  client_game_result_id: Uuid;
  game_type: GameType;
  score: number;
  response_times: number[];
  error_count: number;
  total_questions: number;
  matched_pairs?: number;
  attempt_count?: number;
  duration_sec: number;
  restarted_count?: number;
  completed: boolean;
}

export interface GameResultResponse {
  game_result_id: Uuid;
  cognitive_index: number | null;
  xp_earned: number;
  character_level: number;
  level_up: boolean;
  deduplicated: boolean;
}

export interface GameHistoryItem {
  game_result_id: Uuid;
  game_type: string;
  score: number;
  matched_pairs: number | null;
  attempt_count: number | null;
  duration_sec: number;
  restarted_count: number;
  completed: boolean;
  cognitive_index: number | null;
  xp_earned: number;
  played_at: IsoInstant;
}

export interface GameHistoryResponse {
  records: GameHistoryItem[];
  total: number;
  limit: number;
}

export interface XpHistoryItem {
  xp_ledger_id: Uuid;
  reason: string;
  display_title: string | null;
  amount: number;
  event_id: string | null;
  earned_at: IsoInstant;
}

export interface XpHistoryResponse {
  records: XpHistoryItem[];
  next_cursor: string | null;
}

/* ── notifications ──────────────────────────────────────────────────────── */

export interface NotificationResponse {
  notification_id: Uuid;
  title: string;
  body: string;
  type: string;
  severity: string | null;
  status_label: string | null;
  data: unknown;
  is_read: boolean;
  read_at: IsoInstant | null;
  created_at: IsoInstant;
}

export interface NotificationsResponse {
  notifications: NotificationResponse[];
  unread_count: number;
}

export interface NotificationReadResponse {
  notification_id: Uuid;
  is_read: boolean;
  read_at: IsoInstant;
}

export interface NotificationsReadAllResponse {
  updated_count: number;
  read_at: IsoInstant;
}

/* ── guardian link & invitations ────────────────────────────────────────── */

export interface InvitationVerifyResponse {
  invitation_id: Uuid;
  status: string;
  relation: string | null;
  access_scope: string[] | null;
  expires_at: IsoInstant;
  requires_consent: boolean;
}

export interface InvitationCreateResponse {
  invitation_id: Uuid;
  invite_code: string;
  status: string;
  relation: string | null;
  access_scope: string[] | null;
  expires_at: IsoInstant;
}

export interface InvitationCreateRequest {
  relation?: string;
  access_scope?: string[];
  expires_in?: number;
}

export interface GuardianLinkUpdateRequest {
  status?: "active" | "revoked";
  access_scope?: string[];
  relation?: string;
}

export interface GuardianLinkResponse {
  invitation_id: Uuid | null;
  link_id: Uuid | null;
  elder_id: Uuid;
  guardian_id: Uuid | null;
  status: string;
  access_scope: string[] | null;
  consent_required: boolean;
  created_at: IsoInstant;
  updated_at: IsoInstant;
}

export interface ElderSummaryResponse {
  elder_id: Uuid;
  elder_name: string;
  link_id: Uuid | null;
  status: string;
  access_scope: string[] | null;
  consent_status: string | null;
  latest_display_score: number | null;
  latest_score_max: number | null;
  latest_score_rate: number | null;
  latest_risk_level: string | null;
  last_session_at: IsoInstant | null;
}

export interface EldersResponse {
  elders: ElderSummaryResponse[];
}

/* ── counseling ─────────────────────────────────────────────────────────── */

export interface CounselingCenterResponse {
  center_id: Uuid;
  name: string;
  facility_type: string | null;
  province_code: string | null;
  district_code: string | null;
  province_name: string | null;
  district_name: string | null;
  address: string | null;
  latitude: number | null;
  longitude: number | null;
  phone: string | null;
  naver_map_url: string | null;
  homepage_url: string | null;
  reservation_mode: string | null;
  source_name: string | null;
  source_updated_at: IsoInstant | null;
}

export interface CounselingCentersResponse {
  centers: CounselingCenterResponse[];
  total: number;
}
