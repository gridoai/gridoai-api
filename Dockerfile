# Use an official OpenJDK base image
FROM openjdk:11-jre-slim

# Set the working directory
WORKDIR /app

COPY ./target/scala-3.2.2/API-assembly-0.1.0-SNAPSHOT.jar /app/api.jar
EXPOSE 8080

# Define the ENTRYPOINT to run the .jar file with the specified argument
ENTRYPOINT ["java", "-jar", "/app/api.jar"]