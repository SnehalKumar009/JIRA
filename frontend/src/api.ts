// Thin fetch wrapper for our API: same-origin session cookie, CSRF header on writes, and RFC 9457
// problem details turned into ApiError.

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly requestId?: string,
  ) {
    super(message);
  }
}

function csrfToken(): string | undefined {
  return document.cookie
    .split("; ")
    .find((cookie) => cookie.startsWith("XSRF-TOKEN="))
    ?.slice("XSRF-TOKEN=".length);
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  if (method !== "GET" && method !== "HEAD") {
    const token = csrfToken();
    if (token) {
      headers.set("X-XSRF-TOKEN", token);
    }
  }
  const response = await fetch(path, { ...init, method, headers, credentials: "same-origin" });
  if (!response.ok) {
    let detail = `Request failed (${response.status})`;
    let requestId: string | undefined;
    try {
      const problem = (await response.json()) as { detail?: string; requestId?: string };
      detail = problem.detail ?? detail;
      requestId = problem.requestId;
    } catch {
      // Not a problem-details body; keep the generic message.
    }
    throw new ApiError(response.status, detail, requestId);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const signInUrl = "/oauth2/authorization/keycloak";

/** Ends the app session, then sends the browser to Keycloak to end the IdP session too. */
export async function signOut(): Promise<void> {
  const { redirectUrl } = await api<{ redirectUrl: string }>("/logout", { method: "POST" });
  window.location.assign(redirectUrl);
}
