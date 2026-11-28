# Image unique : front Angular servi par Spring Boot. Les thèmes anime sont générés par le serveur
# au démarrage depuis la base PostgreSQL (module curation) : voir docs/DEPLOY.md.
# Multi-architecture (x86 et Arm) : front et jar sont compilés une fois sur la machine de build
# ($BUILDPLATFORM, sans émulation), seule l'image d'exécution Java dépend du processeur.

# 1. Front
FROM --platform=$BUILDPLATFORM node:22-alpine AS web
WORKDIR /src
COPY web/package.json web/package-lock.json web/
RUN cd web && npm ci
COPY web web
RUN cd web && npx ng build

# 2. Back
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-17 AS back
WORKDIR /src
COPY . .
RUN mvn -q clean install -DskipTests

# 3. Exécution
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=back /src/app/target/app-0.1.0-SNAPSHOT.jar app.jar
COPY content content
COPY --from=web /src/web/dist/blindle-web/browser static
ENV JAVA_OPTS="-Xmx350m"
ENV BLINDLE_ALLOWED_ORIGINS="https://*.onrender.com,https://*.koyeb.app,https://*.trycloudflare.com,https://*.discordsays.com,http://localhost:*"
EXPOSE 8080
CMD java $JAVA_OPTS -jar app.jar \
    --blindle.content.dir=content \
    --spring.web.resources.static-locations=file:static/ \
    --blindle.allowed-origins=$BLINDLE_ALLOWED_ORIGINS
