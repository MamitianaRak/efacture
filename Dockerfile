# One image serving both the Angular app and the API from the same origin.

# 1. Angular production build
FROM node:24-alpine AS frontend
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npx ng build

# 2. Spring Boot jar, with the Angular build as static resources
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /app
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
# Dependencies first, in their own layer: rebuilt only when pom.xml changes.
RUN sh mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=frontend /app/dist/frontend/browser src/main/resources/static
# Tests run in CI; the image build only packages.
RUN sh mvnw -B -q package -DskipTests

# 3. Runtime: JRE only, non-root user
FROM eclipse-temurin:25-jre
WORKDIR /app
# A home directory is needed: FOP, used to render the Factur-X PDF, writes its font cache there.
RUN useradd --system --create-home efacture
COPY --from=backend /app/target/backend-*.jar app.jar
USER efacture
# Sized for a 512 MB container (free hosting tiers), measured without swap: the JVM defaults and a 60 % heap
# were killed for lack of memory; a 40 % heap holds, with the Factur-X validator turned off.
ENV JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=40 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -Xss512k"
ENV SERVER_TOMCAT_THREADS_MAX=20 \
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5
EXPOSE 8080
# PORT is set by hosting platforms such as Render.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
