FROM openjdk:17-jdk-slim-buster

EXPOSE 8090
WORKDIR /findams_javabackend

COPY target/findams-0.0.1-SNAPSHOT.jar ./findams-0.0.1-SNAPSHOT.jar
COPY docker/Configurations/.env ./docker/Configurations/.env
COPY docker/Configurations/application.yml ./docker/Configurations/application.yml

# Copy additional folders
COPY docker/Configurations/processed ./processed
COPY docker/Configurations/tests ./tests
COPY docker/Configurations/whonet ./whonet

# HEALTHCHECK --interval=25s --timeout=3s --retries=2 CMD wget --spider http://localhost:8090/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "findams-0.0.1-SNAPSHOT.jar"]
