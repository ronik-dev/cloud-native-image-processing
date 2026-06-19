FROM maven:3.9.16-eclipse-temurin-21-alpine AS build
WORKDIR /build

COPY pom.xml .
COPY common common/
COPY orchestrator/pom.xml orchestrator/pom.xml 
COPY gateway/ gateway/

RUN mvn -B -N install
RUN mvn -B clean install -DskipTests -pl common
RUN mvn -B clean install -DskipTests -pl gateway

FROM alpine/java:21-jre
WORKDIR /app

COPY --from=build /build/gateway/target/gateway-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
