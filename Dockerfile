FROM openjdk:17-jdk-slim-buster

EXPOSE 8090
WORKDIR /findams_javabackend


COPY target/findams-0.0.1-SNAPSHOT.jar ./findams-0.0.1-SNAPSHOT.jar
COPY docker/Configurations/.env ./docker/Configurations/.env
COPY docker/Configurations/application.yml ./docker/Configurations/application.yml
COPY tests ./tests

# Copy the entrypoint script
COPY entrypoint.sh /entrypoint.sh

# Ensure the entrypoint script is executable
RUN chmod +x /entrypoint.sh

# Set the entrypoint
ENTRYPOINT ["/entrypoint.sh"]

