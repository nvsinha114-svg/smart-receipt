# Build Stage
FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Install Tesseract OCR and English trained data
RUN apt-get update && \
    apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/target/smart-receipt-backend-*.jar app.jar

ENV SERVER_PORT=8080
ENV SPRING_DATA_MONGODB_URI=mongodb://mongodb:27017/smart_receipt
ENV JWT_SECRET=9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f9a8b
ENV JWT_EXPIRATION_MS=86400000
ENV TESSERACT_DATAPATH=/usr/share/tesseract-ocr/4.00/tessdata

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
