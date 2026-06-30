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
# El perfil de producción escribe logs en /var/log/almacenes (logback-spring.xml).
# El contenedor corre como usuario NO-root (almacenes), por lo que el directorio
# debe existir y pertenecerle ANTES de cambiar de usuario; de lo contrario logback
# no puede crear el archivo y el logging a archivo con rotación no funciona.
RUN chown almacenes:almacenes app.jar \
    && mkdir -p /var/log/almacenes \
    && chown almacenes:almacenes /var/log/almacenes
USER almacenes
EXPOSE 8080
ENTRYPOINT ["java", "-Xms512m", "-Xmx1g", "-jar", "app.jar"]
