# =============================================================================
# Performance Problem Simulator - Java Blessed Image
# Dockerfile for Azure App Service Linux Container
# =============================================================================

# Use Microsoft's official Java 25 runtime image (matches Azure blessed image)
FROM mcr.microsoft.com/openjdk/jdk:25-ubuntu

# Set working directory
WORKDIR /app

# Copy the pre-built JAR file
COPY target/*.jar app.jar

# Expose port 8080 (Azure App Service expects this)
EXPOSE 8080

# Set JVM options for container environment
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+UseContainerSupport"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
