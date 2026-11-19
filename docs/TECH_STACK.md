# Technology Stack, Trade-offs & Scaling Plan

## 1. Why Java 21?
Java 21 provides strict static typing, interfaces, abstract base classes, `EnumMap`, `record` DTOs, and pattern-matching `switch` expressions, allowing every ITSM design pattern (`State`, `Strategy`, `Observer`, `Factory`, `Adapter`) to be verified at compile time with mature enterprise tooling.

## 2. Why Thymeleaf + HTMX + Pico.css?
Thymeleaf renders escaped HTML directly on the server, HTMX adds reactive partial updates (live category/duplicate hints, 15s notification bell polling, live KPI refresh) using HTML attributes instead of custom JavaScript, and Pico.css delivers clean styling without Node.js, npm, or bundler build steps.

## 3. Why Spring Boot 3.3?
Spring Boot 3.3 (`spring-boot-starter-parent` 3.3.5) unifies Dependency Injection, `@Transactional` service boundaries, Spring Data JPA `Specification` queries, Spring Security (BCrypt, CSRF, HTTP Basic), Bean Validation, `@Scheduled` jobs, `JavaMailSender`, and `springdoc-openapi` Swagger UI into one self-contained runnable JAR.

## 4. Why H2 (File Mode `./data/iitsm`)?
H2 file mode requires zero external database installation during development, automated CI (`mem` profile), and live university grading while persisting all seeded and runtime data across restarts and speaking standard SQL/JPA for seamless database migration.

## 5. Why Layered Architecture (`controller -> service -> domain -> repository`)?
Strict unidirectional layering keeps HTTP/CSRF/Thymeleaf concerns in `controller`, transaction/orchestration logic in `service`, pure OOP invariants and GoF patterns in `domain` (zero web imports), and persistence in `repository`—making every layer independently unit- and integration-testable.

## 6. Alternatives Considered
| Area | Chosen | Alternative Considered | Reason Rejected |
| --- | --- | --- | --- |
| Frontend | Thymeleaf + HTMX + Pico.css | React / Angular SPA | Requires Node toolchain, duplicate client validation, and CORS/token boilerplate |
| Database | H2 File + JPA | MongoDB / Raw JDBC | Relational ACID constraints & `JOINED` inheritance suit ITSM; raw JDBC lacks `Specification` safety |
| Boilerplate | Explicit Java + DTO records | Lombok | Lombok hides encapsulation/validation and obscures OOP mechanics in coursework review |

## 7. Pros & Cons of the Stack
- **Pros**: Single `./mvnw spring-boot:run` startup, compile-time OOP contracts, zero frontend build step, full CSRF/BCrypt/SQLi/XSS protection out of the box, and 56 automated tests running in ~40s.
- **Cons**: H2 file mode permits only one JVM process at a time; synchronous observer notifications can add slight latency under heavy SMTP traffic; `open-in-view=false` demands careful `@EntityGraph`/`hydrateAssociations` handling.

## 8. How Java Supports OOP in I-ITSM
- **Encapsulation**: Private entity fields, validated setters throwing `InvalidInputException`, `Collections.unmodifiable*` collection views, immutable `AuditEntry` (no setters), and state-proof status transitions (`transitionStatus(next, stateProof)`).
- **Inheritance & Abstraction**: Abstract `User` specialized by `Employee`, `Technician -> TeamLead`, and `Administrator`; interfaces `IncidentState`, `PriorityStrategy`, `AssignmentStrategy`, `IncidentObserver`, `Notifier`, and `AiTextService`.
- **Polymorphism & Composition**: `Incident` delegates transitions to `IncidentStateFactory.forStatus(status)`, composes `SlaInfo`/`Resolution`/`Closure`/`Feedback`, and aggregates `Team -> Technician`.

## 9. Scaling Plan to Many Concurrent Users
1. **Database Upgrade**: Switch H2 to managed **PostgreSQL** (`spring.datasource.url`) with Flyway migrations, leveraging existing indexes on `status`, `priority`, `service_id`, `technician_id`, and `created_at`.
2. **Distributed Caching**: Add **Redis** (`@Cacheable`) for read-heavy `Category`, `ServiceEntity`, `SlaPolicy`, and `AppSetting` catalogs plus read-replica routing for `ReportService`.
3. **Async Event Messaging**: Replace synchronous `EmailNotifier`/`SmsNotifier` calls inside `IncidentEventPublisher` with **Kafka / RabbitMQ** queues so external mail/SMS latency never blocks user threads.
4. **Horizontal & Microservice Split**: Because services are stateless (`open-in-view=false`, stateless `/api/**`), externalize UI sessions via **Spring Session + Redis** behind a load balancer and extract `Notification` and `AI` adapters into independent workers.
