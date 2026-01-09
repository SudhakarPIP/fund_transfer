FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache netcat-openbsd

ARG JAR_FILE=target/notification-service-1.0.0-SNAPSHOT.jar

WORKDIR /app

COPY ${JAR_FILE} app.jar

EXPOSE 8083

ENTRYPOINT ["java","-jar","/app/app.jar"]

