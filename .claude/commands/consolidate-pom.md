# POM Consolidation Analysis

Analyze all `pom.xml` files in this multi-module Maven project and identify what can be moved to the parent POM to reduce duplication. Then implement the consolidation.

## Architecture context

- **Parent POM**: `pom.xml` at project root — `<groupId>com.demo</groupId>`, `<artifactId>microservice-demo</artifactId>`
- **Modules**: `common`, `eureka-server`, `api-gateway`, `user-service`, `task-service`, `audit-service`, `file-service`
- **Base**: Spring Boot 3.4.5 parent (provides BOM for Spring, Testcontainers, Flyway, etc.)

## What to check

### 1 — Hardcoded version literals in modules
- Read every `pom.xml` under each module
- Find any `<version>` tag on a `<dependency>` that is **not** already managed by the Spring Boot BOM or Spring Cloud BOM
- These are candidates for `<properties>` + `<dependencyManagement>` in the parent

### 2 — Repeated dependency blocks across modules
- Look for the same `<dependency>` (same groupId + artifactId) declared in 3 or more modules
- If the Spring Boot BOM already manages the version (no `<version>` needed), these are fine as-is — do not move them to `<dependencies>` in the parent (that would force them on all modules including `eureka-server` and `api-gateway`)
- If the version is NOT managed by Spring Boot BOM, centralize via `<dependencyManagement>`

### 3 — Repeated `<build>` blocks
- Flag any `<build><plugins>` block that is identical to what is already declared in parent `<pluginManagement>` or inherited from `spring-boot-starter-parent`
- Note: `spring-boot-maven-plugin` must still be declared in each executable module to activate the fat-JAR repackage goal — do not remove it

### 4 — Version properties already defined
- Check what properties are already in the parent `<properties>` block
- Avoid duplicating properties already managed upstream

## Instructions

1. Read all `pom.xml` files — do not guess; check the actual content.
2. Produce an analysis table:

| Dependency | Current location (modules) | Version | Managed by BOM? | Action |
|---|---|---|---|---|
| `springdoc-openapi-starter-webmvc-ui` | user, task, audit, file | 2.5.0 | No | Move to parent dependencyManagement |
| ... | ... | ... | ... | ... |

3. For each dependency to centralize:
   - Add a `<property>` to the parent `<properties>` block: `<lib.version>x.y.z</lib.version>`
   - Add a `<dependency>` to parent `<dependencyManagement>` referencing `${lib.version}`
   - Remove the `<version>` literal from every module that declares the dependency
4. Create a new branch `consolidate_parent_pom`, implement all changes, run `mvn clean install -DskipTests=true` to verify, commit, and open a PR.
5. Report what was changed and what was intentionally left as-is (and why).
