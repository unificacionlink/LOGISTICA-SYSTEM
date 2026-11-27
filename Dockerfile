# Build multi-stage: frontend (Vite) -> backend (Maven) sirviendo el build estatico.
# Un solo Web Service en Render (mismo criterio de simplicidad que el render.yaml anterior).

FROM node:22-slim AS frontend-build
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /backend
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=frontend-build /frontend/dist ./src/main/resources/static
RUN mvn -q -B package -DskipTests

# Ubuntu (glibc) y no Alpine: el OCR (Tess4j/JNA) necesita la libreria nativa de
# Tesseract. El idioma (spa.traineddata) viaja dentro del jar.
FROM eclipse-temurin:21-jre
RUN apt-get update  && apt-get install -y --no-install-recommends libtesseract5  && rm -rf /var/lib/apt/lists/*  && cd "/usr/lib/$(uname -m)-linux-gnu"  && for f in libtesseract.so.* liblept.so.* libleptonica.so.*; do       [ -e "$f" ] || continue; ln -sf "$f" "${f%%.so.*}.so";     done  && ls -l libtesseract.so
WORKDIR /app
COPY --from=backend-build /backend/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
