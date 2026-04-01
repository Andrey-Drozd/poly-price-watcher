FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build /workspace/target/poly-price-watcher-0.0.1-SNAPSHOT.jar /app/app.jar

USER spring:spring

EXPOSE 8086

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
