
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

The project implements Oracle database triggers to automatically validate data and perform actions when specific database events occur.

| Trigger | What it Does |
|---|---|
| `TRG_STOCK_NO_NEGATIVE` | Prevents inserting or updating stock with a negative quantity. |
| `TRG_ORDER_ITEM_VALIDATION` | Ensures order quantity is greater than zero and item price is not negative. |
| `TRG_ORDER_DATE_VALIDATION` | Prevents orders from being created with a future order date. |
| `TRG_ORDER_STATUS_VALIDATION` | Allows only valid order statuses such as `NEW`, `SHIPPED`, `DELIVERED`, and `CANCELLED`. |
| `TRG_SALARY_VALIDATION` | Prevents an employee's salary from being reduced. |
| `TRG_SALARY_AUDIT` | Automatically records old salary, new salary, employee ID, and change date whenever salary is updated. |
| `TRG_ORDER_SHIP_STATUS` | Automatically changes an order's status to `SHIPPED` when a shipment is created. |
| `TRG_CUSTOMER_DELETE_CHECK` | Prevents deleting a customer if the customer already has orders. |
| `TRG_RETURN_AMOUNT_VALIDATION` | Prevents negative refund amounts in goods returns. |
| `TRG_INSPECTION_RESULT` | Automatically sets the inspection result to `PENDING` when no result is provided. |

The **Triggers** page in the dashboard displays:

- Trigger name
- Status
- Triggering event
- Trigger type

---

### 🔄 Cursors

The project demonstrates Oracle **PL/SQL cursor concepts** for processing query results and performing calculations.

| Cursor | Input | What it Does | Output |
|---|---|---|---|
| **Customer Cursor** | No input | Fetches customer records one by one. | Customer ID, name, and city |
| **Parameterized Order Cursor** | Customer ID | Fetches only the orders belonging to the specified customer. | Order ID, order date, and status |
| **Order Total Cursor** | No input | Processes order items and calculates the total amount for each order. | Order ID and total order amount |

#### Customer Cursor

Processes customer records sequentially.

```text
Customer Table
      ↓
Customer Cursor
      ↓
Fetch Customer Records
      ↓
Customer ID + Name + City


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
