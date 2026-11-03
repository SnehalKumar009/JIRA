# syntax=docker/dockerfile:1
# Application image: frontend + backend in one container (ADR-0001, ADR-0010).
# Base images are pinned by digest; Renovate keeps tags and digests current (ADR-0015).

# ---- Frontend build ----------------------------------------------------------
FROM node:22.23.4-alpine@sha256:fe4f6d74e20370acb04ad889077d14d0f64a9c8ea6a42bc60cddf1e844699914 AS frontend
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---- Backend build -----------------------------------------------------------
FROM maven:3.9.16-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS backend
WORKDIR /src/backend
COPY backend/pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /src/frontend/dist ./src/main/resources/static
# Tests run in CI with Testcontainers; the image build only packages.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/tracker.jar extract --destination /app

# ---- Runtime -----------------------------------------------------------------
FROM eclipse-temurin:21.0.12.1_1-jre@sha256:f37956012e0cb88400e5b3689aeae2d2d735364c1922ae2225536163b5a99f56
RUN groupadd --system --gid 10001 tracker \
 && useradd --system --uid 10001 --gid tracker --no-create-home --shell /usr/sbin/nologin tracker
WORKDIR /app
COPY --from=backend /app ./
# Class Data Sharing archive from a training run that stops once the context is refreshed — faster
# startup and lower memory (ADR-0016). No database exists here: Flyway is off and the placeholder URL
# is never connected to, because the connection pool only connects on first use.
RUN java -XX:ArchiveClassesAtExit=tracker.jsa -Xlog:cds=off -Xlog:cds+dynamic=off \
        -Dspring.context.exit=onRefresh -Dspring.flyway.enabled=false \
        -Dspring.datasource.url=jdbc:postgresql://127.0.0.1:5432/cds-training -jar tracker.jar \
 && chown -R root:root /app && chmod -R a+rX /app
USER tracker
EXPOSE 8080 8081
ENV LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-XX:+ExitOnOutOfMemoryError", "-XX:SharedArchiveFile=tracker.jsa", "-jar", "tracker.jar"]
