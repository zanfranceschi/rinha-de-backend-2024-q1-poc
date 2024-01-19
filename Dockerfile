FROM openjdk:23-slim

ADD target/rinha-2024q1-crebito-0.1.0-SNAPSHOT-standalone.jar /rinha-2024q1-crebito/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/rinha-2024q1-crebito/app.jar"]
