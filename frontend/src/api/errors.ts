import type { ApiErrorBody } from "./types";

/**
 * Every failure the app can show the user, normalised to one shape.
 *
 * The backend's `GlobalExceptionHandler` answers with
 * `{ error, code, detail, request_id }` on every non-2xx, so parsing happens
 * once here rather than at each call site. Network failures and timeouts are
 * wrapped in the same class with `status = 0` so screens only handle one type.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: number | "EMPTY_TRANSCRIPT" | null;
  readonly detail: string | null;
  readonly requestId: string | null;

  constructor(
    status: number,
    message: string,
    body?: Partial<ApiErrorBody> | null,
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = body?.code ?? null;
    this.detail = body?.detail ?? null;
    this.requestId = body?.request_id ?? null;
  }

  /** No response at all — offline, DNS failure, or the request timed out. */
  get isNetworkFailure() {
    return this.status === 0;
  }

  /** The session is gone; the caller should send the user back to login. */
  get isUnauthorized() {
    return this.status === 401;
  }

  /**
   * Elder data the guardian is not (or no longer) allowed to read. The spec
   * requires an explanation screen here rather than an empty dashboard.
   */
  get isForbidden() {
    return this.status === 403;
  }
}

/** Korean copy for the states a screen is expected to render. */
export function apiErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "알 수 없는 문제가 생겼어요. 잠시 후 다시 시도해 주세요.";
  }
  if (error.isNetworkFailure) {
    return "인터넷 연결을 확인해 주세요.";
  }
  switch (error.status) {
    case 400:
      // The server explains what was wrong with the request; show that rather
      // than a generic line, since it is usually about the value the user typed.
      return error.detail ?? "입력하신 내용을 다시 확인해 주세요.";
    case 401:
      return "로그인이 만료되었어요. 다시 로그인해 주세요.";
    case 403:
      return "이 정보를 볼 수 있는 권한이 없어요.";
    case 404:
      return "요청하신 정보를 찾을 수 없어요.";
    case 409:
      if (error.message.includes("가입된 이메일")) {
        return "이미 가입된 이메일입니다. 로그인해 주세요.";
      }
      return "이미 처리된 요청이에요.";
    case 410:
      // Invite codes are one-shot and time-limited (api-spec 4.2).
      return "만료되었거나 이미 사용된 코드예요. 새 코드를 받아 주세요.";
    case 413:
      return "파일이 너무 커요. 다시 녹음해 주세요.";
    case 422:
      if (error.code === "EMPTY_TRANSCRIPT") return error.message;
      return error.detail ?? "입력하신 내용을 처리할 수 없어요. 다시 확인해 주세요.";
    case 429:
      return "요청이 너무 많아요. 잠시 후 다시 시도해 주세요.";
    case 500:
      return "서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.";
    case 503:
      return "서버가 잠시 쉬고 있어요. 잠시 후 다시 시도해 주세요.";
    default:
      return error.detail ?? "잠시 후 다시 시도해 주세요.";
  }
}

/**
 * OAuth callbacks do not have an existing app session yet, so a 401 here
 * must not be shown as if the user's normal login session expired.
 */
export function oauthErrorMessage(error: unknown, provider: string): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      if (error.detail?.includes("검증된 이메일") || error.detail?.includes("이메일")) {
        return `${provider} 로그인에 필요한 이메일 제공 동의가 필요해요. 다시 시도해 주세요.`;
      }
      if (error.detail?.includes("authorization code") || error.detail?.includes("인증 코드")) {
        return `${provider} 인증 코드가 만료되었어요. 로그인 화면에서 다시 시도해 주세요.`;
      }
      return `${provider} 인증에 실패했어요. 로그인 화면에서 다시 시도해 주세요.`;
    }
    if (error.status === 503) {
      if (error.detail?.includes("필수 사용자 정보") || error.detail?.includes("이메일")) {
        return `${provider}에서 이메일 제공 동의가 필요해요. 동의 후 다시 시도해 주세요.`;
      }
      return `${provider} 로그인 연결이 잠시 불안정해요. 잠시 후 다시 시도해 주세요.`;
    }
  }
  return apiErrorMessage(error);
}

/** Guardian screens need to distinguish consent, link state and scope failures. */
export function guardianAccessErrorMessage(error: ApiError, resource: string): string {
  if (!error.isForbidden) return apiErrorMessage(error);
  if (error.detail?.includes("동의")) {
    return "어르신이 아직 보호자 정보 열람에 동의하지 않았어요.";
  }
  if (error.detail?.includes("활성 보호자 연결")) {
    return "보호자 연결이 해제되었거나 활성 상태가 아니에요.";
  }
  return `${resource} 열람 권한이 없어요. 연결 관리에서 허용 범위를 확인해 주세요.`;
}
