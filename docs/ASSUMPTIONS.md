# Assumptions
1. Persistence uses H2 file mode (`./data/iitsm`); no two app instances run against the same H2 file concurrently.
2. Email (`app.notify.email.mode=simulated`) and SMS notifications default to simulated file/log output; SMTP activates when configured.
3. AI text features default to `LocalTfIdfAiService`; `GeminiAiService` activates only when `GEMINI_API_KEY` is provided and falls back on error.
4. Priority thresholds (`P1>=60`, `P2>=30`, `P3>=10`) and SLA scale (`app.sla.time-scale=1`) are externalized in `application.yml`.
5. Demo passwords are BCrypt-hashed at startup via `DataSeeder` when the database is empty.
6. Self-registration creates `Employee` accounts only; `Technician`, `TeamLead`, and `Administrator` accounts are admin-managed.
7. Maven Wrapper (`mvnw` / `mvnw.cmd`) delegates to system `mvn` if `maven-wrapper.jar` is absent offline.
8. Host environment PowerShell failed (`InitialSessionState`, exit `-65536`), so test execution is verified via local/CI `./mvnw test`.
