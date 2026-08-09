FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY gateway/build.gradle ./gateway/build.gradle
COPY src ./src

RUN --mount=type=cache,id=devkit-gradle,target=/root/.gradle \
    ./gradlew --no-daemon :bootJar \
    && cp build/libs/dev-kit-0.0.1-SNAPSHOT.jar /workspace/application.jar

FROM alpine:3.22 AS otel-agent
ARG OTEL_JAVA_AGENT_VERSION=2.28.1
ARG OTEL_JAVA_AGENT_SHA256=faa89bdeebf9b1f52be4a4374689176717b02a59df2d8f8b6eb9aa39f9292589

# Keep observability dependencies immutable: the checksum prevents a modified
# release asset from silently becoming part of the production application image.
RUN apk add --no-cache ca-certificates curl \
    && curl -fsSL --retry 3 \
      "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar" \
      -o /opentelemetry-javaagent.jar \
    && echo "${OTEL_JAVA_AGENT_SHA256}  /opentelemetry-javaagent.jar" | sha256sum -c -

FROM eclipse-temurin:25-jre-alpine
WORKDIR /application
RUN apk upgrade --no-cache \
    && addgroup -S -g 10001 devkit \
    && adduser -S -D -H -u 10001 -G devkit devkit
COPY --from=builder /workspace/application.jar ./application.jar
COPY --from=otel-agent /opentelemetry-javaagent.jar /opt/otel/opentelemetry-javaagent.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/application/application.jar"]
