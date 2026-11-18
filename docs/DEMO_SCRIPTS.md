# Demo Scripts & Presentation Timeline (18 Minutes)

## Setup for Live Demo (Accelerated SLA Clock)
Start the server from a clean or seeded DB with `60×` SLA acceleration (`5m -> 5s`) and `5s` monitor polling:
```cmd
set APP_SLA_TIME_SCALE=60
set APP_SLA_MONITOR_INTERVAL_SECONDS=5
.\mvnw.cmd spring-boot:run
```
Open `http://localhost:8080` and `http://localhost:8080/swagger-ui.html`. All demo passwords: **`Password123!`**.

## 15–20 Minute Team Presentation Timeline (Group 83)
| Slot | Member | Module Explained | Live Demo Action |
| --- | --- | --- | --- |
| 0:00–6:00 | **Vinayak Nath** (`2024B2AD1046H`) | Problem Analysis, `User` Inheritance, `IncidentState` Pattern (`NEW -> CLOSED`), `IncidentService`, `DataSeeder` | Introduce Group 83, show `/login` demo logins & `INC1001`, run **Workflow 1** (Report -> Prioritize -> Assign) |
| 6:00–12:00 | **Anubhav Amarnath Savur** (`2024B3AD0581H`) | `PriorityStrategy` / `AssignmentStrategy`, `TfIdfSimilarityEngine`, `SlaEscalationMonitor`, `Observer` & `Notifier` Adapters | Run **Workflow 2** (Accelerated SLA Warning/Breach/Escalation -> Resolve -> Verify -> Close) & `./data/email-notifications.log` |
| 12:00–18:00 | **Sai Yuvraj Guntha** (`2024B3AD0540H`) | Dependency Correlation (`OutageCorrelator`), `ReportService` (Chart.js + HTMX), Spring Security, `Specification` Search, REST/Swagger & Tests | Run **Workflow 3** (Duplicates + `Database -> Payment` Outage + `/reports`), Swagger `/api/v1/reports/summary`, `/admin/audit`, and `./mvnw test` |

## Workflow 1: Report -> Prioritize -> Assign -> Notify
1. Sign in as **`emp1`** (`Employee E101`) -> click **Report Incident** (`/employee/incidents/new`).
2. Select Service **`Payment`**, leave Category blank, type: *"Payment gateway checkout keeps throwing 502 error for customers"*.
3. Pause 1s: HTMX `/incidents/suggest` displays **AI Suggested Category: Application | Suggested Service: Payment** and flags related `INC1001`.
4. Set Impact=`5`, Urgency=`5`, Affected Users=`120` -> Click **Submit Incident**.
5. Verify redirect to `/incidents/INC1031`: `P1 Critical` (`score 187.5`), status `ASSIGNED` to `T101`, `Audit Timeline` shows `CREATED -> CLASSIFIED -> ASSIGNED`, and check Bell + `./data/email-notifications.log`.

## Workflow 2: Investigate -> SLA Warning & Escalation -> Resolve -> Verify -> Close
1. Wait ~6 seconds with `APP_SLA_TIME_SCALE=60`: `SlaEscalationMonitor` marks `INC1031` `AT_RISK` at 80%, then `BREACHED`, records `SlaViolation`, and escalates `Level 1 (T201) -> Level 2 (A001) -> Level 3 (A002)`.
2. Sign in as **`sysadmin`** -> open **SLA Violations** (`/admin/sla-violations`) and **Audit Log** (`/admin/audit`) to show single breach + escalation entries.
3. Sign in as **`tech1`** -> open `INC1031` -> click **Acknowledge Incident** -> click **Start Investigation** -> view **Suggested Resolution** -> submit Resolve summary/steps (`RESOLVED`).
4. Sign in as **`emp1`** -> open `INC1031` -> click **Confirm Fix (Verify)** -> submit **5/5 Feedback**.
5. Sign in as **`tech1`** -> click **Close Verified Incident** (`CLOSED`).

## Workflow 3: Duplicates, Outage Correlation & Dependency Root Cause
1. As **`emp1`**, open `/employee/incidents/new` -> submit *"The payment application is unavailable and users are unable to complete transactions"* -> blocked with `DuplicateIncidentException` warning linking existing incident.
2. As **`emp1`/`emp2`**, submit 3 distinct `Database` incidents (`"Database deadlock..."`, `"SQL replica lag..."`, `"Connection pool timeout..."`). On the 3rd, `outageFlagged = true` appears (`Possible Service Outage`).
3. Submit a new `Payment` incident (`"Checkout card processing slow"`): banner displays **`Possible root cause: Database`** via `Payment -> Database` dependency in `/admin/catalog`.
4. Open `/reports` as `sysadmin` to view updated Chart.js charts, recurring problems, and HTMX auto-refreshing KPIs.
