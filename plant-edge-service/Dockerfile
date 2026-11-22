FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw \
    && ./mvnw -B -q -Dmaven.test.skip=true dependency:resolve
COPY src src
RUN ./mvnw -B -q -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy
RUN useradd --system --uid 10001 --home-dir /app app \
    && mkdir -p /data \
    && chown app:app /data
WORKDIR /app
COPY --from=build /src/target/plant-edge-service-*.jar /app/app.jar
USER app
EXPOSE 8081
VOLUME /data
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx384m -XX:MaxMetaspaceSize=128m"
ENV EDGE_BUFFER_PATH=/data/edge-outbound.db
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=8 \
    CMD curl -fsS http://127.0.0.1:8081/api/edge/status || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
