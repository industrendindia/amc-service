# SevaSetu AMC Service

Spring Boot and PostgreSQL backend for the SevaSetu multi-tenant AMC platform.

## Runtime configuration

Set these environment variables:

```text
DB_URL=jdbc:postgresql://127.0.0.1:5432/amcservice?currentSchema=amcservice
DB_USERNAME=amcservice
DB_PASSWORD=replace-me
JWT_SECRET=replace-with-at-least-32-random-characters
PORT=8080
```

## Run

```bash
mvn clean test
mvn spring-boot:run
```

Flyway creates the isolated `amcservice` schema and initial demonstration accounts on first startup.
