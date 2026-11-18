# Security Architecture

## 1. Authentication & Password Policy
- **BCrypt Hashing**: `PasswordConfig` configures `BCryptPasswordEncoder`; `DataSeeder` and `UserService` hash all passwords at creation.
- **Password Minimum Length**: Enforced via Bean Validation (`@Size(min = 8)`) on `RegisterForm` and `CreateUserForm` and in `UserService.validateNewUserCredentials`.
- **Self-Registration Safety**: `POST /register` calls `UserService.registerEmployee(...)`, which can only create `Role.EMPLOYEE` (`E101+`); `Technician`, `TeamLead`, and `Administrator` creation is restricted to `/admin/users`.

## 2. Dual Security Filter Chains (`SecurityConfig`)
| Chain | Matcher | Session & CSRF | Auth Mechanism |
| --- | --- | --- | --- |
| `@Order(1)` API | `/api/**` | `STATELESS`, CSRF disabled | HTTP Basic (`CustomUserDetailsService`) |
| `@Order(2)` Web UI | `/**` | Stateful session, CSRF enabled (except `/h2-console/**`) | Form Login (`/login`), Logout (`/logout`) |

- **HTMX + CSRF**: `fragments/layout.html` embeds `<meta name="_csrf">` and `<meta name="_csrf_header">` and attaches the token automatically via `htmx:configRequest`.

## 3. Authorization & Ownership Enforcement
- **URL Guards**: `/admin/**` requires `ROLE_ADMIN`; `/technician/**` requires `ROLE_TECHNICIAN` or `ROLE_ADMIN`; `/employee/**` requires `ROLE_EMPLOYEE` or `ROLE_ADMIN`.
- **Method Security (`@PreAuthorize`)**: Enforced on `EmployeeController`, `TechnicianController`, `AdminController`, `ReportController`, `IncidentRestController`, and `ReportRestController`.
- **Record Ownership (`IncidentService.ensureCanAccessIncident`)**:
  - `EMPLOYEE` can view/edit/verify/reopen only incidents where `reporter.userCode == actor.userCode` (else `403 UnauthorizedException`).
  - `TECHNICIAN` accesses assigned incidents (or `TeamLead`/escalated target); search APIs scope `reporterCode` or `technicianCode` automatically.

## 4. Input, Injection & Console Hardening
- **XSS Prevention**: Thymeleaf escapes all `th:text` expressions by default.
- **SQL Injection Prevention**: Parameterized Spring Data JPA queries and Criteria `IncidentSpecifications` avoid string-concatenated SQL.
- **H2 Console Isolation**: `spring.h2.console.enabled` is `false` by default and enabled only under the `dev` Spring profile in `application.yml`.
