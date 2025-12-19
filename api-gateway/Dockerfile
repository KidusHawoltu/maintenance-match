# Stage 1: Build the application using Maven
FROM maven:3.9-eclipse-temurin-17 AS builder

# Set the working directory inside the container
WORKDIR /app

# Copy the pom.xml file to download dependencies first
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the rest of the source code
COPY src ./src

# Package the application, skipping tests for faster builds in this context
RUN mvn package -DskipTests

# Stage 2: Create the final, smaller runtime image
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copy the built JAR file from the 'builder' stage
COPY --from=builder /app/target/*.jar app.jar

# Expose the port the application will run on
EXPOSE 8080

# The command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]