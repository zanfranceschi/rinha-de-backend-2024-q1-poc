#FROM openjdk:8-alpine
FROM openjdk:23-slim

ADD target/rinha-concurrency-control-0.0.1-SNAPSHOT-standalone.jar /rinha-concurrency-control/app.jar

EXPOSE 9999

CMD ["java", "-jar", "/rinha-concurrency-control/app.jar"]
