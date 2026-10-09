# syntax=docker/dockerfile:1.7
#
# StockSteps backend (Ktor, `:server`) for Google Cloud Run — Phase 5A. Mobile apps are not part of this image.
#
# Build for Cloud Run's platform (required on Apple silicon Macs):
#   docker buildx build --platform linux/amd64 -t stocksteps-server:local .
#
# No secret, key, .env or service-account file is ever copied in (see .dockerignore): Cloud Run supplies configuration and
# Secret Manager values as environment variables at runtime, and Firebase Admin/Firestore use the runtime service account (ADC).
# Base images are pinned by version and digest; update both together.

# Azul Zulu 21 matches gradle/gradle-daemon-jvm.properties (AZUL, 21), so Gradle doesn't download a JDK during the build.
ARG BUILD_IMAGE=azul/zulu-openjdk:21.0.11-21.50@sha256:df4952073643ebaecf1415ee04d9f0c07a72ec99f9a12a98cff1928ef6a7c49d
ARG RUNTIME_IMAGE=eclipse-temurin:21.0.12_8-jre-noble@sha256:7739f0ffce786528961eea6bf46d9610ee968ac6127c9b2e93494757bdecce9f

FROM ${BUILD_IMAGE} AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY core ./core
COPY server ./server
# `stocksteps.serverOnly` leaves the Android/iOS modules out of the build (no Android SDK or Xcode needed); iOS targets of :core are
# skipped on Linux. installDist is the packaging the project already uses (`runMock`); tests run in CI, not in the image build.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain \
      -Pstocksteps.serverOnly=true \
      -Pkotlin.native.ignoreDisabledTargets=true \
      -Pkotlin.compiler.execution.strategy=in-process \
      -Dorg.gradle.jvmargs=-Xmx3g \
      :server:installDist

FROM ${RUNTIME_IMAGE}
# Unprivileged runtime user; the application files stay owned by root and read-only to it.
RUN groupadd --system --gid 10001 stocksteps \
 && useradd --system --uid 10001 --gid stocksteps --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin stocksteps
WORKDIR /app
COPY --from=build /src/server/build/install/server /app
# LOG_FORMAT=json: one Cloud Logging entry per line with severity. Heap follows the Cloud Run memory limit; an OOM exits so Cloud Run
# replaces the instance instead of leaving it half-working. PORT is set by Cloud Run (8080 by default; 0.0.0.0 is bound in main()).
ENV LOG_FORMAT=json \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 8080
# The Gradle start script `exec`s java, so the JVM is PID 1 and receives Cloud Run's SIGTERM (Ktor's shutdown hook: readiness 503,
# in-flight requests get up to 8 s).
ENTRYPOINT ["/app/bin/server"]
