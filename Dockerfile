FROM openjdk:23-slim

ADD target/rinha-2024q1-crebito-0.0.1-SNAPSHOT-standalone.jar /rinha-2024q1-crebito/app.jar

EXPOSE 9999

CMD ["java", "-jar", "/rinha-2024q1-crebito/app.jar"]
