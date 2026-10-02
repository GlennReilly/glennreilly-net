# Drop this into the root of a Ktor demo repo (e.g. year-round).
#
# Assumes a Gradle `application` module. If your server lives in a sub-module,
# change SERVER_MODULE (e.g. "server"); leave it empty for a single-module build.
# If the Kotlin/JS frontend is bundled into the server's resources by Gradle,
# this one build produces everything.

ARG SERVER_MODULE=server

FROM gradle:8-jdk21 AS build
ARG SERVER_MODULE
WORKDIR /src
COPY . .
RUN if [ -n "$SERVER_MODULE" ]; then TASK=":${SERVER_MODULE}:installDist"; DIST="${SERVER_MODULE}/build/install"; \
    else TASK="installDist"; DIST="build/install"; fi && \
    ./gradlew --no-daemon "$TASK" && \
    mkdir -p /out && cp -r "$DIST"/*/. /out/ && \
    ln -s "$(ls /out/bin | grep -v '\.bat$' | head -n1)" /out/bin/app

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /out /app
ENV PORT=8080
EXPOSE 8080
# The Ktor server must bind 0.0.0.0 and read PORT (see README in this folder).
ENTRYPOINT ["/app/bin/app"]
