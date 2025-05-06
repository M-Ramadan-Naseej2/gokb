FROM gradle:6.2-jdk11

WORKDIR /build

COPY . .

WORKDIR server

EXPOSE 8080

CMD ["./gradlew", "clean", "bootRun"]

