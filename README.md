# 🔧 Maintenance Match

A cloud-native microservices platform that connects users with nearby maintenance professionals (plumbers, electricians, HVAC technicians, etc.) for on-demand service requests. Built with Spring Boot, Spring Cloud Gateway, Apache Kafka, and PostgreSQL with PostGIS for geospatial queries.

---

## 📋 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Microservices](#microservices)
  - [API Gateway](#api-gateway)
  - [Auth Service](#auth-service)
  - [Matching Service](#matching-service)
  - [Notification Service](#notification-service)
- [Database Design](#database-design)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Running with Docker Compose](#running-with-docker-compose)
  - [Running on Kubernetes](#running-on-kubernetes)
- [API Documentation](#api-documentation)
- [Environment Variables](#environment-variables)
- [Project Structure](#project-structure)

---

## 🎯 Overview

**Maintenance Match** is a service marketplace platform that enables:

- **Users** to find and request maintenance services from nearby professionals
- **Maintainers** to register, manage their availability, and accept jobs
- **Admins** to approve/reject maintainer registrations and manage users

### Key Features

✅ **Geospatial Search** – Find maintainers within a specified radius using PostGIS  
✅ **JWT Authentication** – Secure RSA-based token authentication with refresh token rotation  
✅ **Real-time Notifications** – Kafka-powered event-driven notifications via email  
✅ **Role-Based Access Control** – USER, MAINTAINER, and ADMIN roles  
✅ **Job Lifecycle Management** – Create, track, complete, or cancel maintenance jobs  
✅ **Maintainer Approval Workflow** – Admin approval required for new maintainers

---

## 🏗️ Architecture

![Maintenance Match Architecture](https://github.com/KidusHawoltu/group-5-maintenance-match/blob/main/final_ds_hld.png)

---

## 🛠️ Tech Stack

| Category              | Technology                     |
| --------------------- | ------------------------------ |
| **Language**          | Java 17                        |
| **Framework**         | Spring Boot 3.3.7              |
| **API Gateway**       | Spring Cloud Gateway           |
| **Database**          | PostgreSQL 15, PostGIS 3.4     |
| **Message Broker**    | Apache Kafka (Confluent 7.5.3) |
| **Authentication**    | JWT (RSA256) with jjwt library |
| **API Documentation** | SpringDoc OpenAPI (Swagger UI) |
| **Containerization**  | Docker, Docker Compose         |
| **Orchestration**     | Kubernetes                     |
| **Build Tool**        | Maven                          |

---

## 📦 Microservices

### API Gateway

The entry point for all client requests. Routes traffic to downstream services and validates JWT tokens.

**Responsibilities:**

- Request routing based on path predicates
- JWT token validation using RSA public key from Auth Service
- Centralized Swagger UI aggregation for all services
- Header injection (X-User-ID, X-User-Role) for downstream services

**Routes:**
| Path Pattern | Target Service |
|--------------|----------------|
| `/api/auth/**` | Auth Service (8081) |
| `/api/matching/**` | Matching Service (8082) |
| `/api/notifications/**` | Notification Service (8083) |

---

### Auth Service

Handles user identity, authentication, and authorization.

**Endpoints:**

| Method | Endpoint                                   | Description                            | Access |
| ------ | ------------------------------------------ | -------------------------------------- | ------ |
| POST   | `/api/auth/signup/user`                    | Register a new user                    | Public |
| POST   | `/api/auth/signup/maintainer`              | Register maintainer (pending approval) | Public |
| POST   | `/api/auth/login`                          | Authenticate and get tokens            | Public |
| POST   | `/api/auth/refresh`                        | Refresh access token                   | Public |
| GET    | `/api/auth/public-key`                     | Get RSA public key                     | Public |
| GET    | `/api/auth/admin/users`                    | List all users                         | Admin  |
| GET    | `/api/auth/admin/maintainers/pending`      | List pending maintainers               | Admin  |
| POST   | `/api/auth/admin/maintainers/{id}/approve` | Approve maintainer                     | Admin  |
| POST   | `/api/auth/admin/maintainers/{id}/reject`  | Reject maintainer                      | Admin  |

**Data Model:**

```
User
├── id (UUID)
├── email (unique)
├── password (hashed)
├── firstName, lastName
├── phoneNumber
├── role (USER | MAINTAINER | ADMIN)
├── approvalStatus (PENDING | APPROVED | REJECTED)
└── isActive (boolean)
```

---

### Matching Service

Core business logic for maintainer discovery and job management.

**Endpoints:**

| Method | Endpoint                           | Description                  | Access          |
| ------ | ---------------------------------- | ---------------------------- | --------------- |
| GET    | `/api/matching/maintainers/nearby` | Find maintainers by location | User            |
| POST   | `/api/matching/jobs`               | Create a new job             | User            |
| GET    | `/api/matching/jobs/my-jobs`       | Get user's jobs              | Authenticated   |
| POST   | `/api/matching/jobs/{id}/complete` | Mark job completed           | User/Maintainer |
| POST   | `/api/matching/jobs/{id}/cancel`   | Cancel a job                 | User/Maintainer |
| PATCH  | `/api/matching/maintainers/me`     | Update maintainer profile    | Maintainer      |

**Data Models:**

```
Maintainer                          Job
├── id (UUID)                       ├── id (UUID)
├── userId (FK to Auth User)        ├── userId
├── name                            ├── maintainerId
├── isAvailable                     ├── status (ACTIVE | COMPLETED | CANCELLED)
├── location (PostGIS Point)        ├── problemDescription
├── capacity                        ├── userLocation (PostGIS Point)
└── activeJobs                      ├── createdAt
                                    └── completedAt
```

---

### Notification Service

Event-driven notification delivery via email.

**Features:**

- Kafka consumer for `notification.send` topic
- Thymeleaf HTML email templates
- Notification history persistence
- Read/unread status tracking

**Email Templates:**
| Template | Trigger |
|----------|---------|
| `welcome-email.html` | User registration |
| `maintainer-signup.html` | Maintainer registration |
| `maintainer-approved.html` | Admin approves maintainer |
| `maintainer-rejected.html` | Admin rejects maintainer |
| `job-matched-user.html` | Job created (sent to user) |
| `job-matched-maintainer.html` | Job created (sent to maintainer) |
| `job-completed.html` | Job marked complete |
| `job-cancelled.html` | Job cancelled |

**Endpoints:**

| Method | Endpoint                          | Description              |
| ------ | --------------------------------- | ------------------------ |
| GET    | `/api/notifications`              | Get user's notifications |
| GET    | `/api/notifications/unread-count` | Get unread count         |
| PUT    | `/api/notifications/{id}/read`    | Mark as read             |
| PUT    | `/api/notifications/read-all`     | Mark all as read         |

---

## 🗄️ Database Design

Each service has its own dedicated PostgreSQL database following the Database-per-Service pattern:

| Database          | Service      | Port | Special Features                   |
| ----------------- | ------------ | ---- | ---------------------------------- |
| `auth_db`         | Auth         | 5434 | User credentials & roles           |
| `matching_db`     | Matching     | 5435 | **PostGIS** for geospatial queries |
| `notification_db` | Notification | 5436 | Notification history               |

---

## 🚀 Getting Started

### Prerequisites

- **Docker** & **Docker Compose** (v2.x+)
- **Java 17** (for local development)
- **Maven 3.8+** (for building)

### Running with Docker Compose

1. **Clone the repository:**

   ```bash
   git clone https://github.com/your-org/maintenance-match.git
   cd maintenance-match
   ```

2. **Start all services:**

   ```bash
   docker-compose up --build
   ```

3. **Access the services:**

   | Service              | URL                                   |
   | -------------------- | ------------------------------------- |
   | API Gateway          | http://localhost:8080                 |
   | Swagger UI           | http://localhost:8080/swagger-ui.html |
   | Auth Service         | http://localhost:8081                 |
   | Matching Service     | http://localhost:8082                 |
   | Notification Service | http://localhost:8083                 |
   | AKHQ (Kafka UI)      | http://localhost:8888                 |

4. **Stop all services:**
   ```bash
   docker-compose down
   ```

### Running on Kubernetes

Kubernetes manifests are provided in the `k8s/` directory.

1. **Create the namespace:**

   ```bash
   kubectl apply -f k8s/config/namespace.yml
   ```

2. **Deploy infrastructure:**

   ```bash
   kubectl apply -f k8s/config/
   kubectl apply -f k8s/databases/
   kubectl apply -f k8s/infrastructure/
   ```

3. **Deploy applications:**

   ```bash
   kubectl apply -f k8s/apps/
   ```

4. **Access via NodePort:**
   ```
   API Gateway: http://<node-ip>:30080
   ```

---

## 📖 API Documentation

Interactive API documentation is available via **Swagger UI** at:

```
http://localhost:8080/swagger-ui.html
```

The gateway aggregates OpenAPI specs from all services:

- **Auth API** – Authentication & user management
- **Matching API** – Maintainer search & job management
- **Notification API** – Notification retrieval

---

## ⚙️ Environment Variables

### Common Variables

| Variable                  | Description            | Default                                      |
| ------------------------- | ---------------------- | -------------------------------------------- |
| `DB_URL`                  | JDBC connection string | `jdbc:postgresql://localhost:5432/{db_name}` |
| `DB_USER`                 | Database username      | `ds_user`                                    |
| `DB_PASS`                 | Database password      | `ds_pass`                                    |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address   | `kafka:9092`                                 |
| `API_GATEWAY_URL`         | Public gateway URL     | `http://localhost:8080`                      |

### Service-Specific

| Service      | Variable                 | Description                   |
| ------------ | ------------------------ | ----------------------------- |
| Auth         | `MATCHING_URL`           | Matching service URL          |
| Auth         | `NOTIFICATION_TOPIC`     | Kafka topic for notifications |
| Matching     | `AUTH_URL`               | Auth service URL              |
| Notification | `MAIL_HOST`, `MAIL_PORT` | SMTP server config            |
| API Gateway  | `AUTH_PUBLIC_KEY_URL`    | URL to fetch JWT public key   |

---

## 📁 Project Structure

```
maintenance-match/
├── api-gateway/              # Spring Cloud Gateway
│   ├── src/main/java/.../
│   │   ├── config/           # Security & routing config
│   │   ├── filter/           # JWT authentication filter
│   │   └── util/             # JWT utilities
│   └── src/main/resources/
│       └── application.yaml
│
├── auth/                     # Authentication Service
│   ├── src/main/java/.../
│   │   ├── controller/       # REST endpoints
│   │   ├── service/          # Business logic
│   │   ├── model/            # JPA entities
│   │   ├── repository/       # Data access
│   │   └── dto/              # Request/Response DTOs
│   └── src/main/resources/
│       └── application.yaml
│
├── matching/                 # Matching Service
│   ├── src/main/java/.../
│   │   ├── controller/       # REST endpoints
│   │   ├── service/          # Business logic + geospatial
│   │   ├── model/            # JPA entities with PostGIS
│   │   └── client/           # Feign clients
│   └── src/main/resources/
│       └── application.yaml
│
├── notification/             # Notification Service
│   ├── src/main/java/.../
│   │   ├── controller/       # REST endpoints
│   │   ├── service/          # Kafka consumer + email
│   │   └── model/            # Notification entity
│   └── src/main/resources/
│       ├── application.yaml
│       └── templates/        # Thymeleaf email templates
│
├── k8s/                      # Kubernetes manifests
│   ├── apps/                 # Deployment & Service for each app
│   ├── config/               # ConfigMaps, Secrets, Namespace
│   ├── databases/            # PostgreSQL StatefulSets
│   └── infrastructure/       # Kafka, Zookeeper, AKHQ
│
├── docker-compose.yml        # Local development orchestration
└── README.md                 # This file
```

---

## 👥 Contributors

**Group 5** – Distributed Systems Course Project

| Name              | Student ID |
| ----------------- | ---------- |
| Hermela Dereje    | ETS0794/14 |
| Kalkidan Amare    | ETS0884/14 |
| Kidus Asebe       | ETS0925/14 |
| Kidus Berhane     | ETS0926/14 |
| Kidus Hawoltu     | ETS0924/14 |
| Kirubel Legese    | ETS0944/14 |
| Kirubel Wondwosen | ETS0948/14 |

---

## 📄 License

This project is developed for educational purposes as part of a distributed systems course.

---

## 🔗 Related Links

- [Spring Cloud Gateway Docs](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)
- [PostGIS Documentation](https://postgis.net/documentation/)
- [Apache Kafka](https://kafka.apache.org/documentation/)
- [SpringDoc OpenAPI](https://springdoc.org/)
