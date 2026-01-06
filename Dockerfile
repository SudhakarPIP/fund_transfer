FROM eclipse-temurin:17-jre-alpine

ARG JAR_FILE=target/fund-transfer-1.0.0-SNAPSHOT.jar

WORKDIR /app

COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]


