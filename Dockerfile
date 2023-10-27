FROM maven:3.8.3-openjdk-17-slim AS build
FROM eclipse-temurin:17-jdk-alpine

# Expose the port used by the application
EXPOSE 8090

# Set the working directory in the container
WORKDIR /findams
COPY target/springboot-restful-webservices-0.0.1-SNAPSHOT.jar /Configurations/application.yml ./

# Set the command to run when the container starts
CMD ["java","-jar","./findams-0.0.1-SNAPSHOT.jar", "--spring.config.location=./application.properties"]