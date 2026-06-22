FROM maven:3.9.16-eclipse-temurin-21-alpine AS build
WORKDIR /build

COPY pom.xml .
COPY common common/
COPY gateway/pom.xml gateway/pom.xml 
COPY orchestrator orchestrator/

RUN mvn -B -N install
RUN mvn -B clean install -DskipTests -pl common
RUN mvn -B clean install -DskipTests -pl orchestrator

FROM alpine/java:21-jre
WORKDIR /app

COPY --from=build /build/orchestrator/target/orchestrator-*.jar app.jar

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN mkdir -p /tmp/imageprocessing/data && \
    chown -R appuser:appgroup /app /tmp/imageprocessing/data
USER appuser

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
