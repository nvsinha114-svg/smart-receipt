# Smart Receipt - Receipt Scanner App Backend

Production-grade Spring Boot 3 & Java 17 backend service for the **Smart Receipt - Receipt Scanner App**. Featuring clean layered architecture, JWT authentication, MongoDB persistence, Tesseract OCR text extraction for images & PDFs, OpenPDF report generation, Bean Validation, OpenAPI 3 (Swagger UI), Docker containerization, and JUnit 5 unit/integration tests.

---

## Table of Contents
1. [Project Overview](#project-overview)
2. [Key Features](#key-features)
3. [Tech Stack](#tech-stack)
4. [Architecture & Layered Structure](#architecture--layered-structure)
5. [Project Structure](#project-structure)
6. [MongoDB Setup & Indexes](#mongodb-setup--indexes)
7. [Tesseract OCR Setup](#tesseract-ocr-setup)
8. [Environment Variables](#environment-variables)
9. [How to Run Locally](#how-to-run-locally)
10. [How to Run Using Docker](#how-to-run-using-docker)
11. [API Documentation & Swagger](#api-documentation--swagger)
12. [API Endpoints Reference](#api-endpoints-reference)
13. [Example Requests & Responses](#example-requests--responses)
14. [Testing](#testing)
15. [Future Improvements](#future-improvements)

---

## Project Overview
**Smart Receipt Backend** automates receipt tracking and expense management. Users can upload receipt files (JPG, JPEG, PNG, PDF) or manually input receipts. The backend extracts merchant details, transaction date, category, line items, and total amounts using a combined **OCR + AI Parser pipeline** (with robust rule-based Tesseract local fallback), persists data in **MongoDB**, and generates downloadable PDF summaries.

---

## Spring AI Integration & Architecture Flow

### Why OCR + AI are both used
- **Tesseract OCR**: Responsible for scanning the uploaded receipt image/PDF and extracting the raw, unstructured, noisy text.
- **Spring AI / LLM**: Responsible for processing the raw, unstructured OCR text, parsing out merchant name, receipt date, currency, item name, quantity, unit price, and assigning optional categorizations (e.g., Food, Transportation, Shopping, Education, Healthcare, Utilities, Other).

### Data Flow
```
Receipt Image/PDF File
       │
       ▼
Tesseract OCR / PDFBox
       │
       ▼
Raw OCR Text
       │
       ▼
Spring AI (OpenAI chatClient.entity())
       │
       ▼
Structured DTO (ReceiptAIResponse)
       │
       ▼
Java Validation & Processing
       │
       ▼
Deterministic Calculations (subtotal = qty × price, total = sum of subtotals)
       │
       ▼
MongoDB Persisted Entity
       │
       ▼
React Frontend / PDF Generation
```

### CRITICAL: Deterministic Financial Calculations
To ensure absolute accuracy, **the LLM is never trusted with calculating receipt totals**. The AI is solely responsible for extracting and normalizing raw quantity and unit price tokens. The Spring Boot backend uses `BigDecimal` to compute all item subtotals and the final total dynamically in Java.

### AI Fallback Heuristic
If the AI service is disabled (no API key configured), times out, fails, or returns invalid JSON:
1. The exception is logged properly.
2. The application **does not crash**.
3. It seamlessly falls back to the deterministic, rule-based local OCR parser (`OcrService` regex parser) to extract receipt metadata and items.

---

## Key Features
- **User Authentication & Authorization**:
  - Stateless JWT token-based security.
  - Role-Based Access Control (`USER`, `ADMIN`).
  - BCrypt password hashing.
- **Receipt Management**:
  - Full CRUD operations (`POST`, `GET`, `GET /{id}`, `PUT`, `DELETE`).
  - Strict ownership rules: `USER` can only access their own receipts; `ADMIN` can access all receipts.
- **Receipt File Upload & OCR Engine**:
  - Supports image formats (`JPG`, `JPEG`, `PNG`) and `PDF` documents.
  - Isolated `OcrService` running Tess4J + Apache PDFBox text extraction.
  - Rule-based parser extracting merchant name, date, total amount, and line items.
- **PDF Report Generation**:
  - Dynamic PDF document creation with header metadata, itemized table, and total summary.
- **Validation & Error Handling**:
  - Comprehensive Jakarta Bean Validation (`@Valid`).
  - Unified `@RestControllerAdvice` with clean, standardized error payloads and appropriate HTTP status codes (201, 200, 204, 400, 401, 403, 404, 409, 500).
- **API Documentation**:
  - Interactive Swagger UI at `/swagger-ui.html` with Bearer JWT SecurityScheme.
- **Dockerization**:
  - Multi-stage Dockerfile and Docker Compose orchestration with MongoDB container.

---

## Tech Stack
- **Language**: Java 17 (LTS)
- **Framework**: Spring Boot 3.4.3
- **Security**: Spring Security 6 & JJWT 0.12.6
- **Database**: MongoDB 7 with Spring Data MongoDB
- **OCR & PDF Engines**: Tess4J 5.13.0, Apache PDFBox 3.0.3, OpenPDF 2.0.3
- **Documentation**: Springdoc OpenAPI Starter 2.8.5
- **Utilities**: Lombok, Jakarta Validation
- **Build Tool**: Apache Maven 3.9+
- **Containerization**: Docker & Docker Compose

---

## Architecture & Layered Structure
The application follows a clean 4-tier layered architecture:
```
Client / Swagger UI
       │
       ▼
Controllers (REST API Layer - DTO Mapping & HTTP handling)
       │
       ▼
Services (Business Logic, OCR Engine, Security, PDF Generation)
       │
       ▼
Repositories (Spring Data Mongo Repositories)
       │
       ▼
MongoDB Database (`smart_receipt` - `users` & `receipts` collections)
```

---

## Project Structure
```
src/main/java/com/smartreceipt/
├── SmartReceiptApplication.java
├── config/
│   ├── MongoConfig.java
│   └── OpenApiConfig.java
├── controller/
│   ├── AuthController.java
│   ├── ReceiptController.java
│   └── SwaggerRedirectController.java
├── dto/
│   ├── AuthRequest.java
│   ├── AuthResponse.java
│   ├── ErrorResponse.java
│   ├── ReceiptItemDto.java
│   ├── ReceiptRequest.java
│   ├── ReceiptResponse.java
│   └── RegisterRequest.java
├── entity/
│   ├── Receipt.java
│   ├── ReceiptItem.java
│   ├── Role.java
│   └── User.java
├── exception/
│   ├── DuplicateResourceException.java
│   ├── GlobalExceptionHandler.java
│   ├── OcrException.java
│   ├── ResourceNotFoundException.java
│   └── UnauthorizedAccessException.java
├── repository/
│   ├── ReceiptRepository.java
│   └── UserRepository.java
├── security/
│   ├── CustomUserDetailsService.java
│   ├── JwtAuthenticationFilter.java
│   ├── JwtService.java
│   ├── SecurityConfig.java
│   └── UserPrincipal.java
└── service/
    ├── AuthService.java
    ├── OcrService.java
    ├── PdfService.java
    ├── ReceiptService.java
    └── UserService.java

src/main/resources/
└── application.yml
```

---

## MongoDB Setup & Indexes
The app uses MongoDB database `smart_receipt` with two primary collections:
1. **users**:
   - `_id`: Unique identifier (String)
   - `email`: Unique index (`@Indexed(unique = true)`) for fast lookups and registration duplicate prevention.
   - `password`: BCrypt hash.
   - `role`: `USER` or `ADMIN`.
2. **receipts**:
   - `_id`: Unique identifier (String)
   - `userId`: Indexed (`@Indexed`) for high-performance user query filtering.
   - `createdAt`: Timestamp.

---

## Tesseract OCR Setup
### 1. Windows Setup
- Tess4J bundles native Tesseract DLLs automatically.
- Ensure language file `eng.traineddata` exists in `./tessdata/eng.traineddata` or configure environment variable `TESSERACT_DATAPATH`.

### 2. Linux Setup
Install Tesseract native package and English language data:
```bash
sudo apt-get update
sudo apt-get install -y tesseract-ocr tesseract-ocr-eng
```

### 3. Docker Setup
The included multi-stage `Dockerfile` automatically installs `tesseract-ocr` and `tesseract-ocr-eng` in the runtime JRE image.

---

## Environment Variables
Create a `.env` file or export the following variables:

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `SERVER_PORT` | `8080` | Application HTTP Port |
| `SPRING_DATA_MONGODB_URI` | `mongodb://localhost:27017/smart_receipt` | MongoDB Connection URI |
| `JWT_SECRET` | `9a8b7c6d5e4f3a2b1c0d9e8f...` | 256-bit Secret Key for signing JWTs |
| `JWT_EXPIRATION_MS` | `86400000` | JWT Validity duration in milliseconds (24 hours) |
| `TESSERACT_DATAPATH` | `./tessdata` | Path to directory containing `eng.traineddata` |
| `OPENAI_API_KEY` | *(optional)* | OpenAI API Key for AI receipt parsing layer (Leave blank to use Tesseract local fallback) |
| `OPENAI_MODEL` | `gpt-4o-mini` | Spring AI OpenAI Chat Model to utilize |

---

## How to Run Locally

### Prerequisites
- JDK 17+
- Maven 3.9+
- Running MongoDB instance on port 27017 (e.g. `localhost:27017`)

### Commands
```bash
# Clone and enter project directory
cd smart_reciept

# Build and run tests
mvn clean test

# Package application
mvn clean package

# Run Spring Boot application
mvn spring-boot:run
```

---

## How to Run Using Docker

To launch both Spring Boot App and MongoDB 7 in isolated containers:
```bash
# Start all containers in background
docker-compose up -d --build

# View logs
docker-compose logs -f app

# Stop containers
docker-compose down -v
```

---

## API Documentation & Swagger
Once the application is running, open your browser and navigate to:
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

To test protected endpoints in Swagger UI:
1. Call `POST /api/auth/register` or `POST /api/auth/login`.
2. Copy the returned `token`.
3. Click **Authorize** at the top right of Swagger UI, enter `Bearer <your_token>`, and click Authorize.

---

## API Endpoints Reference

### Public Authentication Endpoints
| Method | Endpoint | Description | Status Code |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Register new user account | 201 CREATED |
| `POST` | `/api/auth/login` | Authenticate user and issue JWT | 200 OK |

### Protected Receipt Endpoints (Requires Bearer JWT)
| Method | Endpoint | Description | Status Code |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/receipts` | Manually create receipt | 201 CREATED |
| `GET` | `/api/receipts` | List receipts (User sees own, Admin sees all) | 200 OK |
| `GET` | `/api/receipts/{id}` | Get receipt details by ID | 200 OK |
| `PUT` | `/api/receipts/{id}` | Update receipt by ID | 200 OK |
| `DELETE`| `/api/receipts/{id}` | Delete receipt by ID | 204 NO CONTENT |
| `POST` | `/api/receipts/upload` | Upload receipt image/PDF for OCR scanning | 201 CREATED |
| `GET` | `/api/receipts/{id}/pdf` | Download formatted receipt PDF summary | 200 OK |

---

## Example Requests & Responses

### 1. User Registration (`POST /api/auth/register`)
**Request:**
```json
{
  "name": "Alex Johnson",
  "email": "alex.johnson@example.com",
  "password": "Password123!",
  "role": "USER"
}
```

**Response (201 Created):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiI2NmMxY...",
  "type": "Bearer",
  "id": "66c1a8f9e4b0123456789abc",
  "name": "Alex Johnson",
  "email": "alex.johnson@example.com",
  "role": "USER"
}
```

---

### 2. Manual Receipt Creation (`POST /api/receipts`)
**Headers:**
`Authorization: Bearer <your_jwt_token>`

**Request:**
```json
{
  "merchantName": "Whole Foods Market",
  "receiptDate": "2026-08-12",
  "totalAmount": 42.50,
  "items": [
    {
      "name": "Organic Almond Milk",
      "quantity": 2,
      "price": 4.50
    },
    {
      "name": "Fresh Strawberries",
      "quantity": 1,
      "price": 5.50
    },
    {
      "name": "Wild Salmon Fillet",
      "quantity": 1,
      "price": 28.00
    }
  ]
}
```

**Response (201 Created):**
```json
{
  "id": "66c1b012e4b0987654321def",
  "merchantName": "Whole Foods Market",
  "receiptDate": "2026-08-12",
  "totalAmount": 42.50,
  "items": [
    {
      "name": "Organic Almond Milk",
      "quantity": 2,
      "price": 4.50
    },
    {
      "name": "Fresh Strawberries",
      "quantity": 1,
      "price": 5.50
    },
    {
      "name": "Wild Salmon Fillet",
      "quantity": 1,
      "price": 28.00
    }
  ],
  "userId": "66c1a8f9e4b0123456789abc",
  "createdAt": "2026-08-12T13:20:00"
}
```

---

### 3. OCR Receipt Upload (`POST /api/receipts/upload`)
**Headers:**
`Authorization: Bearer <your_jwt_token>`
`Content-Type: multipart/form-data`

**Form Parameter:**
- `file`: `sample_receipt.png`

**Response (201 Created):**
```json
{
  "id": "66c1c234e4b0555555555555",
  "merchantName": "WALMART SUPERCENTER",
  "receiptDate": "2026-08-10",
  "totalAmount": 17.80,
  "items": [
    {
      "name": "Milk",
      "quantity": 2,
      "price": 4.50
    },
    {
      "name": "Bread",
      "quantity": 1,
      "price": 2.99
    },
    {
      "name": "Coffee",
      "quantity": 1,
      "price": 8.99
    }
  ],
  "userId": "66c1a8f9e4b0123456789abc",
  "createdAt": "2026-08-12T13:22:00"
}
```

---

### 4. PDF Download (`GET /api/receipts/{id}/pdf`)
**Headers:**
`Authorization: Bearer <your_jwt_token>`

**Response (200 OK):**
Binary stream (`Content-Type: application/pdf`, `Content-Disposition: attachment; filename="receipt_{id}.pdf"`).

---

## Testing

Execute full automated unit and integration tests using Maven:
```bash
mvn clean test
```

---

## Future Improvements
- Multi-currency receipt conversion using exchange rate APIs.
- AI-assisted OCR refinement using LLM text parsing for complex multi-page invoices.
- Receipt category breakdown (Groceries, Electronics, Dining, Travel) with spending analytics charts.
