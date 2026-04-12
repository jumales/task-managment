# Finding #9 — Add structured JSON logging (logback-spring.xml) to 3 missing services

## Status
UNRESOLVED

## Severity
MEDIUM — 3 of 7 services produce no structured JSON logs in Docker; Kibana receives no data from them

## Context
`notification-service`, `search-service`, and `reporting-service` have no `logback-spring.xml`.
They use Spring Boot's default text-format console logging. In Docker deployments, the `logstash`
profile is activated via JVM args (`-Dspring.profiles.active=logstash`), but without
`logback-spring.xml` the profile does nothing — no JSON encoding, no TCP log shipping.

The other 4 services (task, user, audit, file) already have correct `logback-spring.xml` files.

## Correct Template
Source: `task-service/src/main/resources/logback-spring.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <springProperty scope="context" name="appName"
                    source="spring.application.name"
                    defaultValue="SERVICE_NAME_HERE"/>

    <!-- JSON console appender — used in all profiles -->
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"${appName}"}</customFields>
        </encoder>
    </appender>

    <!-- Logstash TCP appender — only active when spring.profiles.active=logstash -->
    <springProfile name="logstash">
        <appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
            <destination>${logback.destination:-logstash:5000}</destination>
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <customFields>{"service":"${appName}"}</customFields>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON_CONSOLE"/>
            <appender-ref ref="LOGSTASH"/>
        </root>
    </springProfile>

    <springProfile name="!logstash">
        <root level="INFO">
            <appender-ref ref="JSON_CONSOLE"/>
        </root>
    </springProfile>

</configuration>
```

## Files to Create

### `notification-service/src/main/resources/logback-spring.xml`
Use template above, set `defaultValue="notification-service"`

### `search-service/src/main/resources/logback-spring.xml`
Use template above, set `defaultValue="search-service"`

### `reporting-service/src/main/resources/logback-spring.xml`
Use template above, set `defaultValue="reporting-service"`

## Verification
1. Start any of the 3 services with `-Dspring.profiles.active=logstash`
2. Check Kibana → Discover — logs from the service should appear with JSON fields (`service`, `level`, `message`, `@timestamp`)
3. Without the `logstash` profile, logs should appear on stdout as JSON (not plain text)
4. Log fields must include `"service":"notification-service"` (or respective name)

## Notes
- All 3 services already have `logstash-logback-encoder` on the classpath (via `common` pom.xml)
- No pom.xml changes needed
- The `${logback.destination:-logstash:5000}` default points to the Docker service name; in dev without Docker, TCP appender will fail to connect but JSON_CONSOLE still works
