import { createNavigationContainerRef } from "@react-navigation/native";

import type { RootStackParamList } from "./types";

/**
 * Navigation handle for code that runs outside a screen.
 *
 * The only user of this is the session teardown in `AppContext`: when a token
 * refresh fails there is no screen to raise the error to — the user simply must
 * not stay inside the app — so the root stack is reset from there.
 */
export const navigationRef = createNavigationContainerRef<RootStackParamList>();

/** Sends the user back to sign-in, discarding whatever stack they were in. */
export function resetToLogin() {
  if (!navigationRef.isReady()) return;
  navigationRef.reset({ index: 0, routes: [{ name: "Login" }] });
}
