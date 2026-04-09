FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build /workspace/target/poly-price-watcher-0.0.1-SNAPSHOT.jar /app/app.jar

USER spring:spring

EXPOSE 8086

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8086/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
