/**
 * One entry point for the API layer.
 *
 * Screens import from `@/api` and get the endpoint groups (`auth`, `sessions`,
 * `reports`, …) plus every wire type. The pieces that only some call sites need
 * — the raw client, the token store, the fixtures — stay behind their own
 * modules (`@/api/client`, `@/api/tokens`, `@/api/mock`) so a screen cannot
 * reach for them by accident.
 */
export * from "./endpoints";
export * from "./types";
export { ApiError, apiErrorMessage } from "./errors";
export { API_BASE_URL, APP_TIMEZONE, USE_MOCK_API } from "./config";
