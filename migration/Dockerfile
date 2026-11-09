# syntax=docker/dockerfile:1

# Bergacast migration image: Liquibase applies the schema changes of the database. Run as a job (Scaleway Serverless
# Jobs) before the backend is redeployed; it exits once the database is up to date. Build context: migration/. Tests
# are not run here: they run in CI.

ARG JAVA_MAJOR=25

# ---------------------------------------------------------------------------
# Build: the application and its dependencies as jars
# ---------------------------------------------------------------------------
FROM eclipse-temurin:${JAVA_MAJOR}-jdk AS build
WORKDIR /workspace
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"

# Wrapper and build scripts first so the Gradle distribution is cached across source changes.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --version

COPY src/main/ src/main/
# Release version (vX.Y.Z without the v), set by the release workflow.
ARG VERSION=0.0.0-dev
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew installDist -PreleaseVersion="${VERSION}"

# ---------------------------------------------------------------------------
# Runtime: JRE only, no shell
# ---------------------------------------------------------------------------
FROM gcr.io/distroless/java${JAVA_MAJOR}-debian13:nonroot AS runtime

COPY --from=build /workspace/build/install/bergacast-migration/lib/ /app/lib/

USER nonroot:nonroot
# The class path wildcard is expanded by the JVM: there is no shell in this image.
ENTRYPOINT ["/usr/bin/java", "-cp", "/app/lib/*", "fr.bergadev.bergacast.migration.MigrationMain"]
