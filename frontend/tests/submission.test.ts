import test from "node:test";
import assert from "node:assert/strict";
import {
  createConsentIntent,
  createPendingOrder,
  readPendingOrder,
} from "../src/submission.ts";

const user = { id: "user-a", organizationId: "org-a" };
const applicantId = "11111111-1111-4111-8111-111111111111";
const packageAssignmentId = "22222222-2222-4222-8222-222222222222";
const consentId = "33333333-3333-4333-8333-333333333333";
const requestId = "44444444-4444-4444-8444-444444444444";

test("explicit attestation and nonempty authorization reference are required", () => {
  assert.throws(() =>
    createConsentIntent(applicantId, "TENANT", "record-1", false, "policy-v1"),
  );
  assert.throws(() =>
    createConsentIntent(applicantId, "TENANT", "  ", true, "policy-v1"),
  );
  assert.throws(() =>
    createConsentIntent(applicantId, "TENANT", "record-1", true, ""),
  );
});

test("consent retry intent freezes subject, purpose and reference", () => {
  const intent = createConsentIntent(
    applicantId,
    "EMPLOYMENT",
    "  archive/record-1  ",
    true,
    "policy-v1",
    requestId,
  );
  assert.equal(intent.authorizationReference, "archive/record-1");
  assert.equal(intent.requestId, requestId);
  assert.equal(intent.attestationVersion, "policy-v1");
  assert.throws(() => {
    intent.attestationVersion = "policy-v2";
  }, TypeError);
  assert.throws(() => {
    intent.applicantId = "other-person";
  }, TypeError);
});

test("pending retry survives reload with the same request ID and exact payload", () => {
  const pending = createPendingOrder(
    user,
    { applicantId, packageAssignmentId, consentId },
    requestId,
  );
  const recovered = readPendingOrder(JSON.stringify(pending), user);
  assert.deepEqual(recovered, pending);
  assert.equal(recovered?.request.requestId, requestId);
  assert.throws(() => {
    pending.request.consentId = "different";
  }, TypeError);
});

test("pending request cannot cross an account or tenant boundary", () => {
  const raw = JSON.stringify(
    createPendingOrder(
      user,
      { applicantId, packageAssignmentId, consentId },
      requestId,
    ),
  );
  assert.equal(readPendingOrder(raw, { ...user, id: "user-b" }), null);
  assert.equal(
    readPendingOrder(raw, { ...user, organizationId: "org-b" }),
    null,
  );
});

test("invalid or extra persisted properties are not blindly replayed", () => {
  assert.equal(readPendingOrder("{bad", user), null);
  const valid = createPendingOrder(
    user,
    { applicantId, packageAssignmentId, consentId },
    requestId,
  );
  assert.equal(
    readPendingOrder(
      JSON.stringify({
        ...valid,
        request: { ...valid.request, requestId: "bad" },
      }),
      user,
    ),
    null,
  );
  const recovered = readPendingOrder(
    JSON.stringify({
      ...valid,
      request: { ...valid.request, authorizationReference: "sensitive" },
    }),
    user,
  );
  assert.equal("authorizationReference" in recovered!.request, false);
});
