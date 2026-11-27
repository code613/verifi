export type Purpose = "EMPLOYMENT" | "TENANT";

export interface User {
  id: string;
  email: string;
  displayName: string;
  organizationId: string;
  role: "CLIENT_ADMIN" | "MEMBER";
}

export interface Session {
  user: User | null;
  csrfToken: string;
}

export interface Applicant {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  createdAt: string;
}

export interface PackageAssignment {
  id: string;
  packageId: string;
  name: string;
  description: string;
  purpose: Purpose;
}

export interface ConsentPolicy {
  version: string;
  attestationText: string;
}

export interface Consent {
  id: string;
  requestId: string;
  applicantId: string;
  purpose: Purpose;
  authorizationReference: string;
  attestationText: string;
  attestationVersion: string;
  evidenceType: "REFERENCE_ONLY";
  source: "OPERATOR_ATTESTATION";
  recordedAt: string;
  recordedBy: string;
}

export interface OrderRequest {
  requestId: string;
  applicantId: string;
  packageAssignmentId: string;
  consentId: string;
}

export interface ScreeningOrder extends OrderRequest {
  id: string;
  applicantName: string;
  packageName: string;
  status: "QUEUED";
  createdAt: string;
  createdBy: string;
}

export type RequestOptions = {
  method?: "GET" | "POST";
  body?: unknown;
  signal?: AbortSignal;
};
export type Request = <T>(path: string, options?: RequestOptions) => Promise<T>;
