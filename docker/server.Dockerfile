# syntax=docker/dockerfile:1
# Packages one server's Spring Boot jar, built on the host with `./gradlew :<module>:bootJar` so Gradle keeps
# its cache. Pass the jar as JAR, e.g. --build-arg JAR=zone-server/build/libs/zone-server-0.1.0.jar.
# A JVM image on purpose, not a native one: docs/server/releases in bestia-docs says why.
FROM eclipse-temurin:21-jre-noble AS layers
WORKDIR /work
ARG JAR
COPY ${JAR} app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-noble
RUN useradd --system --uid 10001 --no-create-home bestia \
    && mkdir /data && chown bestia /data
WORKDIR /app
# Least to most often changed, so a release that only changes our code ships one small layer.
COPY --from=layers /work/extracted/dependencies/ ./
COPY --from=layers /work/extracted/spring-boot-loader/ ./
COPY --from=layers /work/extracted/snapshot-dependencies/ ./
COPY --from=layers /work/extracted/application/ ./
USER bestia
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
