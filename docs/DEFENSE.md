# Viva Defense Q&A

**1. Why use the State pattern instead of `if/switch` on `IncidentStatus`?**
Each of the 9 lifecycle statuses encapsulates its own legal transitions (`getAllowedTransitions()`) and behavior in a dedicated class resolved from `IncidentStateFactory`'s `EnumMap`. Illegal moves like `NEW -> CLOSED` throw `InvalidStateTransitionException` by default via `AbstractIncidentState`. Adding a new state requires one class without editing existing transition blocks.

**2. Why use the Strategy pattern for Priority and Assignment?**
Priority formulas (`WeightedScorePriorityStrategy`, `RuleBasedPriorityStrategy`) and technician routing (`SkillBased`, `LoadBased`, `RoundRobin`, `ExperienceBased`, `PriorityBased`) change independently of the incident entity. Behind interfaces, administrators switch active strategies at runtime via `AppSetting` without redeploying. `AbstractAssignmentStrategy` avoids duplicating eligibility and skill-scoring helpers.

**3. Why use custom Observer (`IncidentEventPublisher`) instead of direct service calls?**
Publishing `IncidentEvent` decouples core incident state persistence from side effects like immutable auditing (`AuditObserver`) and multi-channel alerting (`NotificationObserver`). New cross-cutting reactions (e.g., Slack webhook or metrics exporter) attach as a new `IncidentObserver` bean. Core `IncidentService` stays focused strictly on workflow orchestration.

**4. Why use inheritance only for `User` (`Employee`, `Technician -> TeamLead`, `Administrator`)?**
Only `User` subtypes share a true `is-a` relationship with shared identity, credentials, and polymorphic `getDisplayIdentity()`, persisted cleanly with `@Inheritance(strategy = InheritanceType.JOINED)`. For `Incident` parts (`SlaInfo`, `Resolution`, `Closure`, `Feedback`), composition (`has-a`) avoids rigid hierarchies. `Team` and `Technician` use aggregation since technicians exist independently of teams.

**5. How do you add a new notification channel (e.g., Slack) or category?**
To add a notification channel, implement one class implementing `Notifier` (`getChannel()` returning `"SLACK"`) annotated with `@Component`; `NotifierFactory` auto-discovers it into its `ConcurrentHashMap`. Adding a category requires zero code changes—administrators create it in `/admin/catalog` (`Category` DB table) with matching keywords.

**6. How does `Incident` guarantee state integrity and prevent tampering?**
`Incident` exposes no raw `setStatus()` setter; `transitionStatus(next, stateProof)` validates that `stateProof.getStatus()` matches the current status. Collections return `Collections.unmodifiable*` views, `updateDetails()` throws `InvalidIncidentException` once `status >= RESOLVED`, and `AuditEntry` has no setters at all.

**7. How does TF-IDF similarity work offline, and why smooth IDF?**
`TfIdfSimilarityEngine` tokenizes text, strips English stopwords, applies deterministic suffix stemming (`payments -> payment`, `unavailable -> unavail`), and weights terms with smoothed IDF `1 + 0.25*ln((N+1)/(df+1))`. Smoothing prevents common domain words like `payment` from collapsing to zero weight in small corpora. Cosine `>= 0.8` within 10m by the same reporter blocks exact duplicates; `>= 0.2` within 24h links related incidents.

**8. How does Gemini integration work without breaking offline environments?**
`AiConfig` selects `LocalTfIdfAiService` by default when `GEMINI_API_KEY` is blank, ensuring zero external network calls during tests or offline demos. When a key is supplied, `GeminiAiService` calls `generateContent` via `RestClient` and catches any HTTP/parse exception to fall back immediately to `LocalTfIdfAiService` with a warning log.

**9. How does the `@Scheduled` SLA monitor handle warnings, breaches, and escalation?**
`SlaEscalationMonitor` runs every `app.sla.monitor-interval-seconds` and evaluates open incidents separately for response and resolution deadlines. At `>= 80%` elapsed it marks `AT_RISK` and warns once; past deadline it persists a single `SlaViolation` row guarded by a unique DB constraint (`uk_sla_violation_incident_type`). Unacknowledged `P1` tickets escalate every response interval across `TeamLead -> SYSTEM_ADMIN -> IT_MANAGER`.

**10. How did you avoid `LazyInitializationException` with `spring.jpa.open-in-view=false`?**
Queries use `@EntityGraph(..., type = EntityGraphType.LOAD)` so `EAGER` `@ElementCollection` fields (`keywords`, `skills`) stay loaded alongside `@ManyToOne` associations. Service `@Transactional` methods hydrate child lists (`investigationNotes`, `infoRequests`, `relatedIncidentIds`) or map directly to immutable Java `record` DTOs before returning to views or REST.

**11. How do web UI security and `/api/**` security differ?**
`SecurityConfig` defines two `@Order` chains: `/api/**` (`@Order(1)`) is `STATELESS` with HTTP Basic and CSRF disabled for REST clients; `/**` (`@Order(2)`) uses Form Login, session state, and CSRF tokens forwarded by HTMX via `htmx:configRequest`. Both share URL matchers, `@PreAuthorize`, BCrypt hashing, and `IncidentService` ownership guards.

**12. What happens if SMTP email delivery fails?**
`EmailNotifier` throws `NotificationDeliveryException` on missing mail configuration or `MailException`. `NotificationService.dispatch()` catches it inside the notification boundary, logs an immutable `"NOTIFICATION_DELIVERY_FAILED"` `AuditEntry`, and falls back to `InAppNotifier` so users never lose critical alerts and the incident transaction never rolls back.

**13. How does service dependency correlation find root causes?**
`ServiceEntity.dependsOn` models upstream dependencies (e.g., `Payment -> Database`). When `OutageCorrelator` detects `>= 3` open incidents on `Database` within 1 hour, it flags `outageFlagged = true` and inspects `affectedService.getDependsOn()` on incoming incidents, attaching `"Possible root cause: Database"` to affected `Payment` tickets.

**14. How would this system scale to thousands of concurrent users?**
Replace the H2 file URL with PostgreSQL (preserving existing DB indexes on `status`, `priority`, `service`, `technician`, `createdAt`), externalize web sessions to Redis, cache catalog/strategy lookups, move email/SMS dispatch to an async Kafka/RabbitMQ worker, and deploy stateless replicas behind a load balancer.

**15. What are the system's current limitations?**
Notifications for SMS are simulated file logs (requiring a Twilio/SNS adapter for real SMS), `open-in-view=false` requires keeping DTO hydration disciplined on new queries, and `H2` file mode supports only a single JVM process (preventing multi-instance scaling until switched to PostgreSQL).
