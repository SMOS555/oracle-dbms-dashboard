# Oracle DBMS Dashboard

A full-stack Oracle Database Management Dashboard built using **Spring Boot, Oracle Database, JDBC, HTML, CSS, and JavaScript**.

The project provides a web-based interface for exploring database tables, executing SQL queries, performing CRUD operations, viewing database triggers, and demonstrating cursor-based database operations.

---

## 📌 Project Overview

The Oracle DBMS Dashboard is designed as a web-based database management interface.

Instead of interacting with the Oracle database only through SQL Developer, users can access important database operations through a browser-based dashboard.

### Main functionalities

- Database table explorer
- View table records
- View table structure/columns
- Primary key information
- Insert records
- Edit/update records
- Delete records
- SQL Console
- Execute SELECT queries
- Execute INSERT queries
- Execute UPDATE queries
- Execute DELETE queries
- Database trigger monitoring
- Trigger validation demonstrations
- Cursor operations
- Parameterized cursor operations
- Order total calculations
- Customer and order data management
- Responsive dashboard UI
- Oracle JDBC connectivity
- REST API backend
- Error handling and user notifications

---

# 🏗️ Technology Stack

## Frontend

- HTML5
- CSS3
- JavaScript
- Google Fonts
- Responsive UI

## Backend

- Java
- Spring Boot
- Spring JDBC
- REST APIs
- Maven

## Database

- Oracle Database
- Oracle SQL
- PL/SQL
- Database Triggers
- Cursors
- Views
- Constraints
- Foreign Keys
- Primary Keys

## Connectivity

- Oracle JDBC Driver
- Spring `JdbcTemplate`

---

# 🧩 System Architecture

```text
                    ┌──────────────────────┐
                    │       User           │
                    │      Browser         │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │    HTML / CSS / JS   │
                    │      Dashboard       │
                    └──────────┬───────────┘
                               │
                         REST API Calls
                               │
                               ▼
                    ┌──────────────────────┐
                    │    Spring Boot       │
                    │      Backend         │
                    │                      │
                    │ DatabaseController   │
                    │ DatabaseService      │
                    └──────────┬───────────┘
                               │
                         JDBC Connection
                               │
                               ▼
                    ┌──────────────────────┐
                    │   Oracle Database    │
                    │                      │
                    │ Tables               │
                    │ Constraints          │
                    │ Triggers             │
                    │ Cursors              │
                    │ Views                │
                    └──────────────────────┘
