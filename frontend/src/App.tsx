import { useCallback, useEffect, useRef, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { apiRequest, ApiError, errorMessage } from "./api.ts";
import type {
  Request,
  RequestOptions,
  ScreeningOrder,
  Session,
  User,
} from "./types.ts";
import { PENDING_ORDER_KEY, readPendingOrder } from "./submission.ts";
import type { PendingOrder } from "./submission.ts";
import { RequestWizard } from "./RequestWizard.tsx";

export function Icon({
  name,
  size = 20,
}: {
  name:
    | "orders"
    | "plus"
    | "arrow"
    | "check"
    | "user"
    | "exit"
    | "refresh"
    | "shield";
  size?: number;
}) {
  const paths = {
    orders: (
      <>
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
        <path d="M14 2v6h6M8 13h8M8 17h5" />
      </>
    ),
    plus: <path d="M12 5v14M5 12h14" />,
    arrow: <path d="m9 5 7 7-7 7" />,
    check: <path d="m5 12 4 4L19 6" />,
    user: (
      <>
        <circle cx="12" cy="8" r="4" />
        <path d="M4 21v-2a8 8 0 0 1 16 0v2" />
      </>
    ),
    exit: (
      <>
        <path d="M9 4H4v16h5M14 8l4 4-4 4M8 12h12" />
      </>
    ),
    refresh: (
      <>
        <path d="M20 8a8 8 0 1 0 1 7M20 3v5h-5" />
      </>
    ),
    shield: (
      <>
        <path d="M12 3 3 7v5c0 5 9 10 9 10s9-5 9-10V7Z" />
        <path d="m8 12 3 3 5-6" />
      </>
    ),
  };
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name]}
    </svg>
  );
}

export function Notice({
  children,
  kind = "error",
}: {
  children: ReactNode;
  kind?: "error" | "info" | "success";
}) {
  return (
    <div
      className={"notice notice-" + kind}
      role={kind === "error" ? "alert" : "status"}
    >
      {children}
    </div>
  );
}

export function Loading({ children = "Loading…" }: { children?: ReactNode }) {
  return (
    <div className="loading" role="status">
      <span className="spinner" aria-hidden="true" />
      {children}
    </div>
  );
}

function formatDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "Date unavailable"
    : new Intl.DateTimeFormat(undefined, {
        month: "short",
        day: "numeric",
        year: "numeric",
        hour: "numeric",
        minute: "2-digit",
      }).format(date);
}

function Login({
  session,
  refresh,
  message,
}: {
  session: Session;
  refresh: () => Promise<void>;
  message: string;
}) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function login(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await apiRequest<void>(
        "/api/session/login",
        { method: "POST", body: { email: email.trim(), password } },
        session.csrfToken,
      );
      setPassword("");
      await refresh();
    } catch (failure) {
      setPassword("");
      if (failure instanceof ApiError && failure.code === "CSRF_INVALID") {
        try {
          await refresh();
        } catch {
          /* The app presents the session refresh failure. */
        }
      }
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="login-layout">
      <section className="login-form-area">
        <img
          className="login-logo"
          src="/verifi-logo.png"
          alt="Verifi backgrounds"
        />
        <div className="login-form-content">
          <h1>Sign in</h1>
          <p className="muted">
            Sign in to manage your organization's screening requests.
          </p>
          {message && <Notice kind="info">{message}</Notice>}
          {error && <Notice>{error}</Notice>}
          <form onSubmit={login} className="form-stack">
            <label htmlFor="email">
              Email address
              <input
                id="email"
                type="email"
                autoComplete="username"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                disabled={busy}
              />
            </label>
            <label htmlFor="password">
              Password
              <input
                id="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                disabled={busy}
              />
            </label>
            <button
              className="button primary full"
              disabled={busy}
              type="submit"
            >
              {busy ? "Signing in…" : "Sign in"}
              <Icon name="arrow" size={18} />
            </button>
          </form>
          <p className="fine-print">
            Use the account provided by your organization. For account access,
            contact your administrator.
          </p>
        </div>
        <p className="login-footer">Verifi backgrounds</p>
      </section>
      <aside className="login-brand" aria-label="About this portal">
        <div className="brand-lines" aria-hidden="true" />
        <div className="login-brand-content">
          <div className="brand-symbol">
            <Icon name="shield" size={32} />
          </div>
          <h2>Manage screening requests.</h2>
          <p>
            Select applicants, record authorization references and queue
            requests for your organization.
          </p>
        </div>
      </aside>
    </main>
  );
}

export default function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const refresh = useCallback(async () => {
    try {
      const next = await apiRequest<Session>("/api/session");
      setSession(next);
      setError("");
    } catch (failure) {
      setError(errorMessage(failure));
      throw failure;
    }
  }, []);
  useEffect(() => {
    void refresh().catch(() => undefined);
  }, [refresh]);

  const request: Request = useCallback(
    async <T,>(path: string, options?: RequestOptions) => {
      try {
        return await apiRequest<T>(path, options, session?.csrfToken);
      } catch (failure) {
        if (
          failure instanceof ApiError &&
          failure.status === 401 &&
          failure.code !== "INVALID_CREDENTIALS"
        ) {
          setNotice("Your session has expired. Sign in again to continue.");
          setSession((current) =>
            current ? { ...current, user: null } : null,
          );
          void refresh().catch(() => undefined);
        } else if (
          failure instanceof ApiError &&
          failure.code === "CSRF_INVALID"
        ) {
          await refresh();
          throw new ApiError(
            "Your session was refreshed. Please retry the same request.",
            403,
            "CSRF_INVALID",
          );
        }
        throw failure;
      }
    },
    [session?.csrfToken, refresh],
  );

  if (error)
    return (
      <main className="center-screen">
        <img
          className="login-logo"
          src="/verifi-logo.png"
          alt="Verifi backgrounds"
        />
        <h1>We couldn't connect.</h1>
        <p className="muted">
          The portal needs its application server to load your session.
        </p>
        <Notice>{error}</Notice>
        <button
          className="button primary"
          onClick={() => {
            setError("");
            void refresh().catch(() => undefined);
          }}
        >
          Try again
        </button>
      </main>
    );
  if (!session)
    return (
      <main className="center-screen">
        <Loading>Opening your workspace…</Loading>
      </main>
    );
  if (!session.user)
    return <Login session={session} refresh={refresh} message={notice} />;
  return (
    <Workspace
      key={session.user.id + ":" + session.user.organizationId}
      user={session.user}
      request={request}
      onLogout={async () => {
        await request<void>("/api/session/logout", { method: "POST" });
        try {
          sessionStorage.removeItem(PENDING_ORDER_KEY);
        } catch {
          /* Session storage may be unavailable. */
        }
        setNotice("You have signed out.");
        setSession({ ...session, user: null });
        await refresh();
      }}
    />
  );
}

function initialView() {
  return window.location.hash === "#new-request" ? "new" : "orders";
}

function Workspace({
  user,
  request,
  onLogout,
}: {
  user: User;
  request: Request;
  onLogout: () => Promise<void>;
}) {
  const [view, setView] = useState(initialView);
  const [orders, setOrders] = useState<ScreeningOrder[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [logoutBusy, setLogoutBusy] = useState(false);
  const [success, setSuccess] = useState("");
  const [selected, setSelected] = useState<ScreeningOrder | null>(null);
  const [query, setQuery] = useState("");
  const [pending, setPending] = useState<PendingOrder | null>(() => {
    try {
      return readPendingOrder(sessionStorage.getItem(PENDING_ORDER_KEY), user);
    } catch {
      return null;
    }
  });
  const updatePending = useCallback((value: PendingOrder | null) => {
    setPending(value);
    try {
      if (value)
        sessionStorage.setItem(PENDING_ORDER_KEY, JSON.stringify(value));
      else sessionStorage.removeItem(PENDING_ORDER_KEY);
    } catch {
      /* In-memory retry remains available when storage is restricted. */
    }
  }, []);
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const records = await request<ScreeningOrder[]>("/api/orders");
      setOrders(records.sort((a, b) => b.createdAt.localeCompare(a.createdAt)));
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setLoading(false);
    }
  }, [request]);
  useEffect(() => {
    void load();
  }, [load]);
  useEffect(() => {
    const change = () => {
      setView(initialView());
    };
    window.addEventListener("hashchange", change);
    return () => window.removeEventListener("hashchange", change);
  }, []);
  function navigate(next: "orders" | "new") {
    window.location.hash = next === "orders" ? "orders" : "new-request";
    setView(next);
  }
  const visible = orders.filter((order) =>
    (order.applicantName + " " + order.packageName)
      .toLowerCase()
      .includes(query.toLowerCase()),
  );
  const queued = orders.filter((order) => order.status === "QUEUED").length;
  return (
    <div className="portal">
      <a
        className="skip-link"
        href="#main-content"
        onClick={(event) => {
          event.preventDefault();
          document.getElementById("main-content")?.focus();
        }}
      >
        Skip to main content
      </a>
      <aside className="sidebar">
        <a href="#orders" className="sidebar-logo" aria-label="Verifi orders">
          <img src="/verifi-logo.png" alt="Verifi backgrounds" />
        </a>
        <div className="workspace-label">YOUR WORKSPACE</div>
        <nav aria-label="Main navigation">
          <a
            href="#orders"
            aria-current={view === "orders" ? "page" : undefined}
          >
            <Icon name="orders" />
            Orders
          </a>
          <a
            href="#new-request"
            aria-current={view === "new" ? "page" : undefined}
          >
            <Icon name="plus" />
            New request
          </a>
        </nav>
        <div className="sidebar-bottom">Screening portal</div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <span className="topbar-title">
            Your organization <span className="separator">/</span>{" "}
            <strong>{view === "new" ? "New request" : "Orders"}</strong>
          </span>
          <div className="account">
            <span className="avatar" aria-hidden="true">
              {(user.displayName || user.email).charAt(0).toUpperCase()}
            </span>
            <span className="account-text">
              <strong>{user.displayName || user.email}</strong>
              <small>
                {user.role === "CLIENT_ADMIN"
                  ? "Client administrator"
                  : "Member"}
              </small>
            </span>
            <button
              className="icon-button"
              title="Sign out"
              aria-label="Sign out"
              disabled={logoutBusy}
              onClick={async () => {
                setLogoutBusy(true);
                setError("");
                try {
                  await onLogout();
                } catch (failure) {
                  setError(
                    "Could not confirm sign out. " + errorMessage(failure),
                  );
                } finally {
                  setLogoutBusy(false);
                }
              }}
            >
              <Icon name="exit" />
            </button>
          </div>
        </header>
        <main id="main-content" className="main-content" tabIndex={-1}>
          {error && (
            <Notice>
              {error}{" "}
              <button className="text-button" onClick={() => void load()}>
                Reload orders
              </button>
            </Notice>
          )}
          {success && <Notice kind="success">{success}</Notice>}
          {view === "new" ? (
            <RequestWizard
              user={user}
              request={request}
              pending={pending}
              setPending={updatePending}
              onCancel={() => navigate("orders")}
              onComplete={(order) => {
                updatePending(null);
                setOrders((current) => [
                  order,
                  ...current.filter((existing) => existing.id !== order.id),
                ]);
                navigate("orders");
                setSuccess(
                  "Request saved and queued locally. It has not been sent to the screening provider.",
                );
              }}
            />
          ) : (
            <>
              <div className="page-heading">
                <div>
                  <h1>Orders</h1>
                  <p className="muted">
                    Track your screening requests in one place.
                  </p>
                </div>
                <button
                  className="button primary"
                  onClick={() => navigate("new")}
                >
                  <Icon name="plus" size={18} />
                  New screening request
                </button>
              </div>
              <div className="transmission-notice">
                <Icon name="shield" />
                <div>
                  <strong>Local queue only</strong>
                  <p>
                    Provider transmission is not available yet. Queued requests
                    have not been sent, and no screening results are available.
                  </p>
                </div>
              </div>
              {pending && (
                <Notice kind="info">
                  An earlier request is awaiting confirmation.{" "}
                  <button
                    className="text-button"
                    onClick={() => navigate("new")}
                  >
                    Resume the same request
                  </button>
                </Notice>
              )}
              <section className="stats" aria-label="Order overview">
                <div>
                  <span>Total requests</span>
                  <strong>{loading ? "—" : orders.length}</strong>
                  <small>In your organization</small>
                </div>
                <div>
                  <span>Queued locally</span>
                  <strong>{loading ? "—" : queued}</strong>
                  <small>Awaiting provider transmission</small>
                </div>
                <div className="stat-explanation">
                  <Icon name="shield" size={27} />
                  <p>
                    Review the applicant, assigned package and authorization
                    before queuing each request.
                  </p>
                </div>
              </section>
              <section
                className="panel order-panel"
                aria-labelledby="orders-heading"
              >
                <div className="panel-toolbar">
                  <h2 id="orders-heading">
                    All requests <span className="count">{orders.length}</span>
                  </h2>
                  <div className="toolbar-controls">
                    <label className="search-label">
                      <span className="sr-only">
                        Search requests by applicant or package
                      </span>
                      <input
                        type="search"
                        placeholder="Search applicant or package"
                        value={query}
                        onChange={(event) => setQuery(event.target.value)}
                      />
                    </label>
                    <button
                      className="icon-button"
                      aria-label="Refresh requests"
                      title="Refresh requests"
                      disabled={loading}
                      onClick={() => void load()}
                    >
                      <Icon name="refresh" />
                    </button>
                  </div>
                </div>
                {loading ? (
                  <Loading>Loading requests…</Loading>
                ) : error && orders.length === 0 ? (
                  <div className="empty-state">
                    <h3>Requests could not be loaded.</h3>
                    <p>Try refreshing when the server is available.</p>
                  </div>
                ) : visible.length === 0 ? (
                  <div className="empty-state">
                    <div className="empty-icon">
                      <Icon name="orders" size={30} />
                    </div>
                    <h3>
                      {query
                        ? "No matching requests"
                        : "Your first request starts here."}
                    </h3>
                    <p>
                      {query
                        ? "Try another applicant or package name."
                        : "Select an applicant, choose an assigned package and record the authorization reference."}
                    </p>
                    {!query && (
                      <button
                        className="button secondary"
                        onClick={() => navigate("new")}
                      >
                        Create a screening request
                        <Icon name="arrow" size={16} />
                      </button>
                    )}
                  </div>
                ) : (
                  <div className="table-scroll">
                    <table>
                      <thead>
                        <tr>
                          <th scope="col">Applicant</th>
                          <th scope="col">Package</th>
                          <th scope="col">Status</th>
                          <th scope="col">Created</th>
                          <th scope="col">
                            <span className="sr-only">Details</span>
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {visible.map((order) => (
                          <tr key={order.id}>
                            <td>
                              <button
                                className="table-link"
                                onClick={() => setSelected(order)}
                              >
                                {order.applicantName}
                              </button>
                            </td>
                            <td>{order.packageName}</td>
                            <td>
                              <span className="status-pill">
                                <span />
                                {order.status === "QUEUED"
                                  ? "Queued locally"
                                  : "Status unavailable"}
                              </span>
                            </td>
                            <td className="date-cell">
                              {formatDate(order.createdAt)}
                            </td>
                            <td>
                              <button
                                className="icon-button"
                                aria-label={
                                  "View request for " + order.applicantName
                                }
                                onClick={() => setSelected(order)}
                              >
                                <Icon name="arrow" size={18} />
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </>
          )}
          <footer className="page-footer">
            <span>Verifi backgrounds</span>
          </footer>
        </main>
      </div>
      {selected && (
        <OrderDetail order={selected} onClose={() => setSelected(null)} />
      )}
    </div>
  );
}

function OrderDetail({
  order,
  onClose,
}: {
  order: ScreeningOrder;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    dialog.current?.showModal();
  }, []);
  return (
    <dialog
      className="detail-dialog"
      ref={dialog}
      aria-labelledby="detail-title"
      onCancel={onClose}
      onClose={onClose}
    >
      <div className="dialog-heading">
        <div>
          <p className="eyebrow">SCREENING REQUEST</p>
          <h2 id="detail-title">{order.applicantName}</h2>
        </div>
        <button
          className="icon-button"
          aria-label="Close request details"
          onClick={onClose}
        >
          ×
        </button>
      </div>
      <span className="status-pill">
        <span />
        Queued locally
      </span>
      <p className="muted">
        This request is recorded locally. It has not been transmitted to the
        screening provider.
      </p>
      <dl className="review-list">
        <div>
          <dt>Package</dt>
          <dd>{order.packageName}</dd>
        </div>
        <div>
          <dt>Created</dt>
          <dd>{formatDate(order.createdAt)}</dd>
        </div>
        <div>
          <dt>Authorization</dt>
          <dd>
            Operator-attested reference recorded. Signature and document
            verification are not established by this request.
          </dd>
        </div>
        <div>
          <dt>Request reference</dt>
          <dd className="reference-value">{order.id}</dd>
        </div>
      </dl>
      <button className="button secondary full" onClick={onClose}>
        Back to orders
      </button>
    </dialog>
  );
}
