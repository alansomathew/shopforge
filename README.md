# 🛒 ShopForge Backend (REST API)

[![Java](https://img.shields.io/badge/Java-21%20%7C%2025-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2%2B%20%2F%204.1-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%2B-316192?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-Migration-CC0200?style=for-the-badge&logo=flyway&logoColor=white)](https://flywaydb.org/)
[![Spring Security](https://img.shields.io/badge/Spring_Security-6.x-6DB33F?style=for-the-badge&logo=spring-security&logoColor=white)](https://spring.io/projects/spring-security)

The core REST API service for **ShopForge**, built on **Spring Boot**, **Spring Security**, **Spring Data JPA**, and **PostgreSQL 16**.

For full-stack system architecture and frontend instructions, refer to the [Root README](../README.md).

---

## 🌟 Key Features

- **Authentication & Security**:
  - Stateless JWT token-based authentication with short-lived access tokens (15m) and secure refresh token rotation (7d).
  - Google OAuth2 token exchange login (`/api/v1/auth/google`).
  - Role-Based Access Control (RBAC): `CUSTOMER`, `SELLER`, `ADMIN`, `SUPPORT`.
  - Account lockout protection after consecutive failed login attempts.
  - Email verification & timed password reset token flows via SMTP.
- **Product Catalog & Search**:
  - Paginated product browsing with dynamic filtering (price ranges, categories, brands, ratings, stock).
  - Native PostgreSQL Full-Text Search with `tsvector` and `GIN` indexes.
  - Recursive active category trees (`/api/v1/categories/tree`).
  - Product CRUD and stock adjustment APIs guarded by `@PreAuthorize`.
- **Database & Data Integrity**:
  - Flyway version-controlled schema migrations (`db/migration/V1__init.sql`, `V2__create_password_reset_tokens.sql`).
  - PL/pgSQL triggers for `updated_at`, automated product rating recalculation, inventory deltas, and collision-free order numbering (`ORD-YYYYMMDD-XXXX`).
  - `SeedDataRunner` automated catalog and user seeding on startup.

---

## 📂 Project Structure

```plaintext
shopforge/
├── pom.xml                            # Maven build configuration & dependencies
├── src/
│   ├── main/
│   │   ├── java/com/codewithalanso/shopforge/
│   │   │   ├── ShopforgeApplication.java
│   │   │   ├── auth/                  # Authentication & OAuth2 core
│   │   │   │   ├── controller/        # AuthController (register, login, refresh)
│   │   │   │   ├── dto/               # Request / Response DTOs
│   │   │   │   ├── security/          # JwtFilter, JwtUtil, CustomUserDetails
│   │   │   │   └── service/           # AuthService, GoogleTokenVerifier, UserService
│   │   │   ├── common/                # Shared exceptions & ApiResponse envelope
│   │   │   ├── config/                # SecurityConfig, CORS, SeedDataRunner
│   │   │   ├── controllers/           # ProductController, CategoryController, UserController
│   │   │   ├── entities/              # 20+ JPA entities (User, Product, Variant, Order, etc.)
│   │   │   ├── repositories/          # Spring Data JPA repositories
│   │   │   └── services/              # ProductService, CategoryService, EmailService
│   │   └── resources/
│   │       ├── application.properties # Server, database, JWT, and SMTP configuration
│   │       └── db/migration/          # Flyway SQL migration scripts
│   └── test/                          # Unit and integration test suites
```

---

## 🚀 Getting Started

### 1. Database Setup
Ensure PostgreSQL is running and create the database:
```sql
CREATE DATABASE shopforge;
```

### 2. Configure `application.properties`
Check `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/shopforge
spring.datasource.username=postgres
spring.datasource.password=12345
```

### 3. Build & Run
Run with the Maven wrapper:
```bash
./mvnw clean compile
./mvnw spring-boot:run
```
The API will be available at `http://localhost:8080`.

---

## 🔑 Default Seeded Accounts

| Role | Email | Password |
| :--- | :--- | :--- |
| **Admin** | `admin@shopforge.com` | `Admin@123` |
| **Seller** | `seller@shopforge.com` | `Seller@123` |

---

## 🧪 Testing

Run backend unit and integration tests:
```bash
./mvnw test
```
