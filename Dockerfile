# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# The Maven wrapper needs curl/wget to fetch its jar when it isn't bundled.
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Copy Maven wrapper + project descriptor first to leverage layer caching.
COPY mvnw ./
COPY .mvn ./.mvn
COPY pom.xml ./
RUN chmod +x mvnw

# Pre-fetch dependencies (cached unless pom.xml changes).
RUN ./mvnw -B -q dependency:go-offline

# Copy source and build the executable jar (skip tests; run them in CI).
COPY src ./src
RUN ./mvnw -B -q package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# Run as a non-root user for security.
RUN groupadd --system smartos && useradd --system --gid smartos smartos
USER smartos

# Copy only the built jar (no sources, no Maven, no JDK).
COPY --from=build --chown=smartos:smartos /app/target/*.jar /app/app.jar

# Render provides PORT; default to 8080 for local runs.
ENV PORT=8080
EXPOSE 8080

# Bind to all interfaces (default) and honor the PORT env var.
ENTRYPOINT ["sh", "-c", "exec java -jar /app/app.jar --server.port=${PORT}"]