# Demo Scripts & Presentation Timeline (18 Minutes)

## Setup for Live Demo (Accelerated SLA Clock)
Start the server from a clean or seeded DB with `60×` SLA acceleration (`5m -> 5s`) and `5s` monitor polling:
```cmd
set APP_SLA_TIME_SCALE=60
set APP_SLA_MONITOR_INTERVAL_SECONDS=5
.\mvnw.cmd spring-boot:run
```
Open `http://localhost:8080` and `http://localhost:8080/swagger-ui.html`. All demo passwords: **`Password123!`**.

## 15–20 Minute Team Presentation Timeline
| Slot | Member | Module Explained | Live Demo Action |
| --- | --- | --- | --- |
| 0:00–2:30 | Member 1 (Lead) | Problem Analysis, Domain Entities, User Hierarchy (`User -> Employee/Technician/TeamLead/Admin`) | Introduce team, open `/login` demo credentials, explain `INC1001` Payment scenario |
| 2:30–6:30 | Member 2 | Intake Pipeline, `PriorityStrategy` (`WEIGHTED`/`RULE_BASED`), `KeywordClassifier`, `TfIdfSimilarityEngine` | **Workflow 1** (Report -> Prioritize -> Assign -> Notify) |
| 6:30–11:00 | Member 3 | State Pattern (`IncidentState`), `SlaEscalationMonitor`, Multi-Level Escalation, `Observer` + `Notifier` Adapters | **Workflow 2** (Investigate -> SLA Breach/Escalation -> Resolve -> Verify -> Close) |
| 11:00–14:30 | Member 4 | Outage Correlation, `ServiceEntity.dependsOn` Root Cause, `ReportService` (Chart.js + HTMX) | **Workflow 3** (Duplicates + Dependency Outage + Live Reports) |
| 14:30–17:30 | Member 5 | Spring Security (CSRF, BCrypt, Roles), JPA Specifications Search, REST `/api/v1` + Swagger + CI/Docker | Run Swagger `/api/v1/reports/summary`, `/admin/audit`, `/admin/catalog` Mermaid graph, show `./mvnw test` green |

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
