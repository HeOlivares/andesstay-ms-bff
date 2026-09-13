# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src src
RUN chmod +x mvnw && ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENV SERVER_PORT=8080 \
    SECURITY_MODE=local \
    CATALOG_BASE_URL=http://catalog:8081 \
    RESERVATIONS_BASE_URL=http://reservations:8082 \
    AZURE_AUDIENCE=api://andesstay-api
ENTRYPOINT ["java","-jar","/app/app.jar"]
