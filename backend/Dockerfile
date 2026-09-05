# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Run stage ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# Devanagari-capable font for bilingual (Hindi/English) PDF orders (movement lifecycle) - fonts-lohit-deva
# is a small, openly-licensed (OFL) Debian package, installed at build time rather than committing any
# font binary into the repo (Windows-bundled fonts like Nirmala UI are proprietary and not redistributable).
# PdfHeaderFooterHelper looks for it at /usr/share/fonts/truetype/lohit-devanagari/Lohit-Devanagari.ttf and
# degrades gracefully (English-only, no exception) if this layer is ever removed.
RUN apt-get update && apt-get install -y --no-install-recommends fonts-lohit-deva && rm -rf /var/lib/apt/lists/*
RUN useradd --system --no-create-home appuser
COPY --from=build /app/target/hrms-backend-*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
