# Technology Stack & Scaling Plan

## 1. Selected Stack & Justifications
| Layer / Tool | Choice | Justification |
| --- | --- | --- |
| **Language** | Java 21 | Strong static typing, sealed/record DTOs, interfaces, abstract classes, and pattern matching `switch` for clean OOP modeling. |
| **Framework** | Spring Boot 3.3.5 + Spring Data JPA + Spring Security | Rapid DI, `@Transactional` safety, JPA Specifications, BCrypt, CSRF, and dual filter chains without boilerplate. |
| **Database** | H2 (file mode `./data/iitsm`) | Zero-install persistent relational DB during development/grading; standard JPA/SQL ensures drop-in PostgreSQL migration. |
| **UI** | Thymeleaf + HTMX + Pico.css + Chart.js CDN | Server-rendered HTML with reactive partial updates (bell polling, live AI/duplicate hints, report refresh) and zero JS build pipeline. |
| **API & Docs** | Spring Web + springdoc-openapi 2.6 | Reuses the exact `@Transactional` service layer for `/api/v1/**` with interactive Swagger UI at `/swagger-ui.html`. |
| **Testing** | JUnit 5 + Mockito + Spring MockMvc | Fast unit tests for domain patterns plus full Spring/H2/Security integration & MockMvc smoke coverage. |

## 2. Alternatives Considered
- **React/Angular SPA vs. Thymeleaf + HTMX**: SPA requires Node/Vite toolchain and duplicated client validation; HTMX delivers dynamic UX directly from Spring controllers.
- **NoSQL (MongoDB) vs. Relational JPA (H2)**: Incident lifecycles, `JOINED` user inheritance, SLA foreign keys, and `Specification` queries fit relational ACID guarantees better.
- **Lombok vs. Explicit Java**: Avoiding Lombok makes getters, validated setters, unmodifiable collection wrappers, and immutable `AuditEntry` visibly clear for CS F213 OOP evaluation.

## 3. How Java Supports OOP in I-ITSM
- **Interfaces & Polymorphism**: `IncidentState`, `PriorityStrategy`, `AssignmentStrategy`, `IncidentObserver`, `Notifier`, `AiTextService` enable runtime dispatch and open/closed extensibility.
- **Inheritance (`is-a`)**: Abstract `User` (`Employee`, `Technician -> TeamLead`, `Administrator`) enforces polymorphic `getDisplayIdentity()` while JPA `InheritanceType.JOINED` preserves normalized tables.
- **Encapsulation**: Private fields, validated domain setters throwing `InvalidInputException`, `Collections.unmodifiable*` views, and state-proof transitions (`transitionStatus(next, stateProof)`).

## 4. Production Scaling Plan
1. **Database Migration**: Swap H2 URL/driver to managed **PostgreSQL** (`spring.datasource.url`) with Flyway/Liquibase migrations and existing DB indexes (`status`, `priority`, `service_id`, `technician_id`, `created_at`).
2. **Caching & Read Offloading**: Add **Redis** (`@Cacheable`) for catalog `Category`, `ServiceEntity`, `SlaPolicy`, and `AppSetting` lookups plus read replicas for `ReportService` analytics.
3. **Async Event Bus**: Replace synchronous `InApp/Email/SMS` observer dispatch with **Kafka / RabbitMQ** topics so SMTP latency never blocks HTTP request threads.
4. **Stateless Horizontal Scaling**: Application services are stateless (`open-in-view=false`, `SessionCreationPolicy.STATELESS` on `/api/**`); externalize web sessions to **Spring Session + Redis** behind a load balancer and extract `Notification` / `AI` into separate microservices.
