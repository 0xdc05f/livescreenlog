# Reference runtime image only — not a full multi-stage CI build.
# Build the jar on the host (or CI) first:
#   ./gradlew bootJar
# Then from repo root:
#   docker build -f deploy/Dockerfile -t livescreenlog:local .

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
