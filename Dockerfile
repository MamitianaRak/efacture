# syntax=docker/dockerfile:1
# One image serving both the Angular app and the API from the same origin.

# 1. Angular production build
FROM node:24-alpine AS frontend
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
# Cache mount: npm packages are not downloaded again on each build.
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
RUN npx ng build

# 2. Spring Boot jar, with the Angular build as static resources, extracted into layers
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /app
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=frontend /app/dist/frontend/browser src/main/resources/static
# Tests run in CI; the image build only packages. The jar is then split into Spring Boot's layers
# (dependencies, loader, snapshots, application) so each becomes its own image layer.
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/backend-*.jar extract --layers --launcher --destination extracted

# 3. Runtime: JRE only, non-root user
FROM eclipse-temurin:25-jre
WORKDIR /app
# A home directory is needed: FOP, used to render the Factur-X PDF, writes its font cache there.
RUN useradd --system --create-home --uid 10001 efacture
# Least to most frequently changed: after a code change, only the small application layer is rebuilt and pushed,
# instead of the whole 113 MB jar.
COPY --from=backend /app/extracted/dependencies/ ./
COPY --from=backend /app/extracted/spring-boot-loader/ ./
COPY --from=backend /app/extracted/snapshot-dependencies/ ./
COPY --from=backend /app/extracted/application/ ./
# Numeric UID: Kubernetes can only enforce runAsNonRoot when the user is given as a number.
USER 10001
# Sized for a 512 MB container (free hosting tiers), measured without swap: the JVM defaults and a 60 % heap
# were killed for lack of memory; a 40 % heap holds, with the Factur-X validator turned off.
ENV JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=40 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -Xss512k"
ENV SERVER_TOMCAT_THREADS_MAX=20 \
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5
EXPOSE 8080
# PORT is set by hosting platforms such as Render.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} org.springframework.boot.loader.launch.JarLauncher"]
