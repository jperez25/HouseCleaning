# Test runner image. Build from the repository root:
#   docker build -f house-cleaning-integration-tests/Dockerfile -t house-cleaning-integration-tests:local .
# Run: set TEST_BASE_URL (the chart does); extra container args are passed to Maven.
FROM eclipse-temurin:25-jdk-alpine
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY house-cleaning-common/pom.xml house-cleaning-common/
COPY house-cleaning-app/pom.xml house-cleaning-app/
COPY house-cleaning-integration-tests/pom.xml house-cleaning-integration-tests/
COPY house-cleaning-bdd-tests/pom.xml house-cleaning-bdd-tests/
COPY house-cleaning-common/src house-cleaning-common/src
# the integration tests depend on the app module
COPY house-cleaning-app/src house-cleaning-app/src
COPY house-cleaning-integration-tests/src house-cleaning-integration-tests/src
# Pre-resolve dependencies and plugins at build time (cached layer). Anything resolved only at test time
# (e.g. the surefire JUnit provider) is fetched by Maven when the pod runs, so the pod needs access to a Maven repository.
RUN ./mvnw -q -B -pl house-cleaning-integration-tests -am install -DskipTests \
 && ./mvnw -q -B -pl house-cleaning-integration-tests test -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false
ENTRYPOINT ["./mvnw", "-B", "-pl", "house-cleaning-integration-tests", "test"]
