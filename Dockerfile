# syntax=docker/dockerfile:1

# Bergacast image: builds the admin SPA, embeds it in the Spring Boot jar and runs it on a JRE.
# Tests are not run here: they run in CI.

ARG NODE_MAJOR=24
ARG JAVA_MAJOR=25

# ---------------------------------------------------------------------------
# Admin SPA (SvelteKit, static build under /admin)
# ---------------------------------------------------------------------------
FROM node:${NODE_MAJOR}-alpine AS frontend
ARG NPM_VERSION=12.2.1
WORKDIR /workspace/frontend

# package.json pins npm (packageManager) and .npmrc enables engine-strict.
RUN --mount=type=cache,target=/root/.npm \
    npm install --global "npm@${NPM_VERSION}"

COPY frontend/package.json frontend/package-lock.json frontend/.npmrc ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

COPY frontend/ ./
RUN npm run build

# ---------------------------------------------------------------------------
# Backend (Spring Boot fat jar, then split into layers)
# ---------------------------------------------------------------------------
FROM eclipse-temurin:${JAVA_MAJOR}-jdk AS backend
WORKDIR /workspace/backend
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"

# Wrapper and build scripts first so the Gradle distribution is cached across source changes.
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts backend/gradle.properties ./
COPY backend/gradle/ gradle/
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --version

COPY backend/config/ config/
COPY backend/src/ src/
# The backend serves the SPA from classpath static/admin/.
COPY --from=frontend /workspace/frontend/build/ src/main/resources/static/admin/

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

WORKDIR /workspace/extracted
RUN java -Djarmode=tools -jar /workspace/app.jar extract --layers --launcher --destination .

# ---------------------------------------------------------------------------
# Runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:${JAVA_MAJOR}-jre-alpine AS runtime
ARG APP_UID=10001

RUN addgroup -S -g "${APP_UID}" bergacast \
    && adduser -S -D -H -u "${APP_UID}" -G bergacast bergacast

WORKDIR /app
# Least to most frequently changing layers.
COPY --from=backend /workspace/extracted/dependencies/ ./
COPY --from=backend /workspace/extracted/spring-boot-loader/ ./
COPY --from=backend /workspace/extracted/snapshot-dependencies/ ./
COPY --from=backend /workspace/extracted/application/ ./

USER ${APP_UID}:${APP_UID}
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD ["wget", "-q", "-O", "/dev/null", "http://127.0.0.1:8080/actuator/health"]

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
