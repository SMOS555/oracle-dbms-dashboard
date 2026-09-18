# Oracle DBMS Dashboard

A full-stack web-based **Oracle Database Management Dashboard** developed using **Spring Boot, Oracle JDBC, HTML, CSS, and JavaScript**. It provides a database-workbench interface for exploring tables, managing records, executing SQL queries, and demonstrating Oracle DBMS concepts.

---

## 🚀 Features

### 📊 Data Explorer
- View all database tables
- Select and explore tables
- View table columns and primary keys
- Display table records
- Refresh table data

### ✏️ CRUD Operations
- **Insert** new records
- **Update/Edit** existing records
- **Delete** records
- Database constraints and triggers validate operations

### 💻 SQL Console
Execute SQL queries directly from the dashboard:

- `SELECT`
- `INSERT`
- `UPDATE`
- `DELETE`
- `WITH`

Example:

```sql
SELECT CUSTID, CNAME, CITY
FROM CUSTOMER
WHERE CITY = 'Chennai';

Structural commands such as DROP, ALTER, TRUNCATE, GRANT, and REVOKE are blocked.

⚡ Triggers

The project implements Oracle triggers for:

Negative stock prevention
Order quantity and price validation
Future order-date validation
Order-status validation
Salary validation
Salary change auditing
Automatic order status update after shipment
Customer deletion validation
Refund amount validation
Default inspection result

The dashboard also displays trigger name, status, event, and trigger type.

🔄 Cursors

The project demonstrates:

Customer Cursor – Processes customer records
Parameterized Order Cursor – Accepts a customer ID and retrieves their orders
Order Total Cursor – Calculates total order amounts

Example:

Customer ID → Cursor → Orders → Result
👁️ Database View

CUSTOMER_ORDER_VIEW combines customer and order information using a SQL JOIN.

🔗 Oracle Database Connectivity
Frontend
   ↓
Spring Boot REST API
   ↓
JdbcTemplate / Oracle JDBC
   ↓
Oracle Database
🗄️ Database

The project contains 22 main relational tables covering:

City
Supplier
Supplier Phone
Product
Perishable Product
Hazardous Product
Supplies
Inspection
Warehouse
Stock
Employee
Manager
Driver
Driver City
Customer
Orders
Order Item
Order Driver
Delivery Duty
Goods Movement
Shipment
Goods Return

An additional SALARY_AUDIT table is used for salary auditing.

DBMS Concepts
Primary Keys
Composite Keys
Foreign Keys
NOT NULL Constraints
CHECK Constraints
SQL
PL/SQL
Triggers
Cursors
Parameterized Cursors
Views
JDBC
REST APIs
🛠️ Technology Stack
Component	Technology
Frontend	HTML, CSS, JavaScript
Backend	Java, Spring Boot
Database	Oracle Database
Connectivity	Oracle JDBC, JdbcTemplate
Database Language	SQL / PL/SQL
Build Tool	Maven
IDE	Visual Studio Code
Version Control	Git / GitHub
Deployment	AWS EC2
📁 Project Structure
oracle-dbms-dashboard/
│
├── src/
│   └── main/
│       ├── java/
│       └── resources/
│           ├── static/
│           │   ├── index.html
│           │   ├── app.js
│           │   └── style.css
│           └── application.properties
│
├── pom.xml
└── README.md
▶️ Run the Project
1. Configure Oracle

Update:

src/main/resources/application.properties
spring.datasource.url=jdbc:oracle:thin:@localhost:1521/FREE
spring.datasource.username=YOUR_USERNAME
spring.datasource.password=YOUR_PASSWORD
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver
2. Start Spring Boot
mvn spring-boot:run
3. Open Dashboard
http://localhost:8080
🔌 Main REST APIs
GET    /api/tables
GET    /api/tables/{table}/columns
GET    /api/tables/{table}/rows
PUT    /api/tables/{table}
DELETE /api/tables/{table}
POST   /api/query

GET    /api/triggers
GET    /api/cursors/customers
GET    /api/cursors/orders?custId=C-01
GET    /api/cursors/order-totals
☁️ Deployment

The Spring Boot application can be hosted on AWS EC2.

User
 ↓
AWS EC2
 ↓
Spring Boot Application
 ↓
Oracle Database
📌 Project Status
 Oracle Database Schema
 22 Relational Tables
 Primary & Foreign Keys
 Database Constraints
 Sample Data
 Spring Boot Backend
 Oracle JDBC Connectivity
 REST APIs
 Data Explorer
 CRUD Operations
 SQL Console
 Triggers
 Cursors
 Parameterized Cursor
 Order Total Calculation
 Database View
 Dashboard UI
👨‍💻 Project Information
Property	Details
Project	Oracle DBMS Dashboard
Type	Academic DBMS Project
Frontend	HTML, CSS, JavaScript
Backend	Java + Spring Boot
Database	Oracle Database
Connectivity	Oracle JDBC
Version Control	Git / GitHub
Deployment	AWS EC2
