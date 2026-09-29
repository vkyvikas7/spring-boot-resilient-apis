FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN useradd --system --uid 10001 --home /app app
USER app
WORKDIR /app
COPY --from=build /src/target/spring-boot-resilient-apis.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
