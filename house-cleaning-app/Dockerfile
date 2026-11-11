# Build from the repository root:  docker build -f house-cleaning-app/Dockerfile -t house-cleaning-app:local .
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
# Reactor skeleton first (poms only), then the sources this image needs
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY house-cleaning-common/pom.xml house-cleaning-common/
COPY house-cleaning-app/pom.xml house-cleaning-app/
COPY house-cleaning-integration-tests/pom.xml house-cleaning-integration-tests/
COPY house-cleaning-bdd-tests/pom.xml house-cleaning-bdd-tests/
COPY house-cleaning-common/src house-cleaning-common/src
COPY house-cleaning-app/src house-cleaning-app/src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -q -B -pl house-cleaning-app -am -DskipTests package

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /workspace/house-cleaning-app/target/house-cleaning-app-*-exec.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
