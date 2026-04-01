# Bill Generation System

Spring Boot backend project for product management, multi-item order processing, GST-based billing, stock updates, CSV reports, audit logging, and notification delivery.

## Overview

This project is built to manage product inventory and customer orders through REST APIs. It handles billing, stock deduction, low-stock alerts, payment status tracking, and external notifications using Twilio and email integration.

## Features

- Product CRUD, bulk add, pagination, and restocking
- Multi-item order placement with discount support
- GST calculation and bill generation
- Payment status and order status tracking
- Idempotent order API using `X-Idempotency-Key`
- Low-stock alert handling
- Stock, order, and bill CSV reports
- Audit logging for major system events
- SMS, WhatsApp, and email notifications
- Retry and circuit breaker support for external services
- Caching, indexing, and query optimization for better performance

## Tech Stack

- Java 21
- Spring Boot 3
- Spring MVC
- Spring Data JPA
- Hibernate
- MySQL
- PostgreSQL Driver
- Maven
- Spring Mail
- Twilio API
- Spring Retry
- JUnit 5, Mockito, MockMvc

## Main APIs

- `POST /orders`
- `GET /orders/{id}`
- `GET /bills/{id}`
- `GET /bills/number/{billNo}`
- `POST /admin/products`
- `POST /admin/products/bulk`
- `GET /admin/products`
- `GET /admin/products/page`
- `PATCH /admin/products/{id}/quantity`
- `GET /admin/orders/page`
- `GET /admin/bills/page`
- `GET /admin/reports/stock`
- `GET /admin/reports/orders`
- `GET /admin/reports/bills`
- `GET /admin/audit-logs/page`

Example order request:

```json
{
  "customerName": "Riya Patel",
  "mobileNo": "+919876543210",
  "items": [
    {
      "productId": 1,
      "quantity": 2,
      "discountAmount": 0
    }
  ]
}
```

## Performance Work

- Added indexes for important lookup and filter columns
- Used batch product loading in order placement
- Added caching for product read APIs
- Used projection-based queries for admin pages and reports
- Moved audit log persistence off the main request path

## Run

Requirements:

- Java 21 JDK
- MySQL
- Gmail SMTP credentials
- Twilio credentials

Start locally:

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Run tests:

```bash
./mvnw test
```

## Resume Bullet

- Built a Spring Boot based Bill Generation System with multi-item order processing, GST billing, inventory management, audit logging, CSV reporting, and Twilio/Gmail notification integration, with caching and query optimization for better backend performance.

## ATS Keywords

`Java`, `Java 21`, `Spring Boot`, `REST API`, `Spring MVC`, `Spring Data JPA`, `Hibernate`, `MySQL`, `Maven`, `Inventory Management`, `Order Management`, `Billing System`, `GST Calculation`, `Idempotency`, `Caching`, `Database Indexing`, `Performance Optimization`, `Asynchronous Processing`, `Audit Logging`, `CSV Report Generation`, `Bean Validation`, `Global Exception Handling`, `Twilio API`, `SMS Integration`, `WhatsApp Integration`, `JavaMailSender`, `Spring Retry`, `Circuit Breaker`, `JUnit 5`, `Mockito`, `MockMvc`
