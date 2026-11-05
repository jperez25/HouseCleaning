# Technology Stack

This stack maps the project to the enterprise technologies specified in the OCC
job description and aligns it with the engineering standards described there.

## Core Language and Framework

**Java (Spring Boot 4.x)**

Leverages modern Java features, such as records and static factory methods, and
Spring's dependency injection for a clean, maintainable microservice
architecture.

## Database and Data Persistence

**PostgreSQL**

Supports testing ACID compliance, transaction rollbacks, multi-column indexing,
and data pipeline integrity across financial ledgers.

## API Testing and Contract Validation

**RestAssured**

Provides rigorous microservice payload validation, status-code verification,
and fault-tolerance testing across internal clearing APIs.

## Test Runner and Concurrency Framework

**TestNG and JUnit 5**

TestNG supports thread-safe dependency injection patterns and scalable parallel
test execution without state leakage.

## Behavior-Driven Development

**Cucumber with Gherkin syntax**

Translates complex financial compliance rules and margin calculations into
human-readable feature files and shared step-definition libraries.

## Containerization and Orchestration

**Docker and Kubernetes (Minikube)**

Containerizes test execution environments and microservices to ensure
consistent dependencies and portability across development, staging, and
pipeline environments.

## CI/CD Pipeline Orchestration

**GitHub Actions or Jenkins**

Builds production-grade pipelines with stage-gated quality controls, such as
blocking deployments if test suites or database transaction checks fail.
