FROM gradle:8.11.1-jdk21 AS build
WORKDIR /workspace/app
COPY . .
RUN gradle -PserverOnly :server:bootJar --no-daemon
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/tmp/builds/server/libs/server-1.0.0.jar /app/server.jar
EXPOSE 8080
USER 10001
ENTRYPOINT ["java","-jar","/app/server.jar"]
