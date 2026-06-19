# ── Stage 1: build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S almacenes && adduser -S almacenes -G almacenes
WORKDIR /app
COPY --from=builder /app/target/almacenes-*.jar app.jar
RUN chown almacenes:almacenes app.jar
USER almacenes
EXPOSE 8080
ENTRYPOINT ["java", "-Xms512m", "-Xmx1g", "-jar", "app.jar"]
