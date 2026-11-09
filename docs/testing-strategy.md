# Application Testing Strategy

## Purpose

This document defines the standard approach for testing the application through
its deployed service interface. It is intended to guide engineers and
AI-assisted contributions toward consistent, maintainable tests.

## Test Type and Execution Model

Application-level tests are deployment tests, also referred to as integration
tests. They run separately from service deployment: start or deploy the service
first, then execute the test suite against the running application.

Tests communicate with the service over the network using its configured base
URL and port. They must not start the application inside the test process or
use `@SpringBootTest`. Use REST Assured as the HTTP client for service requests.
The target URL must be configurable so the same suite can run against local,
CI, and other test environments.

## Abstract Test Pattern

Concrete test classes extend a shared `AbstractTest` base class. The base class
provides the common test support components:

- **Client** — sends HTTP requests to the running service.
- **Repositories** — provides repository support when test data setup or
  persistence verification is required by the test environment.
- **Generators** — creates valid and, where needed, invalid request data.
- **Validators** — centralizes response assertions and common validation rules.

Concrete tests should reuse these shared components rather than duplicate
client setup, request construction, or response validation. Service behavior
under test must be exercised through the network-facing client; repository
access, when needed, is supplementary and does not replace the service request.

## Test Naming and Annotation

Mark each test with the test framework's `@Test` annotation. Name test methods
using a verb followed by the expected outcome, in lower camel case. The name
should clearly describe the action and the result being verified.

Example: `postTradeRequestValidateResponse`

Prefer names that communicate the expected behavior, such as successful
validation, rejection of an invalid request, or retrieval of a requested
resource.

## Standard Test Flow

Each service-level test should follow this sequence:

1. **Generate the request.** Use a generator to build the request data. Avoid
   hand-building repetitive payloads in each test.
2. **Send the request.** Use the shared client to call the deployed service
   over HTTP.
3. **Validate the response.** Use the shared validator and soft assertions to
   verify the response.

## Response Validation

Each validator uses soft assertions so that it can report multiple mismatches
for its response in one run. Validate the response in this order:

1. Check the HTTP status code against the expected result.
2. Check the response fields and values, including that relevant fields match
   the data sent to the service.
3. Complete the soft-assertion check so that any collected failures fail the
   test. The fluent validator methods perform this check internally.

`TradeValidator` methods perform their checks with AssertJ soft assertions and
return the validator, allowing response checks to be chained:

```java
tradeValidator
        .validateNovateTradeResponse(response, tradeRequest)
        .validateTradeAuditResponse(auditResponse, tradeRequest)
        .validateNovatedContractsResponse(contractsResponse, tradeRequest);
```

Each validator method checks the expected HTTP status before checking response
fields. Soft assertion failures are reported by the method that validates that
response; use a separate shared `SoftAssertions` instance for supplementary
assertions outside the validator.

## Test Class Organization

Split each feature's tests into separate classes by intent:

- **`<Feature>PositiveTest`** — valid requests and successful retrieval.
- **`<Feature>NegativeTest`** — invalid input, business-rule rejections and
  missing resources.

Test methods contain no assertions of their own. Every check lives in a
`TradeValidator` method (or an equivalent shared validator) and is invoked
through the fluent chain, so assertion rules are defined once and reused. When a
test needs a check that does not exist yet, add a validator method rather than
asserting inline.

Data that must stay deterministic when tests run in parallel should use
members reserved for a single scenario (for example `MEM-LOWCREDIT`); do not
assert exact balances on the shared member pool.

## Quality Expectations

- Tests should exercise the externally reachable service contract.
- Request generation and response validation should be reusable and
  consistent across test classes.
- Test names and assertions should make the action and expected behavior
  understandable without relying on implementation details.
- Tests should target an explicitly configured service URL and port rather
  than assuming that the application is embedded in the test process.
