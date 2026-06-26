FROM gradle:8.7-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle --no-daemon clean bootJar

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
ENV PORT=8080
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar"]
