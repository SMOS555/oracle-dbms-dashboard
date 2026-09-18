
# Oracle DBMS Dashboard

Oracle DBMS Dashboard is a web application built to manage and interact with an Oracle database through a simple database-workbench style interface.

It was developed as a DBMS project using Spring Boot, Oracle JDBC, HTML, CSS and JavaScript.

## Project Objective

The main objective of this project is to connect an Oracle database with a web application and demonstrate important DBMS concepts through a working interface.

The dashboard allows users to:

- View and explore database tables
- Insert, update and delete records
- Run SQL queries
- View and test database triggers
- Work with PL/SQL cursors
- Use a parameterized cursor by entering a Customer ID
- View data using a database view
- Access the database through REST APIs and JDBC

## Features

### Data Explorer

The Data Explorer allows users to:

- View all available tables
- Select a table
- View its columns and primary keys
- View the records stored in the table
- Refresh the table data

### CRUD Operations

Records can be managed directly from the dashboard.

- Insert records
- Edit existing records
- Delete records

Oracle constraints and triggers are used to validate the data.

### SQL Console

The SQL Console allows SQL commands to be executed from the website.

Supported queries include:

```sql
SELECT
INSERT
UPDATE
DELETE
WITH
````

Example:

```sql
SELECT CUSTID, CNAME, CITY
FROM CUSTOMER
WHERE CITY = 'Chennai';
```

## Triggers

The database contains 10 triggers. They are used mainly for validation and automatic database actions.

| Trigger                        | Purpose                                                   |
| ------------------------------ | --------------------------------------------------------- |
| `TRG_STOCK_NO_NEGATIVE`        | Prevents negative stock quantity                          |
| `TRG_ORDER_ITEM_VALIDATION`    | Checks order quantity and price                           |
| `TRG_ORDER_DATE_VALIDATION`    | Prevents future order dates                               |
| `TRG_ORDER_STATUS_VALIDATION`  | Checks whether the order status is valid                  |
| `TRG_SALARY_VALIDATION`        | Prevents reducing an employee's salary                    |
| `TRG_SALARY_AUDIT`             | Records salary changes in `SALARY_AUDIT`                  |
| `TRG_ORDER_SHIP_STATUS`        | Changes order status to `SHIPPED` after shipment          |
| `TRG_CUSTOMER_DELETE_CHECK`    | Prevents deleting customers who have orders               |
| `TRG_RETURN_AMOUNT_VALIDATION` | Prevents negative refund amounts                          |
| `TRG_INSPECTION_RESULT`        | Sets inspection result to `PENDING` if no result is given |

The Triggers page displays the trigger name, status, event and trigger type.

## Cursors

Three cursor examples are included in the project.

### Customer Cursor

Fetches customer records one by one and displays:

* Customer ID
* Customer name
* City

### Parameterized Order Cursor

This cursor accepts a **Customer ID as input** and retrieves the orders for that customer.

Example:

```text
Customer ID: C-01
        ↓
Order Cursor
        ↓
Orders belonging to C-01
        ↓
Order ID, Date and Status
```

### Order Total Cursor

Calculates the total amount of each order using:

```text
Quantity × Price
```

The result displays the order ID and its total amount.

## Database View

The project includes:

```text
CUSTOMER_ORDER_VIEW
```

This view combines customer and order information using a SQL JOIN.

## Database

The main database contains 22 tables:

```text
CITY
SUPPLIER
SUPPLIER_PHONE
PRODUCT
PERISHABLE_PRODUCT
HAZARDOUS_PRODUCT
SUPPLIES
INSPECTION
WAREHOUSE
STOCK
EMPLOYEE
MANAGER
DRIVER
DRIVER_CITY
CUSTOMER
ORDERS
ORDER_ITEM
ORDER_DRIVER
DELIVERY_DUTY
GOODS_MOVEMENT
SHIPMENT
GOODS_RETURN
```

An additional `SALARY_AUDIT` table is used by the salary audit trigger.

The database uses:

* Primary Keys
* Composite Keys
* Foreign Keys
* NOT NULL constraints
* CHECK constraints
* SQL
* PL/SQL
* Triggers
* Cursors
* Views

## Technology Used

* **Frontend:** HTML, CSS, JavaScript
* **Backend:** Java, Spring Boot
* **Database:** Oracle Database
* **Connectivity:** Oracle JDBC, JdbcTemplate
* **Database Language:** SQL, PL/SQL
* **Build Tool:** Maven
* **IDE:** Visual Studio Code
* **Version Control:** Git, GitHub
* **Deployment:** AWS EC2

## Project Structure

```text
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
```

## Running the Project

Configure the Oracle database connection in:

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

Run the application:

```bash
mvn spring-boot:run
```

Then open:

```text
http://localhost:8080
```

## REST API

Main endpoints:

```text
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
```

## Database Connection

The application follows this structure:

```text
HTML / CSS / JavaScript
          ↓
     Spring Boot
          ↓
       JDBC
          ↓
   Oracle Database
```

## Deployment

The Spring Boot application can be deployed on AWS EC2.

```text
User
 ↓
AWS EC2
 ↓
Spring Boot
 ↓
Oracle Database
```

```

