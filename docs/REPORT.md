# I-ITSM Project Report (CS F213)

## 1. Problem Analysis
Manual IT helpdesks lack structured triage, automated routing, SLA accountability, and duplicate storm detection. I-ITSM solves this with an intelligent object-oriented service management platform covering incident intake, priority scoring, technician assignment, multi-channel alerting, SLA monitoring, escalation, dependency correlation, and live operational analytics. Key stakeholders are Employees (`E101+`), Technicians (`T101+`), Team Leads (`T201+`), System Admins (`A001`), and IT Managers (`A002`). See [PROBLEM_ANALYSIS.md](PROBLEM_ANALYSIS.md) and [ASSUMPTIONS.md](ASSUMPTIONS.md).

## 2. Requirements
- **Role Workflows**: Employees self-register, submit/edit pre-resolved incidents, answer info requests, verify/reopen, and rate fixes. Technicians manage availability, acknowledge, investigate, request info, resolve, decline, or close. Admins manage users, teams, DB-driven categories, services/dependencies, SLA policies, runtime strategies, audit logs, and SLA violations.
- **Priority Scoring**: Weighted formula $\text{score} = \text{impact} \times \text{urgency} \times \text{criticality} \times \text{usersFactor}$ (`1.0`/`1.2`/`1.5` tiers) with externalized thresholds (`P1>=60`, `P2>=30`, `P3>=10`, else `P4`), paired with `RuleBasedPriorityStrategy` (`P1` for Security or Criticality-5 high-impact).
- **SLA & Escalation**: Default `P1` 5m/30m, `P2` 15m/2h, `P3` 1h/8h, `P4` 4h/24h (overridable per service, scaled by `app.sla.time-scale`), 80% `AT_RISK` warning, single `SlaViolation` recording, and 3-level escalation (`TeamLead -> SYSTEM_ADMIN -> IT_MANAGER`). See [REQUIREMENTS.md](REQUIREMENTS.md).

## 3. Architecture
I-ITSM enforces strict `controller -> service -> domain -> repository` separation (`open-in-view=false`, `EntityGraphType.LOAD`, `Domain` free of web dependencies). Dual Spring Security chains protect the Thymeleaf/HTMX web UI with stateful FormLogin + CSRF while securing `/api/v1/**` with stateless HTTP Basic. See [ARCHITECTURE.md](ARCHITECTURE.md).

## 4. OOP Design & UML Diagrams
Every core OOP principle and six GoF/enterprise patterns are explicitly applied:
- **Encapsulation**: private fields, validated setters, unmodifiable collections, immutable `AuditEntry`, state-proof transitions.
- **Inheritance & Polymorphism**: `abstract User -> Employee | Technician (-> TeamLead) | Administrator`; interfaces `IncidentState`, `PriorityStrategy`, `AssignmentStrategy`, `IncidentObserver`, `Notifier`, `AiTextService`.
- **Patterns**: State (`EnumMap` of 9 states), Strategy (2 priority + 5 assignment, runtime-swappable), Observer (`IncidentEventPublisher` -> `AuditObserver`, `NotificationObserver`), Factory (`UserFactory`, `IncidentFactory`, `NotificationFactory`, `NotifierFactory`), Adapter (`EmailNotifier`, `GeminiAiService`), Repository (`JpaSpecificationExecutor`).
See [OOP_MAPPING.md](OOP_MAPPING.md) and [UML.md](UML.md).

## 5. Tech Stack & Production Scaling
Built on Java 21, Spring Boot 3.3.5, Spring Data JPA, H2 persistent file DB (`./data/iitsm`), Spring Security 6, Thymeleaf + HTMX + Pico.css + Chart.js, `springdoc-openapi`, and JUnit 5 + MockMvc. Production scaling switches H2 to PostgreSQL, caches catalog/strategy reads in Redis, offloads email/SMS via Kafka/RabbitMQ, and scales stateless app nodes horizontally. See [TECH_STACK.md](TECH_STACK.md).

## 6. Database Design & Security
Uses `JOINED` inheritance for users, indexed `incidents` table (`status`, `priority`, `service_id`, `technician_id`, `createdAt`), and unique violation constraints (`uk_sla_violation_incident_type`). Security enforces BCrypt (`>=8` char passwords), CSRF (HTMX header injection), XSS escaping, parameterized JPA queries, role checks, and record ownership validation. See [DB_DESIGN.md](DB_DESIGN.md) and [SECURITY.md](SECURITY.md).

## 7. Innovation & Intelligence Features
Includes TF-IDF cosine similarity (stopword + stemming) for duplicate blocking (`>=0.8`) and related linking (`>=0.2`), live HTMX keyword category/service suggestions, transparent technician scoring (`TechnicianRecommender`), past-resolution matching + Gemini REST adapter with local fallback, predictive SLA risk (`SlaRiskPredictor`), dependency graph root-cause correlation (`OutageCorrelator`), and Chart.js + HTMX recurring problem analytics. See [INNOVATION.md](INNOVATION.md).

## 8. Testing & Verification
Covered by 50 JUnit 5 / Mockito / MockMvc tests across 10 test suites validating domain invariants, state transitions, boundary priority scores, TF-IDF examples (`"Payment application unavailable"` / `"Payment transaction failing"` / `"Unable to make payment"`), SLA monitoring & 3-level escalation, SMTP fallback, security/CSRF/ownership, REST/Swagger, and cold-start `DataSeeder` idempotency. See [TEST_CASES.md](TEST_CASES.md) and [TRACEABILITY.md](TRACEABILITY.md).

## 9. Demo Scenarios
Three structured demo workflows (`APP_SLA_TIME_SCALE=60`) exercise intake/notification, accelerated SLA escalation & resolution lifecycle, and duplicate/dependency outage correlation with 15–20 minute team speaker slots. See [DEMO_SCRIPTS.md](DEMO_SCRIPTS.md).

## 10. Evaluation & Limitations
- **Strengths**: Clean separation of domain patterns from web/persistence plumbing, zero-JS-build reactive UI, deterministic offline AI + optional Gemini adapter, and full auditability.
- **Limitations**: SMS delivery and default email (`simulated` mode) write to local logs unless configured for live gateways/SMTP; H2 file mode restricts deployment to a single JVM process until migrated to PostgreSQL; `LocalTfIdfAiService` relies on keyword/corpus overlap when `GEMINI_API_KEY` is unset.

## 11. Team Contributions (Group 83)
- **Vinayak Nath (`2024B2AD1046H`)**: Domain model, `User` inheritance, `IncidentState` pattern, `ITSMException` hierarchy, `IncidentService`, `DataSeeder`, Docker/CI.
- **Anubhav Amarnath Savur (`2024B3AD0581H`)**: Priority & Assignment strategies, `TfIdfSimilarityEngine`, `KeywordClassifier`, `AiTextService`, `OutageCorrelator`, `SlaEscalationMonitor`, `Observer` & `Notifier` adapters.
- **Sai Yuvraj Guntha (`2024B3AD0540H`)**: JPA repositories & `Specification` search, Spring Security, Thymeleaf + HTMX + Pico.css UI, `ReportService` (Chart.js), `/api/v1` REST + Swagger, test suites & documentation.

See [TEAM.md](TEAM.md) and [DEFENSE.md](DEFENSE.md).
