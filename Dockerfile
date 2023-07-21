# Use the specified base image
FROM sbtscala/scala-sbt:graalvm-ce-17.0.4_1.8.2_3.3.0

# Set the working directory in the Docker image
WORKDIR /app

# Copy the entire project into the Docker image
COPY . /app

# Run sbt assembly to build the project
RUN sbt assembly

# Expose port 8080 for the application
EXPOSE 8080

# Command to run the application
CMD ["java", "-jar", "target/scala-3.3.0/API-assembly-0.1.0-SNAPSHOT.jar"]
