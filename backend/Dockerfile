# syntax=docker/dockerfile:1
# =============================================================================
#  LifeOS — one image, one process.
#
#  Built by Render from this file. Nothing else has to be running for the
#  container to be healthy except the Postgres named in DATABASE_URL.
# =============================================================================

# ------------------------------------------------------------------- build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# The POM alone first, so a source edit does not re-download the world. This is
# the difference between a 40-second and a four-minute deploy on a free runner.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests are skipped here on purpose: they start a real Postgres, and the deploy
# builder has neither the memory nor the minutes. CI runs them before merge —
# see .github/workflows/ci.yml.
RUN mvn -B -q clean package -DskipTests

# ----------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Not root. A container that never needs to write outside /tmp has no reason to.
RUN addgroup -S lifeos && adduser -S lifeos -G lifeos
COPY --from=build --chown=lifeos:lifeos /build/target/lifeos.jar app.jar
USER lifeos

# 512 MB is the whole free instance, and the JVM's default is a quarter of it —
# which leaves the heap too small while the container still gets OOM-killed for
# the rest. 70% of what the cgroup allows is the number that actually fits.
# SerialGC because one small heap on a fraction of a CPU has nothing to
# parallelise, and G1's threads are pure overhead there.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"
ENV SPRING_PROFILES_ACTIVE=prod

# Documentation only: the platform injects PORT and the app binds to it.
EXPOSE 9080

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- "http://localhost:${PORT:-9080}/actuator/health" >/dev/null 2>&1 || exit 1

ENTRYPOINT ["sh", "-c", "exec java -jar app.jar"]
