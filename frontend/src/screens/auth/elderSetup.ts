import { ApiError, guardian, users } from "@/api";
import type { ConsentType } from "@/api/types";
import type { PendingElderSetup } from "@/navigation/types";

const CONSENT_VERSION = "elder-profile-v1";

async function saveConsent(userId: string, consentType: ConsentType, agreedAt: string) {
  try {
    await users.saveConsent(userId, {
      consent_type: consentType,
      agreed: true,
      agreed_at: agreedAt,
      version: CONSENT_VERSION,
    });
  } catch (cause) {
    // Retrying the same document version after an interrupted request is safe.
    if (!(cause instanceof ApiError) || cause.status !== 409) throw cause;
  }
}

export async function persistElderSetup(
  userId: string,
  setup: PendingElderSetup,
  inviteCode?: string,
) {
  if (Object.keys(setup.profile).length > 0) {
    await users.updateProfile(userId, setup.profile);
  }
  if (Object.keys(setup.preferences).length > 0) {
    await users.updatePreferences(userId, setup.preferences);
  }

  const agreedAt = new Date().toISOString();
  for (const consentType of setup.consents) {
    await saveConsent(userId, consentType, agreedAt);
  }

  if (inviteCode && setup.guardianConsentAccepted) {
    await saveConsent(userId, "guardian_access", agreedAt);
    await saveConsent(userId, "report_sharing", agreedAt);
    await guardian.acceptInvitation(inviteCode, true);
  }

  // Profile fields are optional in the UI. Mark the profile/setup handoff only
  // after all required consents and invitation sharing have been persisted so
  // a later login resumes at onboarding instead of asking for consent again.
  await users.updateProfile(userId, { onboarding_step: "intro" });
}
