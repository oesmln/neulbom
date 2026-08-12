import { Platform } from "react-native";
import * as SecureStore from "expo-secure-store";

import type { Role, Uuid } from "./types";

/**
 * Where the signed-in session lives between launches.
 *
 * `POST /auth/login` returns the pair plus `user_id` / `role` /
 * `profile_completed`, and every later call needs the user id in the path or
 * query, so the whole envelope is stored rather than the tokens alone.
 *
 * Tokens go to the OS keystore via `expo-secure-store` — never AsyncStorage.
 * SecureStore has no web backend, so the web bundle keeps the session in memory
 * and the user signs in again on reload; that is the safe direction to fail.
 */
export interface AuthSession {
  accessToken: string;
  refreshToken: string;
  userId: Uuid;
  role: Role;
  profileCompleted: boolean;
}

const KEY = "neulbom.auth.session";

let cached: AuthSession | null = null;

const secureStoreAvailable = Platform.OS !== "web";

export async function loadSession(): Promise<AuthSession | null> {
  if (cached) return cached;
  if (!secureStoreAvailable) return null;
  try {
    const raw = await SecureStore.getItemAsync(KEY);
    if (!raw) return null;
    cached = JSON.parse(raw) as AuthSession;
    return cached;
  } catch {
    // A corrupt or undecryptable entry is not worth crashing over — the user
    // simply signs in again.
    return null;
  }
}

export async function saveSession(session: AuthSession): Promise<void> {
  cached = session;
  if (!secureStoreAvailable) return;
  try {
    await SecureStore.setItemAsync(KEY, JSON.stringify(session));
  } catch {
    // Keep the in-memory session so the current run still works.
  }
}

export async function clearSession(): Promise<void> {
  cached = null;
  if (!secureStoreAvailable) return;
  try {
    await SecureStore.deleteItemAsync(KEY);
  } catch {
    // Nothing to recover from — the in-memory copy is already gone.
  }
}

/** Synchronous read for the request path, which cannot await on every call. */
export function currentSession(): AuthSession | null {
  return cached;
}
