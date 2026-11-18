# System Requirements

## 1. Functional Requirements by Role
- **Employee (`ROLE_EMPLOYEE`)**: Self-register (`Employee` only), login, submit incidents with live category/service/duplicate suggestions, view own incidents, edit details while `status < RESOLVED`, answer `PENDING_INFO` requests, add notes, verify or reopen `RESOLVED` tickets, and submit 1–5 feedback.
- **Technician (`ROLE_TECHNICIAN`)**: View assigned queue & skill-recommended unassigned incidents, toggle availability, claim unassigned tickets, acknowledge (`ASSIGNED -> ACKNOWLEDGED`), start progress, add notes, request info (`IN_PROGRESS -> PENDING_INFO`), view AI resolution hints, resolve (`IN_PROGRESS -> RESOLVED`), decline assignment with reason, override priority, close `VERIFIED` tickets.
- **Team Lead (`TeamLead extends Technician`)**: All technician capabilities plus team oversight and Level-1 SLA escalation receipt.
- **Administrator (`ROLE_ADMIN`, `SYSTEM_ADMIN` / `IT_MANAGER`)**: Provision users/technicians/team leads/admins, CRUD teams/categories/services/dependencies/SLA policies, switch `PriorityStrategy` and `AssignmentStrategy` at runtime, manually assign/reassign, view global audit log, view persisted `SlaViolation`s, view reports & recurring problems.

## 2. Non-Functional Requirements
- **Security**: BCrypt password hashing (min 8 chars), Bean Validation, CSRF on web UI (HTMX header forwarding), stateless HTTP Basic on `/api/**`, role & ownership authorization, XSS/SQLi protection.
- **Scalability**: `Pageable` pagination on all lists, DB indexes on `status`, `priority`, `service_id`, `technician_id`, `createdAt`, `open-in-view=false` with `EntityGraphType.LOAD`.
- **Reliability & Extensibility**: Externalized configuration via env vars, `ITSMException` hierarchy with `@ControllerAdvice`, DB-driven catalog (adding categories requires zero code changes).

## 3. Priority Calculation Algorithms & Rationale
### Weighted Score Strategy (`WeightedScorePriorityStrategy`, default)
$$\text{score} = \text{impact} \times \text{urgency} \times \text{businessCriticality} \times \text{usersFactor}$$
- `usersFactor`: `1.0` (`< 10` users), `1.2` (`10–99` users), `1.5` (`>= 100` users).
- Configurable thresholds (`application.yml`): `P1 >= 60` (**Critical**), `P2 >= 30` (**High**), `P3 >= 10` (**Medium**), else `P4` (**Low**).
- **Rationale**: Multiplicative scaling balances technical severity (`impact * urgency`) with business exposure (`businessCriticality * usersFactor`), ensuring core services like `Payment` (criticality 5) escalate quickly when many users are affected.

### Rule-Based Strategy (`RuleBasedPriorityStrategy`)
- Promotes to `P1` immediately if `(criticality == 5 && impact >= 4)`, `(category == Security && impact >= 4)`, or `(affectedUsers >= 100 && urgency >= 4)`.
- **Rationale**: Captures non-linear compliance and security emergencies where a low urgency estimate would otherwise delay response.

## 4. SLA & Escalation Rules
- Default policies: `P1` 5m/30m, `P2` 15m/2h, `P3` 1h/8h, `P4` 4h/24h; per-service overrides take precedence.
- `app.sla.time-scale` shrinks all SLA durations `N×` for live demonstrations.
- At `>= 80%` elapsed: `AT_RISK` warning emitted once. Past deadline: `SlaViolation` (`RESPONSE` or `RESOLUTION`) persisted once (`BREACHED`).
- Unacknowledged incidents meeting `escalation.min-priority` escalate across levels: `0 (Tech) -> 1 (TeamLead) -> 2 (SYSTEM_ADMIN) -> 3 (IT_MANAGER)` per response interval.
