# --- Etapa 1: Construcción (Build) ---
FROM maven:3.9.9-eclipse-temurin-17 as builder

WORKDIR /workspace
COPY pom.xml .
COPY src ./src

RUN mvn package -Dmaven.test.skip=true

# --- Etapa 2: Ejecución (Run) ---
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=builder /workspace/target/cliente-cli-java-0.0.1-SNAPSHOT-jar-with-dependencies.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]