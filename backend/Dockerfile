FROM eclipse-temurin:17-jdk-alpine AS build
RUN apk add --no-cache curl
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S library && adduser -S library -G library
WORKDIR /app
COPY --from=build /app/target/library-backend-1.0.0.jar app.jar
RUN mkdir -p /app/data && chown -R library:library /app
USER library
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s CMD wget -q -O - http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75","-jar","app.jar"]
