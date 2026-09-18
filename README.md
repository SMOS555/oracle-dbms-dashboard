
# Oracle DBMS Dashboard

A full-stack web-based **Oracle Database Management Dashboard** developed using **Spring Boot, Oracle JDBC, HTML, CSS, and JavaScript**.

The project provides a database-workbench interface for exploring tables, managing records, executing SQL queries, and demonstrating Oracle DBMS concepts.

---

## 🚀 Features

### 📊 Data Explorer
- View all database tables
- Select and explore tables
- View table columns and primary keys
- Display table records
- Refresh table data

### ✏️ CRUD Operations
- Insert new records
- Update/Edit existing records
- Delete records
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
````

### ⚡ Triggers

The project implements Oracle triggers for:

* Negative stock prevention
* Order quantity and price validation
* Future order-date validation
* Order-status validation
* Salary validation
* Salary change auditing
* Automatic order status update after shipment
* Customer deletion validation
* Refund amount validation
* Default inspection result

The dashboard displays:

* Trigger name
* Status
* Event
* Trigger type

### 🔄 Cursors

The project demonstrates Oracle cursor concepts:

* **Customer Cursor** – Processes customer records
* **Parameterized Order Cursor** – Accepts a customer ID and retrieves their orders
* **Order Total Cursor** – Calculates total order amounts

Example flow:

```text
Customer ID
     ↓
Parameterized Cursor
     ↓
Customer Orders
     ↓
Result
```

### 👁️ Database View

`CUSTOMER_ORDER_VIEW` combines customer and order information using a SQL `JOIN`.

### 🔗 Oracle Database Connectivity

```text
Frontend
   ↓
Spring Boot REST API
   ↓
JdbcTemplate / Oracle JDBC
   ↓
Oracle Database
```

---

## 🗄️ Database

The project contains **22 main relational tables**:

1. CITY
2. SUPPLIER
3. SUPPLIER_PHONE
4. PRODUCT
5. PERISHABLE_PRODUCT
6. HAZARDOUS_PRODUCT
7. SUPPLIES
8. INSPECTION
9. WAREHOUSE
10. STOCK
11. EMPLOYEE
12. MANAGER
13. DRIVER
14. DRIVER_CITY
15. CUSTOMER
16. ORDERS
17. ORDER_ITEM
18. ORDER_DRIVER
19. DELIVERY_DUTY
20. GOODS_MOVEMENT
21. SHIPMENT
22. GOODS_RETURN

Additional table:

* `SALARY_AUDIT` – Stores salary change audit records.

### DBMS Concepts

* Primary Keys
* Composite Keys
* Foreign Keys
* `NOT NULL` Constraints
* `CHECK` Constraints
* SQL
* PL/SQL
* Triggers
* Cursors
* Parameterized Cursors
* Views
* JDBC
* REST APIs

---

## 🛠️ Technology Stack

| Component         | Technology                |
| ----------------- | ------------------------- |
| Frontend          | HTML, CSS, JavaScript     |
| Backend           | Java, Spring Boot         |
| Database          | Oracle Database           |
| Connectivity      | Oracle JDBC, JdbcTemplate |
| Database Language | SQL / PL/SQL              |
| Build Tool        | Maven                     |
| IDE               | Visual Studio Code        |
| Version Control   | Git / GitHub              |
| Deployment        | AWS EC2                   |

---

## 📁 Project Structure

```text
oracle-dbms-dashboard/
│
├── src/
│   └── main/
│       ├── java/
│       │   └── ...
│       │
│       └── resources/
│           ├── static/
│           │   ├── index.html
│           │   ├── app.js
│           │   └── style.css
│           │
│           └── application.properties
│
├── pom.xml
└── README.md
```

---

## ▶️ Run the Project

### 1. Configure Oracle Database

Update:

```text
src/main/resources/application.properties
```

Example:

```properties
spring.datasource.url=jdbc:oracle:thin:@localhost:1521/FREE
spring.datasource.username=YOUR_USERNAME
spring.datasource.password=YOUR_PASSWORD
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver
```

### 2. Start Spring Boot

```bash
mvn spring-boot:run
```

### 3. Open Dashboard

```text
http://localhost:8080
```

---

## 🔌 REST APIs

| Method | Endpoint                          | Purpose                        |
| ------ | --------------------------------- | ------------------------------ |
| GET    | `/api/tables`                     | Get database tables            |
| GET    | `/api/tables/{table}/columns`     | Get table columns              |
| GET    | `/api/tables/{table}/rows`        | Get table records              |
| PUT    | `/api/tables/{table}`             | Update records                 |
| DELETE | `/api/tables/{table}`             | Delete records                 |
| POST   | `/api/query`                      | Execute SQL query              |
| GET    | `/api/triggers`                   | Get database triggers          |
| GET    | `/api/cursors/customers`          | Run customer cursor            |
| GET    | `/api/cursors/orders?custId=C-01` | Run parameterized order cursor |
| GET    | `/api/cursors/order-totals`       | Calculate order totals         |

---

## ☁️ Deployment

The Spring Boot application can be hosted on **AWS EC2**.

```text
User
 ↓
AWS EC2
 ↓
Spring Boot Application
 ↓
Oracle Database
```

---

## 📌 Project Information

| Property        | Details               |
| --------------- | --------------------- |
| Project         | Oracle DBMS Dashboard |
| Type            | Academic DBMS Project |
| Frontend        | HTML, CSS, JavaScript |
| Backend         | Java + Spring Boot    |
| Database        | Oracle Database       |
| Connectivity    | Oracle JDBC           |
| Version Control | Git / GitHub          |
| Deployment      | AWS EC2               |

```
```
