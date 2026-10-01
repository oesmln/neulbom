import type { AuthSession } from "./tokens";

const KEY = "neulbom.auth.session";

function sessionStorage(): Storage | null {
  try {
    return globalThis.sessionStorage ?? null;
  } catch {
    return null;
  }
}

export function loadWebSession(): AuthSession | null {
  const storage = sessionStorage();
  if (!storage) return null;
  try {
    const raw = storage.getItem(KEY);
    if (!raw) return null;
    const value: unknown = JSON.parse(raw);
    if (
      !value || typeof value !== "object" ||
      typeof (value as AuthSession).accessToken !== "string" ||
      typeof (value as AuthSession).refreshToken !== "string" ||
      typeof (value as AuthSession).userId !== "string" ||
      !["elder", "guardian"].includes((value as AuthSession).role)
    ) {
      storage.removeItem(KEY);
      return null;
    }
    return value as AuthSession;
  } catch {
    // A corrupt value or blocked storage must not prevent the app from loading.
    return null;
  }
}

export function saveWebSession(session: AuthSession): void {
  try {
    sessionStorage()?.setItem(KEY, JSON.stringify(session));
  } catch {
    // The current tab still uses the in-memory session when storage is blocked.
  }
}

export function clearWebSession(): void {
  try {
    sessionStorage()?.removeItem(KEY);
  } catch {
    // The in-memory copy is cleared by the caller.
  }
}
