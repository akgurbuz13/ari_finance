FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY ../gradle gradle
COPY ../gradlew .
COPY ../settings.gradle.kts .
COPY ../build.gradle.kts .
COPY ../gradle.properties .
COPY core-banking/build.gradle.kts core-banking/
COPY core-banking/src core-banking/src
RUN chmod +x gradlew && ./gradlew :core-banking:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S ova && adduser -S ova -G ova
COPY --from=build /app/core-banking/build/libs/*.jar app.jar
RUN chown -R ova:ova /app
USER ova
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -q --spider http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
