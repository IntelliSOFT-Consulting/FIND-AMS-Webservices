FROM maven:3.8.3-openjdk-17-slim AS build
#FROM eclipse-temurin:17-jdk-alpine
FROM openjdk:17-jdk-slim-buster
EXPOSE 8090
WORKDIR /findams_javabackend

COPY target/findams-0.0.1-SNAPSHOT.jar /Configurations/application.yml ./
COPY target/findams-0.0.1-SNAPSHOT.jar /Configurations/.env ./

#HEALTHCHECK --interval=25s --timeout=3s --retries=2 CMD wget --spider http://199.192.27.107:8085/actuator/health || exit 1
ADD target/findams-0.0.1-SNAPSHOT.jar findams-0.0.1-SNAPSHOT.jar
ENTRYPOINT ["java","-jar","target/findams-0.0.1-SNAPSHOT.jar"]