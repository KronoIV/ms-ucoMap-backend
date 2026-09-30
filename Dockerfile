FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn

RUN ./mvnw dependency:go-offline -q

COPY src ./src
RUN ./mvnw package -B -ntp

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENV PORT=8080
ENV INIT_DATA=true
ENV CORS_ORIGINS=*

ENTRYPOINT ["java", "-jar", "app.jar"]