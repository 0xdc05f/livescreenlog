# Runtime image. Build the jar first:
#   ./gradlew bootJar
# Then from repo root:
#   docker build -f deploy/Dockerfile -t livescreenlog:local .
# Build: docker build --platform linux/amd64 -f deploy/Dockerfile ...

FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache wget \
    && addgroup -S lsl \
    && adduser -S lsl -G lsl

WORKDIR /app

COPY build/libs/livescreenlog-*.jar app.jar
RUN chown lsl:lsl app.jar

USER lsl

ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
