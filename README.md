# Verifi

A React and TypeScript portal backed by Spring Boot, Spring Security, Spring Data JPA, and PostgreSQL. Flyway builds the database from an empty PostgreSQL instance. The local application makes no screening-provider requests.

## Run the local portal

Install Docker with Compose and start its engine. From the repository root, run these commands in PowerShell.

```powershell
.\scripts\Initialize-Local.ps1
docker compose up --build -d --wait
```

Open **http://localhost:5180**. Sign in with `VERIFI_DEMO_EMAIL` and `VERIFI_DEMO_PASSWORD` from the git-ignored `.env` next to this file. The initialization script generates different random passwords for the database and the local user. It preserves an existing `.env`. Alternatively, copy `.env.example` to `.env` and supply your own unique credentials. Choose a demo password between 12 and 72 UTF-8 bytes. On first startup Spring provisions the demo account, company, and package assignments when both the local profile and demo setting are enabled. Changing the password in the file does not change an existing account.

Both published ports bind to the loopback interface. Use `VERIFI_WEB_PORT` and `VERIFI_DB_PORT` in `.env` to choose different host ports if needed. `docker compose stop` stops the application and keeps the PostgreSQL volume.

## Try a screening request

1. Sign in as the local operator. Use a fictional applicant for this walkthrough.
2. Choose **New screening request**, create or select an applicant, and select an assigned package.
3. Enter a reference for the applicant's authorization and attest that it applies to this applicant and screening purpose. This captures the operator's statement. Verification or storage of the signed document is a separate workflow.
4. Submit and open the order in the list. **Queued** means saved in this portal, awaiting provider submission. A retry of the same request returns the same order.

Business behavior lives in Java services. PostgreSQL enforces relationships, tenant consistency, and uniqueness. React sends same-origin requests to `/api`; Spring validates the session and CSRF token. Each user belongs to one organization in this version. The local client administrator and member may prepare orders for their own company.

## Develop and test

The backend requires JDK 21 or later and Docker for the PostgreSQL integration tests. The frontend uses Node 22.12+ or a compatible newer release. Use the Maven wrapper so a separate Maven install is unnecessary.

```powershell
cd backend
.\mvnw.cmd verify
cd ..\frontend
npm ci
npm test
npm run build
```

To develop with hot reload, start the database with `docker compose up -d db`. Export `VERIFI_DB_PASSWORD` from the local settings into the backend process. For the seeded account also export the demo settings and set `SPRING_PROFILES_ACTIVE=local`. Start Spring with `.\mvnw.cmd spring-boot:run` from `backend` and run `npm run dev` from `frontend`. Vite proxies `/api` to the backend at `localhost:8080`. If the database runs on another port, set `VERIFI_DB_URL` accordingly.

With the Compose portal running, the browser test signs in, creates a fictional applicant and order, reloads to check persistence, and signs out. A second test changes the applicant on a mobile-size screen and checks that the earlier consent clears. Run these commands from `frontend` with Node 22.12+.

```powershell
$env:PLAYWRIGHT_SKIP_BROWSER_GC = '1'
npx playwright install chromium --only-shell
node --env-file=../.env ./node_modules/@playwright/test/cli.js test
```

The browser test defaults to port 5180. Set `VERIFI_TEST_BASE_URL` if your local port differs.

## Product decisions

The [owner's decision log](docs/owner-questions.md) tracks scope, ordering authority, consent evidence, data carryover, pricing, report access, and account lifecycle. The first implementation covers local staff-entered requests. Applicant invitations, private document upload, provider dispatch and callbacks, report delivery, billing, MFA, password recovery, and production provisioning are pending.
