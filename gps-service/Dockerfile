# Runtime image built from the prebuilt host jar. Maven Central is effectively
# unreachable at a useful speed from inside Docker on this machine, so the
# Maven build stage is skipped here. Rebuild the jar on the host with
# `mvn -DskipTests package` before `docker compose build`.
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY target/gps-service-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
