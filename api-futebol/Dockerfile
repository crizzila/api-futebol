# Etapa 1: build com JDK 17 (gera o .jar usando o Maven Wrapper)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Corrige fim de linha (CRLF do Windows) e dá permissão de execução
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
# Baixa dependências antes do código: o Docker reaproveita esse cache
RUN ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

# Etapa 2: imagem final só com JRE 17 (menor, sem Maven nem código-fonte)
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# O Render injeta a variável PORT; o Spring lê em server.port
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
