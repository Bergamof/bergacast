# syntax=docker/dockerfile:1

# Bergacast migration image: Liquibase applies the schema changes of the database. Run as a job (Scaleway Serverless
# Jobs) before the backend is redeployed; it exits once the database is up to date. Build context: migration/. Tests
# are not run here: they run in CI.
#
# The JRE is trimmed with jlink and runs on the same distroless base as the backend: the distroless Java images add
# Debian packages (expat, util-linux...) with unfixed HIGH vulnerabilities that this application never uses.

# Base images are pinned by digest (a tag can be rewritten); Dependabot updates tag and digest together.

# ---------------------------------------------------------------------------
# Build: the application and its dependencies as jars, and the JRE they need
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk@sha256:83f4a7fc27df94dcc7b8de04d14cbbba8da14406f921e966969c3b09000aa507 AS build
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

# Modules of the JRE: the JDBC driver and Liquibase need SQL, XML, JNDI, JMX, java.beans (java.desktop) and Kerberos
# (java.security.jgss, PostgreSQL authentication); jdeps cannot list them because of the automatic module jaxb-api.
RUN jlink --add-modules java.base,java.logging,java.sql,java.xml,java.naming,java.management,java.desktop,java.security.jgss,jdk.crypto.ec,jdk.charsets,jdk.unsupported \
        --strip-debug --no-man-pages --no-header-files --compress=zip-6 --output /jre

# ---------------------------------------------------------------------------
# Runtime: glibc, CA certificates and tzdata only, no shell
# ---------------------------------------------------------------------------
FROM gcr.io/distroless/base-debian13:nonroot@sha256:7fe4df6a4413adf5889960d345a28b4574aea0872ea47684c7244c8d88027dca AS runtime

COPY --from=build /jre/ /opt/java/
COPY --from=build /workspace/build/install/bergacast-migration/lib/ /app/lib/

USER nonroot:nonroot
# The class path wildcard is expanded by the JVM: there is no shell in this image.
ENTRYPOINT ["/opt/java/bin/java", "-cp", "/app/lib/*", "fr.bergadev.bergacast.migration.MigrationMain"]
