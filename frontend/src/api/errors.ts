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
  readonly code: number | null;
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
      return "이미 처리된 요청이에요.";
    case 410:
      // Invite codes are one-shot and time-limited (api-spec 4.2).
      return "만료되었거나 이미 사용된 코드예요. 새 코드를 받아 주세요.";
    case 413:
      return "파일이 너무 커요. 다시 녹음해 주세요.";
    case 422:
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
