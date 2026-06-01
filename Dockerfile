FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY settings.gradle.kts build.gradle.kts ./

RUN bash ./gradlew dependencies --no-daemon

COPY src src

RUN bash ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system cheerup && useradd --system --gid cheerup cheerup

COPY --from=build /workspace/build/libs/*.jar app.jar

USER cheerup

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
