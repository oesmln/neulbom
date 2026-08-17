import type { AuthTokenResponse, OnboardingStep, Role } from "@/api/types";

type AuthProgress = {
  role: Role;
  onboardingStep: OnboardingStep;
  baselineCompleted: boolean;
};

export type AuthDestination = "Guardian" | "SignupInvite" | "Onboarding" | "Elder";

/**
 * Route a returning account to the first unfinished elder setup stage.
 *
 * `profile_completed` cannot be the sole signal because every basic profile
 * field is optional. Saving an empty profile still advances onboarding to
 * `intro`, which proves that the profile/consent stage was completed.
 */
export function destinationForAuth(progress: AuthProgress): AuthDestination {
  if (progress.role === "guardian") return "Guardian";
  if (progress.baselineCompleted || progress.onboardingStep === "completed") return "Elder";
  if (progress.onboardingStep === "not_started") return "SignupInvite";
  return "Onboarding";
}

export function destinationForTokens(tokens: AuthTokenResponse): AuthDestination {
  return destinationForAuth({
    role: tokens.role,
    onboardingStep: tokens.onboarding_step ?? "not_started",
    baselineCompleted: tokens.baseline_completed ?? false,
  });
}
