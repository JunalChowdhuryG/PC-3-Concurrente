
FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /workspace

COPY ./pom.xml .
#RUN mvn dependency:go-offline

COPY ./src ./src

RUN mvn package -Dmaven.test.skip=true
#RUN mvn clean
#RUN mvn package

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=builder /workspace/target/lp1-banco-java-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]