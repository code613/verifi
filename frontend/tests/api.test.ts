import test from "node:test";
import assert from "node:assert/strict";
import { apiRequest, ApiError } from "../src/api.ts";

test("write uses same-origin cookies and current CSRF token, including 204 auth responses", async () => {
  let seen: RequestInit | undefined;
  const transport: typeof fetch = async (url, init) => {
    assert.equal(url, "/api/session/login");
    seen = init;
    return new Response(null, { status: 204 });
  };
  const result = await apiRequest<void>(
    "/api/session/login",
    {
      method: "POST",
      body: { email: "synthetic@example.test", password: "only-in-memory" },
    },
    "token-from-session",
    transport,
  );
  assert.equal(result, undefined);
  assert.equal(seen?.credentials, "same-origin");
  assert.equal(
    (seen?.headers as Record<string, string>)["X-CSRF-TOKEN"],
    "token-from-session",
  );
});

test("missing CSRF prevents any mutation request", async () => {
  let calls = 0;
  await assert.rejects(
    apiRequest(
      "/api/orders",
      { method: "POST", body: {} },
      undefined,
      async () => {
        calls++;
        return Response.json({});
      },
    ),
    (error) => error instanceof ApiError && error.code === "CSRF_INVALID",
  );
  assert.equal(calls, 0);
});

test("ambiguous network failure does not replay a write", async () => {
  let calls = 0;
  await assert.rejects(
    apiRequest(
      "/api/orders",
      { method: "POST", body: {} },
      "csrf",
      async () => {
        calls++;
        throw new TypeError("connection lost");
      },
    ),
    (error) => error instanceof ApiError && error.code === "NETWORK_ERROR",
  );
  assert.equal(calls, 1);
});

test("preserves typed backend conflicts without treating them as a success", async () => {
  await assert.rejects(
    apiRequest("/api/orders", { method: "POST", body: {} }, "csrf", async () =>
      Response.json(
        {
          code: "IDEMPOTENCY_CONFLICT",
          message: "Request ID was already used for different inputs.",
        },
        { status: 409 },
      ),
    ),
    (error) =>
      error instanceof ApiError &&
      error.status === 409 &&
      error.code === "IDEMPOTENCY_CONFLICT",
  );
});
