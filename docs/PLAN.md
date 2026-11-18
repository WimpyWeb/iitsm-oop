# I-ITSM Build Plan

## Package Tree (`com.iitsm`)
- `config/` — SecurityConfig (UI FormLogin+CSRF, `/api/**` Basic stateless), OpenApiConfig, AsyncSchedulingConfig, DataSeeder
- `domain/`
  - `model/` — User (Employee, Technician, TeamLead, Administrator), Team, Incident, SlaInfo (embeddable), SlaPolicy, SlaViolation, ServiceEntity, Category, InvestigationNote, InfoRequest, Resolution, Closure, Feedback, Notification, AuditEntry, AppSetting
  - `enums/` — IncidentStatus, Priority, SlaStatus, AdminLevel, ViolationType, InfoRequestStatus
  - `state/` — IncidentState, IncidentStateFactory, NewState, ClassifiedState, AssignedState, AcknowledgedState, InProgressState, PendingInfoState, ResolvedState, VerifiedState, ClosedState
  - `strategy/` — PriorityStrategy (WeightedScore, RuleBased), AssignmentStrategy (SkillBased, LoadBased, RoundRobin, ExperienceBased, PriorityBased)
  - `factory/` — IncidentFactory, UserFactory, NotifierFactory
  - `observer/` — IncidentEvent, IncidentObserver, IncidentEventPublisher, AuditObserver, NotificationObserver
- `intelligence/` — TfIdfSimilarityEngine (stopwords+stemmer), KeywordClassifier, TechnicianRecommender, OutageCorrelator, SlaRiskPredictor, AiTextService (LocalTfIdfAiService, GeminiAiService)
- `notification/` — Notifier, InAppNotifier, EmailNotifier (simulated/JavaMail SMTP), SmsNotifier
- `exception/` — ITSMException + 13 subclasses, GlobalExceptionHandler (`@ControllerAdvice` HTML + JSON)
- `repository/` — Spring Data JPA repositories + IncidentSpecifications
- `service/` — UserService, IncidentService, AdminCatalogService, SlaEscalationMonitor (`@Scheduled`), ReportService, AuditService, NotificationService
- `controller/` — AuthController, EmployeeController, TechnicianController, AdminController, NotificationController, `api/` (IncidentRestController, ReportRestController) + `dto/` (Java records)
- `resources/` — `application.yml`, `templates/` (Thymeleaf + HTMX + Pico.css + Chart.js), `static/`

## Build Order (Phases)
1. **Phase (a) Domain & Unit Tests**: `pom.xml`, Maven wrapper, `.gitignore`, `domain/*` (entities, enums, State pattern, Strategy, Factory, Observer), `exception/*`, `intelligence/TfIdfSimilarityEngine`, unit tests for states, priorities, TF-IDF similarity (`./mvnw test`).
2. **Phase (b) Persistence & Services**: `repository/*` (with DB indexes + Specifications), `notification/*` (InApp, Email, SMS adapters + fallback), `intelligence/*` (AI service, recommender, correlator), `service/*` (transactional pipeline, unassigned PriorityQueue, SLA/escalation), integration tests (`./mvnw test`).
3. **Phase (c) Controllers & Role-Based UI**: `config/SecurityConfig`, `GlobalExceptionHandler`, `controller/*`, Thymeleaf/HTMX/Pico.css views for Employee, Technician, Admin, Notification bell, MockMvc security & flow tests (`./mvnw test`).
4. **Phase (d) Intelligence, Scheduler, Reports & REST**: `SlaEscalationMonitor` (`@Scheduled` with `time-scale`), Chart.js + HTMX live reports & recurring problems, `/api/v1/**` REST controllers + Swagger, tests (`./mvnw test`).
5. **Phase (e) Seed, Docker, CI & README**: `DataSeeder` (services + dependency graph, T101–T104, TeamLead, admins, E101+, demo incident), `Dockerfile`, `.github/workflows/ci.yml`, `README.md` (`./mvnw test`).
6. **Phase (f) Course Documentation**: `docs/` (`ASSUMPTIONS`, `PROBLEM_ANALYSIS`, `REQUIREMENTS`, `ARCHITECTURE`, `UML`, `DB_DESIGN`, `TECH_STACK`, `SECURITY`, `OOP_MAPPING`, `INNOVATION`, `TEST_CASES`, `DEMO_SCRIPTS`, `TRACEABILITY`, `TEAM`, `DEFENSE`, `REPORT`).
