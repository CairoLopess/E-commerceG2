# syntax=docker/dockerfile:1

# 1) Build: compila o .jar com o Maven Wrapper do projeto
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Garante LF no mvnw mesmo se alguém fizer checkout com CRLF no Windows
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src src
# O cache do ~/.m2 evita baixar todas as dependências a cada build
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package

# 2) Execução: só o JRE e o .jar
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 3001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
