# Source Code Maintainability Audit

Perform a maintainability audit of this codebase. This project is a Spring Boot microservices backend with a React frontend. Use the knowledge below about the architecture to focus your analysis.

## Architecture context

- **Backend**: Java 17, Spring Boot 3.4.5, Spring Security OAuth2 Resource Server, Keycloak JWT
- **Frontend**: React 18, TypeScript, Axios, Keycloak-JS
- **Data**: PostgreSQL (Spring Data JPA, soft deletes via `@SQLRestriction`), MinIO (file storage)
- **Messaging**: Kafka (outbox pattern)
- **Services**: eureka-server, api-gateway, user-service, task-service, audit-service, file-service, common (shared)

## What to check for each dimension

### 1 — Cyclomatic Complexity
- Scan all Java service/controller/repository files for methods with high complexity (many if/else, switch, loops)
- Flag anything above 10 (SonarQube default threshold)
- Pay attention to service methods, not just controllers

### 2 — Code Duplication
- Look for repeated patterns across services: SecurityConfig, application.yml structure, test setup (TestSecurityConfig, IT test boilerplate)
- Check if DTOs/events that belong in common are still duplicated per-service
- Check if any config or constant is defined in multiple places

### 3 — Coupling
- Check Feign clients — how services communicate
- Are all shared DTOs in common? Any services directly importing from other services?
- Check for any circular dependencies

### 4 — Test Coverage
- List all IT test files and count @Test methods per file
- Note any controllers or service methods with zero test coverage
- Check frontend test coverage

### 5 — Documentation
- Spot-check Javadoc on controllers and services
- Are all public and package-private methods documented?
- Are class-level Javadoc present on service and config classes?

### 6 — Configuration Management
- Compare application.yml files across services — is there duplication?
- Are secrets or URLs hardcoded vs. externalized?
- Are all magic strings replaced with constants?

### 7 — Error Handling
- Check GlobalExceptionHandler — are all domain exceptions mapped?
- Are there any unhandled exceptions or broad catch blocks?
- Do controllers throw domain exceptions (not return nulls)?

### 8 — Dependency Management
- Check pom.xml files — are versions managed in the parent pom?
- Are there version conflicts, redundancies, or unused dependencies?

### 9 — Naming & Conventions
- Check that CLAUDE.md code style rules are followed: constants vs magic strings, early returns, single responsibility methods, Lombok usage
- Look for abbreviations, unclear names, or inconsistent naming

### 10 — Frontend
- Check web-client/src for component size, type safety (any usage), API error handling
- Check for unused imports, hardcoded strings, missing error states

---

## Instructions

1. Read each relevant file — do not guess; check the actual code.
2. For each dimension, report:
   - **Rating**: GOOD / WARN / POOR
   - **Finding**: what was found (or confirmed safe)
   - **File**: exact file path and line number where relevant
   - **Recommendation**: specific fix if WARN or POOR
3. At the end, produce a **priority action list** ordered: POOR → WARN → GOOD.
4. For every WARN or POOR, suggest the exact code change or file to create.
5. Give an overall score out of 10.

Be thorough — read actual files, don't guess. Focus on what has changed or could have regressed since the last audit.
