FROM maven:3.9.16-eclipse-temurin-21-alpine AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -N install

COPY common/pom.xml common/pom.xml
RUN mvn -B -f common/pom.xml dependency:go-offline
COPY common/src common/src
RUN mvn -B -f common/pom.xml clean install -DskipTests

COPY orchestrator/pom.xml orchestrator/pom.xml
RUN mvn -B -f orchestrator/pom.xml dependency:go-offline
COPY orchestrator/src orchestrator/src
RUN mvn -B -f orchestrator/pom.xml clean package -DskipTests

FROM alpine/java:21-jre
WORKDIR /app

COPY --from=build /build/orchestrator/target/orchestrator-*.jar app.jar

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN mkdir -p /data/imageprocessing && \
    chown -R appuser:appgroup /app /data/imageprocessing
USER appuser

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
