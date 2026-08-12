/**
 * Where the app talks to the backend.
 *
 * The Spring app mounts every controller under `/api/v1` (see the
 * `@RequestMapping` on each controller), so the base URL is host + `/api/v1`.
 * Set `EXPO_PUBLIC_API_BASE_URL` to the host only — the version prefix is added
 * here so no call site has to remember it.
 *
 *   EXPO_PUBLIC_API_BASE_URL=http://10.0.2.2:8080   # Android emulator → host
 *   EXPO_PUBLIC_API_BASE_URL=http://192.168.0.10:8080  # real device on LAN
 *
 * With nothing set the app runs on the bundled fixtures in `apiMock.ts`. Those
 * fixtures are shaped exactly like the backend responses, so screens read the
 * same fields either way and wiring a real server changes no screen code.
 */

const RAW_HOST = process.env.EXPO_PUBLIC_API_BASE_URL?.trim() ?? "";

/** Host without a trailing slash, or "" when the app runs on fixtures. */
export const API_HOST = RAW_HOST.replace(/\/+$/, "");

/** Full base URL including the version prefix, or "" in mock mode. */
export const API_BASE_URL = API_HOST ? `${API_HOST}/api/v1` : "";

/** True while no server is configured — every endpoint answers from fixtures. */
export const USE_MOCK_API = API_BASE_URL === "";

/** Request timeout in ms. Uploads get their own, longer budget. */
export const REQUEST_TIMEOUT_MS = 15_000;
export const UPLOAD_TIMEOUT_MS = 60_000;

/** Server default; the backend reads this timezone for daily aggregation. */
export const APP_TIMEZONE = "Asia/Seoul";
