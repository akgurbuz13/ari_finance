FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew .
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY gradle.properties* .
COPY blockchain-service/build.gradle.kts blockchain-service/
COPY blockchain-service/src blockchain-service/src
RUN chmod +x gradlew && ./gradlew :blockchain-service:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S ari && adduser -S ari -G ari
COPY --from=build /app/blockchain-service/build/libs/*.jar app.jar
RUN chown -R ari:ari /app
USER ari
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=3s CMD wget -q --spider http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
