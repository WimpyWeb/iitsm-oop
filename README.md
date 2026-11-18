# I-ITSM — Intelligent IT Service Management & Incident Resolution System

A Spring Boot 3.3 + Java 21 object-oriented IT service management platform featuring State-driven incident lifecycles, runtime-swappable Priority & Assignment strategies, Observer-based audit/multi-channel alerting, SLA monitoring & multi-level escalation, TF-IDF duplicate/resolution intelligence, and Chart.js + HTMX analytics.

## Quick Start

```bash
# Run unit and integration tests
./mvnw test

# Start the application (H2 persistent file DB at ./data/iitsm)
./mvnw spring-boot:run
```

On Windows `cmd`:
```cmd
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

> **Note:** Never run two instances concurrently against the same `./data/iitsm` H2 file.

- **Web UI**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **H2 Console (`dev` profile only)**: http://localhost:8080/h2-console (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`, JDBC URL `jdbc:h2:file:./data/iitsm`)

## Demo Logins

All seeded accounts share the password **`Password123!`** (BCrypt-hashed on first empty-DB startup):

| Role | Code | Username | Focus |
| --- | --- | --- | --- |
| Employee | `E101`–`E104` | `emp1` .. `emp4` | Report, track, answer info, verify/reopen, feedback |
| Technician | `T101`–`T104` | `tech1` .. `tech4` | Acknowledge, investigate, resolve, decline, toggle availability |
| Team Lead | `T201`–`T202` | `lead1`, `lead2` | Technician duties + Level-1 SLA escalation target |
| System Admin | `A001` | `sysadmin` | Strategy switching, catalog/SLA CRUD, audit, Level-2 escalation |
| IT Manager | `A002` | `itmanager` | Full admin controls + Level-3 SLA escalation target |

## Demo & Environment Configuration

| Property / Env Var | Default | Purpose |
| --- | --- | --- |
| `APP_SLA_TIME_SCALE` | `1` | Shrinks all SLA durations `N×` (e.g., `60` turns 5m into 5s for live demos) |
| `APP_SLA_MONITOR_INTERVAL_SECONDS` | `30` | `@Scheduled` SLA warning/breach/escalation poll interval |
| `APP_NOTIFY_EMAIL_MODE` | `simulated` | `simulated` (writes `./data/email-notifications.log`) or `smtp` (Gmail SMTP) |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USERNAME` / `SMTP_PASSWORD` | `smtp.gmail.com` / `587` | Live SMTP credentials when `smtp` mode is enabled |
| `GEMINI_API_KEY` / `GEMINI_MODEL` | *(empty)* / `gemini-2.5-flash` | Optional Gemini REST text suggestion adapter (falls back to local TF-IDF) |

## Docker

```bash
docker build -t iitsm:latest .
docker run -p 8080:8080 -e APP_SLA_TIME_SCALE=60 iitsm:latest
```

## Architecture & Docs

- Layered packages under `com.iitsm`: `controller` → `service` → `domain` → `repository` (+ `config`, `exception`, `notification`, `intelligence`).
- Course & design documentation lives in [`docs/`](docs/).
