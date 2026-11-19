# Demo Readiness Check (Group 83)

Each of the 3 demonstration workflows runs end-to-end against seeded data (`DataSeeder`: 30 incidents, 8 services, 9 categories, 12 users, password `Password123!`) in **≤ 5 minutes** per workflow.

## Pre-Demo Launch (Clean Start + Accelerated SLA Clock)
```cmd
rmdir /s /q data 2>nul
set APP_SLA_TIME_SCALE=60
set APP_SLA_MONITOR_INTERVAL_SECONDS=5
.\mvnw.cmd spring-boot:run
```
Wait for `I-ITSM seeding complete.` -> Open `http://localhost:8080`.

---

## Workflow 1 Check (~3 minutes): Report -> Prioritize -> Assign -> Notify
1. **Login**: Open `/login` -> enter **`emp1`** / **`Password123!`** -> click **Sign In** (lands on **Employee Portal**, `/employee/dashboard`).
2. **Navigate**: Click **Report Incident** in navbar (`/employee/incidents/new`).
3. **Live Suggestions**: Select Service **`Payment`**, leave Category **`Auto-detect`**, type:
   `"Payment gateway checkout throwing HTTP 502 errors for customers"`
   -> HTMX `/incidents/suggest` renders **AI Suggested Category: Application | Suggested Service: Payment** and lists related **`INC1001`**.
4. **Submit**: Set Impact `5`, Urgency `5`, Affected Users `120` -> click **Submit Incident**.
5. **Verify**: Redirects to `/incidents/INC1031` showing **`P1 Critical` (score `187.5`)**, **`ASSIGNED`** to **`Technician T101`**, `Audit Timeline` (`CREATED -> CLASSIFIED -> ASSIGNED`), Bell unread alert incremented, and `./data/email-notifications.log` + `./data/sms-notifications.log` written.

---

## Workflow 2 Check (~4 minutes): Investigate -> SLA Breach & Escalation -> Resolve -> Verify -> Close
1. **Wait 6–12 seconds** (`APP_SLA_TIME_SCALE=60` shrinks `P1` 5m response deadline to 5s; `SlaEscalationMonitor` polls every 5s):
   `INC1031` triggers **80% warning (`AT_RISK`)** -> **`BREACHED`** + `SlaViolation` -> **Escalation Level 1 (`T201`) -> Level 2 (`A001`) -> Level 3 (`A002`)** within ~15 seconds.
2. **Admin Verification**: Login **`sysadmin`** / **`Password123!`** (lands on **Admin Command Center**, `/admin/dashboard`) -> click **SLA Violations** (`/admin/sla-violations`) and **Audit Log** (`/admin/audit`) -> confirm `RESPONSE` violation and `ESCALATED` entries appear.
3. **Technician Resolution**: Login **`tech1`** / **`Password123!`** (lands on **Technician Workbench**, `/technician/dashboard`) -> open `INC1031` -> click **Acknowledge Incident** -> click **Start Investigation** -> check **Suggested Resolution** box -> enter Summary + Steps -> click **Mark Resolved** (`RESOLVED`).
4. **Employee Verify & Close**: Login **`emp1`** -> open `INC1031` -> click **Confirm Fix (Verify)** (`VERIFIED`) -> submit **5/5 Feedback** -> login **`tech1`** -> click **Close Verified Incident** (`CLOSED`).

---

## Workflow 3 Check (~4 minutes): Duplicates, Dependency Outage Correlation & Live Analytics
1. **Duplicate Detection**: As **`emp1`**, open `/employee/incidents/new` -> select `Payment`, type `"The payment application is unavailable and users are unable to complete transactions."` -> click **Submit Incident** -> blocked cleanly with inline warning referencing **`INC1001`** (`DuplicateIncidentException`).
2. **Related Payment Check**: Submit `"Payment transaction failing"` and `"Unable to make payment"` as **`emp2`** -> both succeed and link `INC1001` under **Related Incidents**.
3. **Dependency Root-Cause Correlation**: Submit 3 distinct `Database` incidents (`"Database connection pool exhausted"`, `"SQL replica read lag spike"`, `"Database deadlock on orders"`). On the 3rd, banner flags **Possible Service Outage**. Submit a `Payment` ticket -> banner shows **`Possible root cause: Database`** (from `Payment -> Database` in `/admin/catalog`).
4. **Live Analytics & Swagger**: Login **`sysadmin`** -> click **Reports** (`/reports`) -> verify Chart.js charts + recurring services (`Payment`/`Database`) + avg feedback; open `/swagger-ui.html` and test `GET /api/v1/reports/summary` with `sysadmin` / `Password123!`.
