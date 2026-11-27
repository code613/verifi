# Verifi frontend

React/TypeScript portal for the local Java API. No Supabase client, seeded records, provider requests, or credentials are included.

## Run

Use Node 24 and npm:

- npm ci
- npm run dev
- npm run build
- npm test
- npm run test:e2e (requires the local backend, its dedicated test account and the browser test configuration)

Vite serves http://127.0.0.1:5173 and proxies relative /api requests to http://localhost:8080. Production hosting must serve the built dist files and route /api to the same-origin backend. No frontend environment secrets are required.

The session endpoint provides the CSRF token. Every write sends X-CSRF-TOKEN, and login/logout refetch the session. Passwords are held in component memory only.

## Behavior and limits

The interface displays actual API records. Requests can be queued locally; provider transmission and results are unavailable. Authorization records contain an operator-attested reference, not an uploaded or signature-verified document.

Order submission keeps the same request ID and exact inputs on retries. A pending envelope containing only IDs is saved in sessionStorage, scoped to the signed-in user and organization. Refreshing the page can resume that request. Authorization creation also uses its own stable request ID for retries. Applicant creation is not automatically retried after an uncertain response: refresh the list and choose the saved record instead.

Local tests exercise CSRF behavior, no automatic write retry, immutable consent intent, persistence/replay identity, and tenant/account boundaries. They do not substitute for a running-backend browser test.
