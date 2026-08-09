FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY gateway/build.gradle ./gateway/build.gradle
COPY src ./src

RUN --mount=type=cache,id=devkit-gradle,target=/root/.gradle \
    ./gradlew --no-daemon :bootJar \
    && cp build/libs/dev-kit-0.0.1-SNAPSHOT.jar /workspace/application.jar

FROM eclipse-temurin:25-jre-alpine
WORKDIR /application
RUN apk upgrade --no-cache \
    && addgroup -S -g 10001 devkit \
    && adduser -S -D -H -u 10001 -G devkit devkit
COPY --from=builder /workspace/application.jar ./application.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/application/application.jar"]
