# Task Management System

> **Built entirely with [Claude Code](https://claude.ai/claude-code) — Anthropic's AI coding assistant.**
>
> This project exists for **learning purposes only**. It is not production-ready.
> Before any real deployment, a thorough review by domain experts is required — see the warning section below.

---

## What This Is

A microservices-based task management platform built to explore modern Java backend architecture, event-driven systems, and cloud-native patterns. Every line of code was generated and iterated through conversations with Claude Code.

---

## Architecture Overview

```
┌─────────────┐     ┌──────────────────────────────────────────────────┐
│   React UI  │────▶│                  API Gateway                      │
│  (Vite/TS)  │     │  (Spring Cloud Gateway + OAuth2 + Redis sessions) │
└─────────────┘     └──────────────┬───────────────────────────────────┘
                                   │  Routes via Eureka Service Discovery
          ┌────────────────────────┼────────────────────────────┐
          │                        │                            │
   ┌──────▼──────┐        ┌────────▼──────┐         ┌─────────▼──────┐
   │ User Service│        │ Task Service  │         │  File Service  │
   │  (Keycloak) │        │  (core logic) │         │   (MinIO/S3)   │
   └─────────────┘        └───────┬───────┘         └────────────────┘
                                  │ Kafka events
          ┌───────────────────────┼──────────────────────────┐
          │                       │                          │
   ┌──────▼──────┐    ┌───────────▼────────┐    ┌──────────▼──────┐
   │  Audit Svc  │    │ Notification Svc   │    │  Reporting Svc  │
   └─────────────┘    │  (Mailhog/SMTP)    │    └─────────────────┘
                      └────────────────────┘
                                  │
                      ┌───────────▼────────┐
                      │   Search Service   │
                      │  (Elasticsearch)   │
                      └────────────────────┘
```

### Services

| Service | Port | Responsibility |
|---|---|---|
| `api-gateway` | 8080 | Entry point, routing, auth, rate limiting |
| `eureka-server` | 8761 | Service registry (HA: peer1 + peer2) |
| `user-service` | 8081 | User management, Keycloak integration |
| `task-service` | 8082 | Tasks, projects, phases, comments, booked work |
| `file-service` | 8083 | File uploads/downloads via MinIO |
| `audit-service` | 8084 | Immutable audit log from Kafka events |
| `search-service` | 8085 | Full-text search via Elasticsearch |
| `notification-service` | 8086 | Email notifications via Kafka events |
| `reporting-service` | 8087 | Analytics and reporting endpoints |

### Infrastructure

| Component | Purpose |
|---|---|
| PostgreSQL | Primary relational database (per-service schemas, Flyway migrations) |
| Apache Kafka | Event streaming between services (KRaft mode, no ZooKeeper) |
| Keycloak 24 | OAuth2/OIDC identity provider |
| Redis 7 | Gateway session store and service-level caching |
| MinIO | S3-compatible object storage for file uploads |
| Elasticsearch 8 | Full-text search indexing |
| Logstash + Kibana | Log aggregation and visualization (ELK stack) |
| Prometheus + Grafana | Metrics collection and dashboards |
| Mailhog | SMTP mock server for local email testing |

### Tech Stack

- **Java 17** / **Spring Boot 3.4.5** / **Spring Cloud 2024.0.1**
- **React + TypeScript** (Vite, Ant Design, Keycloak.js)
- **Resilience4j** — circuit breakers and bulkheads
- **Micrometer + OpenTelemetry** — distributed tracing
- **Flyway** — database migrations
- **Testcontainers** — integration testing

---

## Getting Started (Local Dev)

### Prerequisites

- Docker Desktop
- Java 17+
- Node 18+ (for frontend)

### Start

```bash
./scripts/start-dev.sh
```

This starts all infrastructure containers and microservices. Health checks are built in.

### Stop

```bash
./scripts/stop-dev.sh
```

### Seed Test Data

```bash
python3 scripts/seed_task_data.py
```

### Key Local URLs

| URL | Service |
|---|---|
| http://localhost:3000 | React frontend |
| http://localhost:8080 | API Gateway |
| http://localhost:8761 | Eureka dashboard |
| http://localhost:9090 | Keycloak admin |
| http://localhost:5601 | Kibana |
| http://localhost:3001 | Grafana |
| http://localhost:8025 | Mailhog (email preview) |
| http://localhost:9001 | MinIO console |

### API Collections

Postman collections for all services live in `postman/`. Import `postman/local.postman_environment.json` as the environment.

---

## ⚠️ Production Warning

**This project is a learning exercise. Do not deploy it to production without expert review.**

The following areas require analysis by qualified specialists before any real-world use:

### Security
- OAuth2/OIDC configuration and Keycloak realm hardening
- JWT validation, token expiry, and refresh token rotation
- API Gateway authorization rules and scope enforcement
- Inter-service authentication (client credentials flow)
- Input validation and injection attack surface
- File upload validation and MIME type handling
- CORS policy, CSP headers, and XSS surface on the frontend
- Secrets management (no hardcoded credentials, vault integration)
- Dependency vulnerability scan (CVE audit on all Maven and npm packages)

### Database
- Query performance and index coverage under real load
- Connection pool sizing per service
- Flyway migration safety for schema changes on live data
- Backup and point-in-time recovery strategy
- Encryption at rest for sensitive user data

### Network & Infrastructure
- Service-to-service communication encryption (mTLS)
- Network segmentation and firewall rules
- Kafka topic ACLs and producer/consumer authorization
- MinIO bucket policies and access control
- Redis AUTH and TLS configuration
- Container image hardening and non-root user enforcement
- Rate limiting and DDoS protection at the gateway layer

---

## Disclaimer

All code in this repository was produced through AI-assisted development with Claude Code. It reflects an exploratory, iterative process — not a vetted, production architecture. Treat it as a reference for patterns and learning, not as a template for live systems.
