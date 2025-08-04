# ---- Build Stage ----
FROM gradle:7.6.2-jdk11 AS build
WORKDIR /build
COPY . .
WORKDIR /build/server
RUN ./gradlew clean bootJar

# ---- Production Stage ----
FROM openjdk:11-jre-slim
WORKDIR /app
COPY --from=build /build/server/build/libs/*.jar app.jar

# Health endpoint env for K8s/Rancher readiness/liveness probes
ENV MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]