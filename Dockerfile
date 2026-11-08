FROM gradle:9.8.2-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts ./
RUN --mount=type=cache,target=/home/gradle/.gradle/caches gradle --no-daemon dependencies > /dev/null
COPY src src
RUN --mount=type=cache,target=/home/gradle/.gradle/caches gradle --no-daemon bootJar -x test
RUN java -Djarmode=tools -jar build/libs/ledger.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 ledger
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER ledger
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
