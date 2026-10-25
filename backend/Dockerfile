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

# The executable and the JDK libraries it loads at runtime, which native-image emits next to it: AWT and ImageIO
# (cover image resizing) call into JNI libraries (libawt, libawt_headless, libjavajpeg, liblcms), which link against
# the libjava/libjvm shims. They only need glibc. Left out on purpose: libawt_xawt (X11, unused in headless mode),
# libfontmanager (FreeType, no text is drawn) and libmanagement_ext (JMX extensions, unused).
RUN mkdir /dist \
    && cd build/native/nativeCompile \
    && cp bergacast libawt.so libawt_headless.so libjavajpeg.so liblcms.so libjava.so libjvm.so /dist/

# ---------------------------------------------------------------------------
# Runtime: glibc, CA certificates and tzdata only, no shell
# ---------------------------------------------------------------------------
FROM gcr.io/distroless/base-debian13:nonroot AS runtime

# Native-image looks for the JDK libraries in the directory of the executable.
COPY --from=build /dist/ /app/

USER nonroot:nonroot
# Overridden by the platform through PORT (Scaleway Serverless Containers).
EXPOSE 8080
ENTRYPOINT ["/app/bergacast"]
