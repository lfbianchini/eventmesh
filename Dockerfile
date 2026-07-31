# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package -DskipTests

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S eventmesh && adduser -S eventmesh -G eventmesh
WORKDIR /app
COPY --from=build /workspace/target/eventmesh.jar /app/eventmesh.jar
USER eventmesh:eventmesh
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/eventmesh.jar"]
