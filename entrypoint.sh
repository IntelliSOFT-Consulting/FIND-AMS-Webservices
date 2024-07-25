#!/bin/sh

mkdir -p /findams_javabackend/whonet
mkdir -p /findams_javabackend/processed

# Run the Java application
java -jar /findams_javabackend/findams-0.0.1-SNAPSHOT.jar
