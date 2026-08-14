import { ApiError, users } from "@/api";
import type { Uuid } from "@/api/types";

const SIGNUP_CONSENT_VERSION = "signup-v1";
const REQUIRED_SIGNUP_CONSENTS = ["terms_of_service", "privacy_collection"] as const;

/** Save the two account-level consents after the account has a user id. */
export async function saveRequiredSignupConsents(userId: Uuid): Promise<void> {
  const agreedAt = new Date().toISOString();
  for (const consentType of REQUIRED_SIGNUP_CONSENTS) {
    try {
      await users.saveConsent(userId, {
        consent_type: consentType,
        agreed: true,
        agreed_at: agreedAt,
        version: SIGNUP_CONSENT_VERSION,
      });
    } catch (cause) {
      // Retrying after a verification resend should be harmless.
      if (!(cause instanceof ApiError) || cause.status !== 409) throw cause;
    }
  }
}
