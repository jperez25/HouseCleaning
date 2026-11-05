# Clearinghouse Core Implementation Plan

This plan translates the feature, requirements, and technology-stack documents
into an implementation sequence. It reflects the current repository scaffold:
the application and integration-test modules do not yet have POMs, the common
module has no implementation, and starter application sources are duplicated.

## 1. Align the project structure and stack

- Complete the Maven configuration for `house-cleaning-app` and
  `house-cleaning-integration-tests`.
- Consolidate duplicate starter application sources so there is one application
  entry point.
- Reconcile the configured Spring Boot and Java versions with the documented
  Spring Boot 3.x target.
- Keep shared DTOs and models in `house-cleaning-common`, application code in
  `house-cleaning-app`, and API/integration tests in
  `house-cleaning-integration-tests`.

## 2. Define domain contracts and persistence

- Specify trade, clearing-member, position, margin, settlement, and ledger
  records.
- Define API request and response payloads, validation rules, and error
  responses.
- Design the PostgreSQL schema, constraints, indexes, audit fields, and
  transaction boundaries.
- Confirm business rules not specified in the docs before implementation:
  margin calculation, settlement timing, and duplicate-trade handling.

## 3. Implement trade intake and novation

- Accept and validate trade execution records from simulated trading
  platforms.
- Persist each original trade and its central-counterparty buyer/seller
  pairings atomically.
- Test valid trades, invalid records, and duplicate submissions.

## 4. Implement risk and margin

- Calculate margin from position volume and simulated market volatility.
- Update clearing-member ledger balances atomically.
- Define margin-call triggers.
- Test boundary conditions and concurrent updates.

## 5. Implement settlement and ledger queries

- Implement settlement processing for cash, options exercise/assignment,
  futures delivery, and securities-lending transactions as applicable to the
  supported scope.
- Provide endpoints for settled positions, trade logs, and cash balances.
- Verify that failures in multi-step transactions roll back all related
  changes.

## 6. Build the test framework alongside the services

- Use JUnit 5 for focused application tests and TestNG for parallel
  trade-validation runs with thread-safe dependency injection.
- Add reusable API-client and database-helper utilities.
- Use RestAssured for integration tests covering payloads, error handling,
  service communication, and fault tolerance.
- Run PostgreSQL-backed tests for ACID behavior, ledger consistency, and
  rollback.

## 7. Add BDD, containers, and CI quality gates

- Add Cucumber feature files for trade novation rules, margin-call triggers,
  and settlement exceptions, with shared step definitions and feature-review
  conventions.
- Containerize the application and test runner with Docker.
- Add Kubernetes manifests for Minikube.
- Configure GitHub Actions or Jenkins to run automated suites and integrity
  checks, blocking the build when required thresholds or checks fail.

## Recommended delivery order

Settle the stack and business-rule decisions first. Then deliver trade
novation, risk and margin, and settlement in that order, adding and running
tests with each feature. Finish by integrating the BDD suite and deployment
quality gates.

## Decisions to resolve before implementation

- Should the domain boundaries be separate deployable services or modules in
  one application?
- What margin formula and volatility inputs should the risk engine use?
- What are the settlement schedules and supported product types?
- What is the expected behavior for duplicate or corrected trade submissions?
- Which CI orchestrator should be used: GitHub Actions or Jenkins?
