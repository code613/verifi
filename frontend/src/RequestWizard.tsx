import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { ApiError, errorMessage } from "./api.ts";
import { Icon, Loading, Notice } from "./App.tsx";
import {
  createConsentIntent,
  createPendingOrder,
  purposeLabel,
} from "./submission.ts";
import type { ConsentIntent, PendingOrder } from "./submission.ts";
import type {
  Applicant,
  Consent,
  ConsentPolicy,
  PackageAssignment,
  Request,
  ScreeningOrder,
  User,
} from "./types.ts";

type Props = {
  user: User;
  request: Request;
  pending: PendingOrder | null;
  setPending: (pending: PendingOrder | null) => void;
  onComplete: (order: ScreeningOrder) => void;
  onCancel: () => void;
};

export function RequestWizard({
  user,
  request,
  pending,
  setPending,
  onComplete,
  onCancel,
}: Props) {
  const [step, setStep] = useState(pending ? 4 : 1);
  const [applicants, setApplicants] = useState<Applicant[]>([]);
  const [packages, setPackages] = useState<PackageAssignment[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [applicantId, setApplicantId] = useState(
    pending?.request.applicantId ?? "",
  );
  const [packageId, setPackageId] = useState(
    pending?.request.packageAssignmentId ?? "",
  );
  const [adding, setAdding] = useState(false);
  const [applicantUnconfirmed, setApplicantUnconfirmed] = useState(false);
  const [newApplicant, setNewApplicant] = useState({
    firstName: "",
    lastName: "",
    email: "",
  });
  const [reference, setReference] = useState("");
  const [attested, setAttested] = useState(false);
  const [consentPolicy, setConsentPolicy] = useState<ConsentPolicy | null>(
    null,
  );
  const [consent, setConsent] = useState<Consent | null>(null);
  const [consentIntent, setConsentIntent] = useState<ConsentIntent | null>(
    null,
  );
  const [previousConsents, setPreviousConsents] = useState<Consent[]>([]);
  const [consentHistoryError, setConsentHistoryError] = useState("");
  const [existingConsentLoading, setExistingConsentLoading] = useState(false);
  const title = useRef<HTMLHeadingElement>(null);
  const selectedApplicant = applicants.find((item) => item.id === applicantId);
  const selectedPackage = packages.find((item) => item.id === packageId);
  const steps = ["Applicant", "Package", "Authorization", "Review"];

  async function loadOptions() {
    setLoading(true);
    setError("");
    try {
      const [people, assignments, policy] = await Promise.all([
        request<Applicant[]>("/api/applicants"),
        request<PackageAssignment[]>("/api/packages"),
        request<ConsentPolicy>("/api/consent-policy"),
      ]);
      setApplicants(people);
      setPackages(assignments);
      if (!consentIntent) {
        setConsentPolicy(policy);
        setAttested(false);
      }
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    void loadOptions();
  }, [request]);
  useEffect(() => {
    title.current?.focus();
  }, [step]);
  useEffect(() => {
    if (!applicantId) {
      setPreviousConsents([]);
      return;
    }
    const controller = new AbortController();
    setExistingConsentLoading(true);
    setConsentHistoryError("");
    void request<Consent[]>(
      "/api/applicants/" + encodeURIComponent(applicantId) + "/consents",
      { signal: controller.signal },
    )
      .then((records) => {
        if (!controller.signal.aborted) setPreviousConsents(records);
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) {
          setPreviousConsents([]);
          setConsentHistoryError(errorMessage(failure));
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setExistingConsentLoading(false);
      });
    return () => controller.abort();
  }, [applicantId, request]);

  function resetAuthorization() {
    setReference("");
    setAttested(false);
    setConsent(null);
    setConsentIntent(null);
    setError("");
  }
  function chooseApplicant(id: string) {
    if (id !== applicantId) {
      setApplicantId(id);
      resetAuthorization();
    }
  }
  function choosePackage(id: string) {
    if (id !== packageId) {
      setPackageId(id);
      resetAuthorization();
    }
  }
  async function saveApplicant(event: FormEvent) {
    event.preventDefault();
    if (busy || applicantUnconfirmed) return;
    setBusy(true);
    setError("");
    try {
      const person = await request<Applicant>("/api/applicants", {
        method: "POST",
        body: {
          firstName: newApplicant.firstName.trim(),
          lastName: newApplicant.lastName.trim(),
          email: newApplicant.email.trim(),
        },
      });
      setApplicants((current) => [
        person,
        ...current.filter((item) => item.id !== person.id),
      ]);
      chooseApplicant(person.id);
      setAdding(false);
      setNewApplicant({ firstName: "", lastName: "", email: "" });
    } catch (failure) {
      const uncertain =
        !(failure instanceof ApiError) ||
        failure.status === 0 ||
        failure.status >= 500 ||
        failure.code === "INVALID_RESPONSE";
      setApplicantUnconfirmed(uncertain);
      setError(
        uncertain
          ? "We could not confirm whether the applicant was saved. Refresh the applicant list and check for the record before adding anyone again."
          : errorMessage(failure),
      );
    } finally {
      setBusy(false);
    }
  }
  async function refreshApplicants() {
    setBusy(true);
    setError("");
    try {
      setApplicants(await request<Applicant[]>("/api/applicants"));
      setAdding(false);
      setApplicantUnconfirmed(false);
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }
  async function refreshConsentPolicy() {
    if (busy || consentIntent) return;
    setBusy(true);
    setError("");
    setAttested(false);
    setConsentPolicy(null);
    try {
      setConsentPolicy(await request<ConsentPolicy>("/api/consent-policy"));
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }
  async function saveConsent(event: FormEvent) {
    event.preventDefault();
    if (busy || !selectedApplicant || !selectedPackage || !consentPolicy)
      return;
    setBusy(true);
    setError("");
    try {
      const intent =
        consentIntent ??
        createConsentIntent(
          applicantId,
          selectedPackage.purpose,
          reference,
          attested,
          consentPolicy.version,
        );
      setConsentIntent(intent);
      const record = await request<Consent>(
        "/api/applicants/" +
          encodeURIComponent(intent.applicantId) +
          "/consents",
        {
          method: "POST",
          body: {
            requestId: intent.requestId,
            purpose: intent.purpose,
            authorizationReference: intent.authorizationReference,
            attested: intent.attested,
            attestationVersion: intent.attestationVersion,
          },
        },
      );
      setConsent(record);
      setStep(4);
    } catch (failure) {
      if (
        failure instanceof ApiError &&
        failure.code === "ATTESTATION_CHANGED"
      ) {
        setConsentIntent(null);
        setAttested(false);
        setConsentPolicy(null);
        try {
          setConsentPolicy(await request<ConsentPolicy>("/api/consent-policy"));
          setError(
            "The authorization statement has changed. Review the current statement and attest again before saving.",
          );
        } catch (refreshFailure) {
          setError(
            "The authorization statement has changed. Refresh it before continuing. " +
              errorMessage(refreshFailure),
          );
        }
        return;
      }
      if (
        failure instanceof ApiError &&
        failure.status >= 400 &&
        failure.status < 500 &&
        failure.status !== 409 &&
        failure.code !== "CSRF_INVALID"
      ) {
        setConsentIntent(null); // A rejected validation can be corrected before a new intent.
      }
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }
  async function queue() {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      const attempt =
        pending ??
        createPendingOrder(user, {
          applicantId,
          packageAssignmentId: packageId,
          consentId: consent?.id ?? "",
        });
      setPending(attempt);
      // The backend replays the exact saved request for this idempotency key.
      const order = await request<ScreeningOrder>("/api/orders", {
        method: "POST",
        body: attempt.request,
      });
      onComplete(order);
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }
  function goBack() {
    if (pending || busy) return;
    setError("");
    if (step === 4) {
      setConsent(null);
      setConsentIntent(null);
      setAttested(false);
    }
    setStep((current) => Math.max(1, current - 1));
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <h1>New screening request</h1>
          <p className="muted">
            Select an applicant, package and authorization reference.
          </p>
        </div>
        <button className="button quiet" onClick={onCancel} disabled={busy}>
          Back to orders
        </button>
      </div>
      <ol className="stepper" aria-label="Request progress">
        {steps.map((label, index) => (
          <li
            key={label}
            className={
              step === index + 1 ? "active" : step > index + 1 ? "complete" : ""
            }
            aria-current={step === index + 1 ? "step" : undefined}
          >
            <span className="step-number">
              {step > index + 1 ? <Icon name="check" size={15} /> : index + 1}
            </span>
            <span>{label}</span>
          </li>
        ))}
      </ol>
      <div className="wizard-layout">
        <section className="panel wizard-panel" aria-labelledby="step-title">
          <div className="wizard-heading">
            <span className="step-overline">STEP {step} OF 4</span>
            <h2 id="step-title" ref={title} tabIndex={-1}>
              {
                [
                  "Who is the screening for?",
                  "Choose an assigned package.",
                  "Record the authorization.",
                  "Review your request.",
                ][step - 1]
              }
            </h2>
          </div>
          {error && <Notice>{error}</Notice>}
          {loading ? (
            <Loading>Loading your applicants and packages…</Loading>
          ) : (
            <>
              {step === 1 && (
                <>
                  {!adding ? (
                    <>
                      <p className="muted">
                        Choose an existing applicant or add someone new.
                      </p>
                      <label htmlFor="applicant">
                        Applicant
                        <select
                          id="applicant"
                          value={applicantId}
                          onChange={(event) =>
                            chooseApplicant(event.target.value)
                          }
                          disabled={busy}
                        >
                          <option value="">Select an applicant</option>
                          {applicants.map((person) => (
                            <option key={person.id} value={person.id}>
                              {person.firstName} {person.lastName} ·{" "}
                              {person.email}
                            </option>
                          ))}
                        </select>
                      </label>
                      {applicants.length === 0 && (
                        <p className="field-hint">
                          No applicants have been added to your organization
                          yet.
                        </p>
                      )}
                      <button
                        className="text-button add-applicant"
                        onClick={() => {
                          setAdding(true);
                          setError("");
                        }}
                      >
                        <Icon name="plus" size={17} />
                        Add a new applicant
                      </button>
                      {selectedApplicant && (
                        <div className="selection-summary">
                          <span className="soft-icon">
                            <Icon name="user" />
                          </span>
                          <div>
                            <strong>
                              {selectedApplicant.firstName}{" "}
                              {selectedApplicant.lastName}
                            </strong>
                            <p>{selectedApplicant.email}</p>
                          </div>
                          <Icon name="check" size={18} />
                        </div>
                      )}
                      <div className="wizard-actions">
                        <button
                          className="button quiet"
                          disabled={busy}
                          onClick={() => void refreshApplicants()}
                        >
                          Refresh applicants
                        </button>
                        <button
                          className="button primary"
                          disabled={!selectedApplicant || busy}
                          onClick={() => {
                            setError("");
                            setStep(2);
                          }}
                        >
                          Continue to package
                          <Icon name="arrow" size={16} />
                        </button>
                      </div>
                    </>
                  ) : (
                    <form onSubmit={saveApplicant} className="form-stack">
                      <p className="muted">
                        Add the applicant's basic details. Do not enter an SSN
                        here.
                      </p>
                      <div className="form-columns">
                        <label htmlFor="first-name">
                          First name
                          <input
                            id="first-name"
                            autoComplete="given-name"
                            maxLength={100}
                            required
                            value={newApplicant.firstName}
                            disabled={busy || applicantUnconfirmed}
                            onChange={(event) =>
                              setNewApplicant({
                                ...newApplicant,
                                firstName: event.target.value,
                              })
                            }
                          />
                        </label>
                        <label htmlFor="last-name">
                          Last name
                          <input
                            id="last-name"
                            autoComplete="family-name"
                            maxLength={100}
                            required
                            value={newApplicant.lastName}
                            disabled={busy || applicantUnconfirmed}
                            onChange={(event) =>
                              setNewApplicant({
                                ...newApplicant,
                                lastName: event.target.value,
                              })
                            }
                          />
                        </label>
                      </div>
                      <label htmlFor="applicant-email">
                        Email address
                        <input
                          id="applicant-email"
                          type="email"
                          autoComplete="email"
                          maxLength={254}
                          required
                          value={newApplicant.email}
                          disabled={busy || applicantUnconfirmed}
                          onChange={(event) =>
                            setNewApplicant({
                              ...newApplicant,
                              email: event.target.value,
                            })
                          }
                        />
                      </label>
                      <div className="wizard-actions">
                        <button
                          type="button"
                          className="button quiet"
                          disabled={busy}
                          onClick={() => {
                            setAdding(false);
                            setError("");
                          }}
                        >
                          Choose existing
                        </button>
                        {applicantUnconfirmed ? (
                          <button
                            type="button"
                            className="button primary"
                            disabled={busy}
                            onClick={() => void refreshApplicants()}
                          >
                            Refresh applicants
                          </button>
                        ) : (
                          <button
                            className="button primary"
                            type="submit"
                            disabled={busy}
                          >
                            {busy ? "Saving…" : "Save applicant"}
                          </button>
                        )}
                      </div>
                    </form>
                  )}
                </>
              )}
              {step === 2 && (
                <>
                  <p className="muted">
                    Only packages assigned to your organization appear here.
                  </p>
                  <fieldset className="package-options">
                    <legend className="sr-only">Assigned package</legend>
                    {packages.map((assignment) => (
                      <label
                        className={
                          "package-card " +
                          (packageId === assignment.id ? "selected" : "")
                        }
                        key={assignment.id}
                      >
                        <input
                          type="radio"
                          name="package"
                          value={assignment.id}
                          checked={packageId === assignment.id}
                          onChange={() => choosePackage(assignment.id)}
                        />
                        <span>
                          <span className="package-name">
                            {assignment.name}
                          </span>
                          <span className="purpose">
                            {purposeLabel(assignment.purpose)}
                          </span>
                          {assignment.description && (
                            <span className="package-description">
                              {assignment.description}
                            </span>
                          )}
                        </span>
                      </label>
                    ))}
                  </fieldset>
                  {packages.length === 0 && (
                    <Notice kind="info">
                      No packages are assigned yet. Ask your administrator to
                      assign a package before creating a screening request.
                    </Notice>
                  )}
                  <div className="wizard-actions">
                    <button className="button quiet" onClick={goBack}>
                      Back
                    </button>
                    <button
                      className="button primary"
                      disabled={!selectedPackage}
                      onClick={() => {
                        setError("");
                        setStep(3);
                      }}
                    >
                      Continue to authorization
                      <Icon name="arrow" size={16} />
                    </button>
                  </div>
                </>
              )}
              {step === 3 && (
                <form onSubmit={saveConsent} className="form-stack">
                  <p className="muted">
                    Reference the authorization you already hold for{" "}
                    <strong>
                      {selectedApplicant?.firstName}{" "}
                      {selectedApplicant?.lastName}
                    </strong>
                    , for{" "}
                    <strong>
                      {selectedPackage
                        ? purposeLabel(selectedPackage.purpose).toLowerCase()
                        : "the selected purpose"}
                    </strong>
                    .
                  </p>
                  <div className="reference-notice">
                    <Icon name="shield" />
                    <p>
                      This records your reference and attestation. It does not
                      upload a document or verify a signature.
                    </p>
                  </div>
                  <label htmlFor="authorization-reference">
                    Authorization reference
                    <input
                      id="authorization-reference"
                      maxLength={500}
                      required
                      value={reference}
                      disabled={busy || consentIntent !== null}
                      aria-describedby="reference-help"
                      onChange={(event) => {
                        setReference(event.target.value);
                        setAttested(false);
                      }}
                      placeholder="Document ID or location in your records"
                    />
                  </label>
                  <p id="reference-help" className="field-hint">
                    Use a reference you can retrieve. Do not paste the
                    authorization document, SSN or other sensitive contents.
                  </p>
                  {consentPolicy ? (
                    <label className="checkbox-field">
                      <input
                        type="checkbox"
                        checked={attested}
                        required
                        disabled={busy || consentIntent !== null}
                        onChange={(event) => setAttested(event.target.checked)}
                      />
                      <span>{consentPolicy.attestationText}</span>
                    </label>
                  ) : (
                    <Notice>
                      The current authorization statement must be loaded before
                      you can attest.
                    </Notice>
                  )}
                  {!consentIntent && (
                    <button
                      type="button"
                      className="text-button"
                      disabled={busy}
                      onClick={() => void refreshConsentPolicy()}
                    >
                      Refresh authorization statement
                    </button>
                  )}
                  {existingConsentLoading ? (
                    <p className="field-hint">Checking prior references…</p>
                  ) : (
                    previousConsents.filter(
                      (item) => item.purpose === selectedPackage?.purpose,
                    ).length > 0 && (
                      <details className="prior-consents">
                        <summary>
                          Previously recorded references for this purpose
                        </summary>
                        <ul>
                          {previousConsents
                            .filter(
                              (item) =>
                                item.purpose === selectedPackage?.purpose,
                            )
                            .map((item) => (
                              <li key={item.id}>
                                <span>{item.authorizationReference}</span>
                                <small>
                                  Recorded{" "}
                                  {new Date(
                                    item.recordedAt,
                                  ).toLocaleDateString()}
                                </small>
                              </li>
                            ))}
                        </ul>
                        <p className="field-hint">
                          Confirm that your authorization is still applicable
                          before attesting again.
                        </p>
                      </details>
                    )
                  )}
                  {consentHistoryError && (
                    <p className="field-hint">
                      Prior references could not be loaded.{" "}
                      {consentHistoryError}
                    </p>
                  )}
                  {consentIntent && (
                    <Notice kind="info">
                      The last authorization save is awaiting confirmation.
                      Retry uses the same applicant, purpose, reference and
                      request ID.
                    </Notice>
                  )}
                  <div className="wizard-actions">
                    <button
                      type="button"
                      className="button quiet"
                      onClick={goBack}
                      disabled={busy || consentIntent !== null}
                    >
                      Back
                    </button>
                    <button
                      className="button primary"
                      type="submit"
                      disabled={
                        busy || !consentPolicy || !reference.trim() || !attested
                      }
                    >
                      {busy
                        ? "Saving authorization…"
                        : consentIntent
                          ? "Retry authorization save"
                          : "Save authorization and review"}
                      <Icon name="arrow" size={16} />
                    </button>
                  </div>
                </form>
              )}
              {step === 4 && (
                <>
                  {pending && (
                    <Notice kind="info">
                      This request is awaiting confirmation. Its details are
                      locked so a retry uses the same request ID.
                    </Notice>
                  )}
                  <dl className="review-list">
                    <div>
                      <dt>Applicant</dt>
                      <dd>
                        {selectedApplicant
                          ? selectedApplicant.firstName +
                            " " +
                            selectedApplicant.lastName
                          : "Previously selected applicant"}
                        {selectedApplicant && (
                          <small>{selectedApplicant.email}</small>
                        )}
                      </dd>
                    </div>
                    <div>
                      <dt>Assigned package</dt>
                      <dd>
                        {selectedPackage?.name ?? "Previously selected package"}
                        {selectedPackage && (
                          <small>{purposeLabel(selectedPackage.purpose)}</small>
                        )}
                      </dd>
                    </div>
                    <div>
                      <dt>Authorization</dt>
                      <dd>
                        {consent?.authorizationReference ??
                          previousConsents.find(
                            (item) => item.id === pending?.request.consentId,
                          )?.authorizationReference ??
                          "Previously recorded authorization reference"}
                        <small>Operator attestation; reference only</small>
                      </dd>
                    </div>
                  </dl>
                  <div className="reference-notice">
                    <Icon name="orders" />
                    <p>
                      <strong>What happens next?</strong> Your request will be
                      queued locally. It will not be sent to the screening
                      provider, and no report will be available yet.
                    </p>
                  </div>
                  <div className="wizard-actions">
                    <button
                      className="button quiet"
                      disabled={busy || pending !== null}
                      onClick={goBack}
                    >
                      Back
                    </button>
                    <button
                      className="button primary"
                      disabled={busy || (!pending && !consent)}
                      onClick={() => void queue()}
                    >
                      {busy
                        ? "Confirming request…"
                        : pending
                          ? "Retry same request"
                          : "Queue screening request"}
                      <Icon name="check" size={17} />
                    </button>
                  </div>
                </>
              )}
              {error && step !== 3 && (
                <button
                  className="text-button options-retry"
                  disabled={busy}
                  onClick={() => void loadOptions()}
                >
                  Reload applicants and packages
                </button>
              )}
            </>
          )}
        </section>
        <aside className="wizard-aside">
          <div className="aside-icon">
            <Icon name="shield" size={25} />
          </div>
          <h3>Before you queue</h3>
          <p>
            The applicant, assigned package and authorization must refer to the
            same screening purpose.
          </p>
          <ul>
            <li>
              <Icon name="check" size={15} />
              Choose the correct applicant
            </li>
            <li>
              <Icon name="check" size={15} />
              Use an assigned package
            </li>
            <li>
              <Icon name="check" size={15} />
              Confirm authorization
            </li>
            <li>
              <Icon name="check" size={15} />
              Review before queuing
            </li>
          </ul>
          <div className="aside-note">
            <strong>Provider transmission pending</strong>
            <p>This portal currently records requests locally.</p>
          </div>
        </aside>
      </div>
    </>
  );
}
