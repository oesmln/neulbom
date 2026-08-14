import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";

import { USE_MOCK_API } from "@/api/config";
import { setUnauthorizedListener } from "@/api/client";
import { clearSession, loadSession, saveSession } from "@/api/tokens";
import { auth, users } from "@/api";
import { resetToLogin } from "@/navigation/ref";
import { MOCK_ELDER_ID, MOCK_GUARDIAN_ID } from "@/api/mock";
import type { AuthTokenResponse, OnboardingStep, Uuid } from "@/api/types";
import { installRecordingQueueSync } from "@/recording/recordingQueue";

export type UserRole = "elder" | "guardian" | null;

type AppState = {
  /** True once a stored session has been read; screens wait on this. */
  ready: boolean;

  role: UserRole;
  setRole: (r: UserRole) => void;
  profileCompleted: boolean;
  onboardingStep: OnboardingStep;
  onboardingCompleted: boolean;
  baselineCompleted: boolean;
  characterName: string | null;

  /** Signed-in user. Every backend call needs it in the path or query. */
  userId: Uuid | null;
  userName: string;
  setUserName: (n: string) => void;

  /**
   * Which elder a guardian is currently looking at. The dashboard picks it from
   * `GET /guardian/{guardian_id}/elders`; the chart and diary tabs read it.
   */
  selectedElderId: Uuid | null;
  setSelectedElderId: (id: Uuid | null) => void;

  /** Stores tokens and switches the app into its signed-in state. */
  signIn: (tokens: AuthTokenResponse) => Promise<void>;
  updateOnboardingState: (state: {
    step?: OnboardingStep;
    completed?: boolean;
    baselineCompleted?: boolean;
    characterName?: string | null;
  }) => Promise<void>;
  completeOnboarding: () => Promise<void>;
  completeBaseline: () => Promise<void>;
  signOut: () => Promise<void>;
};

const AppContext = createContext<AppState | undefined>(undefined);

/** In mock mode the two demo users stand in for what a real login would return. */
function mockIdFor(role: UserRole): Uuid | null {
  if (!USE_MOCK_API) return null;
  if (role === "guardian") return MOCK_GUARDIAN_ID;
  if (role === "elder") return MOCK_ELDER_ID;
  return null;
}

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = useState(false);
  const [role, setRoleState] = useState<UserRole>(null);
  const [profileCompleted, setProfileCompleted] = useState(false);
  const [onboardingStep, setOnboardingStep] = useState<OnboardingStep>("not_started");
  const [onboardingCompleted, setOnboardingCompleted] = useState(false);
  const [baselineCompleted, setBaselineCompleted] = useState(false);
  const [characterName, setCharacterName] = useState<string | null>(null);
  const [userId, setUserId] = useState<Uuid | null>(null);
  const [userName, setUserName] = useState<string>("");
  const [selectedElderId, setSelectedElderId] = useState<Uuid | null>(null);

  // Restore a previous session so a returning user does not sign in again.
  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const stored = await loadSession();
      if (cancelled) return;
      if (stored) {
        setRoleState(stored.role);
        setUserId(stored.userId);
        setProfileCompleted(stored.profileCompleted);
        setOnboardingStep(stored.onboardingStep ?? "not_started");
        setOnboardingCompleted(stored.onboardingCompleted ?? false);
        setBaselineCompleted(stored.baselineCompleted ?? false);
        setCharacterName(stored.characterName ?? null);
      }
      setReady(true);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const signOut = useCallback(async () => {
    const stored = await loadSession();
    if (stored?.refreshToken) {
      // Best effort: a failed logout must not trap the user in the app.
      await auth.logout(stored.refreshToken).catch(() => undefined);
    }
    await clearSession();
    setRoleState(null);
    setProfileCompleted(false);
    setOnboardingStep("not_started");
    setOnboardingCompleted(false);
    setBaselineCompleted(false);
    setCharacterName(null);
    setUserId(null);
    setUserName("");
    setSelectedElderId(null);
  }, []);

  // A refresh that cannot be recovered ends the session here rather than
  // leaving screens to each discover the 401 on their own. The user is sent
  // back to sign-in immediately — staying on a screen that can no longer load
  // anything would only show an error on every card.
  useEffect(() => {
    setUnauthorizedListener(() => {
      setRoleState(null);
      setProfileCompleted(false);
      setOnboardingStep("not_started");
      setOnboardingCompleted(false);
      setBaselineCompleted(false);
      setCharacterName(null);
      setUserId(null);
      setUserName("");
      setSelectedElderId(null);
      resetToLogin();
    });
    return () => setUnauthorizedListener(null);
  }, []);

  // The display name lives on `GET /users/{user_id}`, not on the token, so it is
  // fetched once per session instead of at every screen that greets the user.
  useEffect(() => {
    if (!userId || !role) return;
    let cancelled = false;
    void users
      .profile(userId, role)
      .then((profile) => {
        if (!cancelled) setUserName(profile.name);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [userId, role]);

  // A restored session owns its pending audio queue. The installer also
  // retries when connectivity returns or the app becomes active again, and
  // removes stale files that belong to a different signed-in account.
  useEffect(() => {
    if (!userId || !role || USE_MOCK_API) return;
    return installRecordingQueueSync(userId);
  }, [role, userId]);

  const signIn = useCallback(async (tokens: AuthTokenResponse) => {
    await saveSession({
      accessToken: tokens.access_token,
      refreshToken: tokens.refresh_token,
      userId: tokens.user_id,
      role: tokens.role,
      profileCompleted: tokens.profile_completed,
      onboardingStep: tokens.onboarding_step ?? "not_started",
      onboardingCompleted: tokens.onboarding_completed ?? false,
      baselineCompleted: tokens.baseline_completed ?? false,
      characterName: tokens.character_name ?? null,
    });
    setRoleState(tokens.role);
    setUserId(tokens.user_id);
    setProfileCompleted(tokens.profile_completed);
    setOnboardingStep(tokens.onboarding_step ?? "not_started");
    setOnboardingCompleted(tokens.onboarding_completed ?? false);
    setBaselineCompleted(tokens.baseline_completed ?? false);
    setCharacterName(tokens.character_name ?? null);
  }, []);

  const updateOnboardingState = useCallback(async (state: {
    step?: OnboardingStep;
    completed?: boolean;
    baselineCompleted?: boolean;
    characterName?: string | null;
  }) => {
    const stored = await loadSession();
    const nextStep = state.step ?? onboardingStep;
    const nextCompleted = state.completed ?? onboardingCompleted;
    const nextBaseline = state.baselineCompleted ?? baselineCompleted;
    const nextCharacter = state.characterName === undefined ? characterName : state.characterName;
    if (stored) {
      await saveSession({
        ...stored,
        onboardingStep: nextStep,
        onboardingCompleted: nextCompleted,
        baselineCompleted: nextBaseline,
        characterName: nextCharacter,
      });
    }
    setOnboardingStep(nextStep);
    setOnboardingCompleted(nextCompleted);
    setBaselineCompleted(nextBaseline);
    setCharacterName(nextCharacter);
  }, [baselineCompleted, characterName, onboardingCompleted, onboardingStep]);

  const completeOnboarding = useCallback(async () => {
    const stored = await loadSession();
    if (stored) {
      await saveSession({
        ...stored,
        profileCompleted: true,
        onboardingStep: "completed",
        onboardingCompleted: true,
      });
    }
    setProfileCompleted(true);
    setOnboardingStep("completed");
    setOnboardingCompleted(true);
  }, []);

  const completeBaseline = useCallback(async () => {
    await updateOnboardingState({
      step: "completed",
      completed: true,
      baselineCompleted: true,
    });
  }, [updateOnboardingState]);

  const setRole = useCallback((next: UserRole) => {
    setRoleState(next);
    // Without a server the role choice is also what decides which demo user the
    // screens read; with one, the role already came from the token.
    const mockId = mockIdFor(next);
    if (mockId) setUserId(mockId);
  }, []);

  const value = useMemo(
    () => ({
      ready,
      role,
      setRole,
      profileCompleted,
      onboardingStep,
      onboardingCompleted,
      baselineCompleted,
      characterName,
      userId,
      userName,
      setUserName,
      selectedElderId,
      setSelectedElderId,
      signIn,
      updateOnboardingState,
      completeOnboarding,
      completeBaseline,
      signOut,
    }),
    [
      ready,
      role,
      setRole,
      profileCompleted,
      onboardingStep,
      onboardingCompleted,
      baselineCompleted,
      characterName,
      userId,
      userName,
      selectedElderId,
      signIn,
      updateOnboardingState,
      completeOnboarding,
      completeBaseline,
      signOut,
    ],
  );

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp(): AppState {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error("useApp must be used within AppProvider");
  return ctx;
}
