# Decisions for the owner

This log records product decisions that change what a customer can do in Verifi. The local build uses fictional records. It can queue a request internally. Sending an order, billing a customer, and releasing a report each require their own decision and verification.

| ID | Question | Current behavior and why the answer matters | Status |
| --- | --- | --- | --- |
| 1 | Should a Client Member be able to order screenings and view company reports, with a Client Administrator also able to manage company users and billing? | The owner confirmed two customer access levels. The existing portal defines these two roles. Confirm their permissions for the replacement. | Open |
| 2 | Which screening packages can each customer company order, and what price does it pay for each? | The local build has example package assignments. Confirm the packages and prices for each company. | Open |
| 3 | Should we support employment screening, tenant screening, or both at launch? | The selected screening purpose determines which packages and consent apply. | Open |
| 4 | May a customer company enter the details of the person being screened, invite that person to enter them, or do either? | The local flow supports entry by a customer company employee. Applicant invitations are pending. | Open |
| 5 | Besides you, who, if anyone, should have administrator access across all customer companies? | The owner confirmed he administers the portal. Identify any additional platform administrators. | Open |
| 6 | May a client member (a regular user at a customer company) place a paid order, or must the client administrator (who manages that company’s portal users) approve it? | A signed-in user may prepare a local request. It produces no charge or provider order. | Open |
| 7 | Which roles need multifactor authentication (a second sign-in step)? | Local sign-in uses an explicitly provisioned test account. | Open |
| 8 | Who may invite users to a customer company’s account? Who may approve a password reset? | Set production invitation and account recovery authority before issuing customer credentials. | Open |
| 9 | Who keeps the applicant’s signed consent, Verifi or the customer company? | The portal stores an operator attestation and a reference. It does not store or verify a signed document. | Open |
| 10 | What consent records must be kept, for how long, and when must the applicant sign again? | Decide what evidence is acceptable before submitting a real order. | Open |
| 11 | Which roles may view a completed report? Who may release it to the customer company? | Report release and screening decisions are outside the local build. | Open |
| 12 | What status should the customer see while waiting for a confirmed result from the screening provider? | A locally queued order is waiting to be sent to the provider. | Open |
| 13 | When should a customer be charged? When should a cancellation earn a credit? | The local portal creates no invoice or credit. | Open |
| 14 | Which user accounts, company memberships, applicants, orders, and signed documents must move to the new portal? | The new database starts empty. Existing records can be imported after the owner identifies what to preserve. | Open |

### Provider handoff

Before testing submission, obtain the sandbox endpoint and credential, approved outbound proxy access, one allowed client and package assignment, synthetic applicant instructions, and the callback registration procedure. The owner or provider should convey credentials through a password manager. Keep this file for decisions and identifiers safe to share.

When a decision is made, record the date, the owner's answer, and the workflow or test that implements it.
