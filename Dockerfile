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
    SECURITY_MODE=azure \
    MS_CATALOG_URL=http://ms-catalog:8081 \
    MS_RESERVATIONS_URL=http://ms-reservations:8082 \
    AZURE_ISSUER_URI=https://login.microsoftonline.com/cb0b9f53-0ba7-4f09-8da2-c2f5ab4b73ee/v2.0 \
    AZURE_AUDIENCE=api://4cd6df9a-e2f7-4024-aea6-dd67c49709bc
ENTRYPOINT ["java","-jar","/app/app.jar"]
