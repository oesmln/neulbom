import {
  API_BASE_URL,
  REQUEST_TIMEOUT_MS,
  UPLOAD_TIMEOUT_MS,
} from "./config";
import { ApiError } from "./errors";
import {
  clearSession,
  currentSession,
  loadSession,
  saveSession,
} from "./tokens";
import type { ApiErrorBody, AuthTokenResponse } from "./types";

/**
 * The one place that speaks HTTP.
 *
 * Responsibilities, in the order they matter:
 *   1. prefix `/api/v1` (from apiConfig) and drop undefined query params
 *   2. attach `Authorization: Bearer …` from the stored session
 *   3. turn any non-2xx into an `ApiError` carrying the server's error body
 *   4. on 401, refresh once and replay — with every concurrent caller queued
 *      behind the single refresh so a burst of requests cannot spend the
 *      refresh token several times over
 */

export type QueryValue = string | number | boolean | undefined | null;

interface RequestOptions {
  method?: "GET" | "POST" | "PATCH" | "PUT" | "DELETE";
  query?: Record<string, QueryValue>;
  body?: unknown;
  /** Skip the Authorization header — login, register, refresh. */
  anonymous?: boolean;
  timeoutMs?: number;
  /** Idempotency-Key header, required by the recording and answer endpoints. */
  idempotencyKey?: string;
}

type UnauthorizedListener = () => void;

let unauthorizedListener: UnauthorizedListener | null = null;

/**
 * Called when refreshing fails and the session is unrecoverable. AppContext
 * registers here to drop the user back on the login screen.
 */
export function setUnauthorizedListener(listener: UnauthorizedListener | null) {
  unauthorizedListener = listener;
}

function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const url = `${API_BASE_URL}${path}`;
  if (!query) return url;
  const parts: string[] = [];
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null) continue;
    parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
  }
  return parts.length > 0 ? `${url}?${parts.join("&")}` : url;
}

async function parseErrorBody(response: Response): Promise<Partial<ApiErrorBody> | null> {
  try {
    return (await response.json()) as Partial<ApiErrorBody>;
  } catch {
    return null;
  }
}

async function send(
  path: string,
  options: RequestOptions,
  accessToken: string | null,
): Promise<Response> {
  const controller = new AbortController();
  const timeout = setTimeout(
    () => controller.abort(),
    options.timeoutMs ?? REQUEST_TIMEOUT_MS,
  );

  const headers: Record<string, string> = { Accept: "application/json" };
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  if (options.idempotencyKey) headers["Idempotency-Key"] = options.idempotencyKey;

  try {
    return await fetch(buildUrl(path, options.query), {
      method: options.method ?? "GET",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: controller.signal,
    });
  } catch (cause) {
    const aborted = cause instanceof Error && cause.name === "AbortError";
    throw new ApiError(
      0,
      aborted ? "요청 시간이 초과되었습니다." : "서버에 연결하지 못했습니다.",
    );
  } finally {
    clearTimeout(timeout);
  }
}

/** In-flight refresh, shared by every caller that hits a 401 at the same time. */
let refreshInFlight: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  if (refreshInFlight) return refreshInFlight;

  refreshInFlight = (async () => {
    const session = currentSession() ?? (await loadSession());
    if (!session) return null;

    let response: Response;
    try {
      response = await send(
        "/auth/refresh",
        { method: "POST", body: { refresh_token: session.refreshToken }, anonymous: true },
        null,
      );
    } catch {
      // A network blip must not sign the user out; the caller surfaces it.
      return null;
    }

    if (!response.ok) {
      await clearSession();
      unauthorizedListener?.();
      return null;
    }

    const tokens = (await response.json()) as AuthTokenResponse;
    await saveSession({
      accessToken: tokens.access_token,
      refreshToken: tokens.refresh_token,
      userId: tokens.user_id,
      role: tokens.role,
      profileCompleted: tokens.profile_completed,
    });
    return tokens.access_token;
  })();

  try {
    return await refreshInFlight;
  } finally {
    refreshInFlight = null;
  }
}

async function readJson<T>(response: Response): Promise<T> {
  // 204 from logout, diary delete, password reset confirm.
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const session = options.anonymous ? null : currentSession() ?? (await loadSession());
  let response = await send(path, options, session?.accessToken ?? null);

  if (response.status === 401 && !options.anonymous) {
    const renewed = await refreshAccessToken();
    if (renewed) {
      response = await send(path, options, renewed);
    }
  }

  if (!response.ok) {
    const body = await parseErrorBody(response);
    throw new ApiError(
      response.status,
      body?.error ?? `HTTP ${response.status}`,
      body,
    );
  }

  return readJson<T>(response);
}

/**
 * `POST /recordings` is the only multipart endpoint: the audio arrives as a
 * `audio_file` part while every other value rides in the query string, exactly
 * as `RecordingController` declares it (`@RequestPart` + `@RequestParam`).
 */
export async function uploadMultipart<T>(
  path: string,
  form: FormData,
  query: Record<string, QueryValue>,
  idempotencyKey?: string,
): Promise<T> {
  const session = currentSession() ?? (await loadSession());
  const sendMultipart = async (accessToken: string | null) => {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), UPLOAD_TIMEOUT_MS);
    const headers: Record<string, string> = { Accept: "application/json" };
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
    if (idempotencyKey) headers["Idempotency-Key"] = idempotencyKey;
    // Content-Type is deliberately unset: the runtime has to add the multipart
    // boundary itself, and setting it by hand produces a body the server cannot
    // parse.
    try {
      return await fetch(buildUrl(path, query), {
        method: "POST",
        headers,
        body: form,
        signal: controller.signal,
      });
    } catch (cause) {
      const aborted = cause instanceof Error && cause.name === "AbortError";
      throw new ApiError(
        0,
        aborted ? "업로드 시간이 초과되었습니다." : "업로드에 실패했습니다.",
      );
    } finally {
      clearTimeout(timeout);
    }
  };

  let response = await sendMultipart(session?.accessToken ?? null);
  if (response.status === 401) {
    const renewed = await refreshAccessToken();
    if (renewed) response = await sendMultipart(renewed);
  }

  if (!response.ok) {
    const body = await parseErrorBody(response);
    throw new ApiError(response.status, body?.error ?? `HTTP ${response.status}`, body);
  }
  return readJson<T>(response);
}
