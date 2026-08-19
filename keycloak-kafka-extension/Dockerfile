# Stage 1: Build the Java SPI extension using Maven
FROM maven:3.9.16-eclipse-temurin-21-alpine AS extension-build
WORKDIR /build
COPY pom.xml .
COPY keycloak-kafka-extension/pom.xml keycloak-kafka-extension/
COPY keycloak-kafka-extension/src keycloak-kafka-extension/src
RUN mvn -B -f keycloak-kafka-extension/pom.xml clean package -DskipTests

# Stage 2: Bake the extension into Keycloak and run the optimized build step
FROM quay.io/keycloak/keycloak:26.0.7 as builder
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true

COPY --from=extension-build /build/keycloak-kafka-extension/target/keycloak-kafka-extension-0.1.0-jar-with-dependencies.jar /opt/keycloak/providers/

RUN /opt/keycloak/bin/kc.sh build

# Stage 3: Final lightweight runtime image
FROM quay.io/keycloak/keycloak:26.0.7
COPY --from=builder /opt/keycloak/ /opt/keycloak/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
