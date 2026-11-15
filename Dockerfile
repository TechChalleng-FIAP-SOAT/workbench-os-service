FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM amazoncorretto:21-alpine
WORKDIR /app

# UID/GID numéricos: o runAsNonRoot do Kubernetes só consegue conferir um USER numérico.
RUN addgroup -S -g 10001 appgroup && adduser -S -u 10001 -G appgroup appuser

COPY --from=build /app/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER 10001

EXPOSE 8080
# Sem MaxRAMPercentage a JVM usa só 25% da memória do container como heap.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
