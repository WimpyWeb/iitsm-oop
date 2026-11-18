# OOP Mapping & Design Pattern Justification

> **Core Question**: *How does OOP thinking build a maintainable, extensible, intelligent ITSM system?*
> By modeling real ITIL domain responsibilities as cohesive objects with enforced invariants (`Incident`, `Technician`, `SlaInfo`), replacing brittle conditionals with polymorphic abstractions (`IncidentState`, `PriorityStrategy`, `AssignmentStrategy`, `IncidentObserver`, `Notifier`, `AiTextService`), and isolating infrastructure behind adapters so new channels, categories, or AI models plug in with minimal change.

## 1. Core OOP Concepts in `com.iitsm`
| Concept | Implementation in Codebase | Purpose & Benefit |
| --- | --- | --- |
| **Encapsulation** | Private fields + validated setters (`User`, `Incident`, `Technician`, `ServiceEntity`), unmodifiable collection getters (`getSkills()`, `getInvestigationNotes()`, `getDependsOn()`), immutable `AuditEntry` (no setters), `transitionStatus(next, stateProof)` | Prevents invalid domain states (e.g., editing a `RESOLVED` incident throws `InvalidIncidentException`, negative load throws `InvalidInputException`, audit tampering is impossible). |
| **Abstraction** | `abstract class User`, interfaces `IncidentState`, `PriorityStrategy`, `AssignmentStrategy`, `IncidentObserver`, `Notifier`, `AiTextService` | Hides algorithmic and transport details behind clean behavioral contracts. |
| **Inheritance (`is-a`)** | `User -> Employee`, `User -> Technician -> TeamLead`, `User -> Administrator`; `ITSMException` hierarchy (13 subclasses); `AbstractIncidentState`; `AbstractAssignmentStrategy` | Reuses shared identity/filtering code only where true `is-a` specialization holds. |
| **Polymorphism** | `Incident.currentState().resolve(...)`, `PriorityStrategy.calculatePriority(...)`, `AssignmentStrategy.selectTechnician(...)`, `Notifier.send(...)` | Runtime behavior adapts dynamically by status, active strategy setting, or channel without `switch` bloat in services. |
| **Composition (`has-a`, lifecycle owned)** | `Incident` composes `SlaInfo`, `Resolution`, `Closure`, `Feedback`, `List<InvestigationNote>`, `List<InfoRequest>` (`CascadeType.ALL, orphanRemoval = true`) | Child value/lifecycle objects exist solely as part of their owning `Incident`. |
| **Aggregation (`has-a`, shared)** | `Team` aggregates `List<Technician>` + `TeamLead`; `Technician` aggregates `Map<String, Integer> serviceExperienceCounts` | Technicians exist independently of a specific team reorganization. |
| **Association** | `Incident -> Employee (reporter)`, `Incident -> ServiceEntity`, `Incident -> Category`, `ServiceEntity <-> ServiceEntity (dependsOn)` | Models cross-cutting references cleanly across domain entities. |

## 2. Collections Justification
- `EnumMap<IncidentStatus, IncidentState>` in `IncidentStateFactory`: O(1) array-backed lookup keyed by enum ordinal.
- `PriorityQueue<Incident>` in `IncidentService.getUnassignedQueue()`: orders unassigned `CLASSIFIED` tickets by `Priority.rank` then FIFO `createdAt`.
- `ConcurrentHashMap<String, Notifier>` in `NotifierFactory`: thread-safe O(1) channel lookup.
- `CopyOnWriteArrayList<IncidentObserver>` in `IncidentEventPublisher` & `ConcurrentLinkedDeque<AuditEntry>`/`Notification` in observers: lock-free concurrent iteration and bounded O(1) head/tail buffering.
- `LinkedHashSet` / `LinkedHashMap`: preserves deterministic insertion order for keywords, related IDs, and report series.

## 3. Design Patterns
1. **State** (`com.iitsm.domain.state.*`): Eliminates monolithic transition `if/else` blocks; adding a state or rule touches only one class.
2. **Strategy** (`com.iitsm.domain.strategy.*`): Swaps `WeightedScorePriorityStrategy` / `RuleBasedPriorityStrategy` and 5 `AssignmentStrategy` variants at runtime via `AppSetting`.
3. **Observer** (`com.iitsm.domain.observer.*`): Decouples lifecycle transitions from immutable `AuditObserver` logging and multi-channel `NotificationObserver` alerts.
4. **Factory** (`UserFactory`, `IncidentFactory`, `NotificationFactory`, `NotifierFactory`, `IncidentStateFactory`): Centralizes ID formatting (`E101`, `INC1001`) and bean/state construction.
5. **Adapter** (`EmailNotifier` over `JavaMailSender`, `GeminiAiService` over `RestClient`): Translates third-party mail/LLM APIs into domain contracts with graceful fallback.
6. **Repository** (`com.iitsm.repository.*`): Abstracts persistence and combinable `IncidentSpecifications` away from services.
