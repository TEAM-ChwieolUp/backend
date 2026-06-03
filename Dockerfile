FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system cheerup && useradd --system --gid cheerup cheerup

COPY build/libs/*.jar app.jar

USER cheerup

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
