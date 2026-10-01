import assert from "node:assert/strict";
import test from "node:test";

import { clearWebSession, loadWebSession, saveWebSession } from "../src/api/webSession.ts";

const session = {
  accessToken: "access",
  refreshToken: "refresh",
  userId: "user-id",
  role: "elder",
  profileCompleted: true,
  onboardingStep: "completed",
  onboardingCompleted: true,
  baselineCompleted: true,
  characterName: null,
};

test("웹 세션은 새로고침 후 복원되고 로그아웃하면 제거된다", () => {
  const original = Object.getOwnPropertyDescriptor(globalThis, "sessionStorage");
  const values = new Map();
  Object.defineProperty(globalThis, "sessionStorage", {
    configurable: true,
    value: {
      getItem: (key) => values.get(key) ?? null,
      setItem: (key, value) => values.set(key, value),
      removeItem: (key) => values.delete(key),
    },
  });

  try {
    saveWebSession(session);
    assert.deepEqual(loadWebSession(), session);

    clearWebSession();
    assert.equal(loadWebSession(), null);

    values.set("neulbom.auth.session", "{broken");
    assert.equal(loadWebSession(), null);
  } finally {
    if (original) Object.defineProperty(globalThis, "sessionStorage", original);
    else delete globalThis.sessionStorage;
  }
});
