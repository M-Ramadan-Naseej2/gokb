# ---- Build Stage ----
FROM gradle:7.6.2-jdk11 AS build
WORKDIR /build
COPY . .
WORKDIR /build/server
RUN ./gradlew clean bootJar

# ---- Production Stage ----
FROM gcr.io/distroless/java11-debian11:nonroot
WORKDIR /app
COPY --from=build /build/server/build/libs/*.jar app.jar

ENV MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health
 
EXPOSE 8080
CMD ["app.jar"]
