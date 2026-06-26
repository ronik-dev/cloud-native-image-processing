FROM maven:3.9.16-eclipse-temurin-21-alpine AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -N install

COPY common/pom.xml common/pom.xml
RUN mvn -B -f common/pom.xml dependency:go-offline
COPY common/src common/src
RUN mvn -B -f common/pom.xml clean install -DskipTests

COPY gateway/pom.xml gateway/pom.xml
RUN mvn -B -f gateway/pom.xml dependency:go-offline
COPY gateway/src gateway/src 
RUN mvn -B -f gateway/pom.xml clean install -DskipTests

FROM alpine/java:21-jre
WORKDIR /app

COPY --from=build /build/gateway/target/gateway-*.jar app.jar

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
