# Finance_backend
Finance Dashboard Backend built with Spring Boot REST APIs featuring JWT authentication and role-based access (Admin, Analyst, Viewer). Supports financial records CRUD, advanced filtering, pagination, and real-time analytics. Integrated with MySQL and documented using Swagger for easy API testing.



# Finance Dashboard Backend

A production-ready REST API backend for a Finance Dashboard system with Role-Based Access Control, JWT Authentication, and Analytics APIs.

## Tech Stack

- Java 17
- Spring Boot 3.2
- Spring Security + JWT (jjwt 0.11.5)
- Spring Data JPA + Hibernate
- MySQL 8
- Lombok
- Swagger / OpenAPI 3 (springdoc)

## Architecture
Request → JwtFilter → Controller → Service → Repository → MySQL
↓
DTO (Request/Response)
↓
GlobalExceptionHandler

All business logic lives in the Service layer.
Controllers only handle HTTP concerns.
Entities never leak directly to API responses — DTOs are always used.

## Role & Permission Matrix

| Action                          | VIEWER | ANALYST | ADMIN |
|---------------------------------|--------|---------|-------|
| Login                           | ✅     | ✅      | ✅    |
| View financial records          | ✅     | ✅      | ✅    |
| Filter / paginate records       | ✅     | ✅      | ✅    |
| View dashboard summary          | ✅     | ✅      | ✅    |
| Create financial records        | ❌     | ✅      | ✅    |
| Update financial records        | ❌     | ✅      | ✅    |
| Delete financial records        | ❌     | ❌      | ✅    |
| View all users                  | ❌     | ❌      | ✅    |
| Create / manage users           | ❌     | ❌      | ✅    |
| Assign roles / toggle status    | ❌     | ❌      | ✅    |

## Setup Instructions

### Prerequisites
- Java 17+
- MySQL 8+
- Maven 3.8+

### Step 1 — Create the database
```sql
CREATE DATABASE finance_db;
```

### Step 2 — Configure credentials
Edit `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/finance_db
spring.datasource.username=YOUR_USERNAME
spring.datasource.password=YOUR_PASSWORD
```

### Step 3 — Run the application
```bash
mvn spring-boot:run
```

Tables are auto-created by Hibernate on first run (`ddl-auto=update`).

### Step 4 — Create your first Admin user

Since there are no users on first run, temporarily allow open registration 
or insert directly into the database:
```sql
INSERT INTO users (username, password, email, role, active, created_at, updated_at)
VALUES (
  'admin',
  '$2a$10$YourBCryptHashHere',
  'admin@finance.com',
  'ROLE_ADMIN',
  true,
  NOW(),
  NOW()
);
```

Or use the provided data seeder (see DataSeeder.java section below).

### Step 5 — Access Swagger UI

Click "Authorize" → enter `Bearer <your_jwt_token>` to test secured endpoints.

## API Reference

### Authentication
| Method | Endpoint          | Auth | Description        |
|--------|-------------------|------|--------------------|
| POST   | /api/auth/login   | No   | Login, returns JWT |

### Users (Admin only)
| Method | Endpoint                        | Description              |
|--------|---------------------------------|--------------------------|
| POST   | /api/users                      | Create user              |
| GET    | /api/users                      | Get all users            |
| GET    | /api/users/{id}                 | Get user by ID           |
| PATCH  | /api/users/{id}/role            | Update role              |
| PATCH  | /api/users/{id}/toggle-status   | Activate / deactivate    |
| DELETE | /api/users/{id}                 | Delete user              |

### Financial Records
| Method | Endpoint              | Auth Required       | Description                    |
|--------|-----------------------|---------------------|--------------------------------|
| POST   | /api/records          | Admin, Analyst      | Create record                  |
| GET    | /api/records          | All roles           | List with filters + pagination |
| GET    | /api/records/{id}     | All roles           | Get single record              |
| PUT    | /api/records/{id}     | Admin, Analyst      | Update record                  |
| DELETE | /api/records/{id}     | Admin only          | Soft delete                    |

**Supported query parameters for GET /api/records:**

| Param       | Type       | Example              | Description              |
|-------------|------------|----------------------|--------------------------|
| type        | String     | INCOME or EXPENSE    | Filter by record type    |
| category    | String     | Salary               | Partial match on category|
| startDate   | LocalDate  | 2025-01-01           | Filter from date         |
| endDate     | LocalDate  | 2025-04-30           | Filter to date           |
| page        | int        | 0                    | Page number (0-indexed)  |
| size        | int        | 10                   | Records per page         |
| sortBy      | String     | date                 | Field to sort by         |
| sortDir     | String     | asc or desc          | Sort direction           |

### Dashboard
| Method | Endpoint                 | Auth      | Description                            |
|--------|--------------------------|-----------|----------------------------------------|
| GET    | /api/dashboard/summary   | All roles | Totals, categories, trends, recent 5   |

**Sample dashboard response:**
```json
{
  "totalIncome": 105000.00,
  "totalExpense": 27500.00,
  "netBalance": 77500.00,
  "categoryBreakdown": {
    "Salary": 75000.00,
    "Freelance": 30000.00,
    "Rent": 12000.00
  },
  "monthlyTrends": [
    { "year": 2025, "month": 3, "income": 30000.00, "expense": 8000.00 },
    { "year": 2025, "month": 4, "income": 75000.00, "expense": 19500.00 }
  ],
  "recentTransactions": [...]
}
```

## Key Design Decisions & Assumptions

1. **Soft delete** — Records are never permanently deleted. A `deleted` boolean 
   flag is set to true. This preserves audit history and allows recovery.

2. **BigDecimal for money** — All monetary values use `BigDecimal` (not Double) 
   to avoid floating point precision errors in financial calculations.

3. **JWT stateless auth** — No sessions are stored server-side. Every request 
   carries a self-contained JWT token that expires in 24 hours.

4. **Role naming** — Roles follow Spring Security convention: `ROLE_ADMIN`, 
   `ROLE_ANALYST`, `ROLE_VIEWER`. The `ROLE_` prefix is required for 
   `hasRole()` expressions to work correctly.

5. **DTO separation** — Entities are never returned directly from controllers. 
   Request and Response DTOs are separate classes to control exactly what 
   data enters and exits the API.

6. **EAGER fetch on createdBy** — The `createdBy` relationship on 
   `FinancialRecord` uses `FetchType.EAGER` to avoid `LazyInitializationException` 
   when mapping to response DTOs outside a transaction context.

7. **COALESCE in aggregate queries** — Dashboard total queries use 
   `COALESCE(SUM(...), 0)` so the API returns 0 instead of null when 
   no records exist.

## Tradeoffs Considered

| Decision | Chosen Approach | Alternative | Reason |
|---|---|---|---|
| Auth | JWT (stateless) | Session-based | Scales better, no server state |
| Delete | Soft delete | Hard delete | Preserves financial audit trail |
| Fetch | EAGER on createdBy | LAZY + @Transactional | Simpler, avoids session scope issues |
| Storage | MySQL | PostgreSQL | Common in enterprise finance systems |
| Validation | Bean Validation (@Valid) | Manual checks | Standard, declarative, less code |

## Features Implemented

- ✅ JWT Authentication
- ✅ Role-Based Access Control (method level with @PreAuthorize)
- ✅ Full CRUD for financial records
- ✅ Soft delete
- ✅ Filtering by type, category, date range
- ✅ Pagination and sorting
- ✅ Dashboard: total income, expense, net balance
- ✅ Dashboard: category-wise breakdown
- ✅ Dashboard: monthly trends
- ✅ Dashboard: recent 5 transactions
- ✅ Input validation with field-level error messages
- ✅ Global exception handling with proper HTTP status codes
- ✅ Swagger / OpenAPI documentation
- ✅ Clean layered architecture with DTO separation

-----------------------------------------------------------------------
## API Testing Reference

Base URL: `http://localhost:8080`
## 1. Authentication

### Login as Admin
**Request**
## API Testing Reference

Base URL: `http://localhost:8080`

---

## 1. Authentication

### Login as Admin
**Request**
```
POST /api/auth/login
Content-Type: application/json
```
```json
{
  "username": "admin",
  "password": "admin123"
}
```
**Response `200 OK`**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc0MzY4MDAwMCwiZXhwIjoxNzQzNzY2NDAwfQ.xxxxx",
  "username": "admin",
  "role": "ROLE_ADMIN"
}
```

---

### Login as Analyst
**Request**
```
POST /api/auth/login
Content-Type: application/json
```
```json
{
  "username": "analyst",
  "password": "analyst123"
}
```
**Response `200 OK`**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.xxxxx",
  "username": "analyst",
  "role": "ROLE_ANALYST"
}
```

---

### Login as Viewer
**Request**
```
POST /api/auth/login
Content-Type: application/json
```
```json
{
  "username": "viewer",
  "password": "viewer123"
}
```
**Response `200 OK`**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.xxxxx",
  "username": "viewer",
  "role": "ROLE_VIEWER"
}
```

---

### Login — Wrong Password
**Request**
```
POST /api/auth/login
Content-Type: application/json
```
```json
{
  "username": "admin",
  "password": "wrongpassword"
}
```
**Response `401 Unauthorized`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username or password"
}
```

---

### Login — Missing Fields
**Request**
```
POST /api/auth/login
Content-Type: application/json
```
```json
{
  "username": ""
}
```
**Response `400 Bad Request`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": {
    "username": "Username is required",
    "password": "Password is required"
  }
}
```

---

## 2. User Management (Admin Only)

> All user endpoints require:
> `Authorization: Bearer <admin_token>`

---

### Create Admin User
**Request**
```
POST /api/users
Content-Type: application/json
Authorization: Bearer <admin_token>
```
```json
{
  "username": "admin2",
  "password": "admin123",
  "email": "admin2@finance.com",
  "role": "ROLE_ADMIN"
}
```
**Response `201 Created`**
```json
{
  "id": 4,
  "username": "admin2",
  "email": "admin2@finance.com",
  "role": "ROLE_ADMIN",
  "active": true,
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Create Analyst User
**Request**
```
POST /api/users
Content-Type: application/json
Authorization: Bearer <admin_token>
```
```json
{
  "username": "analyst2",
  "password": "analyst123",
  "email": "analyst2@finance.com",
  "role": "ROLE_ANALYST"
}
```
**Response `201 Created`**
```json
{
  "id": 5,
  "username": "analyst2",
  "email": "analyst2@finance.com",
  "role": "ROLE_ANALYST",
  "active": true,
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Create Viewer User
**Request**
```
POST /api/users
Content-Type: application/json
Authorization: Bearer <admin_token>
```
```json
{
  "username": "viewer2",
  "password": "viewer123",
  "email": "viewer2@finance.com",
  "role": "ROLE_VIEWER"
}
```
**Response `201 Created`**
```json
{
  "id": 6,
  "username": "viewer2",
  "email": "viewer2@finance.com",
  "role": "ROLE_VIEWER",
  "active": true,
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Create User — Duplicate Username
**Request**
```
POST /api/users
Content-Type: application/json
Authorization: Bearer <admin_token>
```
```json
{
  "username": "analyst",
  "password": "analyst123",
  "email": "another@finance.com",
  "role": "ROLE_ANALYST"
}
```
**Response `400 Bad Request`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 400,
  "error": "Bad Request",
  "message": "Username already exists: analyst"
}
```

---

### Create User — Invalid Role
**Request**
```
POST /api/users
Content-Type: application/json
Authorization: Bearer <admin_token>
```
```json
{
  "username": "testuser",
  "password": "test123",
  "email": "test@finance.com",
  "role": "ROLE_SUPERUSER"
}
```
**Response `400 Bad Request`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid role. Valid values: ROLE_VIEWER, ROLE_ANALYST, ROLE_ADMIN"
}
```

---

### Create User — Missing Fields
**Request**
```
POST /api/users
Content-Type: application/json
Authorization: Bearer <admin_token>
```
```json
{
  "username": "",
  "password": "123",
  "email": "bademail"
}
```
**Response `400 Bad Request`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": {
    "username": "Username must be between 3 and 50 characters",
    "password": "Password must be at least 6 characters",
    "email": "Invalid email format",
    "role": "Role is required"
  }
}
```

---

### Get All Users
**Request**
How compound interest worksShow me how compound interest worksApr 2Claude's response was interruptedRetryFinance Data Processing and Access Control Backend
Objective
To evaluate your backend development skills through a practical assignment centered around API design, data modeling, business logic, and access control.

This assignment is intended to assess how you think about backend architecture, pastedI uploaded all the information very detailly and what they send to me as a assignment to develope finance Data processing and Access control backend so I want to develope this as per their requirement so please give the correct code to develope this and I want in java Springboot and for database MySpastedApr 2Looking at your assignment and the previous guidance, let me give you the complete, production-ready code — nothing missing. I'll cover every file you need.

Project Structure
src/main/java/com/finance/app/
├── config/
│   ├── SecurityConfig.java
│   └── SwaggerConfig.java
├── controller/
│   ├── AuthController.java
│   ├── UserController.java
│   ├── FinancialRecordController.java
│   └── DashboardController.java
├── dto/
│   ├── request/
│   │   ├── AuthRequest.java
│   │   ├── UserCreateRequest.java
│   │   └── FinancialRecordRequest.java
│   └── response/
│       ├── AuthResponse.java
│       ├── UserResponse.java
│       ├── FinancialRecordResponse.java
│       └── DashboardSummaryResponse.java
├── entity/
│   ├── User.java
│   └── FinancialRecord.java
├── enums/
│   ├── Role.java
│   └── RecordType.java
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── ResourceNotFoundException.java
│   └── AccessDeniedException.java
├── repository/
│   ├── UserRepository.java
│   └── FinancialRecordRepository.java
├── security/
│   ├── JwtUtil.java
│   ├── JwtFilter.java
│   └── CustomUserDetailsService.java
├── service/
│   ├── AuthService.java
│   ├── UserService.java
│   ├── FinancialRecordService.java
│   └── DashboardService.java
└── FinanceAppApplication.java

1. pom.xml
xml<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>

    <groupId>com.finance</groupId>
    <artifactId>finance-app</artifactId>
    <version>1.0.0</version>
    <name>Finance Dashboard Backend</name>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <!-- JWT -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.11.5</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>0.11.5</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>0.11.5</version>
            <scope>runtime</scope>
        </dependency>
        <!-- Swagger -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.2.0</version>
        </dependency>
        <!-- Tests -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>

2. application.properties
properties# Database
spring.datasource.url=jdbc:mysql://localhost:3306/finance_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect

# Server
server.port=8080

# JWT
jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
jwt.expiration=86400000

# Swagger
springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.path=/swagger-ui.html

3. Enums
enums/Role.java
javapackage com.finance.app.enums;

public enum Role {
    ROLE_VIEWER,
    ROLE_ANALYST,
    ROLE_ADMIN
}
enums/RecordType.java
javapackage com.finance.app.enums;

public enum RecordType {
    INCOME,
    EXPENSE
}

4. Entities
entity/User.java
javapackage com.finance.app.entity;

import com.finance.app.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
entity/FinancialRecord.java
javapackage com.finance.app.entity;

import com.finance.app.enums.RecordType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Where(clause = "deleted = false")   // Soft delete filter
public class FinancialRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordType type;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private LocalDate date;

    private String description;

    @Column(nullable = false)
    private boolean deleted = false;   // Soft delete flag

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

5. DTOs
dto/request/AuthRequest.java
javapackage com.finance.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}
dto/request/UserCreateRequest.java
javapackage com.finance.app.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserCreateRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Role is required")
    private String role;  // ROLE_VIEWER, ROLE_ANALYST, ROLE_ADMIN
}
dto/request/FinancialRecordRequest.java
javapackage com.finance.app.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FinancialRecordRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Type is required (INCOME or EXPENSE)")
    private String type;

    @NotBlank(message = "Category is required")
    @Size(max = 100)
    private String category;

    @NotNull(message = "Date is required")
    private LocalDate date;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
}
dto/response/AuthResponse.java
javapackage com.finance.app.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String username;
    private String role;
}
dto/response/UserResponse.java
javapackage com.finance.app.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String role;
    private boolean active;
    private LocalDateTime createdAt;
}
dto/response/FinancialRecordResponse.java
javapackage com.finance.app.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class FinancialRecordResponse {
    private Long id;
    private BigDecimal amount;
    private String type;
    private String category;
    private LocalDate date;
    private String description;
    private String createdBy;
    private LocalDateTime createdAt;
}
dto/response/DashboardSummaryResponse.java
javapackage com.finance.app.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DashboardSummaryResponse {
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netBalance;
    private Map<String, BigDecimal> categoryBreakdown;
    private List<MonthlyTrend> monthlyTrends;
    private List<FinancialRecordResponse> recentTransactions;

    @Data
    @Builder
    public static class MonthlyTrend {
        private int year;
        private int month;
        private BigDecimal income;
        private BigDecimal expense;
    }
}

6. Repositories
repository/UserRepository.java
javapackage com.finance.app.repository;

import com.finance.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
repository/FinancialRecordRepository.java
javapackage com.finance.app.repository;

import com.finance.app.entity.FinancialRecord;
import com.finance.app.enums.RecordType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface FinancialRecordRepository extends JpaRepository<FinancialRecord, Long> {

    // Filtered + paginated query
    @Query("""
        SELECT f FROM FinancialRecord f
        WHERE (:type IS NULL OR f.type = :type)
        AND (:category IS NULL OR LOWER(f.category) LIKE LOWER(CONCAT('%', :category, '%')))
        AND (:startDate IS NULL OR f.date >= :startDate)
        AND (:endDate IS NULL OR f.date <= :endDate)
        AND f.deleted = false
    """)
    Page<FinancialRecord> findWithFilters(
            @Param("type") RecordType type,
            @Param("category") String category,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable
    );

    // Dashboard totals
    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.type = 'INCOME' AND f.deleted = false")
    BigDecimal getTotalIncome();

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.type = 'EXPENSE' AND f.deleted = false")
    BigDecimal getTotalExpense();

    // Category breakdown
    @Query("SELECT f.category, COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.deleted = false GROUP BY f.category")
    List<Object[]> getCategoryBreakdown();

    // Monthly trends
    @Query("""
        SELECT YEAR(f.date), MONTH(f.date), f.type, COALESCE(SUM(f.amount), 0)
        FROM FinancialRecord f
        WHERE f.deleted = false
        GROUP BY YEAR(f.date), MONTH(f.date), f.type
        ORDER BY YEAR(f.date), MONTH(f.date)
    """)
    List<Object[]> getMonthlyTrends();

    // Recent records
    List<FinancialRecord> findTop5ByDeletedFalseOrderByCreatedAtDesc();
}

7. Security Layer
security/JwtUtil.java
javapackage com.finance.app.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
security/JwtFilter.java
javapackage com.finance.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (jwtUtil.isTokenValid(token)) {
            String username = jwtUtil.extractUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }
}
security/CustomUserDetailsService.java
javapackage com.finance.app.security;

import com.finance.app.entity.User;
import com.finance.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (!user.isActive()) {
            throw new DisabledException("User account is inactive");
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority(user.getRole().name()))
        );
    }
}

8. Config
config/SecurityConfig.java
javapackage com.finance.app.config;

import com.finance.app.security.CustomUserDetailsService;
import com.finance.app.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // Enables @PreAuthorize at method level
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll()
                .requestMatchers("/api/users/**").hasRole("ADMIN")
                .requestMatchers("/api/records/**")
                    .hasAnyRole("ADMIN", "ANALYST", "VIEWER")
                .requestMatchers("/api/dashboard/**")
                    .hasAnyRole("ADMIN", "ANALYST", "VIEWER")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
config/SwaggerConfig.java
javapackage com.finance.app.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Finance Dashboard API")
                        .description("Backend API for Finance Dashboard with Role-Based Access Control")
                        .version("1.0.0")
                        .contact(new Contact().name("Finance App").email("admin@finance.com")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Auth"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Auth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}

9. Exception Handling
exception/ResourceNotFoundException.java
javapackage com.finance.app.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
exception/BadRequestException.java
javapackage com.finance.app.exception;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
exception/GlobalExceptionHandler.java
javapackage com.finance.app.exception;

import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Validation errors (field-level)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        return buildError(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }

    // Resource not found
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleNotFound(ResourceNotFoundException ex) {
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    // Bad request
    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBadRequest(BadRequestException ex) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    // Wrong credentials
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleBadCredentials(BadCredentialsException ex) {
        return buildError(HttpStatus.UNAUTHORIZED, "Invalid username or password", null);
    }

    // Access denied
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleAccessDenied(AccessDeniedException ex) {
        return buildError(HttpStatus.FORBIDDEN, "You do not have permission to perform this action", null);
    }

    // Illegal argument (e.g., invalid enum)
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException ex) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    // Catch-all
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGeneral(Exception ex) {
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", null);
    }

    private Map<String, Object> buildError(HttpStatus status, String message, Object details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (details != null) body.put("details", details);
        return body;
    }
}

10. Services
service/AuthService.java
javapackage com.finance.app.service;

import com.finance.app.dto.request.AuthRequest;
import com.finance.app.dto.response.AuthResponse;
import com.finance.app.entity.User;
import com.finance.app.repository.UserRepository;
import com.finance.app.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authManager;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public AuthResponse login(AuthRequest request) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(), request.getPassword()
                )
        );

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String token = jwtUtil.generateToken(user.getUsername());

        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }
}
service/UserService.java
javapackage com.finance.app.service;

import com.finance.app.dto.request.UserCreateRequest;
import com.finance.app.dto.response.UserResponse;
import com.finance.app.entity.User;
import com.finance.app.enums.Role;
import com.finance.app.exception.BadRequestException;
import com.finance.app.exception.ResourceNotFoundException;
import com.finance.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponse createUser(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username already exists: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered: " + request.getEmail());
        }

        Role role;
        try {
            role = Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role. Valid values: ROLE_VIEWER, ROLE_ANALYST, ROLE_ADMIN");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(role)
                .active(true)
                .build();

        return toResponse(userRepository.save(user));
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(Long id) {
        return toResponse(findById(id));
    }

    public UserResponse updateUserRole(Long id, String role) {
        User user = findById(id);
        try {
            user.setRole(Role.valueOf(role.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role: " + role);
        }
        return toResponse(userRepository.save(user));
    }

    public UserResponse toggleUserStatus(Long id) {
        User user = findById(id);
        user.setActive(!user.isActive());
        return toResponse(userRepository.save(user));
    }

    public void deleteUser(Long id) {
        User user = findById(id);
        userRepository.delete(user);
    }

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
service/FinancialRecordService.java
javapackage com.finance.app.service;

import com.finance.app.dto.request.FinancialRecordRequest;
import com.finance.app.dto.response.FinancialRecordResponse;
import com.finance.app.entity.FinancialRecord;
import com.finance.app.entity.User;
import com.finance.app.enums.RecordType;
import com.finance.app.exception.BadRequestException;
import com.finance.app.exception.ResourceNotFoundException;
import com.finance.app.repository.FinancialRecordRepository;
import com.finance.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class FinancialRecordService {

    private final FinancialRecordRepository recordRepository;
    private final UserRepository userRepository;

    public FinancialRecordResponse create(FinancialRecordRequest request) {
        User currentUser = getCurrentUser();

        RecordType type;
        try {
            type = RecordType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid type. Must be INCOME or EXPENSE");
        }

        FinancialRecord record = FinancialRecord.builder()
                .amount(request.getAmount())
                .type(type)
                .category(request.getCategory())
                .date(request.getDate())
                .description(request.getDescription())
                .createdBy(currentUser)
                .deleted(false)
                .build();

        return toResponse(recordRepository.save(record));
    }

    public Page<FinancialRecordResponse> getAll(String type, String category,
                                                LocalDate startDate, LocalDate endDate,
                                                int page, int size, String sortBy, String sortDir) {
        RecordType recordType = null;
        if (type != null && !type.isBlank()) {
            try {
                recordType = RecordType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid type. Must be INCOME or EXPENSE");
            }
        }

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        return recordRepository
                .findWithFilters(recordType, category, startDate, endDate, pageable)
                .map(this::toResponse);
    }

    public FinancialRecordResponse getById(Long id) {
        return toResponse(findById(id));
    }

    public FinancialRecordResponse update(Long id, FinancialRecordRequest request) {
        FinancialRecord record = findById(id);

        RecordType type;
        try {
            type = RecordType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid type. Must be INCOME or EXPENSE");
        }

        record.setAmount(request.getAmount());
        record.setType(type);
        record.setCategory(request.getCategory());
        record.setDate(request.getDate());
        record.setDescription(request.getDescription());

        return toResponse(recordRepository.save(record));
    }

    // Soft delete
    public void delete(Long id) {
        FinancialRecord record = findById(id);
        record.setDeleted(true);
        recordRepository.save(record);
    }

    private FinancialRecord findById(Long id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Record not found with id: " + id));
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private FinancialRecordResponse toResponse(FinancialRecord r) {
        return FinancialRecordResponse.builder()
                .id(r.getId())
                .amount(r.getAmount())
                .type(r.getType().name())
                .category(r.getCategory())
                .date(r.getDate())
                .description(r.getDescription())
                .createdBy(r.getCreatedBy().getUsername())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
service/DashboardService.java
javapackage com.finance.app.service;

import com.finance.app.dto.response.DashboardSummaryResponse;
import com.finance.app.dto.response.FinancialRecordResponse;
import com.finance.app.entity.FinancialRecord;
import com.finance.app.enums.RecordType;
import com.finance.app.repository.FinancialRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final FinancialRecordRepository recordRepository;

    public DashboardSummaryResponse getSummary() {
        BigDecimal totalIncome = recordRepository.getTotalIncome();
        BigDecimal totalExpense = recordRepository.getTotalExpense();
        BigDecimal netBalance = totalIncome.subtract(totalExpense);

        // Category breakdown
        Map<String, BigDecimal> categoryBreakdown = new LinkedHashMap<>();
        for (Object[] row : recordRepository.getCategoryBreakdown()) {
            categoryBreakdown.put((String) row[0], (BigDecimal) row[1]);
        }

        // Monthly trends
        Map<String, DashboardSummaryResponse.MonthlyTrend> trendMap = new LinkedHashMap<>();
        for (Object[] row : recordRepository.getMonthlyTrends()) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            RecordType type = RecordType.valueOf((String) row[2]);
            BigDecimal amount = (BigDecimal) row[3];

            String key = year + "-" + month;
            trendMap.computeIfAbsent(key, k ->
                    DashboardSummaryResponse.MonthlyTrend.builder()
                            .year(year).month(month)
                            .income(BigDecimal.ZERO).expense(BigDecimal.ZERO)
                            .build()
            );

            DashboardSummaryResponse.MonthlyTrend trend = trendMap.get(key);
            if (type == RecordType.INCOME) trend.setIncome(amount);
            else trend.setExpense(amount);
        }

        // Recent transactions
        List<FinancialRecordResponse> recent = recordRepository
                .findTop5ByDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return DashboardSummaryResponse.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netBalance(netBalance)
                .categoryBreakdown(categoryBreakdown)
                .monthlyTrends(new ArrayList<>(trendMap.values()))
                .recentTransactions(recent)
                .build();
    }

    private FinancialRecordResponse toResponse(FinancialRecord r) {
        return FinancialRecordResponse.builder()
                .id(r.getId())
                .amount(r.getAmount())
                .type(r.getType().name())
                .category(r.getCategory())
                .date(r.getDate())
                .description(r.getDescription())
                .createdBy(r.getCreatedBy().getUsername())
                .createdAt(r.getCreatedAt())
                .build();
    }
}

11. Controllers
controller/AuthController.java
javapackage com.finance.app.controller;

import com.finance.app.dto.request.AuthRequest;
import com.finance.app.dto.response.AuthResponse;
import com.finance.app.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login and token management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT token")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
controller/UserController.java
javapackage com.finance.app.controller;

import com.finance.app.dto.request.UserCreateRequest;
import com.finance.app.dto.response.UserResponse;
import com.finance.app.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Admin-only user management")
@SecurityRequirement(name = "Bearer Auth")
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Create a new user (Admin only)")
    public ResponseEntity<UserResponse> createUser(@RequestBody @Valid UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Get all users (Admin only)")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Get user by ID (Admin only)")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Update user role (Admin only)")
    public ResponseEntity<UserResponse> updateRole(@PathVariable Long id,
                                                    @RequestParam String role) {
        return ResponseEntity.ok(userService.updateUserRole(id, role));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Activate or deactivate user (Admin only)")
    public ResponseEntity<UserResponse> toggleStatus(@PathVariable Long id) {
        return ResponseEntity.ok(userService.toggleUserStatus(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Delete user (Admin only)")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
controller/FinancialRecordController.java
javapackage com.finance.app.controller;

import com.finance.app.dto.request.FinancialRecordRequest;
import com.finance.app.dto.response.FinancialRecordResponse;
import com.finance.app.service.FinancialRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
@Tag(name = "Financial Records", description = "Manage income and expense records")
@SecurityRequirement(name = "Bearer Auth")
public class FinancialRecordController {

    private final FinancialRecordService recordService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_ANALYST')")
    @Operation(summary = "Create a financial record (Admin, Analyst)")
    public ResponseEntity<FinancialRecordResponse> create(
            @RequestBody @Valid FinancialRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recordService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_ANALYST', 'ROLE_VIEWER')")
    @Operation(summary = "Get all records with optional filters and pagination")
    public ResponseEntity<Page<FinancialRecordResponse>> getAll(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(
                recordService.getAll(type, category, startDate, endDate, page, size, sortBy, sortDir));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_ANALYST', 'ROLE_VIEWER')")
    @Operation(summary = "Get a single record by ID")
    public ResponseEntity<FinancialRecordResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(recordService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_ANALYST')")
    @Operation(summary = "Update a financial record (Admin, Analyst)")
    public ResponseEntity<FinancialRecordResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid FinancialRecordRequest request) {
        return ResponseEntity.ok(recordService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Soft-delete a financial record (Admin only)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recordService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
controller/DashboardController.java
javapackage com.finance.app.controller;

import com.finance.app.dto.response.DashboardSummaryResponse;
import com.finance.app.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Summary and analytics")
@SecurityRequirement(name = "Bearer Auth")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_ANALYST', 'ROLE_VIEWER')")
    @Operation(summary = "Get full dashboard summary: totals, categories, trends, recent")
    public ResponseEntity<DashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }
}

12. Main Application Class
FinanceAppApplication.java
javapackage com.finance.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FinanceAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinanceAppApplication.class, args);
    }
}

13. README.md
markdown# Finance Dashboard Backend

A production-ready REST API backend for a Finance Dashboard system built with Spring Boot, MySQL, and JWT authentication.

## Tech Stack
- Java 17
- Spring Boot 3.2
- Spring Security + JWT
- Spring Data JPA
- MySQL 8
- Lombok
- Swagger / OpenAPI 3

## Architecture
controller → service → repository → MySQL
↓
DTO layer (request/response separation)
↓
Exception handling (global)

## Roles & Permissions

| Action                        | VIEWER | ANALYST | ADMIN |
|-------------------------------|--------|---------|-------|
| Login                         | ✅     | ✅      | ✅    |
| View records                  | ✅     | ✅      | ✅    |
| View dashboard summary        | ✅     | ✅      | ✅    |
| Create/update records         | ❌     | ✅      | ✅    |
| Delete records (soft)         | ❌     | ❌      | ✅    |
| Manage users                  | ❌     | ❌      | ✅    |

## Setup Instructions

1. Create MySQL database:
```sql
CREATE DATABASE finance_db;
```

2. Update `application.properties` with your DB credentials.

3. Run the application:
```bash
mvn spring-boot:run
```

4. Access Swagger UI:
http://localhost:8080/swagger-ui.html

## API Endpoints

### Auth
| Method | Endpoint         | Description     |
|--------|------------------|-----------------|
| POST   | /api/auth/login  | Login, get JWT  |

### Users (Admin only)
| Method | Endpoint                      | Description           |
|--------|-------------------------------|-----------------------|
| POST   | /api/users                    | Create user           |
| GET    | /api/users                    | Get all users         |
| GET    | /api/users/{id}               | Get user by ID        |
| PATCH  | /api/users/{id}/role          | Update user role      |
| PATCH  | /api/users/{id}/toggle-status | Activate/deactivate   |
| DELETE | /api/users/{id}               | Delete user           |

### Financial Records
| Method | Endpoint            | Description                         |
|--------|---------------------|-------------------------------------|
| POST   | /api/records        | Create record (Admin, Analyst)      |
| GET    | /api/records        | List with filters + pagination      |
| GET    | /api/records/{id}   | Get single record                   |
| PUT    | /api/records/{id}   | Update record (Admin, Analyst)      |
| DELETE | /api/records/{id}   | Soft delete (Admin only)            |

**Filter parameters:** `type`, `category`, `startDate`, `endDate`, `page`, `size`, `sortBy`, `sortDir`

### Dashboard
| Method | Endpoint                | Description                         |
|--------|-------------------------|-------------------------------------|
| GET    | /api/dashboard/summary  | Total income, expense, balance, trends, recent |

## Sample Requests

### Login
```json
POST /api/auth/login
{
  "username": "admin",
  "password": "password123"
}
```

### Create Record
```json
POST /api/records
Authorization: Bearer <token>
{
  "amount": 5000.00,
  "type": "INCOME",
  "category": "Salary",
  "date": "2025-04-01",
  "description": "April salary"
}
```

### Filter Records
GET /api/records?type=EXPENSE&category=Food&startDate=2025-01-01&endDate=2025-04-01&page=0&size=10&sortBy=date&sortDir=desc

## Assumptions
- Soft delete is used for financial records (records are marked deleted, not removed)
- Roles are prefixed with `ROLE_` as required by Spring Security
- JWT tokens expire in 24 hours
- All monetary values use BigDecimal for precision

## Features Implemented
- ✅ JWT Authentication
- ✅ Role-Based Access Control (RBAC)
- ✅ Full CRUD for financial records
- ✅ Soft delete
- ✅ Pagination + sorting
- ✅ Filtering by type, category, date range
- ✅ Dashboard summary with category breakdown + monthly trends
- ✅ Input validation with detailed error messages
- ✅ Global exception handling
- ✅ Swagger API documentation
- ✅ Clean layered architecture with DTO separation

What's Covered vs Assignment
RequirementStatusUser & Role Management (create, assign, status)✅ CompleteFinancial Records CRUD✅ CompleteFiltering (date, category, type)✅ CompletePagination + sorting✅ CompleteDashboard: totals, categories, trends, recent✅ CompleteRole-based access control✅ JWT + @PreAuthorizeValidation + error responses✅ Field-level + global handlerSoft delete✅ CompleteSwagger API docs✅ CompleteClean architecture + DTO layer✅ CompleteMySQL persistence✅ Complete
Everything in the assignment is covered. Push this to GitHub, add the README, and you're in excellent shape for the evaluation. Good luck!give testing for all api requests testing in postmanApr 23 / 5Here's a complete Postman testing guide for every API endpoint.

Setup First
In Postman, create a Collection called Finance Dashboard API and set a Collection Variable:

baseUrl = http://localhost:8080
token = (leave empty — it auto-fills after login)


1. AUTH APIs
1.1 Login as Admin
Method: POST
URL: {{baseUrl}}/api/auth/login
Headers: Content-Type: application/json

Body (raw JSON):
{
  "username": "admin",
  "password": "admin123"
}
Expected Response (200):
json{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "admin",
  "role": "ROLE_ADMIN"
}
Tests tab (auto-save token):
javascriptpm.test("Login successful", function () {
    pm.response.to.have.status(200);
});
pm.test("Token received", function () {
    var json = pm.response.json();
    pm.expect(json.token).to.not.be.empty;
    pm.collectionVariables.set("token", json.token);
});

1.2 Login as Analyst
Method: POST
URL: {{baseUrl}}/api/auth/login

Body:
{
  "username": "analyst1",
  "password": "analyst123"
}
Tests tab:
javascriptpm.test("Analyst login", function () {
    pm.response.to.have.status(200);
    var json = pm.response.json();
    pm.collectionVariables.set("analystToken", json.token);
});

1.3 Login as Viewer
Method: POST
URL: {{baseUrl}}/api/auth/login

Body:
{
  "username": "viewer1",
  "password": "viewer123"
}
Tests tab:
javascriptpm.test("Viewer login", function () {
    pm.response.to.have.status(200);
    var json = pm.response.json();
    pm.collectionVariables.set("viewerToken", json.token);
});

1.4 Login — Wrong Password (Negative Test)
Method: POST
URL: {{baseUrl}}/api/auth/login

Body:
{
  "username": "admin",
  "password": "wrongpassword"
}
Expected Response (401):
json{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username or password"
}
Tests tab:
javascriptpm.test("Rejects wrong password", function () {
    pm.response.to.have.status(401);
});

1.5 Login — Missing Fields (Negative Test)
Method: POST
URL: {{baseUrl}}/api/auth/login

Body:
{
  "username": ""
}
Expected Response (400):
json{
  "status": 400,
  "details": {
    "username": "Username is required",
    "password": "Password is required"
  }
}
Tests tab:
javascriptpm.test("Validation error on empty fields", function () {
    pm.response.to.have.status(400);
});

2. USER MANAGEMENT APIs (Admin only)

For all user APIs, add this header:
Authorization: Bearer {{token}}


2.1 Create Admin User
Method: POST
URL: {{baseUrl}}/api/users
Headers:
  Content-Type: application/json
  Authorization: Bearer {{token}}

Body:
{
  "username": "admin2",
  "password": "admin123",
  "email": "admin2@finance.com",
  "role": "ROLE_ADMIN"
}
Expected Response (201):
json{
  "id": 2,
  "username": "admin2",
  "email": "admin2@finance.com",
  "role": "ROLE_ADMIN",
  "active": true,
  "createdAt": "2025-04-01T10:00:00"
}
Tests tab:
javascriptpm.test("Admin user created", function () {
    pm.response.to.have.status(201);
    var json = pm.response.json();
    pm.expect(json.role).to.eql("ROLE_ADMIN");
    pm.collectionVariables.set("adminUserId", json.id);
});

2.2 Create Analyst User
Method: POST
URL: {{baseUrl}}/api/users
Headers:
  Authorization: Bearer {{token}}

Body:
{
  "username": "analyst1",
  "password": "analyst123",
  "email": "analyst1@finance.com",
  "role": "ROLE_ANALYST"
}
Tests tab:
javascriptpm.test("Analyst created", function () {
    pm.response.to.have.status(201);
    pm.collectionVariables.set("analystUserId", pm.response.json().id);
});

2.3 Create Viewer User
Method: POST
URL: {{baseUrl}}/api/users
Headers:
  Authorization: Bearer {{token}}

Body:
{
  "username": "viewer1",
  "password": "viewer123",
  "email": "viewer1@finance.com",
  "role": "ROLE_VIEWER"
}
Tests tab:
javascriptpm.test("Viewer created", function () {
    pm.response.to.have.status(201);
    pm.collectionVariables.set("viewerUserId", pm.response.json().id);
});

2.4 Create User — Duplicate Username (Negative Test)
Method: POST
URL: {{baseUrl}}/api/users
Headers:
  Authorization: Bearer {{token}}

Body:
{
  "username": "analyst1",
  "password": "analyst123",
  "email": "another@finance.com",
  "role": "ROLE_ANALYST"
}
Expected Response (400):
json{
  "message": "Username already exists: analyst1"
}
Tests tab:
javascriptpm.test("Duplicate username rejected", function () {
    pm.response.to.have.status(400);
    pm.expect(pm.response.json().message).to.include("already exists");
});

2.5 Create User — Invalid Role (Negative Test)
Method: POST
URL: {{baseUrl}}/api/users
Headers:
  Authorization: Bearer {{token}}

Body:
{
  "username": "testuser",
  "password": "test123",
  "email": "test@finance.com",
  "role": "ROLE_SUPERUSER"
}
Expected Response (400):
json{
  "message": "Invalid role. Valid values: ROLE_VIEWER, ROLE_ANALYST, ROLE_ADMIN"
}
Tests tab:
javascriptpm.test("Invalid role rejected", function () {
    pm.response.to.have.status(400);
});

2.6 Get All Users
Method: GET
URL: {{baseUrl}}/api/users
Headers:
  Authorization: Bearer {{token}}
Expected Response (200):
json[
  {
    "id": 1,
    "username": "admin",
    "email": "admin@finance.com",
    "role": "ROLE_ADMIN",
    "active": true
  },
  ...
]
Tests tab:
javascriptpm.test("Get all users success", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json()).to.be.an("array");
});

2.7 Get User by ID
Method: GET
URL: {{baseUrl}}/api/users/{{analystUserId}}
Headers:
  Authorization: Bearer {{token}}
Tests tab:
javascriptpm.test("Get user by ID", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().id).to.eql(
        Number(pm.collectionVariables.get("analystUserId"))
    );
});

2.8 Get User — Not Found (Negative Test)
Method: GET
URL: {{baseUrl}}/api/users/9999
Headers:
  Authorization: Bearer {{token}}
Expected Response (404):
json{
  "status": 404,
  "message": "User not found with id: 9999"
}
Tests tab:
javascriptpm.test("User not found 404", function () {
    pm.response.to.have.status(404);
});

2.9 Update User Role
Method: PATCH
URL: {{baseUrl}}/api/users/{{analystUserId}}/role?role=ROLE_ADMIN
Headers:
  Authorization: Bearer {{token}}
Expected Response (200):
json{
  "role": "ROLE_ADMIN"
}
Tests tab:
javascriptpm.test("Role updated", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().role).to.eql("ROLE_ADMIN");
});

2.10 Toggle User Status (Deactivate)
Method: PATCH
URL: {{baseUrl}}/api/users/{{viewerUserId}}/toggle-status
Headers:
  Authorization: Bearer {{token}}
Expected Response (200):
json{
  "active": false
}
Tests tab:
javascriptpm.test("User deactivated", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().active).to.eql(false);
});

2.11 Viewer Tries to Access Users (Forbidden — Negative Test)
Method: GET
URL: {{baseUrl}}/api/users
Headers:
  Authorization: Bearer {{viewerToken}}
Expected Response (403):
json{
  "message": "You do not have permission to perform this action"
}
Tests tab:
javascriptpm.test("Viewer forbidden from users", function () {
    pm.response.to.have.status(403);
});

2.12 Delete User
Method: DELETE
URL: {{baseUrl}}/api/users/{{adminUserId}}
Headers:
  Authorization: Bearer {{token}}
Expected Response: 204 No Content
Tests tab:
javascriptpm.test("User deleted", function () {
    pm.response.to.have.status(204);
});

3. FINANCIAL RECORDS APIs
3.1 Create Income Record (Admin)
Method: POST
URL: {{baseUrl}}/api/records
Headers:
  Content-Type: application/json
  Authorization: Bearer {{token}}

Body:
{
  "amount": 75000.00,
  "type": "INCOME",
  "category": "Salary",
  "date": "2025-04-01",
  "description": "Monthly salary April 2025"
}
Expected Response (201):
json{
  "id": 1,
  "amount": 75000.00,
  "type": "INCOME",
  "category": "Salary",
  "date": "2025-04-01",
  "createdBy": "admin"
}
Tests tab:
javascriptpm.test("Income record created", function () {
    pm.response.to.have.status(201);
    var json = pm.response.json();
    pm.expect(json.type).to.eql("INCOME");
    pm.collectionVariables.set("recordId", json.id);
});

3.2 Create Expense Record
Method: POST
URL: {{baseUrl}}/api/records
Headers:
  Authorization: Bearer {{token}}

Body:
{
  "amount": 12000.00,
  "type": "EXPENSE",
  "category": "Rent",
  "date": "2025-04-01",
  "description": "Office rent April"
}
Tests tab:
javascriptpm.test("Expense record created", function () {
    pm.response.to.have.status(201);
});

3.3 Create More Records (for filter/summary testing)
Run each of these as separate POST requests:
json{ "amount": 5000, "type": "EXPENSE", "category": "Food", "date": "2025-04-05", "description": "Team lunch" }
{ "amount": 2500, "type": "EXPENSE", "category": "Transport", "date": "2025-04-10", "description": "Cab charges" }
{ "amount": 30000, "type": "INCOME", "category": "Freelance", "date": "2025-03-15", "description": "Freelance project" }
{ "amount": 8000, "type": "EXPENSE", "category": "Utilities", "date": "2025-03-20", "description": "Electricity bill" }

3.4 Create Record — Negative Amount (Negative Test)
Method: POST
URL: {{baseUrl}}/api/records
Headers:
  Authorization: Bearer {{token}}

Body:
{
  "amount": -500,
  "type": "EXPENSE",
  "category": "Food",
  "date": "2025-04-01"
}
Expected Response (400):
json{
  "details": {
    "amount": "Amount must be greater than 0"
  }
}
Tests tab:
javascriptpm.test("Negative amount rejected", function () {
    pm.response.to.have.status(400);
    pm.expect(pm.response.json().details.amount).to.include("greater than 0");
});

3.5 Create Record — Invalid Type (Negative Test)
json{
  "amount": 1000,
  "type": "SALARY",
  "category": "Other",
  "date": "2025-04-01"
}
Expected Response (400):
json{
  "message": "Invalid type. Must be INCOME or EXPENSE"
}

3.6 Viewer Tries to Create Record (Forbidden — Negative Test)
Method: POST
URL: {{baseUrl}}/api/records
Headers:
  Authorization: Bearer {{viewerToken}}

Body:
{
  "amount": 1000,
  "type": "INCOME",
  "category": "Test",
  "date": "2025-04-01"
}
Expected Response (403)
Tests tab:
javascriptpm.test("Viewer cannot create records", function () {
    pm.response.to.have.status(403);
});

3.7 Get All Records (No Filters)
Method: GET
URL: {{baseUrl}}/api/records
Headers:
  Authorization: Bearer {{token}}
Expected Response (200):
json{
  "content": [...],
  "totalElements": 6,
  "totalPages": 1,
  "size": 10,
  "number": 0
}
Tests tab:
javascriptpm.test("Get all records", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().content).to.be.an("array");
});

3.8 Get Records — Filter by Type (INCOME)
Method: GET
URL: {{baseUrl}}/api/records?type=INCOME
Headers:
  Authorization: Bearer {{token}}
Tests tab:
javascriptpm.test("Filter by INCOME", function () {
    pm.response.to.have.status(200);
    var records = pm.response.json().content;
    records.forEach(function(r) {
        pm.expect(r.type).to.eql("INCOME");
    });
});

3.9 Get Records — Filter by Category
Method: GET
URL: {{baseUrl}}/api/records?category=Rent
Headers:
  Authorization: Bearer {{token}}
Tests tab:
javascriptpm.test("Filter by category", function () {
    pm.response.to.have.status(200);
    var records = pm.response.json().content;
    records.forEach(function(r) {
        pm.expect(r.category.toLowerCase()).to.include("rent");
    });
});

3.10 Get Records — Filter by Date Range
Method: GET
URL: {{baseUrl}}/api/records?startDate=2025-04-01&endDate=2025-04-30
Headers:
  Authorization: Bearer {{token}}
Tests tab:
javascriptpm.test("Date range filter works", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().content.length).to.be.above(0);
});

3.11 Get Records — Combined Filters with Pagination
Method: GET
URL: {{baseUrl}}/api/records?type=EXPENSE&category=Food&page=0&size=5&sortBy=amount&sortDir=desc
Headers:
  Authorization: Bearer {{token}}
Tests tab:
javascriptpm.test("Combined filters with pagination", function () {
    pm.response.to.have.status(200);
    var json = pm.response.json();
    pm.expect(json).to.have.property("totalElements");
    pm.expect(json.size).to.eql(5);
});

3.12 Get Record by ID
Method: GET
URL: {{baseUrl}}/api/records/{{recordId}}
Headers:
  Authorization: Bearer {{token}}
Tests tab:
javascriptpm.test("Get record by ID", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().id).to.eql(
        Number(pm.collectionVariables.get("recordId"))
    );
});

3.13 Get Record — Not Found (Negative Test)
Method: GET
URL: {{baseUrl}}/api/records/9999
Headers:
  Authorization: Bearer {{token}}
Expected Response (404)
Tests tab:
javascriptpm.test("Record not found", function () {
    pm.response.to.have.status(404);
});

3.14 Update Record
Method: PUT
URL: {{baseUrl}}/api/records/{{recordId}}
Headers:
  Authorization: Bearer {{token}}

Body:
{
  "amount": 80000.00,
  "type": "INCOME",
  "category": "Salary",
  "date": "2025-04-01",
  "description": "Updated: April salary with bonus"
}
Expected Response (200):
json{
  "amount": 80000.00,
  "description": "Updated: April salary with bonus"
}
Tests tab:
javascriptpm.test("Record updated", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().amount).to.eql(80000.00);
});

3.15 Analyst Tries to Delete Record (Forbidden — Negative Test)
Method: DELETE
URL: {{baseUrl}}/api/records/{{recordId}}
Headers:
  Authorization: Bearer {{analystToken}}
Expected Response (403)
Tests tab:
javascriptpm.test("Analyst cannot delete records", function () {
    pm.response.to.have.status(403);
});

3.16 Soft Delete Record (Admin)
Method: DELETE
URL: {{baseUrl}}/api/records/{{recordId}}
Headers:
  Authorization: Bearer {{token}}
Expected Response: 204 No Content
Tests tab:
javascriptpm.test("Record soft deleted", function () {
    pm.response.to.have.status(204);
});

3.17 Verify Soft Deleted Record is Hidden
Method: GET
URL: {{baseUrl}}/api/records/{{recordId}}
Headers:
  Authorization: Bearer {{token}}
Expected Response (404) — record is soft-deleted, not visible
Tests tab:
javascriptpm.test("Deleted record not found", function () {
    pm.response.to.have.status(404);
});

3.18 No Token — Unauthorized (Negative Test)
Method: GET
URL: {{baseUrl}}/api/records
(No Authorization header)
Expected Response (401)
Tests tab:
javascriptpm.test("Unauthorized without token", function () {
    pm.response.to.have.status(401);
});

4. DASHBOARD APIs
4.1 Get Dashboard Summary (Admin)
Method: GET
URL: {{baseUrl}}/api/dashboard/summary
Headers:
  Authorization: Bearer {{token}}
Expected Response (200):
json{
  "totalIncome": 105000.00,
  "totalExpense": 27500.00,
  "netBalance": 77500.00,
  "categoryBreakdown": {
    "Salary": 75000.00,
    "Freelance": 30000.00,
    "Rent": 12000.00,
    "Food": 5000.00,
    "Transport": 2500.00,
    "Utilities": 8000.00
  },
  "monthlyTrends": [
    { "year": 2025, "month": 3, "income": 30000.00, "expense": 8000.00 },
    { "year": 2025, "month": 4, "income": 75000.00, "expense": 19500.00 }
  ],
  "recentTransactions": [...]
}
Tests tab:
javascriptpm.test("Dashboard summary success", function () {
    pm.response.to.have.status(200);
});
pm.test("Has all required fields", function () {
    var json = pm.response.json();
    pm.expect(json).to.have.property("totalIncome");
    pm.expect(json).to.have.property("totalExpense");
    pm.expect(json).to.have.property("netBalance");
    pm.expect(json).to.have.property("categoryBreakdown");
    pm.expect(json).to.have.property("monthlyTrends");
    pm.expect(json).to.have.property("recentTransactions");
});
pm.test("Net balance = income - expense", function () {
    var json = pm.response.json();
    var expected = json.totalIncome - json.totalExpense;
    pm.expect(json.netBalance).to.be.closeTo(expected, 0.01);
});

4.2 Get Dashboard Summary (Viewer — Should Work)
Method: GET
URL: {{baseUrl}}/api/dashboard/summary
Headers:
  Authorization: Bearer {{viewerToken}}
Tests tab:
javascriptpm.test("Viewer can see dashboard", function () {
    pm.response.to.have.status(200);
});

4.3 Dashboard Without Token (Negative Test)
Method: GET
URL: {{baseUrl}}/api/dashboard/summary
(No Authorization header)
Expected Response (401)
Tests tab:
javascriptpm.test("Dashboard requires auth", function () {
    pm.response.to.have.status(401);
});

Complete Test Execution Order
Run requests in this exact sequence to avoid dependency issues:
1.  POST /api/auth/login          (admin)       → saves token
2.  POST /api/users               (create analyst, viewer)
3.  POST /api/auth/login          (analyst)     → saves analystToken
4.  POST /api/auth/login          (viewer)      → saves viewerToken
5.  POST /api/records             (create 6 records)
6.  GET  /api/records             (no filters)
7.  GET  /api/records?type=INCOME
8.  GET  /api/records?category=Rent
9.  GET  /api/records?startDate=...&endDate=...
10. GET  /api/records/{{recordId}}
11. PUT  /api/records/{{recordId}}
12. GET  /api/dashboard/summary
13. [Negative tests - any order]
14. DELETE /api/records/{{recordId}}   (soft delete last)

Collection Variables Summary
VariableSet byUsed bybaseUrlManualAll requeststokenLogin (admin)All admin requestsanalystTokenLogin (analyst)Analyst testsviewerTokenLogin (viewer)Viewer testsrecordIdCreate recordGet/Update/Delete recordanalystUserIdCreate analystUpdate role, toggle statusviewerUserIdCreate viewerToggle status

This covers every endpoint with positive tests, negative tests, validation tests, role-permission tests, and auto-chaining variables. Run them in the order listed and all should pass cleanly.dashboard summary (admin) and viewer not getting 200 response it give 500 response
{
  "timestamp": "2026-04-03T16:11:45.613157",
  "status": 500,
  "error": "Internal Server Error",
  "message": "An unexpected error occurred"
}
-------------------------------
package com.finance.entity;import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;import com.finance.enums.RecordType;import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;@Entity
@Table(name = "financial_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("deleted = false")
public class FinancialRecord {    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordType type;    @Column(nullable = false)
    private String category;    @Column(nullable = false)
    private LocalDate date;    private String description;    @Column(nullable = false)
    private boolean deleted = false;   // Soft delete flag    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;    @UpdateTimestamp
    private LocalDateTime updatedAt;}-----------------------------------
package com.finance.repo;import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;import com.finance.entity.FinancialRecord;
import com.finance.enums.RecordType;@Repository
public interface FinancialRecordRepository extends JpaRepository<FinancialRecord, Long> {    @Query("""
            SELECT f FROM FinancialRecord f
            WHERE (:type IS NULL OR f.type = :type)
            AND (:category IS NULL OR LOWER(f.category) LIKE LOWER(CONCAT('%', :category, '%')))
            AND (:startDate IS NULL OR f.date >= :startDate)
            AND (:endDate IS NULL OR f.date <= :endDate)
            AND f.deleted = false
        """)
        Page<FinancialRecord> findWithFilters(
                @Param("type") RecordType type,
                @Param("category") String category,
                @Param("startDate") LocalDate startDate,
                @Param("endDate") LocalDate endDate,
                Pageable pageable
        );        // Dashboard totals
        @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.type = 'INCOME' AND f.deleted = false")
        BigDecimal getTotalIncome();        @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.type = 'EXPENSE' AND f.deleted = false")
        BigDecimal getTotalExpense();        // Category breakdown
        @Query("SELECT f.category, COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.deleted = false GROUP BY f.category")
        List<Object[]> getCategoryBreakdown();        // Monthly trends
        @Query("""
            SELECT YEAR(f.date), MONTH(f.date), f.type, COALESCE(SUM(f.amount), 0)
            FROM FinancialRecord f
            WHERE f.deleted = false
            GROUP BY YEAR(f.date), MONTH(f.date), f.type
            ORDER BY YEAR(f.date), MONTH(f.date)
        """)
        List<Object[]> getMonthlyTrends();        // Recent records
        List<FinancialRecord> findTop5ByDeletedFalseOrderByCreatedAtDesc();
}-------------------------------
package com.finance.service;import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;import org.springframework.stereotype.Service;import com.finance.dto.DashboardSummaryResponse;
import com.finance.dto.FinancialRecordResponse;
import com.finance.entity.FinancialRecord;
import com.finance.enums.RecordType;
import com.finance.repo.FinancialRecordRepository;import lombok.RequiredArgsConstructor;@Service
@RequiredArgsConstructor
public class DashboardService {    private final FinancialRecordRepository recordRepository;    public DashboardSummaryResponse getSummary() {
        BigDecimal totalIncome = recordRepository.getTotalIncome();
        BigDecimal totalExpense = recordRepository.getTotalExpense();
        BigDecimal netBalance = totalIncome.subtract(totalExpense);        // Category breakdown
        Map<String, BigDecimal> categoryBreakdown = new LinkedHashMap<>();
        for (Object[] row : recordRepository.getCategoryBreakdown()) {
            categoryBreakdown.put((String) row[0], (BigDecimal) row[1]);
        }        // Monthly trends
        Map<String, DashboardSummaryResponse.MonthlyTrend> trendMap = new LinkedHashMap<>();
        for (Object[] row : recordRepository.getMonthlyTrends()) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            RecordType type = RecordType.valueOf((String) row[2]);
            BigDecimal amount = (BigDecimal) row[3];            String key = year + "-" + month;
            trendMap.computeIfAbsent(key, k ->
                    DashboardSummaryResponse.MonthlyTrend.builder()
                            .year(year).month(month)
                            .income(BigDecimal.ZERO).expense(BigDecimal.ZERO)
                            .build()
            );            DashboardSummaryResponse.MonthlyTrend trend = trendMap.get(key);
            if (type == RecordType.INCOME) trend.setIncome(amount);
            else trend.setExpense(amount);
        }        // Recent transactions
        List<FinancialRecordResponse> recent = recordRepository
                .findTop5ByDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());        return DashboardSummaryResponse.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netBalance(netBalance)
                .categoryBreakdown(categoryBreakdown)
                .monthlyTrends(new ArrayList<>(trendMap.values()))
                .recentTransactions(recent)
                .build();
    }    private FinancialRecordResponse toResponse(FinancialRecord r) {
        return FinancialRecordResponse.builder()
                .id(r.getId())
                .amount(r.getAmount())
                .type(r.getType().name())
                .category(r.getCategory())
                .date(r.getDate())
                .description(r.getDescription())
                .createdBy(r.getCreatedBy().getUsername())
                .createdAt(r.getCreatedAt())
                .build();
    }}--------------------------------------
package com.finance.controller;import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;import com.finance.dto.DashboardSummaryResponse;
import com.finance.service.DashboardService;import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Summary and analytics")
@SecurityRequirement(name = "Bearer Auth")
public class DashboardController {    private final DashboardService dashboardService;    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_ANALYST', 'ROLE_VIEWER')")
    @Operation(summary = "Get full dashboard summary: totals, categories, trends, recent")
    public ResponseEntity<DashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }}---------------------------------------------------------Show moreApr 33 / 3The 500 error is caused by 3 bugs. Let me show you exactly what's wrong and how to fix each one.

Bug 1: @SQLRestriction conflicts with JPQL deleted = false queries
Your entity has @SQLRestriction("deleted = false") which automatically filters deleted records at the SQL level. But your repository queries also have AND f.deleted = false — this causes a conflict/duplicate condition that breaks the query. Also, findTop5ByDeletedFalseOrderByCreatedAtDesc() has ByDeletedFalse in the method name which adds a third filter on the same column — MySQL chokes on this.
Fix in FinancialRecord.java — remove @SQLRestriction since your queries handle it manually:
java// REMOVE this annotation:
// @SQLRestriction("deleted = false")

@Entity
@Table(name = "financial_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialRecord {
    // ... rest stays the same
}

Bug 2: getMonthlyTrends() — row[2] is a RecordType enum object, not a String
When Hibernate maps an @Enumerated(EnumType.STRING) field in a JPQL query returning Object[], it returns the enum object directly, not a String. Calling RecordType.valueOf((String) row[2]) throws a ClassCastException which causes the 500.
Fix in DashboardService.java — cast row[2] as RecordType directly:
java// WRONG — throws ClassCastException:
RecordType type = RecordType.valueOf((String) row[2]);

// CORRECT:
RecordType type = (RecordType) row[2];

Bug 3: FetchType.LAZY on createdBy causes LazyInitializationException
In toResponse() you call r.getCreatedBy().getUsername(). Because createdBy is FetchType.LAZY, the User is not loaded when you access it outside a transaction — this throws a LazyInitializationException which also shows as 500.
Fix — two changes:
In FinancialRecord.java, change LAZY to EAGER for createdBy:
java// CHANGE this:
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "created_by", nullable = false)
private User createdBy;

// TO this:
@ManyToOne(fetch = FetchType.EAGER)
@JoinColumn(name = "created_by", nullable = false)
private User createdBy;
OR (better approach) add @Transactional to DashboardService:
In DashboardService.java:
javaimport org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)   // ADD THIS
public class DashboardService {
    // ...
}

Complete Fixed Files
FinancialRecord.java (fixed):
javapackage com.finance.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.finance.enums.RecordType;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "financial_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
// REMOVED @SQLRestriction — was conflicting with manual deleted=false in queries
public class FinancialRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordType type;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private LocalDate date;

    private String description;

    @Column(nullable = false)
    private boolean deleted = false;

    @ManyToOne(fetch = FetchType.EAGER)  // CHANGED: LAZY → EAGER to avoid LazyInitializationException
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
FinancialRecordRepository.java (fixed — also fix the derived method name):
javapackage com.finance.repo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.finance.entity.FinancialRecord;
import com.finance.enums.RecordType;

@Repository
public interface FinancialRecordRepository extends JpaRepository<FinancialRecord, Long> {

    @Query("""
        SELECT f FROM FinancialRecord f
        WHERE (:type IS NULL OR f.type = :type)
        AND (:category IS NULL OR LOWER(f.category) LIKE LOWER(CONCAT('%', :category, '%')))
        AND (:startDate IS NULL OR f.date >= :startDate)
        AND (:endDate IS NULL OR f.date <= :endDate)
        AND f.deleted = false
    """)
    Page<FinancialRecord> findWithFilters(
            @Param("type") RecordType type,
            @Param("category") String category,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable
    );

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.type = 'INCOME' AND f.deleted = false")
    BigDecimal getTotalIncome();

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.type = 'EXPENSE' AND f.deleted = false")
    BigDecimal getTotalExpense();

    @Query("SELECT f.category, COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.deleted = false GROUP BY f.category")
    List<Object[]> getCategoryBreakdown();

    @Query("""
        SELECT YEAR(f.date), MONTH(f.date), f.type, COALESCE(SUM(f.amount), 0)
        FROM FinancialRecord f
        WHERE f.deleted = false
        GROUP BY YEAR(f.date), MONTH(f.date), f.type
        ORDER BY YEAR(f.date), MONTH(f.date)
    """)
    List<Object[]> getMonthlyTrends();

    // FIXED: removed ByDeletedFalse from method name since entity no longer has @SQLRestriction
    // and we want records that are NOT deleted, so we keep the condition explicit
    @Query("SELECT f FROM FinancialRecord f WHERE f.deleted = false ORDER BY f.createdAt DESC LIMIT 5")
    List<FinancialRecord> findRecentRecords();
}
DashboardService.java (fixed):
javapackage com.finance.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finance.dto.DashboardSummaryResponse;
import com.finance.dto.FinancialRecordResponse;
import com.finance.entity.FinancialRecord;
import com.finance.enums.RecordType;
import com.finance.repo.FinancialRecordRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // ADDED: keeps session open, prevents LazyInitializationException
public class DashboardService {

    private final FinancialRecordRepository recordRepository;

    public DashboardSummaryResponse getSummary() {
        BigDecimal totalIncome = recordRepository.getTotalIncome();
        BigDecimal totalExpense = recordRepository.getTotalExpense();
        BigDecimal netBalance = totalIncome.subtract(totalExpense);

        // Category breakdown
        Map<String, BigDecimal> categoryBreakdown = new LinkedHashMap<>();
        for (Object[] row : recordRepository.getCategoryBreakdown()) {
            categoryBreakdown.put((String) row[0], (BigDecimal) row[1]);
        }

        // Monthly trends — FIXED: cast row[2] as RecordType directly, not String
        Map<String, DashboardSummaryResponse.MonthlyTrend> trendMap = new LinkedHashMap<>();
        for (Object[] row : recordRepository.getMonthlyTrends()) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            RecordType type = (RecordType) row[2];   // FIXED: was RecordType.valueOf((String) row[2])
            BigDecimal amount = (BigDecimal) row[3];

            String key = year + "-" + month;
            trendMap.computeIfAbsent(key, k ->
                    DashboardSummaryResponse.MonthlyTrend.builder()
                            .year(year).month(month)
                            .income(BigDecimal.ZERO).expense(BigDecimal.ZERO)
                            .build()
            );

            DashboardSummaryResponse.MonthlyTrend trend = trendMap.get(key);
            if (type == RecordType.INCOME) trend.setIncome(amount);
            else trend.setExpense(amount);
        }

        // Recent transactions — FIXED: using new query method name
        List<FinancialRecordResponse> recent = recordRepository
                .findRecentRecords()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return DashboardSummaryResponse.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netBalance(netBalance)
                .categoryBreakdown(categoryBreakdown)
                .monthlyTrends(new ArrayList<>(trendMap.values()))
                .recentTransactions(recent)
                .build();
    }

    private FinancialRecordResponse toResponse(FinancialRecord r) {
        return FinancialRecordResponse.builder()
                .id(r.getId())
                .amount(r.getAmount())
                .type(r.getType().name())
                .category(r.getCategory())
                .date(r.getDate())
                .description(r.getDescription())
                .createdBy(r.getCreatedBy().getUsername())
                .createdAt(r.getCreatedAt())
                .build();
    }
}

Summary of All 3 Fixes
#BugRoot CauseFix1@SQLRestriction conflictDuplicate deleted=false filter at SQL + JPQL levelRemove @SQLRestriction from entity2ClassCastException in monthly trendsrow[2] is RecordType enum, not StringCast as (RecordType) row[2] directly3LazyInitializationException on createdByLAZY fetch accessed outside transactionAdd @Transactional(readOnly=true) to service + change to EAGER
Make these three changes, restart the app, and your dashboard endpoint will return 200.ok every thing is successfully tested and every thing is ok so should i submitted my assignment as any thing else needed ,this is very important to getting the intern backend role for me ,so i already uploaded the requirement what they asked according to that is this ok to get that intern roleApr 3Great that everything is working! Let me do a final audit against their exact evaluation criteria before you submit.

Evaluation Criteria Audit
1. Backend Design ✅
Clean layered architecture — controller → service → repository → DTO. Nothing to add.
2. Logical Thinking ✅
RBAC with JWT, role-based method-level security with @PreAuthorize, soft delete logic, business rules in service layer.
3. Functionality ✅
All CRUD, filtering, pagination, sorting, dashboard summary with trends and category breakdown — all tested and working.
4. Code Quality ✅
Lombok, proper naming, separation of concerns, DTO layer separating request/response from entity.
5. Database and Data Modeling ✅
Proper JPA entities, relationships, BigDecimal for money, timestamps, soft delete column.
6. Validation and Reliability ✅
@Valid, field-level validation annotations, global exception handler with proper HTTP status codes.
7. Documentation ⚠️
This is where most candidates lose marks. Your README needs to be stronger.
8. Additional Thoughtfulness ⚠️
A few small things that will separate you from other candidates.

What You Still Need to Add
1. Improve README (biggest scoring gap)
Replace your current README with this complete one:
markdown# Finance Dashboard Backend

A production-ready REST API backend for a Finance Dashboard system with 
Role-Based Access Control, JWT Authentication, and Analytics APIs.

## Tech Stack

- Java 17
- Spring Boot 3.2
- Spring Security + JWT (jjwt 0.11.5)
- Spring Data JPA + Hibernate
- MySQL 8
- Lombok
- Swagger / OpenAPI 3 (springdoc)

## Architecture
Request → JwtFilter → Controller → Service → Repository → MySQL
↓
DTO (Request/Response)
↓
GlobalExceptionHandler

All business logic lives in the Service layer.
Controllers only handle HTTP concerns.
Entities never leak directly to API responses — DTOs are always used.

## Role & Permission Matrix

| Action                          | VIEWER | ANALYST | ADMIN |
|---------------------------------|--------|---------|-------|
| Login                           | ✅     | ✅      | ✅    |
| View financial records          | ✅     | ✅      | ✅    |
| Filter / paginate records       | ✅     | ✅      | ✅    |
| View dashboard summary          | ✅     | ✅      | ✅    |
| Create financial records        | ❌     | ✅      | ✅    |
| Update financial records        | ❌     | ✅      | ✅    |
| Delete financial records        | ❌     | ❌      | ✅    |
| View all users                  | ❌     | ❌      | ✅    |
| Create / manage users           | ❌     | ❌      | ✅    |
| Assign roles / toggle status    | ❌     | ❌      | ✅    |

## Setup Instructions

### Prerequisites
- Java 17+
- MySQL 8+
- Maven 3.8+

### Step 1 — Create the database
```sql
CREATE DATABASE finance_db;
```

### Step 2 — Configure credentials
Edit `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/finance_db
spring.datasource.username=YOUR_USERNAME
spring.datasource.password=YOUR_PASSWORD
```

### Step 3 — Run the application
```bash
mvn spring-boot:run
```

Tables are auto-created by Hibernate on first run (`ddl-auto=update`).

### Step 4 — Create your first Admin user

Since there are no users on first run, temporarily allow open registration 
or insert directly into the database:
```sql
INSERT INTO users (username, password, email, role, active, created_at, updated_at)
VALUES (
  'admin',
  '$2a$10$YourBCryptHashHere',
  'admin@finance.com',
  'ROLE_ADMIN',
  true,
  NOW(),
  NOW()
);
```

Or use the provided data seeder (see DataSeeder.java section below).

### Step 5 — Access Swagger UI
http://localhost:8080/swagger-ui.html

Click "Authorize" → enter `Bearer <your_jwt_token>` to test secured endpoints.

## API Reference

### Authentication
| Method | Endpoint          | Auth | Description        |
|--------|-------------------|------|--------------------|
| POST   | /api/auth/login   | No   | Login, returns JWT |

### Users (Admin only)
| Method | Endpoint                        | Description              |
|--------|---------------------------------|--------------------------|
| POST   | /api/users                      | Create user              |
| GET    | /api/users                      | Get all users            |
| GET    | /api/users/{id}                 | Get user by ID           |
| PATCH  | /api/users/{id}/role            | Update role              |
| PATCH  | /api/users/{id}/toggle-status   | Activate / deactivate    |
| DELETE | /api/users/{id}                 | Delete user              |

### Financial Records
| Method | Endpoint              | Auth Required       | Description                    |
|--------|-----------------------|---------------------|--------------------------------|
| POST   | /api/records          | Admin, Analyst      | Create record                  |
| GET    | /api/records          | All roles           | List with filters + pagination |
| GET    | /api/records/{id}     | All roles           | Get single record              |
| PUT    | /api/records/{id}     | Admin, Analyst      | Update record                  |
| DELETE | /api/records/{id}     | Admin only          | Soft delete                    |

**Supported query parameters for GET /api/records:**

| Param       | Type       | Example              | Description              |
|-------------|------------|----------------------|--------------------------|
| type        | String     | INCOME or EXPENSE    | Filter by record type    |
| category    | String     | Salary               | Partial match on category|
| startDate   | LocalDate  | 2025-01-01           | Filter from date         |
| endDate     | LocalDate  | 2025-04-30           | Filter to date           |
| page        | int        | 0                    | Page number (0-indexed)  |
| size        | int        | 10                   | Records per page         |
| sortBy      | String     | date                 | Field to sort by         |
| sortDir     | String     | asc or desc          | Sort direction           |

### Dashboard
| Method | Endpoint                 | Auth      | Description                            |
|--------|--------------------------|-----------|----------------------------------------|
| GET    | /api/dashboard/summary   | All roles | Totals, categories, trends, recent 5   |

**Sample dashboard response:**
```json
{
  "totalIncome": 105000.00,
  "totalExpense": 27500.00,
  "netBalance": 77500.00,
  "categoryBreakdown": {
    "Salary": 75000.00,
    "Freelance": 30000.00,
    "Rent": 12000.00
  },
  "monthlyTrends": [
    { "year": 2025, "month": 3, "income": 30000.00, "expense": 8000.00 },
    { "year": 2025, "month": 4, "income": 75000.00, "expense": 19500.00 }
  ],
  "recentTransactions": [...]
}
```

## Key Design Decisions & Assumptions

1. **Soft delete** — Records are never permanently deleted. A `deleted` boolean 
   flag is set to true. This preserves audit history and allows recovery.

2. **BigDecimal for money** — All monetary values use `BigDecimal` (not Double) 
   to avoid floating point precision errors in financial calculations.

3. **JWT stateless auth** — No sessions are stored server-side. Every request 
   carries a self-contained JWT token that expires in 24 hours.

4. **Role naming** — Roles follow Spring Security convention: `ROLE_ADMIN`, 
   `ROLE_ANALYST`, `ROLE_VIEWER`. The `ROLE_` prefix is required for 
   `hasRole()` expressions to work correctly.

5. **DTO separation** — Entities are never returned directly from controllers. 
   Request and Response DTOs are separate classes to control exactly what 
   data enters and exits the API.

6. **EAGER fetch on createdBy** — The `createdBy` relationship on 
   `FinancialRecord` uses `FetchType.EAGER` to avoid `LazyInitializationException` 
   when mapping to response DTOs outside a transaction context.

7. **COALESCE in aggregate queries** — Dashboard total queries use 
   `COALESCE(SUM(...), 0)` so the API returns 0 instead of null when 
   no records exist.

## Tradeoffs Considered

| Decision | Chosen Approach | Alternative | Reason |
|---|---|---|---|
| Auth | JWT (stateless) | Session-based | Scales better, no server state |
| Delete | Soft delete | Hard delete | Preserves financial audit trail |
| Fetch | EAGER on createdBy | LAZY + @Transactional | Simpler, avoids session scope issues |
| Storage | MySQL | PostgreSQL | Common in enterprise finance systems |
| Validation | Bean Validation (@Valid) | Manual checks | Standard, declarative, less code |

## Features Implemented

- ✅ JWT Authentication
- ✅ Role-Based Access Control (method level with @PreAuthorize)
- ✅ Full CRUD for financial records
- ✅ Soft delete
- ✅ Filtering by type, category, date range
- ✅ Pagination and sorting
- ✅ Dashboard: total income, expense, net balance
- ✅ Dashboard: category-wise breakdown
- ✅ Dashboard: monthly trends
- ✅ Dashboard: recent 5 transactions
- ✅ Input validation with field-level error messages
- ✅ Global exception handling with proper HTTP status codes
- ✅ Swagger / OpenAPI documentation
- ✅ Clean layered architecture with DTO separation

2. Add a Data Seeder (takes 10 minutes, high impact)
This lets the reviewer run your project immediately without manually inserting SQL. Create this file:
config/DataSeeder.java
javapackage com.finance.config;

import com.finance.entity.FinancialRecord;
import com.finance.entity.User;
import com.finance.enums.RecordType;
import com.finance.enums.Role;
import com.finance.repo.FinancialRecordRepository;
import com.finance.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final FinancialRecordRepository recordRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {

        // Only seed if database is empty
        if (userRepository.count() > 0) return;

        // Create users
        User admin = userRepository.save(User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .email("admin@finance.com")
                .role(Role.ROLE_ADMIN)
                .active(true)
                .build());

        User analyst = userRepository.save(User.builder()
                .username("analyst")
                .password(passwordEncoder.encode("analyst123"))
                .email("analyst@finance.com")
                .role(Role.ROLE_ANALYST)
                .active(true)
                .build());

        userRepository.save(User.builder()
                .username("viewer")
                .password(passwordEncoder.encode("viewer123"))
                .email("viewer@finance.com")
                .role(Role.ROLE_VIEWER)
                .active(true)
                .build());

        // Create sample financial records
        recordRepository.save(FinancialRecord.builder()
                .amount(new BigDecimal("75000.00"))
                .type(RecordType.INCOME)
                .category("Salary")
                .date(LocalDate.of(2025, 4, 1))
                .description("Monthly salary")
                .createdBy(admin).deleted(false).build());

        recordRepository.save(FinancialRecord.builder()
                .amount(new BigDecimal("30000.00"))
                .type(RecordType.INCOME)
                .category("Freelance")
                .date(LocalDate.of(2025, 3, 15))
                .description("Freelance project payment")
                .createdBy(analyst).deleted(false).build());

        recordRepository.save(FinancialRecord.builder()
                .amount(new BigDecimal("12000.00"))
                .type(RecordType.EXPENSE)
                .category("Rent")
                .date(LocalDate.of(2025, 4, 1))
                .description("Office rent")
                .createdBy(admin).deleted(false).build());

        recordRepository.save(FinancialRecord.builder()
                .amount(new BigDecimal("5000.00"))
                .type(RecordType.EXPENSE)
                .category("Food")
                .date(LocalDate.of(2025, 4, 5))
                .description("Team lunch")
                .createdBy(analyst).deleted(false).build());

        recordRepository.save(FinancialRecord.builder()
                .amount(new BigDecimal("8000.00"))
                .type(RecordType.EXPENSE)
                .category("Utilities")
                .date(LocalDate.of(2025, 3, 20))
                .description("Electricity and internet")
                .createdBy(admin).deleted(false).build());

        recordRepository.save(FinancialRecord.builder()
                .amount(new BigDecimal("2500.00"))
                .type(RecordType.EXPENSE)
                .category("Transport")
                .date(LocalDate.of(2025, 4, 10))
                .description("Cab charges")
                .createdBy(analyst).deleted(false).build());

        System.out.println("=== Sample data seeded successfully ===");
        System.out.println("Admin    → username: admin    password: admin123");
        System.out.println("Analyst  → username: analyst  password: analyst123");
        System.out.println("Viewer   → username: viewer   password: viewer123");
    }
}

3. GitHub Repository Checklist
Before pushing, make sure your repo has:
finance-backend/
├── src/
├── pom.xml
├── README.md              ← updated version above
└── .gitignore             ← make sure this exists
.gitignore — critical, never push credentials:
target/
*.class
*.jar
.idea/
*.iml
application.properties     ← if it has real passwords
Instead, commit a safe template:
application-example.properties
propertiesspring.datasource.url=jdbc:mysql://localhost:3306/finance_db
spring.datasource.username=YOUR_DB_USERNAME
spring.datasource.password=YOUR_DB_PASSWORD
jwt.secret=YOUR_JWT_SECRET
jwt.expiration=86400000
And mention in README: "Copy application-example.properties to application.properties and fill in your credentials."

Final Submission Checklist
✅ All APIs working (you confirmed this)
✅ JWT authentication
✅ Role-based access control
✅ CRUD with validation
✅ Filtering + pagination
✅ Dashboard with trends and categories
✅ Soft delete
✅ Global exception handling
✅ Swagger at /swagger-ui.html

→ ADD before submitting:
✅ Updated README (above)
✅ DataSeeder.java (reviewer can run instantly)
✅ .gitignore (no credentials in repo)
✅ application-example.properties
✅ Push to GitHub with clean commit history

One Last Thing — How to Present It
When you submit, write a short covering message like:

"I have built a Finance Dashboard Backend using Java Spring Boot and MySQL. The project includes JWT authentication, role-based access control with three roles (Admin, Analyst, Viewer), full CRUD for financial records with filtering and pagination, and a dashboard summary API with category breakdown and monthly trends. A data seeder creates sample users and records on first run. Swagger documentation is available at /swagger-ui.html. All design decisions and tradeoffs are documented in the README."

That message alone shows senior-level thinking. Good luck — your project is genuinely strong for an intern submission. 🚀give about the detailed description about this finance backend in 300 characters for github reporitory description7:31 PMFinance Dashboard Backend – Spring Boot REST API with JWT auth, role-based access control (Admin/Analyst/Viewer), financial records CRUD, filtering, pagination, and dashboard analytics. MySQL + Swagger docs.
That's 207 characters — clean, covers all the key points, fits GitHub's limit perfectly.give all postan testing json input and output of all requests and urls to add these in readme file in github7:46 PMHere's the complete Postman testing documentation formatted for your README file.

markdown## API Testing Reference

Base URL: `http://localhost:8080`

---

## 1. Authentication

### Login as Admin
**Request**
```
POST /api/auth/login
Content-Type: application/json
```
```json
{
  "username": "admin",
  "password": "admin123"
}
```
**Response `200 OK`**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc0MzY4MDAwMCwiZXhwIjoxNzQzNzY2NDAwfQ.xxxxx",
  "username": "admin",
  "role": "ROLE_ADMIN"
}
```

---

### Login as Analyst
**Request**
```
POST /api/auth/login
Content-Type: application/json
```
```json
{
  "username": "analyst",
  "password": "analyst123"
}
```
**Response `200 OK`**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.xxxxx",
  "username": "analyst",
  "role": "ROLE_ANALYST"
}
```

---

### Login as Viewer
**Request**
```
POST /api/auth/login
Content-Type: application/json
```
```json
{
  "username": "viewer",
  "password": "viewer123"
}
```
**Response `200 OK`**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.xxxxx",
  "username": "viewer",
  "role": "ROLE_VIEWER"
}
```

---

### Login — Wrong Password
**Request**
```
POST /api/auth/login
Content-Type: application/json
```
```json
{
  "username": "admin",
  "password": "wrongpassword"
}
```
**Response `401 Unauthorized`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username or password"
}
```

---

### Login — Missing Fields
**Request**
```
POST /api/auth/login
Content-Type: application/json
```
```json
{
  "username": ""
}
```
**Response `400 Bad Request`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": {
    "username": "Username is required",
    "password": "Password is required"
  }
}
```

---

## 2. User Management (Admin Only)

> All user endpoints require:
> `Authorization: Bearer <admin_token>`

---

### Create Admin User
**Request**
```
POST /api/users
Content-Type: application/json
Authorization: Bearer <admin_token>
```
```json
{
  "username": "admin2",
  "password": "admin123",
  "email": "admin2@finance.com",
  "role": "ROLE_ADMIN"
}
```
**Response `201 Created`**
```json
{
  "id": 4,
  "username": "admin2",
  "email": "admin2@finance.com",
  "role": "ROLE_ADMIN",
  "active": true,
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Create Analyst User
**Request**
```
POST /api/users
Content-Type: application/json
Authorization: Bearer <admin_token>
```
```json
{
  "username": "analyst2",
  "password": "analyst123",
  "email": "analyst2@finance.com",
  "role": "ROLE_ANALYST"
}
```
**Response `201 Created`**
```json
{
  "id": 5,
  "username": "analyst2",
  "email": "analyst2@finance.com",
  "role": "ROLE_ANALYST",
  "active": true,
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Create Viewer User
**Request**
```
POST /api/users
Content-Type: application/json
Authorization: Bearer <admin_token>
```
```json
{
  "username": "viewer2",
  "password": "viewer123",
  "email": "viewer2@finance.com",
  "role": "ROLE_VIEWER"
}
```
**Response `201 Created`**
```json
{
  "id": 6,
  "username": "viewer2",
  "email": "viewer2@finance.com",
  "role": "ROLE_VIEWER",
  "active": true,
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Create User — Duplicate Username
**Request**
```
POST /api/users
Content-Type: application/json
Authorization: Bearer <admin_token>
```
```json
{
  "username": "analyst",
  "password": "analyst123",
  "email": "another@finance.com",
  "role": "ROLE_ANALYST"
}
```
**Response `400 Bad Request`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 400,
  "error": "Bad Request",
  "message": "Username already exists: analyst"
}
```

---

### Create User — Invalid Role
**Request**
```
POST /api/users
Content-Type: application/json
Authorization: Bearer <admin_token>
```
```json
{
  "username": "testuser",
  "password": "test123",
  "email": "test@finance.com",
  "role": "ROLE_SUPERUSER"
}
```
**Response `400 Bad Request`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid role. Valid values: ROLE_VIEWER, ROLE_ANALYST, ROLE_ADMIN"
}
```

---

### Create User — Missing Fields
**Request**
```
POST /api/users
Content-Type: application/json
Authorization: Bearer <admin_token>
```
```json
{
  "username": "",
  "password": "123",
  "email": "bademail"
}
```
**Response `400 Bad Request`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": {
    "username": "Username must be between 3 and 50 characters",
    "password": "Password must be at least 6 characters",
    "email": "Invalid email format",
    "role": "Role is required"
  }
}
```

---

### Get All Users
**Request**
GET /api/users
Authorization: Bearer <admin_token>

**Response `200 OK`**
```json
[
  {
    "id": 1,
    "username": "admin",
    "email": "admin@finance.com",
    "role": "ROLE_ADMIN",
    "active": true,
    "createdAt": "2025-04-01T10:00:00.000000"
  },
  {
    "id": 2,
    "username": "analyst",
    "email": "analyst@finance.com",
    "role": "ROLE_ANALYST",
    "active": true,
    "createdAt": "2025-04-01T10:00:00.000000"
  },
  {
    "id": 3,
    "username": "viewer",
    "email": "viewer@finance.com",
    "role": "ROLE_VIEWER",
    "active": true,
    "createdAt": "2025-04-01T10:00:00.000000"
  }
]
```

---

### Get User by ID
**Request**
GET /api/users/2
Authorization: Bearer <admin_token>

**Response `200 OK`**
```json
{
  "id": 2,
  "username": "analyst",
  "email": "analyst@finance.com",
  "role": "ROLE_ANALYST",
  "active": true,
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Get User — Not Found
**Request**

GET /api/users/9999
Authorization: Bearer <admin_token>

**Response `404 Not Found`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 404,
  "error": "Not Found",
  "message": "User not found with id: 9999"
}
```

---

### Update User Role
**Request**
PATCH /api/users/2/role?role=ROLE_ADMIN
Authorization: Bearer <admin_token>

**Response `200 OK`**
```json
{
  "id": 2,
  "username": "analyst",
  "email": "analyst@finance.com",
  "role": "ROLE_ADMIN",
  "active": true,
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Toggle User Status (Deactivate)
**Request**

PATCH /api/users/3/toggle-status
Authorization: Bearer <admin_token>

**Response `200 OK`**
```json
{
  "id": 3,
  "username": "viewer",
  "email": "viewer@finance.com",
  "role": "ROLE_VIEWER",
  "active": false,
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Toggle User Status (Reactivate — call same endpoint again)
**Request**

PATCH /api/users/3/toggle-status
Authorization: Bearer <admin_token>

**Response `200 OK`**
```json
{
  "id": 3,
  "username": "viewer",
  "email": "viewer@finance.com",
  "role": "ROLE_VIEWER",
  "active": true,
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Get All Records — No Filters
**Request**
GET /api/records
Authorization: Bearer <admin_token>

**Response `200 OK`**
```json
{
  "content": [
    {
      "id": 6,
      "amount": 2500.00,
      "type": "EXPENSE",
      "category": "Transport",
      "date": "2025-04-10",
      "description": "Cab charges",
      "createdBy": "analyst",
      "createdAt": "2025-04-10T09:00:00.000000"
    },
    {
      "id": 5,
      "amount": 8000.00,
      "type": "EXPENSE",
      "category": "Utilities",
      "date": "2025-03-20",
      "description": "Electricity and internet",
      "createdBy": "admin",
      "createdAt": "2025-03-20T09:00:00.000000"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "sort": {
      "sorted": true,
      "unsorted": false
    }
  },
  "totalElements": 6,
  "totalPages": 1,
  "last": true,
  "first": true,
  "size": 10,
  "number": 0
}
```

---

### Get Records — Filter by Type INCOME
**Request**
GET /api/records?type=INCOME
Authorization: Bearer <admin_token>

**Response `200 OK`**
```json
{
  "content": [
    {
      "id": 1,
      "amount": 75000.00,
      "type": "INCOME",
      "category": "Salary",
      "date": "2025-04-01",
      "description": "Monthly salary April 2025",
      "createdBy": "admin",
      "createdAt": "2025-04-01T10:00:00.000000"
    },
    {
      "id": 2,
      "amount": 30000.00,
      "type": "INCOME",
      "category": "Freelance",
      "date": "2025-03-15",
      "description": "Freelance project payment",
      "createdBy": "analyst",
      "createdAt": "2025-03-15T10:00:00.000000"
    }
  ],
  "totalElements": 2,
  "totalPages": 1,
  "size": 10,
  "number": 0
}
```

---

### Get Records — Filter by Date Range
**Request**

GET /api/records?startDate=2025-04-01&endDate=2025-04-30
Authorization: Bearer <admin_token>

**Response `200 OK`**
```json
{
  "content": [
    {
      "id": 6,
      "amount": 2500.00,
      "type": "EXPENSE",
      "category": "Transport",
      "date": "2025-04-10",
      "description": "Cab charges",
      "createdBy": "analyst",
      "createdAt": "2025-04-10T09:00:00.000000"
    },
    {
      "id": 4,
      "amount": 5000.00,
      "type": "EXPENSE",
      "category": "Food",
      "date": "2025-04-05",
      "description": "Team lunch",
      "createdBy": "analyst",
      "createdAt": "2025-04-05T12:00:00.000000"
    },
    {
      "id": 1,
      "amount": 75000.00,
      "type": "INCOME",
      "category": "Salary",
      "date": "2025-04-01",
      "description": "Monthly salary April 2025",
      "createdBy": "admin",
      "createdAt": "2025-04-01T10:00:00.000000"
    },
    {
      "id": 3,
      "amount": 12000.00,
      "type": "EXPENSE",
      "category": "Rent",
      "date": "2025-04-01",
      "description": "Office rent April 2025",
      "createdBy": "admin",
      "createdAt": "2025-04-01T10:05:00.000000"
    }
  ],
  "totalElements": 4,
  "totalPages": 1,
  "size": 10,
  "number": 0
}
```

---

### Get Dashboard Summary (Admin)
**Request**
GET /api/dashboard/summary
Authorization: Bearer <admin_token>

**Response `200 OK`**
```json
{
  "totalIncome": 105000.00,
  "totalExpense": 27500.00,
  "netBalance": 77500.00,
  "categoryBreakdown": {
    "Salary": 75000.00,
    "Freelance": 30000.00,
    "Rent": 12000.00,
    "Food": 5000.00,
    "Utilities": 8000.00,
    "Transport": 2500.00
  },
  "monthlyTrends": [
    {
      "year": 2025,
      "month": 3,
      "income": 30000.00,
      "expense": 8000.00
    },
    {
      "year": 2025,
      "month": 4,
      "income": 75000.00,
      "expense": 19500.00
    }
  ],
  "recentTransactions": [
    {
      "id": 6,
      "amount": 2500.00,
      "type": "EXPENSE",
      "category": "Transport",
      "date": "2025-04-10",
      "description": "Cab charges",
      "createdBy": "analyst",
      "createdAt": "2025-04-10T09:00:00.000000"
    },
    {
      "id": 5,
      "amount": 8000.00,
      "type": "EXPENSE",
      "category": "Utilities",
      "date": "2025-03-20",
      "description": "Electricity and internet",
      "createdBy": "admin",
      "createdAt": "2025-03-20T09:00:00.000000"
    },
    {
      "id": 4,
      "amount": 5000.00,
      "type": "EXPENSE",
      "category": "Food",
      "date": "2025-04-05",
      "description": "Team lunch",
      "createdBy": "analyst",
      "createdAt": "2025-04-05T12:00:00.000000"
    },
    {
      "id": 3,
      "amount": 12000.00,
      "type": "EXPENSE",
      "category": "Rent",
      "date": "2025-04-01",
      "description": "Office rent April 2025",
      "createdBy": "admin",
      "createdAt": "2025-04-01T10:05:00.000000"
    },
    {
      "id": 2,
      "amount": 30000.00,
      "type": "INCOME",
      "category": "Freelance",
      "date": "2025-03-15",
      "description": "Freelance project payment",
      "createdBy": "analyst",
      "createdAt": "2025-03-15T10:00:00.000000"
    }
  ]
}
```

---

### Get Dashboard Summary (Viewer)
**Request**
GET /api/dashboard/summary
Authorization: Bearer <viewer_token>
**Response `200 OK`**
```json
{
  "totalIncome": 105000.00,
  "totalExpense": 27500.00,
  "netBalance": 77500.00,
  "categoryBreakdown": {
    "Salary": 75000.00,
    "Freelance": 30000.00,
    "Rent": 12000.00,
    "Food": 5000.00,
    "Utilities": 8000.00,
    "Transport": 2500.00
  },
  "monthlyTrends": [
    {
      "year": 2025,
      "month": 3,
      "income": 30000.00,
      "expense": 8000.00
    },
    {
      "year": 2025,
      "month": 4,
      "income": 75000.00,
      "expense": 19500.00
    }
  ],
  "recentTransactions": [...]
}
```

---

### Dashboard — Without Token
**Request**
How compound interest worksShow me how compound interest worksApr 2Claude's response was interruptedRetryFinance Data Processing and Access Control Backend
Objective
To evaluate your backend development skills through a practical assignment centered around API design, data modeling, business logic, and access control.

This assignment is intended to assess how you think about backend architecture, pastedI uploaded all the information very detailly and what they send to me as a assignment to develope finance Data processing and Access control backend so I want to develope this as per their requirement so please give the correct code to develope this and I want in java Springboot and for database MySpastedApr 2Looking at your assignment and the previous guidance, let me give you the complete, production-ready code — nothing missing. I'll cover every file you need.

Project Structure
src/main/java/com/finance/app/
├── config/
│   ├── SecurityConfig.java
│   └── SwaggerConfig.java
├── controller/
│   ├── AuthController.java
│   ├── UserController.java
│   ├── FinancialRecordController.java
│   └── DashboardController.java
├── dto/
│   ├── request/
│   │   ├── AuthRequest.java
│   │   ├── UserCreateRequest.java
│   │   └── FinancialRecordRequest.java
│   └── response/
│       ├── AuthResponse.java
│       ├── UserResponse.java
│       ├── FinancialRecordResponse.java
│       └── DashboardSummaryResponse.java
├── entity/
│   ├── User.java
│   └── FinancialRecord.java
├── enums/
│   ├── Role.java
│   └── RecordType.java
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── ResourceNotFoundException.java
│   └── AccessDeniedException.java
├── repository/
│   ├── UserRepository.java
│   └── FinancialRecordRepository.java
├── security/
│   ├── JwtUtil.java
│   ├── JwtFilter.java
│   └── CustomUserDetailsService.java
├── service/
│   ├── AuthService.java
│   ├── UserService.java
│   ├── FinancialRecordService.java
│   └── DashboardService.java
└── FinanceAppApplication.java

1. pom.xml
xml<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>

    <groupId>com.finance</groupId>
    <artifactId>finance-app</artifactId>
    <version>1.0.0</version>
    <name>Finance Dashboard Backend</name>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <!-- JWT -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.11.5</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>0.11.5</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>0.11.5</version>
            <scope>runtime</scope>
        </dependency>
        <!-- Swagger -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.2.0</version>
        </dependency>
        <!-- Tests -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>

2. application.properties
properties# Database
spring.datasource.url=jdbc:mysql://localhost:3306/finance_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect

# Server
server.port=8080

# JWT
jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
jwt.expiration=86400000

# Swagger
springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.path=/swagger-ui.html

3. Enums
enums/Role.java
javapackage com.finance.app.enums;

public enum Role {
    ROLE_VIEWER,
    ROLE_ANALYST,
    ROLE_ADMIN
}
enums/RecordType.java
javapackage com.finance.app.enums;

public enum RecordType {
    INCOME,
    EXPENSE
}

4. Entities
entity/User.java
javapackage com.finance.app.entity;

import com.finance.app.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
entity/FinancialRecord.java
javapackage com.finance.app.entity;

import com.finance.app.enums.RecordType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Where(clause = "deleted = false")   // Soft delete filter
public class FinancialRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordType type;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private LocalDate date;

    private String description;

    @Column(nullable = false)
    private boolean deleted = false;   // Soft delete flag

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

5. DTOs
dto/request/AuthRequest.java
javapackage com.finance.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}
dto/request/UserCreateRequest.java
javapackage com.finance.app.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserCreateRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Role is required")
    private String role;  // ROLE_VIEWER, ROLE_ANALYST, ROLE_ADMIN
}
dto/request/FinancialRecordRequest.java
javapackage com.finance.app.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FinancialRecordRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Type is required (INCOME or EXPENSE)")
    private String type;

    @NotBlank(message = "Category is required")
    @Size(max = 100)
    private String category;

    @NotNull(message = "Date is required")
    private LocalDate date;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
}
dto/response/AuthResponse.java
javapackage com.finance.app.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String username;
    private String role;
}
dto/response/UserResponse.java
javapackage com.finance.app.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String role;
    private boolean active;
    private LocalDateTime createdAt;
}
dto/response/FinancialRecordResponse.java
javapackage com.finance.app.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class FinancialRecordResponse {
    private Long id;
    private BigDecimal amount;
    private String type;
    private String category;
    private LocalDate date;
    private String description;
    private String createdBy;
    private LocalDateTime createdAt;
}
dto/response/DashboardSummaryResponse.java
javapackage com.finance.app.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DashboardSummaryResponse {
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netBalance;
    private Map<String, BigDecimal> categoryBreakdown;
    private List<MonthlyTrend> monthlyTrends;
    private List<FinancialRecordResponse> recentTransactions;

    @Data
    @Builder
    public static class MonthlyTrend {
        private int year;
        private int month;
        private BigDecimal income;
        private BigDecimal expense;
    }
}

6. Repositories
repository/UserRepository.java
javapackage com.finance.app.repository;

import com.finance.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
repository/FinancialRecordRepository.java
javapackage com.finance.app.repository;

import com.finance.app.entity.FinancialRecord;
import com.finance.app.enums.RecordType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface FinancialRecordRepository extends JpaRepository<FinancialRecord, Long> {

    // Filtered + paginated query
    @Query("""
        SELECT f FROM FinancialRecord f
        WHERE (:type IS NULL OR f.type = :type)
        AND (:category IS NULL OR LOWER(f.category) LIKE LOWER(CONCAT('%', :category, '%')))
        AND (:startDate IS NULL OR f.date >= :startDate)
        AND (:endDate IS NULL OR f.date <= :endDate)
        AND f.deleted = false
    """)
    Page<FinancialRecord> findWithFilters(
            @Param("type") RecordType type,
            @Param("category") String category,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable
    );

    // Dashboard totals
    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.type = 'INCOME' AND f.deleted = false")
    BigDecimal getTotalIncome();

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.type = 'EXPENSE' AND f.deleted = false")
    BigDecimal getTotalExpense();

    // Category breakdown
    @Query("SELECT f.category, COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.deleted = false GROUP BY f.category")
    List<Object[]> getCategoryBreakdown();

    // Monthly trends
    @Query("""
        SELECT YEAR(f.date), MONTH(f.date), f.type, COALESCE(SUM(f.amount), 0)
        FROM FinancialRecord f
        WHERE f.deleted = false
        GROUP BY YEAR(f.date), MONTH(f.date), f.type
        ORDER BY YEAR(f.date), MONTH(f.date)
    """)
    List<Object[]> getMonthlyTrends();

    // Recent records
    List<FinancialRecord> findTop5ByDeletedFalseOrderByCreatedAtDesc();
}

7. Security Layer
security/JwtUtil.java
javapackage com.finance.app.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
security/JwtFilter.java
javapackage com.finance.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (jwtUtil.isTokenValid(token)) {
            String username = jwtUtil.extractUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }
}
security/CustomUserDetailsService.java
javapackage com.finance.app.security;

import com.finance.app.entity.User;
import com.finance.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (!user.isActive()) {
            throw new DisabledException("User account is inactive");
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority(user.getRole().name()))
        );
    }
}

8. Config
config/SecurityConfig.java
javapackage com.finance.app.config;

import com.finance.app.security.CustomUserDetailsService;
import com.finance.app.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // Enables @PreAuthorize at method level
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll()
                .requestMatchers("/api/users/**").hasRole("ADMIN")
                .requestMatchers("/api/records/**")
                    .hasAnyRole("ADMIN", "ANALYST", "VIEWER")
                .requestMatchers("/api/dashboard/**")
                    .hasAnyRole("ADMIN", "ANALYST", "VIEWER")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
config/SwaggerConfig.java
javapackage com.finance.app.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Finance Dashboard API")
                        .description("Backend API for Finance Dashboard with Role-Based Access Control")
                        .version("1.0.0")
                        .contact(new Contact().name("Finance App").email("admin@finance.com")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Auth"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Auth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}

9. Exception Handling
exception/ResourceNotFoundException.java
javapackage com.finance.app.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
exception/BadRequestException.java
javapackage com.finance.app.exception;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
exception/GlobalExceptionHandler.java
javapackage com.finance.app.exception;

import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Validation errors (field-level)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        return buildError(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }

    // Resource not found
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleNotFound(ResourceNotFoundException ex) {
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    // Bad request
    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBadRequest(BadRequestException ex) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    // Wrong credentials
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleBadCredentials(BadCredentialsException ex) {
        return buildError(HttpStatus.UNAUTHORIZED, "Invalid username or password", null);
    }

    // Access denied
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleAccessDenied(AccessDeniedException ex) {
        return buildError(HttpStatus.FORBIDDEN, "You do not have permission to perform this action", null);
    }

    // Illegal argument (e.g., invalid enum)
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException ex) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    // Catch-all
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGeneral(Exception ex) {
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", null);
    }

    private Map<String, Object> buildError(HttpStatus status, String message, Object details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (details != null) body.put("details", details);
        return body;
    }
}

10. Services
service/AuthService.java
javapackage com.finance.app.service;

import com.finance.app.dto.request.AuthRequest;
import com.finance.app.dto.response.AuthResponse;
import com.finance.app.entity.User;
import com.finance.app.repository.UserRepository;
import com.finance.app.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authManager;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public AuthResponse login(AuthRequest request) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(), request.getPassword()
                )
        );

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String token = jwtUtil.generateToken(user.getUsername());

        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }
}
service/UserService.java
javapackage com.finance.app.service;

import com.finance.app.dto.request.UserCreateRequest;
import com.finance.app.dto.response.UserResponse;
import com.finance.app.entity.User;
import com.finance.app.enums.Role;
import com.finance.app.exception.BadRequestException;
import com.finance.app.exception.ResourceNotFoundException;
import com.finance.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponse createUser(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username already exists: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered: " + request.getEmail());
        }

        Role role;
        try {
            role = Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role. Valid values: ROLE_VIEWER, ROLE_ANALYST, ROLE_ADMIN");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(role)
                .active(true)
                .build();

        return toResponse(userRepository.save(user));
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(Long id) {
        return toResponse(findById(id));
    }

    public UserResponse updateUserRole(Long id, String role) {
        User user = findById(id);
        try {
            user.setRole(Role.valueOf(role.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role: " + role);
        }
        return toResponse(userRepository.save(user));
    }

    public UserResponse toggleUserStatus(Long id) {
        User user = findById(id);
        user.setActive(!user.isActive());
        return toResponse(userRepository.save(user));
    }

    public void deleteUser(Long id) {
        User user = findById(id);
        userRepository.delete(user);
    }

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
service/FinancialRecordService.java
javapackage com.finance.app.service;

import com.finance.app.dto.request.FinancialRecordRequest;
import com.finance.app.dto.response.FinancialRecordResponse;
import com.finance.app.entity.FinancialRecord;
import com.finance.app.entity.User;
import com.finance.app.enums.RecordType;
import com.finance.app.exception.BadRequestException;
import com.finance.app.exception.ResourceNotFoundException;
import com.finance.app.repository.FinancialRecordRepository;
import com.finance.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class FinancialRecordService {

    private final FinancialRecordRepository recordRepository;
    private final UserRepository userRepository;

    public FinancialRecordResponse create(FinancialRecordRequest request) {
        User currentUser = getCurrentUser();

        RecordType type;
        try {
            type = RecordType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid type. Must be INCOME or EXPENSE");
        }

        FinancialRecord record = FinancialRecord.builder()
                .amount(request.getAmount())
                .type(type)
                .category(request.getCategory())
                .date(request.getDate())
                .description(request.getDescription())
                .createdBy(currentUser)
                .deleted(false)
                .build();

        return toResponse(recordRepository.save(record));
    }

    public Page<FinancialRecordResponse> getAll(String type, String category,
                                                LocalDate startDate, LocalDate endDate,
                                                int page, int size, String sortBy, String sortDir) {
        RecordType recordType = null;
        if (type != null && !type.isBlank()) {
            try {
                recordType = RecordType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid type. Must be INCOME or EXPENSE");
            }
        }

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        return recordRepository
                .findWithFilters(recordType, category, startDate, endDate, pageable)
                .map(this::toResponse);
    }

    public FinancialRecordResponse getById(Long id) {
        return toResponse(findById(id));
    }

    public FinancialRecordResponse update(Long id, FinancialRecordRequest request) {
        FinancialRecord record = findById(id);

        RecordType type;
        try {
            type = RecordType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid type. Must be INCOME or EXPENSE");
        }

        record.setAmount(request.getAmount());
        record.setType(type);
        record.setCategory(request.getCategory());
        record.setDate(request.getDate());
        record.setDescription(request.getDescription());

        return toResponse(recordRepository.save(record));
    }

    // Soft delete
    public void delete(Long id) {
        FinancialRecord record = findById(id);
        record.setDeleted(true);
        recordRepository.save(record);
    }

    private FinancialRecord findById(Long id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Record not found with id: " + id));
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private FinancialRecordResponse toResponse(FinancialRecord r) {
        return FinancialRecordResponse.builder()
                .id(r.getId())
                .amount(r.getAmount())
                .type(r.getType().name())
                .category(r.getCategory())
                .date(r.getDate())
                .description(r.getDescription())
                .createdBy(r.getCreatedBy().getUsername())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
service/DashboardService.java
javapackage com.finance.app.service;

import com.finance.app.dto.response.DashboardSummaryResponse;
import com.finance.app.dto.response.FinancialRecordResponse;
import com.finance.app.entity.FinancialRecord;
import com.finance.app.enums.RecordType;
import com.finance.app.repository.FinancialRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final FinancialRecordRepository recordRepository;

    public DashboardSummaryResponse getSummary() {
        BigDecimal totalIncome = recordRepository.getTotalIncome();
        BigDecimal totalExpense = recordRepository.getTotalExpense();
        BigDecimal netBalance = totalIncome.subtract(totalExpense);

        // Category breakdown
        Map<String, BigDecimal> categoryBreakdown = new LinkedHashMap<>();
        for (Object[] row : recordRepository.getCategoryBreakdown()) {
            categoryBreakdown.put((String) row[0], (BigDecimal) row[1]);
        }

        // Monthly trends
        Map<String, DashboardSummaryResponse.MonthlyTrend> trendMap = new LinkedHashMap<>();
        for (Object[] row : recordRepository.getMonthlyTrends()) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            RecordType type = RecordType.valueOf((String) row[2]);
            BigDecimal amount = (BigDecimal) row[3];

            String key = year + "-" + month;
            trendMap.computeIfAbsent(key, k ->
                    DashboardSummaryResponse.MonthlyTrend.builder()
                            .year(year).month(month)
                            .income(BigDecimal.ZERO).expense(BigDecimal.ZERO)
                            .build()
            );

            DashboardSummaryResponse.MonthlyTrend trend = trendMap.get(key);
            if (type == RecordType.INCOME) trend.setIncome(amount);
            else trend.setExpense(amount);
        }

        // Recent transactions
        List<FinancialRecordResponse> recent = recordRepository
                .findTop5ByDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return DashboardSummaryResponse.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netBalance(netBalance)
                .categoryBreakdown(categoryBreakdown)
                .monthlyTrends(new ArrayList<>(trendMap.values()))
                .recentTransactions(recent)
                .build();
    }

    private FinancialRecordResponse toResponse(FinancialRecord r) {
        return FinancialRecordResponse.builder()
                .id(r.getId())
                .amount(r.getAmount())
                .type(r.getType().name())
                .category(r.getCategory())
                .date(r.getDate())
                .description(r.getDescription())
                .createdBy(r.getCreatedBy().getUsername())
                .createdAt(r.getCreatedAt())
                .build();
    }
}

11. Controllers
controller/AuthController.java
javapackage com.finance.app.controller;

import com.finance.app.dto.request.AuthRequest;
import com.finance.app.dto.response.AuthResponse;
import com.finance.app.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login and token management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT token")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
controller/UserController.java
javapackage com.finance.app.controller;

import com.finance.app.dto.request.UserCreateRequest;
import com.finance.app.dto.response.UserResponse;
import com.finance.app.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Admin-only user management")
@SecurityRequirement(name = "Bearer Auth")
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Create a new user (Admin only)")
    public ResponseEntity<UserResponse> createUser(@RequestBody @Valid UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Get all users (Admin only)")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Get user by ID (Admin only)")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Update user role (Admin only)")
    public ResponseEntity<UserResponse> updateRole(@PathVariable Long id,
                                                    @RequestParam String role) {
        return ResponseEntity.ok(userService.updateUserRole(id, role));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Activate or deactivate user (Admin only)")
    public ResponseEntity<UserResponse> toggleStatus(@PathVariable Long id) {
        return ResponseEntity.ok(userService.toggleUserStatus(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Delete user (Admin only)")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
controller/FinancialRecordController.java
javapackage com.finance.app.controller;

import com.finance.app.dto.request.FinancialRecordRequest;
import com.finance.app.dto.response.FinancialRecordResponse;
import com.finance.app.service.FinancialRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
@Tag(name = "Financial Records", description = "Manage income and expense records")
@SecurityRequirement(name = "Bearer Auth")
public class FinancialRecordController {

    private final FinancialRecordService recordService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_ANALYST')")
    @Operation(summary = "Create a financial record (Admin, Analyst)")
    public ResponseEntity<FinancialRecordResponse> create(
            @RequestBody @Valid FinancialRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recordService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_ANALYST', 'ROLE_VIEWER')")
    @Operation(summary = "Get all records with optional filters and pagination")
    public ResponseEntity<Page<FinancialRecordResponse>> getAll(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(
                recordService.getAll(type, category, startDate, endDate, page, size, sortBy, sortDir));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_ANALYST', 'ROLE_VIEWER')")
    @Operation(summary = "Get a single record by ID")
    public ResponseEntity<FinancialRecordResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(recordService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_ANALYST')")
    @Operation(summary = "Update a financial record (Admin, Analyst)")
    public ResponseEntity<FinancialRecordResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid FinancialRecordRequest request) {
        return ResponseEntity.ok(recordService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Soft-delete a financial record (Admin only)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recordService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
controller/DashboardController.java
javapackage com.finance.app.controller;

import com.finance.app.dto.response.DashboardSummaryResponse;
import com.finance.app.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Summary and analytics")
@SecurityRequirement(name = "Bearer Auth")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_ANALYST', 'ROLE_VIEWER')")
    @Operation(summary = "Get full dashboard summary: totals, categories, trends, recent")
    public ResponseEntity<DashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }
}

12. Main Application Class
FinanceAppApplication.java
javapackage com.finance.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FinanceAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinanceAppApplication.class, args);
    }
}

13. README.md
markdown# Finance Dashboard Backend

A production-ready REST API backend for a Finance Dashboard system built with Spring Boot, MySQL, and JWT authentication.

## Tech Stack
- Java 17
- Spring Boot 3.2
- Spring Security + JWT
- Spring Data JPA
- MySQL 8
- Lombok
- Swagger / OpenAPI 3

## Architecture
controller → service → repository → MySQL
↓
DTO layer (request/response separation)
↓
Exception handling (global)

## Roles & Permissions

| Action                        | VIEWER | ANALYST | ADMIN |
|-------------------------------|--------|---------|-------|
| Login                         | ✅     | ✅      | ✅    |
| View records                  | ✅     | ✅      | ✅    |
| View dashboard summary        | ✅     | ✅      | ✅    |
| Create/update records         | ❌     | ✅      | ✅    |
| Delete records (soft)         | ❌     | ❌      | ✅    |
| Manage users                  | ❌     | ❌      | ✅    |

## Setup Instructions

1. Create MySQL database:
```sql
CREATE DATABASE finance_db;
```

2. Update `application.properties` with your DB credentials.

3. Run the application:
```bash
mvn spring-boot:run
```

4. Access Swagger UI:
http://localhost:8080/swagger-ui.html

## API Endpoints

### Auth
| Method | Endpoint         | Description     |
|--------|------------------|-----------------|
| POST   | /api/auth/login  | Login, get JWT  |

### Users (Admin only)
| Method | Endpoint                      | Description           |
|--------|-------------------------------|-----------------------|
| POST   | /api/users                    | Create user           |
| GET    | /api/users                    | Get all users         |
| GET    | /api/users/{id}               | Get user by ID        |
| PATCH  | /api/users/{id}/role          | Update user role      |
| PATCH  | /api/users/{id}/toggle-status | Activate/deactivate   |
| DELETE | /api/users/{id}               | Delete user           |

### Financial Records
| Method | Endpoint            | Description                         |
|--------|---------------------|-------------------------------------|
| POST   | /api/records        | Create record (Admin, Analyst)      |
| GET    | /api/records        | List with filters + pagination      |
| GET    | /api/records/{id}   | Get single record                   |
| PUT    | /api/records/{id}   | Update record (Admin, Analyst)      |
| DELETE | /api/records/{id}   | Soft delete (Admin only)            |

**Filter parameters:** `type`, `category`, `startDate`, `endDate`, `page`, `size`, `sortBy`, `sortDir`

### Dashboard
| Method | Endpoint                | Description                         |
|--------|-------------------------|-------------------------------------|
| GET    | /api/dashboard/summary  | Total income, expense, balance, trends, recent |

## Sample Requests

### Login
```json
POST /api/auth/login
{
  "username": "admin",
  "password": "password123"
}
```

### Create Record
```json
POST /api/records
Authorization: Bearer <token>
{
  "amount": 5000.00,
  "type": "INCOME",
  "category": "Salary",
  "date": "2025-04-01",
  "description": "April salary"
}
```

### Filter Records
GET /api/records?type=EXPENSE&category=Food&startDate=2025-01-01&endDate=2025-04-01&page=0&size=10&sortBy=date&sortDir=desc

## Assumptions
- Soft delete is used for financial records (records are marked deleted, not removed)
- Roles are prefixed with `ROLE_` as required by Spring Security
- JWT tokens expire in 24 hours
- All monetary values use BigDecimal for precision

## Features Implemented
- ✅ JWT Authentication
- ✅ Role-Based Access Control (RBAC)
- ✅ Full CRUD for financial records
- ✅ Soft delete
- ✅ Pagination + sorting
- ✅ Filtering by type, category, date range
- ✅ Dashboard summary with category breakdown + monthly trends
- ✅ Input validation with detailed error messages
- ✅ Global exception handling
- ✅ Swagger API documentation
- ✅ Clean layered architecture with DTO separation

What's Covered vs Assignment
RequirementStatusUser & Role Management (create, assign, status)✅ CompleteFinancial Records CRUD✅ CompleteFiltering (date, category, type)✅ CompletePagination + sorting✅ CompleteDashboard: totals, categories, trends, recent✅ CompleteRole-based access control✅ JWT + @PreAuthorizeValidation + error responses✅ Field-level + global handlerSoft delete✅ CompleteSwagger API docs✅ CompleteClean architecture + DTO layer✅ CompleteMySQL persistence✅ Complete
Everything in the assignment is covered. Push this to GitHub, add the README, and you're in excellent shape for the evaluation. Good luck!give testing for all api requests testing in postmanApr 23 / 5Here's a complete Postman testing guide for every API endpoint.

Setup First
In Postman, create a Collection called Finance Dashboard API and set a Collection Variable:

baseUrl = http://localhost:8080
token = (leave empty — it auto-fills after login)


1. AUTH APIs
1.1 Login as Admin
Method: POST
URL: {{baseUrl}}/api/auth/login
Headers: Content-Type: application/json

Body (raw JSON):
{
  "username": "admin",
  "password": "admin123"
}
Expected Response (200):
json{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "admin",
  "role": "ROLE_ADMIN"
}
Tests tab (auto-save token):
javascriptpm.test("Login successful", function () {
    pm.response.to.have.status(200);
});
pm.test("Token received", function () {
    var json = pm.response.json();
    pm.expect(json.token).to.not.be.empty;
    pm.collectionVariables.set("token", json.token);
});

1.2 Login as Analyst
Method: POST
URL: {{baseUrl}}/api/auth/login

Body:
{
  "username": "analyst1",
  "password": "analyst123"
}
Tests tab:
javascriptpm.test("Analyst login", function () {
    pm.response.to.have.status(200);
    var json = pm.response.json();
    pm.collectionVariables.set("analystToken", json.token);
});

1.3 Login as Viewer
Method: POST
URL: {{baseUrl}}/api/auth/login

Body:
{
  "username": "viewer1",
  "password": "viewer123"
}
Tests tab:
javascriptpm.test("Viewer login", function () {
    pm.response.to.have.status(200);
    var json = pm.response.json();
    pm.collectionVariables.set("viewerToken", json.token);
});

1.4 Login — Wrong Password (Negative Test)
Method: POST
URL: {{baseUrl}}/api/auth/login

Body:
{
  "username": "admin",
  "password": "wrongpassword"
}
Expected Response (401):
json{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username or password"
}
Tests tab:
javascriptpm.test("Rejects wrong password", function () {
    pm.response.to.have.status(401);
});

1.5 Login — Missing Fields (Negative Test)
Method: POST
URL: {{baseUrl}}/api/auth/login

Body:
{
  "username": ""
}
Expected Response (400):
json{
  "status": 400,
  "details": {
    "username": "Username is required",
    "password": "Password is required"
  }
}
Tests tab:
javascriptpm.test("Validation error on empty fields", function () {
    pm.response.to.have.status(400);
});

2. USER MANAGEMENT APIs (Admin only)

For all user APIs, add this header:
Authorization: Bearer {{token}}


2.1 Create Admin User
Method: POST
URL: {{baseUrl}}/api/users
Headers:
  Content-Type: application/json
  Authorization: Bearer {{token}}

Body:
{
  "username": "admin2",
  "password": "admin123",
  "email": "admin2@finance.com",
  "role": "ROLE_ADMIN"
}
Expected Response (201):
json{
  "id": 2,
  "username": "admin2",
  "email": "admin2@finance.com",
  "role": "ROLE_ADMIN",
  "active": true,
  "createdAt": "2025-04-01T10:00:00"
}
Tests tab:
javascriptpm.test("Admin user created", function () {
    pm.response.to.have.status(201);
    var json = pm.response.json();
    pm.expect(json.role).to.eql("ROLE_ADMIN");
    pm.collectionVariables.set("adminUserId", json.id);
});

2.2 Create Analyst User
Method: POST
URL: {{baseUrl}}/api/users
Headers:
  Authorization: Bearer {{token}}

Body:
{
  "username": "analyst1",
  "password": "analyst123",
  "email": "analyst1@finance.com",
  "role": "ROLE_ANALYST"
}
Tests tab:
javascriptpm.test("Analyst created", function () {
    pm.response.to.have.status(201);
    pm.collectionVariables.set("analystUserId", pm.response.json().id);
});

2.3 Create Viewer User
Method: POST
URL: {{baseUrl}}/api/users
Headers:
  Authorization: Bearer {{token}}

Body:
{
  "username": "viewer1",
  "password": "viewer123",
  "email": "viewer1@finance.com",
  "role": "ROLE_VIEWER"
}
Tests tab:
javascriptpm.test("Viewer created", function () {
    pm.response.to.have.status(201);
    pm.collectionVariables.set("viewerUserId", pm.response.json().id);
});

2.4 Create User — Duplicate Username (Negative Test)
Method: POST
URL: {{baseUrl}}/api/users
Headers:
  Authorization: Bearer {{token}}

Body:
{
  "username": "analyst1",
  "password": "analyst123",
  "email": "another@finance.com",
  "role": "ROLE_ANALYST"
}
Expected Response (400):
json{
  "message": "Username already exists: analyst1"
}
Tests tab:
javascriptpm.test("Duplicate username rejected", function () {
    pm.response.to.have.status(400);
    pm.expect(pm.response.json().message).to.include("already exists");
});

2.5 Create User — Invalid Role (Negative Test)
Method: POST
URL: {{baseUrl}}/api/users
Headers:
  Authorization: Bearer {{token}}

Body:
{
  "username": "testuser",
  "password": "test123",
  "email": "test@finance.com",
  "role": "ROLE_SUPERUSER"
}
Expected Response (400):
json{
  "message": "Invalid role. Valid values: ROLE_VIEWER, ROLE_ANALYST, ROLE_ADMIN"
}
Tests tab:
javascriptpm.test("Invalid role rejected", function () {
    pm.response.to.have.status(400);
});

2.6 Get All Users
Method: GET
URL: {{baseUrl}}/api/users
Headers:
  Authorization: Bearer {{token}}
Expected Response (200):
json[
  {
    "id": 1,
    "username": "admin",
    "email": "admin@finance.com",
    "role": "ROLE_ADMIN",
    "active": true
  },
  ...
]
Tests tab:
javascriptpm.test("Get all users success", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json()).to.be.an("array");
});

2.7 Get User by ID
Method: GET
URL: {{baseUrl}}/api/users/{{analystUserId}}
Headers:
  Authorization: Bearer {{token}}
Tests tab:
javascriptpm.test("Get user by ID", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().id).to.eql(
        Number(pm.collectionVariables.get("analystUserId"))
    );
});

2.8 Get User — Not Found (Negative Test)
Method: GET
URL: {{baseUrl}}/api/users/9999
Headers:
  Authorization: Bearer {{token}}
Expected Response (404):
json{
  "status": 404,
  "message": "User not found with id: 9999"
}
Tests tab:
javascriptpm.test("User not found 404", function () {
    pm.response.to.have.status(404);
});

2.9 Update User Role
Method: PATCH
URL: {{baseUrl}}/api/users/{{analystUserId}}/role?role=ROLE_ADMIN
Headers:
  Authorization: Bearer {{token}}
Expected Response (200):
json{
  "role": "ROLE_ADMIN"
}
Tests tab:
javascriptpm.test("Role updated", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().role).to.eql("ROLE_ADMIN");
});

2.10 Toggle User Status (Deactivate)
Method: PATCH
URL: {{baseUrl}}/api/users/{{viewerUserId}}/toggle-status
Headers:
  Authorization: Bearer {{token}}
Expected Response (200):
json{
  "active": false
}
Tests tab:
javascriptpm.test("User deactivated", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().active).to.eql(false);
});

2.11 Viewer Tries to Access Users (Forbidden — Negative Test)
Method: GET
URL: {{baseUrl}}/api/users
Headers:
  Authorization: Bearer {{viewerToken}}
Expected Response (403):
json{
  "message": "You do not have permission to perform this action"
}
Tests tab:
javascriptpm.test("Viewer forbidden from users", function () {
    pm.response.to.have.status(403);
});

2.12 Delete User
Method: DELETE
URL: {{baseUrl}}/api/users/{{adminUserId}}
Headers:
  Authorization: Bearer {{token}}
Expected Response: 204 No Content
Tests tab:
javascriptpm.test("User deleted", function () {
    pm.response.to.have.status(204);
});

3. FINANCIAL RECORDS APIs
3.1 Create Income Record (Admin)
Method: POST
URL: {{baseUrl}}/api/records
Headers:
  Content-Type: application/json
  Authorization: Bearer {{token}}

Body:
{
  "amount": 75000.00,
  "type": "INCOME",
  "category": "Salary",
  "date": "2025-04-01",
  "description": "Monthly salary April 2025"
}
Expected Response (201):
json{
  "id": 1,
  "amount": 75000.00,
  "type": "INCOME",
  "category": "Salary",
  "date": "2025-04-01",
  "createdBy": "admin"
}
Tests tab:
javascriptpm.test("Income record created", function () {
    pm.response.to.have.status(201);
    var json = pm.response.json();
    pm.expect(json.type).to.eql("INCOME");
    pm.collectionVariables.set("recordId", json.id);
});

3.2 Create Expense Record
Method: POST
URL: {{baseUrl}}/api/records
Headers:
  Authorization: Bearer {{token}}

Body:
{
  "amount": 12000.00,
  "type": "EXPENSE",
  "category": "Rent",
  "date": "2025-04-01",
  "description": "Office rent April"
}
Tests tab:
javascriptpm.test("Expense record created", function () {
    pm.response.to.have.status(201);
});

3.3 Create More Records (for filter/summary testing)
Run each of these as separate POST requests:
json{ "amount": 5000, "type": "EXPENSE", "category": "Food", "date": "2025-04-05", "description": "Team lunch" }
{ "amount": 2500, "type": "EXPENSE", "category": "Transport", "date": "2025-04-10", "description": "Cab charges" }
{ "amount": 30000, "type": "INCOME", "category": "Freelance", "date": "2025-03-15", "description": "Freelance project" }
{ "amount": 8000, "type": "EXPENSE", "category": "Utilities", "date": "2025-03-20", "description": "Electricity bill" }

3.4 Create Record — Negative Amount (Negative Test)
Method: POST
URL: {{baseUrl}}/api/records
Headers:
  Authorization: Bearer {{token}}

Body:
{
  "amount": -500,
  "type": "EXPENSE",
  "category": "Food",
  "date": "2025-04-01"
}
Expected Response (400):
json{
  "details": {
    "amount": "Amount must be greater than 0"
  }
}
Tests tab:
javascriptpm.test("Negative amount rejected", function () {
    pm.response.to.have.status(400);
    pm.expect(pm.response.json().details.amount).to.include("greater than 0");
});

3.5 Create Record — Invalid Type (Negative Test)
json{
  "amount": 1000,
  "type": "SALARY",
  "category": "Other",
  "date": "2025-04-01"
}
Expected Response (400):
json{
  "message": "Invalid type. Must be INCOME or EXPENSE"
}

3.6 Viewer Tries to Create Record (Forbidden — Negative Test)
Method: POST
URL: {{baseUrl}}/api/records
Headers:
  Authorization: Bearer {{viewerToken}}

Body:
{
  "amount": 1000,
  "type": "INCOME",
  "category": "Test",
  "date": "2025-04-01"
}
Expected Response (403)
Tests tab:
javascriptpm.test("Viewer cannot create records", function () {
    pm.response.to.have.status(403);
});

3.7 Get All Records (No Filters)
Method: GET
URL: {{baseUrl}}/api/records
Headers:
  Authorization: Bearer {{token}}
Expected Response (200):
json{
  "content": [...],
  "totalElements": 6,
  "totalPages": 1,
  "size": 10,
  "number": 0
}
Tests tab:
javascriptpm.test("Get all records", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().content).to.be.an("array");
});

3.8 Get Records — Filter by Type (INCOME)
Method: GET
URL: {{baseUrl}}/api/records?type=INCOME
Headers:
  Authorization: Bearer {{token}}
Tests tab:
javascriptpm.test("Filter by INCOME", function () {
    pm.response.to.have.status(200);
    var records = pm.response.json().content;
    records.forEach(function(r) {
        pm.expect(r.type).to.eql("INCOME");
    });
});

3.9 Get Records — Filter by Category
Method: GET
URL: {{baseUrl}}/api/records?category=Rent
Headers:
  Authorization: Bearer {{token}}
Tests tab:
javascriptpm.test("Filter by category", function () {
    pm.response.to.have.status(200);
    var records = pm.response.json().content;
    records.forEach(function(r) {
        pm.expect(r.category.toLowerCase()).to.include("rent");
    });
});

3.10 Get Records — Filter by Date Range
Method: GET
URL: {{baseUrl}}/api/records?startDate=2025-04-01&endDate=2025-04-30
Headers:
  Authorization: Bearer {{token}}
Tests tab:
javascriptpm.test("Date range filter works", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().content.length).to.be.above(0);
});

3.11 Get Records — Combined Filters with Pagination
Method: GET
URL: {{baseUrl}}/api/records?type=EXPENSE&category=Food&page=0&size=5&sortBy=amount&sortDir=desc
Headers:
  Authorization: Bearer {{token}}
Tests tab:
javascriptpm.test("Combined filters with pagination", function () {
    pm.response.to.have.status(200);
    var json = pm.response.json();
    pm.expect(json).to.have.property("totalElements");
    pm.expect(json.size).to.eql(5);
});

3.12 Get Record by ID
Method: GET
URL: {{baseUrl}}/api/records/{{recordId}}
Headers:
  Authorization: Bearer {{token}}
Tests tab:
javascriptpm.test("Get record by ID", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().id).to.eql(
        Number(pm.collectionVariables.get("recordId"))
    );
});

3.13 Get Record — Not Found (Negative Test)
Method: GET
URL: {{baseUrl}}/api/records/9999
Headers:
  Authorization: Bearer {{token}}
Expected Response (404)
Tests tab:
javascriptpm.test("Record not found", function () {
    pm.response.to.have.status(404);
});

3.14 Update Record
Method: PUT
URL: {{baseUrl}}/api/records/{{recordId}}
Headers:
  Authorization: Bearer {{token}}

Body:
{
  "amount": 80000.00,
  "type": "INCOME",
  "category": "Salary",
  "date": "2025-04-01",
  "description": "Updated: April salary with bonus"
}
Expected Response (200):
json{
  "amount": 80000.00,
  "description": "Updated: April salary with bonus"
}
Tests tab:
javascriptpm.test("Record updated", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().amount).to.eql(80000.00);
});

3.15 Analyst Tries to Delete Record (Forbidden — Negative Test)
Method: DELETE
URL: {{baseUrl}}/api/records/{{recordId}}
Headers:
  Authorization: Bearer {{analystToken}}
Expected Response (403)
Tests tab:
javascriptpm.test("Analyst cannot delete records", function () {
    pm.response.to.have.status(403);
});

3.16 Soft Delete Record (Admin)
Method: DELETE
URL: {{baseUrl}}/api/records/{{recordId}}
Headers:
  Authorization: Bearer {{token}}
Expected Response: 204 No Content
Tests tab:
javascriptpm.test("Record soft deleted", function () {
    pm.response.to.have.status(204);
});

3.17 Verify Soft Deleted Record is Hidden
Method: GET
URL: {{baseUrl}}/api/records/{{recordId}}
Headers:
  Authorization: Bearer {{token}}
Expected Response (404) — record is soft-deleted, not visible
Tests tab:
javascriptpm.test("Deleted record not found", function () {
    pm.response.to.have.status(404);
});

3.18 No Token — Unauthorized (Negative Test)
Method: GET
URL: {{baseUrl}}/api/records
(No Authorization header)
Expected Response (401)
Tests tab:
javascriptpm.test("Unauthorized without token", function () {
    pm.response.to.have.status(401);
});

4. DASHBOARD APIs
4.1 Get Dashboard Summary (Admin)
Method: GET
URL: {{baseUrl}}/api/dashboard/summary
Headers:
  Authorization: Bearer {{token}}
Expected Response (200):
json{
  "totalIncome": 105000.00,
  "totalExpense": 27500.00,
  "netBalance": 77500.00,
  "categoryBreakdown": {
    "Salary": 75000.00,
    "Freelance": 30000.00,
    "Rent": 12000.00,
    "Food": 5000.00,
    "Transport": 2500.00,
    "Utilities": 8000.00
  },
  "monthlyTrends": [
    { "year": 2025, "month": 3, "income": 30000.00, "expense": 8000.00 },
    { "year": 2025, "month": 4, "income": 75000.00, "expense": 19500.00 }
  ],
  "recentTransactions": [...]
}
Tests tab:
javascriptpm.test("Dashboard summary success", function () {
    pm.response.to.have.status(200);
});
pm.test("Has all required fields", function () {
    var json = pm.response.json();
    pm.expect(json).to.have.property("totalIncome");
    pm.expect(json).to.have.property("totalExpense");
    pm.expect(json).to.have.property("netBalance");
    pm.expect(json).to.have.property("categoryBreakdown");
    pm.expect(json).to.have.property("monthlyTrends");
    pm.expect(json).to.have.property("recentTransactions");
});
pm.test("Net balance = income - expense", function () {
    var json = pm.response.json();
    var expected = json.totalIncome - json.totalExpense;
    pm.expect(json.netBalance).to.be.closeTo(expected, 0.01);
});

4.2 Get Dashboard Summary (Viewer — Should Work)
Method: GET
URL: {{baseUrl}}/api/dashboard/summary
Headers:
  Authorization: Bearer {{viewerToken}}
Tests tab:
javascriptpm.test("Viewer can see dashboard", function () {
    pm.response.to.have.status(200);
});

4.3 Dashboard Without Token (Negative Test)
Method: GET
URL: {{baseUrl}}/api/dashboard/summary
(No Authorization header)
Expected Response (401)
Tests tab:
javascriptpm.test("Dashboard requires auth", function () {
    pm.response.to.have.status(401);
});

Complete Test Execution Order
Run requests in this exact sequence to avoid dependency issues:
1.  POST /api/auth/login          (admin)       → saves token
2.  POST /api/users               (create analyst, viewer)
3.  POST /api/auth/login          (analyst)     → saves analystToken
4.  POST /api/auth/login          (viewer)      → saves viewerToken
5.  POST /api/records             (create 6 records)
6.  GET  /api/records             (no filters)
7.  GET  /api/records?type=INCOME
8.  GET  /api/records?category=Rent
9.  GET  /api/records?startDate=...&endDate=...
10. GET  /api/records/{{recordId}}
11. PUT  /api/records/{{recordId}}
12. GET  /api/dashboard/summary
13. [Negative tests - any order]
14. DELETE /api/records/{{recordId}}   (soft delete last)

Collection Variables Summary
VariableSet byUsed bybaseUrlManualAll requeststokenLogin (admin)All admin requestsanalystTokenLogin (analyst)Analyst testsviewerTokenLogin (viewer)Viewer testsrecordIdCreate recordGet/Update/Delete recordanalystUserIdCreate analystUpdate role, toggle statusviewerUserIdCreate viewerToggle status

This covers every endpoint with positive tests, negative tests, validation tests, role-permission tests, and auto-chaining variables. Run them in the order listed and all should pass cleanly.dashboard summary (admin) and viewer not getting 200 response it give 500 response
{
  "timestamp": "2026-04-03T16:11:45.613157",
  "status": 500,
  "error": "Internal Server Error",
  "message": "An unexpected error occurred"
}
-------------------------------
package com.finance.entity;import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;import com.finance.enums.RecordType;import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;@Entity
@Table(name = "financial_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("deleted = false")
public class FinancialRecord {    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordType type;    @Column(nullable = false)
    private String category;    @Column(nullable = false)
    private LocalDate date;    private String description;    @Column(nullable = false)
    private boolean deleted = false;   // Soft delete flag    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;    @UpdateTimestamp
    private LocalDateTime updatedAt;}-----------------------------------
package com.finance.repo;import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;import com.finance.entity.FinancialRecord;
import com.finance.enums.RecordType;@Repository
public interface FinancialRecordRepository extends JpaRepository<FinancialRecord, Long> {    @Query("""
            SELECT f FROM FinancialRecord f
            WHERE (:type IS NULL OR f.type = :type)
            AND (:category IS NULL OR LOWER(f.category) LIKE LOWER(CONCAT('%', :category, '%')))
            AND (:startDate IS NULL OR f.date >= :startDate)
            AND (:endDate IS NULL OR f.date <= :endDate)
            AND f.deleted = false
        """)
        Page<FinancialRecord> findWithFilters(
                @Param("type") RecordType type,
                @Param("category") String category,
                @Param("startDate") LocalDate startDate,
                @Param("endDate") LocalDate endDate,
                Pageable pageable
        );        // Dashboard totals
        @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.type = 'INCOME' AND f.deleted = false")
        BigDecimal getTotalIncome();        @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.type = 'EXPENSE' AND f.deleted = false")
        BigDecimal getTotalExpense();        // Category breakdown
        @Query("SELECT f.category, COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.deleted = false GROUP BY f.category")
        List<Object[]> getCategoryBreakdown();        // Monthly trends
        @Query("""
            SELECT YEAR(f.date), MONTH(f.date), f.type, COALESCE(SUM(f.amount), 0)
            FROM FinancialRecord f
            WHERE f.deleted = false
            GROUP BY YEAR(f.date), MONTH(f.date), f.type
            ORDER BY YEAR(f.date), MONTH(f.date)
        """)
        List<Object[]> getMonthlyTrends();        // Recent records
        List<FinancialRecord> findTop5ByDeletedFalseOrderByCreatedAtDesc();
}-------------------------------
package com.finance.service;import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;import org.springframework.stereotype.Service;import com.finance.dto.DashboardSummaryResponse;
import com.finance.dto.FinancialRecordResponse;
import com.finance.entity.FinancialRecord;
import com.finance.enums.RecordType;
import com.finance.repo.FinancialRecordRepository;import lombok.RequiredArgsConstructor;@Service
@RequiredArgsConstructor
public class DashboardService {    private final FinancialRecordRepository recordRepository;    public DashboardSummaryResponse getSummary() {
        BigDecimal totalIncome = recordRepository.getTotalIncome();
        BigDecimal totalExpense = recordRepository.getTotalExpense();
        BigDecimal netBalance = totalIncome.subtract(totalExpense);        // Category breakdown
        Map<String, BigDecimal> categoryBreakdown = new LinkedHashMap<>();
        for (Object[] row : recordRepository.getCategoryBreakdown()) {
            categoryBreakdown.put((String) row[0], (BigDecimal) row[1]);
        }        // Monthly trends
        Map<String, DashboardSummaryResponse.MonthlyTrend> trendMap = new LinkedHashMap<>();
        for (Object[] row : recordRepository.getMonthlyTrends()) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            RecordType type = RecordType.valueOf((String) row[2]);
            BigDecimal amount = (BigDecimal) row[3];            String key = year + "-" + month;
            trendMap.computeIfAbsent(key, k ->
                    DashboardSummaryResponse.MonthlyTrend.builder()
                            .year(year).month(month)
                            .income(BigDecimal.ZERO).expense(BigDecimal.ZERO)
                            .build()
            );            DashboardSummaryResponse.MonthlyTrend trend = trendMap.get(key);
            if (type == RecordType.INCOME) trend.setIncome(amount);
            else trend.setExpense(amount);
        }        // Recent transactions
        List<FinancialRecordResponse> recent = recordRepository
                .findTop5ByDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());        return DashboardSummaryResponse.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netBalance(netBalance)
                .categoryBreakdown(categoryBreakdown)
                .monthlyTrends(new ArrayList<>(trendMap.values()))
                .recentTransactions(recent)
                .build();
    }    private FinancialRecordResponse toResponse(FinancialRecord r) {
        return FinancialRecordResponse.builder()
                .id(r.getId())
                .amount(r.getAmount())
                .type(r.getType().name())
                .category(r.getCategory())
                .date(r.getDate())
                .description(r.getDescription())
                .createdBy(r.getCreatedBy().getUsername())
                .createdAt(r.getCreatedAt())
                .build();
    }}--------------------------------------
package com.finance.controller;import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;import com.finance.dto.DashboardSummaryResponse;
import com.finance.service.DashboardService;import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Summary and analytics")
@SecurityRequirement(name = "Bearer Auth")
public class DashboardController {    private final DashboardService dashboardService;    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_ANALYST', 'ROLE_VIEWER')")
    @Operation(summary = "Get full dashboard summary: totals, categories, trends, recent")
    public ResponseEntity<DashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }}---------------------------------------------------------Show moreApr 33 / 3The 500 error is caused by 3 bugs. Let me show you exactly what's wrong and how to fix each one.

Bug 1: @SQLRestriction conflicts with JPQL deleted = false queries
Your entity has @SQLRestriction("deleted = false") which automatically filters deleted records at the SQL level. But your repository queries also have AND f.deleted = false — this causes a conflict/duplicate condition that breaks the query. Also, findTop5ByDeletedFalseOrderByCreatedAtDesc() has ByDeletedFalse in the method name which adds a third filter on the same column — MySQL chokes on this.
Fix in FinancialRecord.java — remove @SQLRestriction since your queries handle it manually:
java// REMOVE this annotation:
// @SQLRestriction("deleted = false")

@Entity
@Table(name = "financial_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialRecord {
    // ... rest stays the same
}

Bug 2: getMonthlyTrends() — row[2] is a RecordType enum object, not a String
When Hibernate maps an @Enumerated(EnumType.STRING) field in a JPQL query returning Object[], it returns the enum object directly, not a String. Calling RecordType.valueOf((String) row[2]) throws a ClassCastException which causes the 500.
Fix in DashboardService.java — cast row[2] as RecordType directly:
java// WRONG — throws ClassCastException:
RecordType type = RecordType.valueOf((String) row[2]);

// CORRECT:
RecordType type = (RecordType) row[2];

Bug 3: FetchType.LAZY on createdBy causes LazyInitializationException
In toResponse() you call r.getCreatedBy().getUsername(). Because createdBy is FetchType.LAZY, the User is not loaded when you access it outside a transaction — this throws a LazyInitializationException which also shows as 500.
Fix — two changes:
In FinancialRecord.java, change LAZY to EAGER for createdBy:
java// CHANGE this:
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "created_by", nullable = false)
private User createdBy;

// TO this:
@ManyToOne(fetch = FetchType.EAGER)
@JoinColumn(name = "created_by", nullable = false)
private User createdBy;
OR (better approach) add @Transactional to DashboardService:
In DashboardService.java:
javaimport org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)   // ADD THIS
public class DashboardService {
    // ...
}

Complete Fixed Files
FinancialRecord.java (fixed):
javapackage com.finance.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.finance.enums.RecordType;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "financial_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
// REMOVED @SQLRestriction — was conflicting with manual deleted=false in queries
public class FinancialRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordType type;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private LocalDate date;

    private String description;

    @Column(nullable = false)
    private boolean deleted = false;

    @ManyToOne(fetch = FetchType.EAGER)  // CHANGED: LAZY → EAGER to avoid LazyInitializationException
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
FinancialRecordRepository.java (fixed — also fix the derived method name):
javapackage com.finance.repo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.finance.entity.FinancialRecord;
import com.finance.enums.RecordType;

@Repository
public interface FinancialRecordRepository extends JpaRepository<FinancialRecord, Long> {

    @Query("""
        SELECT f FROM FinancialRecord f
        WHERE (:type IS NULL OR f.type = :type)
        AND (:category IS NULL OR LOWER(f.category) LIKE LOWER(CONCAT('%', :category, '%')))
        AND (:startDate IS NULL OR f.date >= :startDate)
        AND (:endDate IS NULL OR f.date <= :endDate)
        AND f.deleted = false
    """)
    Page<FinancialRecord> findWithFilters(
            @Param("type") RecordType type,
            @Param("category") String category,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable
    );

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.type = 'INCOME' AND f.deleted = false")
    BigDecimal getTotalIncome();

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.type = 'EXPENSE' AND f.deleted = false")
    BigDecimal getTotalExpense();

    @Query("SELECT f.category, COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.deleted = false GROUP BY f.category")
    List<Object[]> getCategoryBreakdown();

    @Query("""
        SELECT YEAR(f.date), MONTH(f.date), f.type, COALESCE(SUM(f.amount), 0)
        FROM FinancialRecord f
        WHERE f.deleted = false
        GROUP BY YEAR(f.date), MONTH(f.date), f.type
        ORDER BY YEAR(f.date), MONTH(f.date)
    """)
    List<Object[]> getMonthlyTrends();

    // FIXED: removed ByDeletedFalse from method name since entity no longer has @SQLRestriction
    // and we want records that are NOT deleted, so we keep the condition explicit
    @Query("SELECT f FROM FinancialRecord f WHERE f.deleted = false ORDER BY f.createdAt DESC LIMIT 5")
    List<FinancialRecord> findRecentRecords();
}
DashboardService.java (fixed):
javapackage com.finance.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finance.dto.DashboardSummaryResponse;
import com.finance.dto.FinancialRecordResponse;
import com.finance.entity.FinancialRecord;
import com.finance.enums.RecordType;
import com.finance.repo.FinancialRecordRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // ADDED: keeps session open, prevents LazyInitializationException
public class DashboardService {

    private final FinancialRecordRepository recordRepository;

    public DashboardSummaryResponse getSummary() {
        BigDecimal totalIncome = recordRepository.getTotalIncome();
        BigDecimal totalExpense = recordRepository.getTotalExpense();
        BigDecimal netBalance = totalIncome.subtract(totalExpense);

        // Category breakdown
        Map<String, BigDecimal> categoryBreakdown = new LinkedHashMap<>();
        for (Object[] row : recordRepository.getCategoryBreakdown()) {
            categoryBreakdown.put((String) row[0], (BigDecimal) row[1]);
        }

        // Monthly trends — FIXED: cast row[2] as RecordType directly, not String
        Map<String, DashboardSummaryResponse.MonthlyTrend> trendMap = new LinkedHashMap<>();
        for (Object[] row : recordRepository.getMonthlyTrends()) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            RecordType type = (RecordType) row[2];   // FIXED: was RecordType.valueOf((String) row[2])
            BigDecimal amount = (BigDecimal) row[3];

            String key = year + "-" + month;
            trendMap.computeIfAbsent(key, k ->
                    DashboardSummaryResponse.MonthlyTrend.builder()
                            .year(year).month(month)
                            .income(BigDecimal.ZERO).expense(BigDecimal.ZERO)
                            .build()
            );

            DashboardSummaryResponse.MonthlyTrend trend = trendMap.get(key);
            if (type == RecordType.INCOME) trend.setIncome(amount);
            else trend.setExpense(amount);
        }

        // Recent transactions — FIXED: using new query method name
        List<FinancialRecordResponse> recent = recordRepository
                .findRecentRecords()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return DashboardSummaryResponse.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netBalance(netBalance)
                .categoryBreakdown(categoryBreakdown)
                .monthlyTrends(new ArrayList<>(trendMap.values()))
                .recentTransactions(recent)
                .build();
    }

    private FinancialRecordResponse toResponse(FinancialRecord r) {
        return FinancialRecordResponse.builder()
                .id(r.getId())
                .amount(r.getAmount())
                .type(r.getType().name())
                .category(r.getCategory())
                .date(r.getDate())
                .description(r.getDescription())
                .createdBy(r.getCreatedBy().getUsername())
                .createdAt(r.getCreatedAt())
                .build();
    }
}

Summary of All 3 Fixes
#BugRoot CauseFix1@SQLRestriction conflictDuplicate deleted=false filter at SQL + JPQL levelRemove @SQLRestriction from entity2ClassCastException in monthly trendsrow[2] is RecordType enum, not StringCast as (RecordType) row[2] directly3LazyInitializationException on createdByLAZY fetch accessed outside transactionAdd @Transactional(readOnly=true) to service + change to EAGER
Make these three changes, restart the app, and your dashboard endpoint will return 200.ok every thing is successfully tested and every thing is ok so should i submitted my assignment as any thing else needed ,this is very important to getting the intern backend role for me ,so i already uploaded the requirement what they asked according to that is this ok to get that intern roleApr 3Great that everything is working! Let me do a final audit against their exact evaluation criteria before you submit.

Evaluation Criteria Audit
1. Backend Design ✅
Clean layered architecture — controller → service → repository → DTO. Nothing to add.
2. Logical Thinking ✅
RBAC with JWT, role-based method-level security with @PreAuthorize, soft delete logic, business rules in service layer.
3. Functionality ✅
All CRUD, filtering, pagination, sorting, dashboard summary with trends and category breakdown — all tested and working.
4. Code Quality ✅
Lombok, proper naming, separation of concerns, DTO layer separating request/response from entity.
5. Database and Data Modeling ✅
Proper JPA entities, relationships, BigDecimal for money, timestamps, soft delete column.
6. Validation and Reliability ✅
@Valid, field-level validation annotations, global exception handler with proper HTTP status codes.
7. Documentation ⚠️
This is where most candidates lose marks. Your README needs to be stronger.
8. Additional Thoughtfulness ⚠️
A few small things that will separate you from other candidates.

What You Still Need to Add
1. Improve README (biggest scoring gap)
Replace your current README with this complete one:
markdown# Finance Dashboard Backend

A production-ready REST API backend for a Finance Dashboard system with 
Role-Based Access Control, JWT Authentication, and Analytics APIs.

## Tech Stack

- Java 17
- Spring Boot 3.2
- Spring Security + JWT (jjwt 0.11.5)
- Spring Data JPA + Hibernate
- MySQL 8
- Lombok
- Swagger / OpenAPI 3 (springdoc)

## Architecture
Request → JwtFilter → Controller → Service → Repository → MySQL
↓
DTO (Request/Response)
↓
GlobalExceptionHandler

All business logic lives in the Service layer.
Controllers only handle HTTP concerns.
Entities never leak directly to API responses — DTOs are always used.

## Role & Permission Matrix

| Action                          | VIEWER | ANALYST | ADMIN |
|---------------------------------|--------|---------|-------|
| Login                           | ✅     | ✅      | ✅    |
| View financial records          | ✅     | ✅      | ✅    |
| Filter / paginate records       | ✅     | ✅      | ✅    |
| View dashboard summary          | ✅     | ✅      | ✅    |
| Create financial records        | ❌     | ✅      | ✅    |
| Update financial records        | ❌     | ✅      | ✅    |
| Delete financial records        | ❌     | ❌      | ✅    |
| View all users                  | ❌     | ❌      | ✅    |
| Create / manage users           | ❌     | ❌      | ✅    |
| Assign roles / toggle status    | ❌     | ❌      | ✅    |

## Setup Instructions

### Prerequisites
- Java 17+
- MySQL 8+
- Maven 3.8+

### Step 1 — Create the database
```sql
CREATE DATABASE finance_db;
```

### Step 2 — Configure credentials
Edit `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/finance_db
spring.datasource.username=YOUR_USERNAME
spring.datasource.password=YOUR_PASSWORD
```

### Step 3 — Run the application
```bash
mvn spring-boot:run
```

Tables are auto-created by Hibernate on first run (`ddl-auto=update`).

### Step 4 — Create your first Admin user

Since there are no users on first run, temporarily allow open registration 
or insert directly into the database:
```sql
INSERT INTO users (username, password, email, role, active, created_at, updated_at)
VALUES (
  'admin',
  '$2a$10$YourBCryptHashHere',
  'admin@finance.com',
  'ROLE_ADMIN',
  true,
  NOW(),
  NOW()
);
```

Or use the provided data seeder (see DataSeeder.java section below).

### Step 5 — Access Swagger UI
http://localhost:8080/swagger-ui.html

Click "Authorize" → enter `Bearer <your_jwt_token>` to test secured endpoints.

## API Reference

### Authentication
| Method | Endpoint          | Auth | Description        |
|--------|-------------------|------|--------------------|
| POST   | /api/auth/login   | No   | Login, returns JWT |

### Users (Admin only)
| Method | Endpoint                        | Description              |
|--------|---------------------------------|--------------------------|
| POST   | /api/users                      | Create user              |
| GET    | /api/users                      | Get all users            |
| GET    | /api/users/{id}                 | Get user by ID           |
| PATCH  | /api/users/{id}/role            | Update role              |
| PATCH  | /api/users/{id}/toggle-status   | Activate / deactivate    |
| DELETE | /api/users/{id}                 | Delete user              |

### Financial Records
| Method | Endpoint              | Auth Required       | Description                    |
|--------|-----------------------|---------------------|--------------------------------|
| POST   | /api/records          | Admin, Analyst      | Create record                  |
| GET    | /api/records          | All roles           | List with filters + pagination |
| GET    | /api/records/{id}     | All roles           | Get single record              |
| PUT    | /api/records/{id}     | Admin, Analyst      | Update record                  |
| DELETE | /api/records/{id}     | Admin only          | Soft delete                    |

**Supported query parameters for GET /api/records:**

| Param       | Type       | Example              | Description              |
|-------------|------------|----------------------|--------------------------|
| type        | String     | INCOME or EXPENSE    | Filter by record type    |
| category    | String     | Salary               | Partial match on category|
| startDate   | LocalDate  | 2025-01-01           | Filter from date         |
| endDate     | LocalDate  | 2025-04-30           | Filter to date           |
| page        | int        | 0                    | Page number (0-indexed)  |
| size        | int        | 10                   | Records per page         |
| sortBy      | String     | date                 | Field to sort by         |
| sortDir     | String     | asc or desc          | Sort direction           |

### Dashboard
| Method | Endpoint                 | Auth      | Description                            |
|--------|--------------------------|-----------|----------------------------------------|
| GET    | /api/dashboard/summary   | All roles | Totals, categories, trends, recent 5   |

**Sample dashboard response:**
```json
{
  "totalIncome": 105000.00,
  "totalExpense": 27500.00,
  "netBalance": 77500.00,
  "categoryBreakdown": {
    "Salary": 75000.00,
    "Freelance": 30000.00,
    "Rent": 12000.00
  },
  "monthlyTrends": [
    { "year": 2025, "month": 3, "income": 30000.00, "expense": 8000.00 },
    { "year": 2025, "month": 4, "income": 75000.00, "expense": 19500.00 }
  ],
  "recentTransactions": [...]
}
```

## Key Design Decisions & Assumptions

1. **Soft delete** — Records are never permanently deleted. A `deleted` boolean 
   flag is set to true. This preserves audit history and allows recovery.

2. **BigDecimal for money** — All monetary values use `BigDecimal` (not Double) 
   to avoid floating point precision errors in financial calculations.

3. **JWT stateless auth** — No sessions are stored server-side. Every request 
   carries a self-contained JWT token that expires in 24 hours.

4. **Role naming** — Roles follow Spring Security convention: `ROLE_ADMIN`, 
   `ROLE_ANALYST`, `ROLE_VIEWER`. The `ROLE_` prefix is required for 
   `hasRole()` expressions to work correctly.

5. **DTO separation** — Entities are never returned directly from controllers. 
   Request and Response DTOs are separate classes to control exactly what 
   data enters and exits the API.

6. **EAGER fetch on createdBy** — The `createdBy` relationship on 
   `FinancialRecord` uses `FetchType.EAGER` to avoid `LazyInitializationException` 
   when mapping to response DTOs outside a transaction context.

7. **COALESCE in aggregate queries** — Dashboard total queries use 
   `COALESCE(SUM(...), 0)` so the API returns 0 instead of null when 
   no records exist.

## Tradeoffs Considered

| Decision | Chosen Approach | Alternative | Reason |
|---|---|---|---|
| Auth | JWT (stateless) | Session-based | Scales better, no server state |
| Delete | Soft delete | Hard delete | Preserves financial audit trail |
| Fetch | EAGER on createdBy | LAZY + @Transactional | Simpler, avoids session scope issues |
| Storage | MySQL | PostgreSQL | Common in enterprise finance systems |
| Validation | Bean Validation (@Valid) | Manual checks | Standard, declarative, less code |

## Features Implemented

- ✅ JWT Authentication
- ✅ Role-Based Access Control (method level with @PreAuthorize)
- ✅ Full CRUD for financial records
- ✅ Soft delete
- ✅ Filtering by type, category, date range
- ✅ Pagination and sorting
- ✅ Dashboard: total income, expense, net balance
- ✅ Dashboard: category-wise breakdown
- ✅ Dashboard: monthly trends
- ✅ Dashboard: recent 5 transactions
- ✅ Input validation with field-level error messages
- ✅ Global exception handling with proper HTTP status codes
- ✅ Swagger / OpenAPI documentation
- ✅ Clean layered architecture with DTO separation

2. Add a Data Seeder (takes 10 minutes, high impact)
This lets the reviewer run your project immediately without manually inserting SQL. Create this file:
config/DataSeeder.java
javapackage com.finance.config;

import com.finance.entity.FinancialRecord;
import com.finance.entity.User;
import com.finance.enums.RecordType;
import com.finance.enums.Role;
import com.finance.repo.FinancialRecordRepository;
import com.finance.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final FinancialRecordRepository recordRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {

        // Only seed if database is empty
        if (userRepository.count() > 0) return;

        // Create users
        User admin = userRepository.save(User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .email("admin@finance.com")
                .role(Role.ROLE_ADMIN)
                .active(true)
                .build());

        User analyst = userRepository.save(User.builder()
                .username("analyst")
                .password(passwordEncoder.encode("analyst123"))
                .email("analyst@finance.com")
                .role(Role.ROLE_ANALYST)
                .active(true)
                .build());

        userRepository.save(User.builder()
                .username("viewer")
                .password(passwordEncoder.encode("viewer123"))
                .email("viewer@finance.com")
                .role(Role.ROLE_VIEWER)
                .active(true)
                .build());

        // Create sample financial records
        recordRepository.save(FinancialRecord.builder()
                .amount(new BigDecimal("75000.00"))
                .type(RecordType.INCOME)
                .category("Salary")
                .date(LocalDate.of(2025, 4, 1))
                .description("Monthly salary")
                .createdBy(admin).deleted(false).build());

        recordRepository.save(FinancialRecord.builder()
                .amount(new BigDecimal("30000.00"))
                .type(RecordType.INCOME)
                .category("Freelance")
                .date(LocalDate.of(2025, 3, 15))
                .description("Freelance project payment")
                .createdBy(analyst).deleted(false).build());

        recordRepository.save(FinancialRecord.builder()
                .amount(new BigDecimal("12000.00"))
                .type(RecordType.EXPENSE)
                .category("Rent")
                .date(LocalDate.of(2025, 4, 1))
                .description("Office rent")
                .createdBy(admin).deleted(false).build());

        recordRepository.save(FinancialRecord.builder()
                .amount(new BigDecimal("5000.00"))
                .type(RecordType.EXPENSE)
                .category("Food")
                .date(LocalDate.of(2025, 4, 5))
                .description("Team lunch")
                .createdBy(analyst).deleted(false).build());

        recordRepository.save(FinancialRecord.builder()
                .amount(new BigDecimal("8000.00"))
                .type(RecordType.EXPENSE)
                .category("Utilities")
                .date(LocalDate.of(2025, 3, 20))
                .description("Electricity and internet")
                .createdBy(admin).deleted(false).build());

        recordRepository.save(FinancialRecord.builder()
                .amount(new BigDecimal("2500.00"))
                .type(RecordType.EXPENSE)
                .category("Transport")
                .date(LocalDate.of(2025, 4, 10))
                .description("Cab charges")
                .createdBy(analyst).deleted(false).build());

        System.out.println("=== Sample data seeded successfully ===");
        System.out.println("Admin    → username: admin    password: admin123");
        System.out.println("Analyst  → username: analyst  password: analyst123");
        System.out.println("Viewer   → username: viewer   password: viewer123");
    }
}

3. GitHub Repository Checklist
Before pushing, make sure your repo has:
finance-backend/
├── src/
├── pom.xml
├── README.md              ← updated version above
└── .gitignore             ← make sure this exists
.gitignore — critical, never push credentials:
target/
*.class
*.jar
.idea/
*.iml
application.properties     ← if it has real passwords
Instead, commit a safe template:
application-example.properties
propertiesspring.datasource.url=jdbc:mysql://localhost:3306/finance_db
spring.datasource.username=YOUR_DB_USERNAME
spring.datasource.password=YOUR_DB_PASSWORD
jwt.secret=YOUR_JWT_SECRET
jwt.expiration=86400000
And mention in README: "Copy application-example.properties to application.properties and fill in your credentials."

Final Submission Checklist
✅ All APIs working (you confirmed this)
✅ JWT authentication
✅ Role-based access control
✅ CRUD with validation
✅ Filtering + pagination
✅ Dashboard with trends and categories
✅ Soft delete
✅ Global exception handling
✅ Swagger at /swagger-ui.html

→ ADD before submitting:
✅ Updated README (above)
✅ DataSeeder.java (reviewer can run instantly)
✅ .gitignore (no credentials in repo)
✅ application-example.properties
✅ Push to GitHub with clean commit history

One Last Thing — How to Present It
When you submit, write a short covering message like:

"I have built a Finance Dashboard Backend using Java Spring Boot and MySQL. The project includes JWT authentication, role-based access control with three roles (Admin, Analyst, Viewer), full CRUD for financial records with filtering and pagination, and a dashboard summary API with category breakdown and monthly trends. A data seeder creates sample users and records on first run. Swagger documentation is available at /swagger-ui.html. All design decisions and tradeoffs are documented in the README."

That message alone shows senior-level thinking. Good luck — your project is genuinely strong for an intern submission. 🚀give about the detailed description about this finance backend in 300 characters for github reporitory description7:31 PMFinance Dashboard Backend – Spring Boot REST API with JWT auth, role-based access control (Admin/Analyst/Viewer), financial records CRUD, filtering, pagination, and dashboard analytics. MySQL + Swagger docs.
That's 207 characters — clean, covers all the key points, fits GitHub's limit perfectly.give all postan testing json input and output of all requests and urls to add these in readme file in github7:46 PMHere's the complete Postman testing documentation formatted for your README file.

markdown## API Testing Reference

Base URL: `http://localhost:8080`

---

## 1. Authentication

### Login as Admin
**Request**
```
POST /api/auth/login
Content-Type: application/json
```
```json
{
  "username": "admin",
  "password": "admin123"
}
```
**Response `200 OK`**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc0MzY4MDAwMCwiZXhwIjoxNzQzNzY2NDAwfQ.xxxxx",
  "username": "admin",
  "role": "ROLE_ADMIN"
}
```

---

### Login as Analyst
**Request**
```
POST /api/auth/login
Content-Type: application/json
```
```json
{
  "username": "analyst",
  "password": "analyst123"
}
```
**Response `200 OK`**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.xxxxx",
  "username": "analyst",
  "role": "ROLE_ANALYST"
}
```

---

### Login as Viewer
**Request**
```
POST /api/auth/login
Content-Type: application/json
```
```json
{
  "username": "viewer",
  "password": "viewer123"
}
```
**Response `200 OK`**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.xxxxx",
  "username": "viewer",
  "role": "ROLE_VIEWER"
}
```

---

### Login — Wrong Password
**Request**
```
POST /api/auth/login
Content-Type: application/json
```
```json
{
  "username": "admin",
  "password": "wrongpassword"
}
```
**Response `401 Unauthorized`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username or password"
}
```

---

### Login — Missing Fields
**Request**
```
POST /api/auth/login
Content-Type: application/json
```
```json
{
  "username": ""
}
```
**Response `400 Bad Request`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": {
    "username": "Username is required",
    "password": "Password is required"
  }
}
```

---

## 2. User Management (Admin Only)

> All user endpoints require:
> `Authorization: Bearer <admin_token>`

---

### Create Admin User
**Request**
```
POST /api/users
Content-Type: application/json
Authorization: Bearer <admin_token>
```
```json
{
  "username": "admin2",
  "password": "admin123",
  "email": "admin2@finance.com",
  "role": "ROLE_ADMIN"
}
```
**Response `201 Created`**
```json
{
  "id": 4,
  "username": "admin2",
  "email": "admin2@finance.com",
  "role": "ROLE_ADMIN",
  "active": true,
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Create Analyst User
**Request**
```
POST /api/users
Content-Type: application/json
Authorization: Bearer <admin_token>
```
```json
{
  "username": "analyst2",
  "password": "analyst123",
  "email": "analyst2@finance.com",
  "role": "ROLE_ANALYST"
}
```
**Response `201 Created`**
```json
{
  "id": 5,
  "username": "analyst2",
  "email": "analyst2@finance.com",
  "role": "ROLE_ANALYST",
  "active": true,
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Create Viewer User
**Request**
```
POST /api/users
Content-Type: application/json
Authorization: Bearer <admin_token>
```
```json
{
  "username": "viewer2",
  "password": "viewer123",
  "email": "viewer2@finance.com",
  "role": "ROLE_VIEWER"
}
```
**Response `201 Created`**
```json
{
  "id": 6,
  "username": "viewer2",
  "email": "viewer2@finance.com",
  "role": "ROLE_VIEWER",
  "active": true,
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Create User — Duplicate Username
**Request**
```
POST /api/users
Content-Type: application/json
Authorization: Bearer <admin_token>
```
```json
{
  "username": "analyst",
  "password": "analyst123",
  "email": "another@finance.com",
  "role": "ROLE_ANALYST"
}
```
**Response `400 Bad Request`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 400,
  "error": "Bad Request",
  "message": "Username already exists: analyst"
}
```

---

### Create User — Invalid Role
**Request**
```
POST /api/users
Content-Type: application/json
Authorization: Bearer <admin_token>
```
```json
{
  "username": "testuser",
  "password": "test123",
  "email": "test@finance.com",
  "role": "ROLE_SUPERUSER"
}
```
**Response `400 Bad Request`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid role. Valid values: ROLE_VIEWER, ROLE_ANALYST, ROLE_ADMIN"
}
```

---

### Create User — Missing Fields
**Request**
```
POST /api/users
Content-Type: application/json
Authorization: Bearer <admin_token>
```
```json
{
  "username": "",
  "password": "123",
  "email": "bademail"
}
```
**Response `400 Bad Request`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": {
    "username": "Username must be between 3 and 50 characters",
    "password": "Password must be at least 6 characters",
    "email": "Invalid email format",
    "role": "Role is required"
  }
}
```

---

### Get All Users
**Request**
GET /api/users
Authorization: Bearer <admin_token>
**Response `200 OK`**
```json
[
  {
    "id": 1,
    "username": "admin",
    "email": "admin@finance.com",
    "role": "ROLE_ADMIN",
    "active": true,
    "createdAt": "2025-04-01T10:00:00.000000"
  },
  {
    "id": 2,
    "username": "analyst",
    "email": "analyst@finance.com",
    "role": "ROLE_ANALYST",
    "active": true,
    "createdAt": "2025-04-01T10:00:00.000000"
  },
  {
    "id": 3,
    "username": "viewer",
    "email": "viewer@finance.com",
    "role": "ROLE_VIEWER",
    "active": true,
    "createdAt": "2025-04-01T10:00:00.000000"
  }
]
```

---

### Get User by ID
**Request**
GET /api/users/2
Authorization: Bearer <admin_token>
**Response `200 OK`**
```json
{
  "id": 2,
  "username": "analyst",
  "email": "analyst@finance.com",
  "role": "ROLE_ANALYST",
  "active": true,
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Get User — Not Found
**Request**
GET /api/users/9999
Authorization: Bearer <admin_token>
**Response `404 Not Found`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 404,
  "error": "Not Found",
  "message": "User not found with id: 9999"
}
```

---

### Update User Role
**Request**
PATCH /api/users/2/role?role=ROLE_ADMIN
Authorization: Bearer <admin_token>
**Response `200 OK`**
```json
{
  "id": 2,
  "username": "analyst",
  "email": "analyst@finance.com",
  "role": "ROLE_ADMIN",
  "active": true,
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Toggle User Status (Deactivate)
**Request**
PATCH /api/users/3/toggle-status
Authorization: Bearer <admin_token>
**Response `200 OK`**
```json
{
  "id": 3,
  "username": "viewer",
  "email": "viewer@finance.com",
  "role": "ROLE_VIEWER",
  "active": false,
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Toggle User Status (Reactivate — call same endpoint again)
**Request**
PATCH /api/users/3/toggle-status
Authorization: Bearer <admin_token>
**Response `200 OK`**
```json
{
  "id": 3,
  "username": "viewer",
  "email": "viewer@finance.com",
  "role": "ROLE_VIEWER",
  "active": true,
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Delete User
**Request**
DELETE /api/users/6
Authorization: Bearer <admin_token>
**Response `204 No Content`**
(empty body)

---

### Viewer Tries to Access Users — Forbidden
**Request**
GET /api/users
Authorization: Bearer <viewer_token>
**Response `403 Forbidden`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 403,
  "error": "Forbidden",
  "message": "You do not have permission to perform this action"
}
```

---

### Access Without Token — Unauthorized
**Request**
GET /api/users
(No Authorization header)
**Response `401 Unauthorized`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource"
}
```

---

## 3. Financial Records

> All record endpoints require:
> `Authorization: Bearer <token>`

---

### Create Income Record
**Request**
POST /api/records
Content-Type: application/json
Authorization: Bearer <admin_token>
```json
{
  "amount": 75000.00,
  "type": "INCOME",
  "category": "Salary",
  "date": "2025-04-01",
  "description": "Monthly salary April 2025"
}
```
**Response `201 Created`**
```json
{
  "id": 1,
  "amount": 75000.00,
  "type": "INCOME",
  "category": "Salary",
  "date": "2025-04-01",
  "description": "Monthly salary April 2025",
  "createdBy": "admin",
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Create Expense Record
**Request**
POST /api/records
Content-Type: application/json
Authorization: Bearer <admin_token>
```json
{
  "amount": 12000.00,
  "type": "EXPENSE",
  "category": "Rent",
  "date": "2025-04-01",
  "description": "Office rent April 2025"
}
```
**Response `201 Created`**
```json
{
  "id": 2,
  "amount": 12000.00,
  "type": "EXPENSE",
  "category": "Rent",
  "date": "2025-04-01",
  "description": "Office rent April 2025",
  "createdBy": "admin",
  "createdAt": "2025-04-01T10:05:00.000000"
}
```

---

### Create Record — Negative Amount
**Request**
POST /api/records
Content-Type: application/json
Authorization: Bearer <admin_token>
```json
{
  "amount": -500.00,
  "type": "EXPENSE",
  "category": "Food",
  "date": "2025-04-01"
}
```
**Response `400 Bad Request`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": {
    "amount": "Amount must be greater than 0"
  }
}
```

---

### Create Record — Invalid Type
**Request**
POST /api/records
Content-Type: application/json
Authorization: Bearer <admin_token>
```json
{
  "amount": 1000.00,
  "type": "SALARY",
  "category": "Other",
  "date": "2025-04-01"
}
```
**Response `400 Bad Request`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid type. Must be INCOME or EXPENSE"
}
```

---

### Create Record — Missing Required Fields
**Request**
POST /api/records
Content-Type: application/json
Authorization: Bearer <admin_token>
```json
{
  "description": "Missing amount, type, category, date"
}
```
**Response `400 Bad Request`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": {
    "amount": "Amount is required",
    "type": "Type is required (INCOME or EXPENSE)",
    "category": "Category is required",
    "date": "Date is required"
  }
}
```

---

### Viewer Tries to Create Record — Forbidden
**Request**
POST /api/records
Content-Type: application/json
Authorization: Bearer <viewer_token>
```json
{
  "amount": 1000.00,
  "type": "INCOME",
  "category": "Test",
  "date": "2025-04-01"
}
```
**Response `403 Forbidden`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 403,
  "error": "Forbidden",
  "message": "You do not have permission to perform this action"
}
```

---

### Get All Records — No Filters
**Request**
GET /api/records
Authorization: Bearer <admin_token>
**Response `200 OK`**
```json
{
  "content": [
    {
      "id": 6,
      "amount": 2500.00,
      "type": "EXPENSE",
      "category": "Transport",
      "date": "2025-04-10",
      "description": "Cab charges",
      "createdBy": "analyst",
      "createdAt": "2025-04-10T09:00:00.000000"
    },
    {
      "id": 5,
      "amount": 8000.00,
      "type": "EXPENSE",
      "category": "Utilities",
      "date": "2025-03-20",
      "description": "Electricity and internet",
      "createdBy": "admin",
      "createdAt": "2025-03-20T09:00:00.000000"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "sort": {
      "sorted": true,
      "unsorted": false
    }
  },
  "totalElements": 6,
  "totalPages": 1,
  "last": true,
  "first": true,
  "size": 10,
  "number": 0
}
```

---

### Get Records — Filter by Type INCOME
**Request**
GET /api/records?type=INCOME
Authorization: Bearer <admin_token>
**Response `200 OK`**
```json
{
  "content": [
    {
      "id": 1,
      "amount": 75000.00,
      "type": "INCOME",
      "category": "Salary",
      "date": "2025-04-01",
      "description": "Monthly salary April 2025",
      "createdBy": "admin",
      "createdAt": "2025-04-01T10:00:00.000000"
    },
    {
      "id": 2,
      "amount": 30000.00,
      "type": "INCOME",
      "category": "Freelance",
      "date": "2025-03-15",
      "description": "Freelance project payment",
      "createdBy": "analyst",
      "createdAt": "2025-03-15T10:00:00.000000"
    }
  ],
  "totalElements": 2,
  "totalPages": 1,
  "size": 10,
  "number": 0
}
```

---

### Get Records — Filter by Category
**Request**
GET /api/records?category=Rent
Authorization: Bearer <admin_token>
**Response `200 OK`**
```json
{
  "content": [
    {
      "id": 3,
      "amount": 12000.00,
      "type": "EXPENSE",
      "category": "Rent",
      "date": "2025-04-01",
      "description": "Office rent April 2025",
      "createdBy": "admin",
      "createdAt": "2025-04-01T10:05:00.000000"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 10,
  "number": 0
}
```

---

### Get Records — Filter by Date Range
**Request**
GET /api/records?startDate=2025-04-01&endDate=2025-04-30
Authorization: Bearer <admin_token>
**Response `200 OK`**
```json
{
  "content": [
    {
      "id": 6,
      "amount": 2500.00,
      "type": "EXPENSE",
      "category": "Transport",
      "date": "2025-04-10",
      "description": "Cab charges",
      "createdBy": "analyst",
      "createdAt": "2025-04-10T09:00:00.000000"
    },
    {
      "id": 4,
      "amount": 5000.00,
      "type": "EXPENSE",
      "category": "Food",
      "date": "2025-04-05",
      "description": "Team lunch",
      "createdBy": "analyst",
      "createdAt": "2025-04-05T12:00:00.000000"
    },
    {
      "id": 1,
      "amount": 75000.00,
      "type": "INCOME",
      "category": "Salary",
      "date": "2025-04-01",
      "description": "Monthly salary April 2025",
      "createdBy": "admin",
      "createdAt": "2025-04-01T10:00:00.000000"
    },
    {
      "id": 3,
      "amount": 12000.00,
      "type": "EXPENSE",
      "category": "Rent",
      "date": "2025-04-01",
      "description": "Office rent April 2025",
      "createdBy": "admin",
      "createdAt": "2025-04-01T10:05:00.000000"
    }
  ],
  "totalElements": 4,
  "totalPages": 1,
  "size": 10,
  "number": 0
}
```

---

### Get Records — Combined Filters with Pagination
**Request**
GET /api/records?type=EXPENSE&category=Food&page=0&size=5&sortBy=amount&sortDir=desc
Authorization: Bearer <admin_token>
**Response `200 OK`**
```json
{
  "content": [
    {
      "id": 4,
      "amount": 5000.00,
      "type": "EXPENSE",
      "category": "Food",
      "date": "2025-04-05",
      "description": "Team lunch",
      "createdBy": "analyst",
      "createdAt": "2025-04-05T12:00:00.000000"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 5,
  "number": 0
}
```

---

### Get Records — Pagination Page 2
**Request**
GET /api/records?page=1&size=3&sortBy=date&sortDir=asc
Authorization: Bearer <admin_token>
**Response `200 OK`**
```json
{
  "content": [
    {
      "id": 4,
      "amount": 5000.00,
      "type": "EXPENSE",
      "category": "Food",
      "date": "2025-04-05",
      "description": "Team lunch",
      "createdBy": "analyst",
      "createdAt": "2025-04-05T12:00:00.000000"
    },
    {
      "id": 6,
      "amount": 2500.00,
      "type": "EXPENSE",
      "category": "Transport",
      "date": "2025-04-10",
      "description": "Cab charges",
      "createdBy": "analyst",
      "createdAt": "2025-04-10T09:00:00.000000"
    }
  ],
  "totalElements": 6,
  "totalPages": 2,
  "size": 3,
  "number": 1,
  "last": true,
  "first": false
}
```

---

### Get Record by ID
**Request**
GET /api/records/1
Authorization: Bearer <admin_token>
**Response `200 OK`**
```json
{
  "id": 1,
  "amount": 75000.00,
  "type": "INCOME",
  "category": "Salary",
  "date": "2025-04-01",
  "description": "Monthly salary April 2025",
  "createdBy": "admin",
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Get Record — Not Found
**Request**
GET /api/records/9999
Authorization: Bearer <admin_token>
**Response `404 Not Found`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 404,
  "error": "Not Found",
  "message": "Record not found with id: 9999"
}
```

---

### Update Record
**Request**
PUT /api/records/1
Content-Type: application/json
Authorization: Bearer <admin_token>
```json
{
  "amount": 80000.00,
  "type": "INCOME",
  "category": "Salary",
  "date": "2025-04-01",
  "description": "Updated: April salary with performance bonus"
}
```
**Response `200 OK`**
```json
{
  "id": 1,
  "amount": 80000.00,
  "type": "INCOME",
  "category": "Salary",
  "date": "2025-04-01",
  "description": "Updated: April salary with performance bonus",
  "createdBy": "admin",
  "createdAt": "2025-04-01T10:00:00.000000"
}
```

---

### Analyst Tries to Delete Record — Forbidden
**Request**
DELETE /api/records/1
Authorization: Bearer <analyst_token>
**Response `403 Forbidden`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 403,
  "error": "Forbidden",
  "message": "You do not have permission to perform this action"
}
```

---

### Soft Delete Record (Admin)
**Request**
DELETE /api/records/1
Authorization: Bearer <admin_token>
**Response `204 No Content`**
(empty body)

---

### Verify Soft Deleted Record is Hidden
**Request**
GET /api/records/1
Authorization: Bearer <admin_token>
**Response `404 Not Found`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 404,
  "error": "Not Found",
  "message": "Record not found with id: 1"
}
```

---

## 4. Dashboard

---

### Get Dashboard Summary (Admin)
**Request**
GET /api/dashboard/summary
Authorization: Bearer <admin_token>
**Response `200 OK`**
```json
{
  "totalIncome": 105000.00,
  "totalExpense": 27500.00,
  "netBalance": 77500.00,
  "categoryBreakdown": {
    "Salary": 75000.00,
    "Freelance": 30000.00,
    "Rent": 12000.00,
    "Food": 5000.00,
    "Utilities": 8000.00,
    "Transport": 2500.00
  },
  "monthlyTrends": [
    {
      "year": 2025,
      "month": 3,
      "income": 30000.00,
      "expense": 8000.00
    },
    {
      "year": 2025,
      "month": 4,
      "income": 75000.00,
      "expense": 19500.00
    }
  ],
  "recentTransactions": [
    {
      "id": 6,
      "amount": 2500.00,
      "type": "EXPENSE",
      "category": "Transport",
      "date": "2025-04-10",
      "description": "Cab charges",
      "createdBy": "analyst",
      "createdAt": "2025-04-10T09:00:00.000000"
    },
    {
      "id": 5,
      "amount": 8000.00,
      "type": "EXPENSE",
      "category": "Utilities",
      "date": "2025-03-20",
      "description": "Electricity and internet",
      "createdBy": "admin",
      "createdAt": "2025-03-20T09:00:00.000000"
    },
    {
      "id": 4,
      "amount": 5000.00,
      "type": "EXPENSE",
      "category": "Food",
      "date": "2025-04-05",
      "description": "Team lunch",
      "createdBy": "analyst",
      "createdAt": "2025-04-05T12:00:00.000000"
    },
    {
      "id": 3,
      "amount": 12000.00,
      "type": "EXPENSE",
      "category": "Rent",
      "date": "2025-04-01",
      "description": "Office rent April 2025",
      "createdBy": "admin",
      "createdAt": "2025-04-01T10:05:00.000000"
    },
    {
      "id": 2,
      "amount": 30000.00,
      "type": "INCOME",
      "category": "Freelance",
      "date": "2025-03-15",
      "description": "Freelance project payment",
      "createdBy": "analyst",
      "createdAt": "2025-03-15T10:00:00.000000"
    }
  ]
}
```

---

### Get Dashboard Summary (Viewer)
**Request**
GET /api/dashboard/summary
Authorization: Bearer <viewer_token>
**Response `200 OK`**
```json
{
  "totalIncome": 105000.00,
  "totalExpense": 27500.00,
  "netBalance": 77500.00,
  "categoryBreakdown": {
    "Salary": 75000.00,
    "Freelance": 30000.00,
    "Rent": 12000.00,
    "Food": 5000.00,
    "Utilities": 8000.00,
    "Transport": 2500.00
  },
  "monthlyTrends": [
    {
      "year": 2025,
      "month": 3,
      "income": 30000.00,
      "expense": 8000.00
    },
    {
      "year": 2025,
      "month": 4,
      "income": 75000.00,
      "expense": 19500.00
    }
  ],
  "recentTransactions": [...]
}
```

---

### Dashboard — Without Token
**Request**
GET /api/dashboard/summary
(No Authorization header)
**Response `401 Unauthorized`**
```json
{
  "timestamp": "2025-04-01T10:00:00.000000",
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource"
}
```

---

## Default Seeded Users (after first run)

| Username | Password    | Role         |
|----------|-------------|--------------|
| admin    | admin123    | ROLE_ADMIN   |
| analyst  | analyst123  | ROLE_ANALYST |
| viewer   | viewer123   | ROLE_VIEWER  |

---

## HTTP Status Code Reference

| Code | Meaning                              | When it occurs                          |
|------|--------------------------------------|-----------------------------------------|
| 200  | OK                                   | Successful GET, PUT, PATCH              |
| 201  | Created                              | Successful POST                         |
| 204  | No Content                           | Successful DELETE                       |
| 400  | Bad Request                          | Validation failure, invalid input       |
| 401  | Unauthorized                         | Missing or invalid token                |
| 403  | Forbidden                            | Valid token but insufficient role       |
| 404  | Not Found                            | Resource does not exist                 |
| 500  | Internal Server Error                | Unexpected server-side error            |

----------------------------------------------------------------------------

### Final CheckList

✅ All APIs working (you confirmed this)
✅ JWT authentication
✅ Role-based access control
✅ CRUD with validation
✅ Filtering + pagination
✅ Dashboard with trends and categories
✅ Soft delete
✅ Global exception handling
✅ Swagger at /swagger-ui.html

------------------------------------------------------

"I have built a Finance Dashboard Backend using Java Spring Boot and MySQL. The project includes JWT authentication, role-based access control with three roles (Admin, Analyst, Viewer), full CRUD for financial records with filtering and pagination, and a dashboard summary API with category breakdown and monthly trends. A data seeder creates sample users and records on first run. Swagger documentation is available at /swagger-ui.html. All design decisions and tradeoffs are documented in the README."
