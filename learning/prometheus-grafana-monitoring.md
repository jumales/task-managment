# Prometheus + Grafana Monitoring

## Overview

The stack uses Prometheus for metrics collection and Grafana for visualization.
All Spring Boot services expose `/actuator/prometheus` via Micrometer.
Prometheus discovers services automatically through Eureka service discovery.

## URLs

| Tool       | URL                        | Credentials   |
|------------|----------------------------|---------------|
| Prometheus | http://localhost:9090      | none          |
| Grafana    | http://localhost:3001      | admin / admin |

## Architecture

```
Spring Boot services (host)
  └─ /actuator/prometheus
       ↑ scraped every 15s
Prometheus (Docker, port 9090)
  └─ Eureka SD → discovers all registered services
  └─ alerts.yml → fires alerts when thresholds exceeded
       ↑ datasource
Grafana (Docker, port 3001)
  └─ auto-provisioned datasource: Prometheus
  └─ auto-provisioned dashboards from provisioning/dashboards/
```

## File Structure

```
docker/
  prometheus/
    prometheus.yml     — scrape config + rule_files reference
    alerts.yml         — alerting rules (availability, errors, latency, JVM, DB)
  grafana/
    provisioning/
      datasources/
        prometheus.yml — auto-provisions Prometheus as default datasource
      dashboards/
        dashboards.yml — tells Grafana where to find dashboard JSON files
        jvm-micrometer.json        — Dashboard ID 4701 (JVM heap, GC, threads)
        spring-boot-statistics.json — Dashboard ID 19004 (HTTP rates, latency)
        spring-boot-apm.json       — Dashboard ID 12900 (full APM: DB, Tomcat, logs)
```

## Dashboards

Three community dashboards are pre-provisioned and appear under the **Task Management** folder in Grafana automatically on startup.

| File | Grafana ID | What it shows |
|------|-----------|---------------|
| `jvm-micrometer.json` | 4701 | Heap/non-heap memory, GC pause time, thread counts, class loading |
| `spring-boot-statistics.json` | 19004 | HTTP request rate, error rate, p99 latency per service |
| `spring-boot-apm.json` | 12900 | Full APM: HikariCP connections, Tomcat sessions, Logback counters |

All dashboards have an **application** variable — select the service you want to inspect from the dropdown at the top.

## Prometheus Scrape Config

Two scrape jobs in `prometheus.yml`:

1. **spring-services** — uses `eureka_sd_configs` to auto-discover all services registered in Eureka. Relabeling rewrites LAN IPs to `host.docker.internal` so Prometheus (inside Docker) can reach services running on the host.
2. **eureka-server** — static target because Eureka does not register itself.

## Alerting Rules (`alerts.yml`)

| Alert | Condition | Severity |
|-------|-----------|----------|
| `ServiceDown` | `up == 0` for 1 min | critical |
| `HighErrorRate` | 5xx > 10% of requests over 5 min | warning |
| `HighP99Latency` | p99 > 2 s over 5 min | warning |
| `HighHeapUsage` | heap > 85% of max for 5 min | warning |
| `DatabaseConnectionPoolExhausted` | HikariCP active/max > 90% for 2 min | critical |

View firing alerts at: http://localhost:9090/alerts

## Actuator Endpoints Exposed

All services expose:
```
health, metrics, prometheus, info, loggers
```

The `loggers` endpoint lets you change a logger's level at runtime without restart:
```bash
# Set task-service Hibernate SQL logging to DEBUG
curl -X POST http://localhost:8083/actuator/loggers/org.hibernate.SQL \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'

# Reset it
curl -X POST http://localhost:8083/actuator/loggers/org.hibernate.SQL \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": null}'
```

## Useful PromQL Queries

```promql
# HTTP request rate per service
rate(http_server_requests_seconds_count[5m])

# 5xx error rate
rate(http_server_requests_seconds_count{status=~"5.."}[5m])

# p99 latency per service
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))

# Heap usage %
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100

# Active DB connections
hikaricp_connections_active

# DB connection pool saturation
hikaricp_connections_active / hikaricp_connections_max * 100

# Cache hit rate
rate(cache_gets_total{result="hit"}[5m]) / rate(cache_gets_total[5m]) * 100
```

## Reloading Prometheus Config Without Restart

Prometheus is started with `--web.enable-lifecycle`. To reload rules or scrape config:
```bash
curl -X POST http://localhost:9090/-/reload
```

## Adding a New Dashboard

1. Create or export the dashboard JSON from the Grafana UI (Dashboard → Share → Export → Save to file)
2. Drop the `.json` file into `docker/grafana/provisioning/dashboards/`
3. Restart Grafana: `docker compose restart ms-grafana`

The dashboard will appear under the **Task Management** folder on next load.
