# Oracle DBMS Dashboard

Full-stack local DBMS lab application using **Oracle + JDBC + Spring Boot + HTML/CSS/JavaScript**.



## Included

- Oracle JDBC connectivity
- Spring Boot backend
- Dashboard table browser
- Automatic column/primary-key detection
- Edit input form
- Delete option
- SQL query window
- SELECT/WITH results displayed as a table
- INSERT/UPDATE/DELETE affected-row display
- Oracle PK/FK/UNIQUE/NOT NULL/CHECK constraints
- Seed data based on the sample values visible in the PDF
- `database/03_lab_queries.sql` with join/subquery examples

## Run

Requirements: JDK 17+, Maven 3.9+, Oracle XE/Oracle-compatible database.

1. Run `database/01_schema.sql` in your Oracle schema.
2. Run `database/02_seed.sql`.
3. Set Oracle connection variables:

Windows CMD:
```bat
set DB_URL=jdbc:oracle:thin:@localhost:1521/XEPDB1
set DB_USERNAME=system
set DB_PASSWORD=your_password
mvn spring-boot:run
```

PowerShell:
```powershell
$env:DB_URL="jdbc:oracle:thin:@localhost:1521/XEPDB1"
$env:DB_USERNAME="system"
$env:DB_PASSWORD="your_password"
mvn spring-boot:run
```

4. Open `http://localhost:8080`.

If your Oracle service name is different, change `DB_URL`, e.g. `FREEPDB1`.

## Dashboard

Choose a table and load it. Every row has:

- **Edit** — opens generated input fields; primary-key columns are locked.
- **Delete** — deletes by primary key after confirmation.

Composite primary keys are supported.

## SQL Query Window

Examples:

```sql
SELECT * FROM EMPLOYEES;
```

```sql
SELECT e.EMPLOYEE_NAME, d.DEPARTMENT_NAME
FROM EMPLOYEES e
JOIN DEPARTMENTS d ON e.DEPARTMENT_ID=d.DEPARTMENT_ID;
```

```sql
UPDATE EMPLOYEES SET SALARY=SALARY*1.50
WHERE EMPLOYEE_ID=101;
```

The local query endpoint permits SELECT/WITH/INSERT/UPDATE/DELETE. DROP, ALTER, TRUNCATE and GRANT/REVOKE are blocked.

## Important

The PDF gives the relations, sample data and query exercises but does not provide a complete formal constraint list for every table. Therefore the schema implements relational constraints inferred from the identifiers and relationships shown: primary keys, foreign keys, unique values, NOT NULL and basic domain checks.

`01_schema.sql` recreates these lab tables, so do not run it against a production schema.
