import type { OrderRequest, Purpose, User } from "./types.ts";

export const PENDING_ORDER_KEY = "verifi.pending-order.v1";

export interface ConsentIntent {
  requestId: string;
  applicantId: string;
  purpose: Purpose;
  authorizationReference: string;
  attested: true;
  attestationVersion: string;
}

export interface PendingOrder {
  userId: string;
  organizationId: string;
  request: OrderRequest;
}

export function createConsentIntent(
  applicantId: string,
  purpose: Purpose,
  authorizationReference: string,
  attested: boolean,
  attestationVersion: string,
  id: string = crypto.randomUUID(),
): ConsentIntent {
  const reference = authorizationReference.trim();
  if (!applicantId || !reference || !attested || !attestationVersion.trim())
    throw new Error(
      "An applicant, authorization reference and explicit attestation of the current policy are required.",
    );
  return Object.freeze({
    requestId: id,
    applicantId,
    purpose,
    authorizationReference: reference,
    attested: true,
    attestationVersion,
  });
}

export function createPendingOrder(
  user: Pick<User, "id" | "organizationId">,
  input: Omit<OrderRequest, "requestId">,
  id: string = crypto.randomUUID(),
): PendingOrder {
  if (!input.applicantId || !input.packageAssignmentId || !input.consentId)
    throw new Error(
      "Complete the applicant, package and authorization steps first.",
    );
  return Object.freeze({
    userId: user.id,
    organizationId: user.organizationId,
    request: Object.freeze({ ...input, requestId: id }),
  });
}

export function readPendingOrder(
  raw: string | null,
  user: Pick<User, "id" | "organizationId">,
): PendingOrder | null {
  if (!raw) return null;
  try {
    const value: unknown = JSON.parse(raw);
    if (!value || typeof value !== "object") return null;
    const pending = value as Partial<PendingOrder>;
    if (
      pending.userId !== user.id ||
      pending.organizationId !== user.organizationId ||
      !pending.request
    )
      return null;
    const request = pending.request;
    if (
      !["requestId", "applicantId", "packageAssignmentId", "consentId"].every(
        (key) =>
          typeof request[key as keyof OrderRequest] === "string" &&
          /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
            request[key as keyof OrderRequest],
          ),
      )
    )
      return null;
    // Rebuild the allowlisted envelope; never replay unknown storage properties.
    return createPendingOrder(
      user,
      {
        applicantId: request.applicantId,
        packageAssignmentId: request.packageAssignmentId,
        consentId: request.consentId,
      },
      request.requestId,
    );
  } catch {
    return null;
  }
}

export function purposeLabel(purpose: Purpose): string {
  return purpose === "TENANT" ? "Tenant screening" : "Employment screening";
}
