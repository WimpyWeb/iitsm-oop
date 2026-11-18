# Problem Analysis

## 1. Problem Statement
Manual IT helpdesks suffer from unstructured ticket triage, misrouted assignments, missed SLA response/resolution deadlines, duplicate ticket storms during service outages, and lack of historical auditability. I-ITSM automates incident intake, priority scoring, technician routing, SLA tracking, escalation, and operational analytics using clean object-oriented design.

## 2. Stakeholders
| Stakeholder | Role in System | Primary Needs |
| --- | --- | --- |
| **Employee** (`E101+`) | Incident Reporter | Fast submission, live duplicate/category hints, transparent status timeline, verification & feedback |
| **Technician** (`T101+`) | First-Line Support | Skill-aligned queue, clear SLA countdowns, investigation notes, AI resolution hints, workload control |
| **Team Lead** (`T201+`) | Escalation & Team Lead | Team oversight, Level-1 escalation alerts, manual queue balancing |
| **System Admin** (`A001`) | Platform Operator | Strategy switching, service/category/SLA catalog CRUD, user management, Level-2 escalation, audit logs |
| **IT Manager** (`A002`) | Service Executive | Level-3 escalation oversight, SLA compliance %, recurring problem & feedback analytics |

## 3. Key Challenges
1. **Dynamic Lifecycle Rules**: Preventing illegal shortcuts (e.g., `NEW -> CLOSED` or editing after `RESOLVED`) without sprawling `if/else` chains.
2. **Configurable Triage & Routing**: Allowing organizations to switch priority formulas and assignment algorithms at runtime without redeploying code.
3. **Duplicate & Outage Storms**: Detecting exact duplicate tickets vs. related symptoms during cascading dependency failures (e.g., `Database -> Payment`).
4. **SLA Accountability**: Tracking response and resolution windows separately, warning at 80% elapsed, recording single breaches, and escalating progressively.

## 4. Solution Overview
I-ITSM implements a layered Spring Boot 3.3 + Java 21 architecture anchored by **State**, **Strategy**, **Observer**, **Factory**, **Adapter**, and **Repository** patterns, paired with an offline **TF-IDF Cosine Similarity** engine, **Service Dependency Graph** correlation, **@Scheduled SLA Monitor**, **Chart.js + HTMX** analytics, and a secured **REST API** with Swagger UI.

## 5. Assumptions
See [ASSUMPTIONS.md](ASSUMPTIONS.md).
