FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/barncart-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]