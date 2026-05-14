# ─────────────────────────────────────────────
# Etapa 1: Build con Maven
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copiar solo los archivos de dependencias primero (cache layer)
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Descargar dependencias (cacheadas si pom.xml no cambia)
RUN ./mvnw dependency:go-offline -q

# Copiar el codigo fuente y compilar
COPY src ./src
RUN ./mvnw package -DskipTests -q

# ─────────────────────────────────────────────
# Etapa 2: Imagen final solo con el JAR
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copiar el JAR generado en la etapa anterior
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Puerto expuesto (Render/Railway inyectan $PORT)
EXPOSE 8080

# Variables de entorno con valores por defecto
ENV PORT=8080
ENV INIT_DATA=true
ENV CORS_ORIGINS=*

ENTRYPOINT ["java", "-jar", "app.jar"]

