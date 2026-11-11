# house-cleaning-bdd-tests

Black-box BDD API tests, a learning sandbox next to `house-cleaning-integration-tests` (which is untouched).
Like the integration tests, these run against an already-running service; nothing is embedded.

| | Cucumber | Karate |
|---|---|---|
| Features | `src/test/resources/features/cucumber` | `src/test/resources/features/karate` |
| Glue | Java steps: `bdd/cucumber/steps` | none, the steps are built in |
| Runner | `bdd/cucumber/CucumberTest` | `bdd/karate/KarateTest` |
| Reports | `target/cucumber-report.html` | `target/karate-reports` |

Base URL (both frameworks, via `TestEnvironment`): `-Dtest.base.url`, else `TEST_BASE_URL`, else `http://localhost:8080`.

Cucumber layering: `bdd/cucumber/steps` (thin glue) -> `bdd/support` (TradeApiClient, TradeRequestGenerator, TradeValidator; copied from the integration module on purpose) -> `bdd/models` (copied DTOs). Karate calls `TradeRequestGenerator` through `Java.type` (config) and uses `buildMap()` because it cannot serialize records.

## Run

```
java -jar house-cleaning-app/target/house-cleaning-app-0.0.1-SNAPSHOT-exec.jar   # start the service
./mvnw -pl house-cleaning-bdd-tests test                                          # run both
./mvnw -pl house-cleaning-bdd-tests test -Dtest=KarateTest                        # one framework
./mvnw -pl house-cleaning-bdd-tests test -Dtest=CucumberTest -Dcucumber.filter.tags=@smoke
./mvnw -pl house-cleaning-bdd-tests test -Dtest.base.url=http://host:8080         # other target
```

Notes: Karate 1.4.1 hung on JDK 25, so this uses Karate 2.x (`io.karatelabs:karate-junit6`).
