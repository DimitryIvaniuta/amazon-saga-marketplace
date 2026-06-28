# syntax=docker/dockerfile:1.7
ARG MODULE
FROM gradle:9.6.0-jdk25 AS build
ARG MODULE
WORKDIR /workspace
COPY --chown=gradle:gradle . .
RUN gradle --no-daemon --stacktrace :${MODULE}:bootJar && \
    JAR_PATH="$(find "${MODULE}/build/libs" -maxdepth 1 -type f \
      -name "${MODULE}-*.jar" ! -name "*-plain.jar" ! -name "*-sources.jar" \
      ! -name "*-javadoc.jar" -print -quit)" && \
    test -n "${JAR_PATH}" && cp "${JAR_PATH}" /workspace/application.jar

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --home /nonexistent --shell /usr/sbin/nologin marketplace
WORKDIR /application
COPY --from=build /workspace/application.jar application.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/application/application.jar"]
