# syntax=docker/dockerfile:1

# Bergacast backend image: Spring Boot compiled ahead of time into a GraalVM native executable, run on distroless.
# Build context: backend/. Tests are not run here: they run in CI.

ARG JAVA_MAJOR=25

# ---------------------------------------------------------------------------
# Native executable
# ---------------------------------------------------------------------------
FROM ghcr.io/graalvm/native-image-community:${JAVA_MAJOR} AS build
WORKDIR /workspace
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"

# Wrapper and build scripts first so the Gradle distribution is cached across source changes.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --version

COPY config/ config/
COPY src/ src/
# Release version (vX.Y.Z without the v), set by the release workflow.
ARG VERSION=0.0.0-dev
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew nativeCompile -PreleaseVersion="${VERSION}"

# ---------------------------------------------------------------------------
# Runtime: glibc, CA certificates and tzdata only, no shell
# ---------------------------------------------------------------------------
FROM gcr.io/distroless/base-debian13:nonroot AS runtime

COPY --from=build /workspace/build/native/nativeCompile/bergacast /app/bergacast

USER nonroot:nonroot
# Overridden by the platform through PORT (Scaleway Serverless Containers).
EXPOSE 8080
ENTRYPOINT ["/app/bergacast"]
