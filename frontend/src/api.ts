import type { RequestOptions } from "./types.ts";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(message: string, status: number, code: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

export function errorMessage(error: unknown): string {
  return error instanceof Error
    ? error.message
    : "Something went wrong. Please try again.";
}

export async function apiRequest<T>(
  path: string,
  options: RequestOptions = {},
  csrfToken?: string,
  transport: typeof fetch = fetch,
): Promise<T> {
  if (!path.startsWith("/api/"))
    throw new Error("Only application API requests are supported.");
  const method = options.method ?? "GET";
  const headers: Record<string, string> = { Accept: "application/json" };
  if (method === "POST") {
    if (!csrfToken)
      throw new ApiError(
        "Your session needs to be refreshed before continuing.",
        403,
        "CSRF_INVALID",
      );
    headers["Content-Type"] = "application/json";
    headers["X-CSRF-TOKEN"] = csrfToken;
  }

  let response: Response;
  try {
    response = await transport(path, {
      method,
      credentials: "same-origin",
      cache: "no-store",
      headers,
      body:
        options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError") throw error;
    throw new ApiError(
      "We could not reach the server. Your last save may not have been confirmed.",
      0,
      "NETWORK_ERROR",
    );
  }

  if (response.status === 204 && response.ok) return undefined as T;
  const text = await response.text();
  let payload: unknown;
  try {
    payload = text ? JSON.parse(text) : undefined;
  } catch {
    throw new ApiError(
      "The server returned an unreadable response. The last save may not have been confirmed.",
      response.status,
      "INVALID_RESPONSE",
    );
  }
  if (!response.ok) {
    const problem = payload as
      { message?: unknown; code?: unknown } | undefined;
    throw new ApiError(
      typeof problem?.message === "string"
        ? problem.message
        : "The request could not be completed.",
      response.status,
      typeof problem?.code === "string" ? problem.code : "REQUEST_FAILED",
    );
  }
  return payload as T;
}
