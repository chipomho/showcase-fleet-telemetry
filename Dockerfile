FROM docker.io/library/eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar -x test

RUN java -Djarmode=tools -jar build/libs/*.jar extract --layers --launcher --destination extracted


FROM docker.io/library/eclipse-temurin:25-jre-alpine

RUN addgroup -S app && adduser -S -G app -H app
WORKDIR /app

# Least to most frequently changed, so a code change only replaces the last layer.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=300s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
