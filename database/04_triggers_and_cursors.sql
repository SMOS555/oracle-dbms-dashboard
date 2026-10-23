-- =========================================================
-- PL/SQL TRIGGERS & CURSORS FOR DBMS LAB
-- Course: Database Management Systems (DBMS)
-- =========================================================

-- =========================================================
-- SECTION 1: 10 DATABASE TRIGGERS
-- =========================================================

-- 1. Prevent negative stock quantity in WAREHOUSE/STOCK
CREATE OR REPLACE TRIGGER TRG_STOCK_NO_NEGATIVE
BEFORE INSERT OR UPDATE ON STOCK
FOR EACH ROW
BEGIN
    IF :NEW.QUANTITY < 0 THEN
        RAISE_APPLICATION_ERROR(-20001, 'Stock quantity cannot be negative.');
    END IF;
END;
/

-- 2. Validate order quantity and unit price in ORDER_ITEM
CREATE OR REPLACE TRIGGER TRG_ORDER_ITEM_VALIDATION
BEFORE INSERT OR UPDATE ON ORDER_ITEM
FOR EACH ROW
BEGIN
    IF :NEW.QTY <= 0 THEN
        RAISE_APPLICATION_ERROR(-20002, 'Order quantity must be greater than zero.');
    END IF;
    IF :NEW.PRICE < 0 THEN
        RAISE_APPLICATION_ERROR(-20003, 'Item price cannot be negative.');
    END IF;
END;
/

-- 3. Prevent future order dates
CREATE OR REPLACE TRIGGER TRG_ORDER_DATE_VALIDATION
BEFORE INSERT OR UPDATE ON ORDERS
FOR EACH ROW
BEGIN
    IF :NEW.ORDERDATE > SYSDATE THEN
        RAISE_APPLICATION_ERROR(-20004, 'Order date cannot be in the future.');
    END IF;
END;
/

-- 4. Validate order status values
CREATE OR REPLACE TRIGGER TRG_ORDER_STATUS_VALIDATION
BEFORE INSERT OR UPDATE ON ORDERS
FOR EACH ROW
BEGIN
    IF :NEW.STATUS NOT IN ('NEW', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED') THEN
        RAISE_APPLICATION_ERROR(-20005, 'Invalid order status: ' || :NEW.STATUS);
    END IF;
END;
/

-- 5. Prevent reducing an employee's salary
CREATE OR REPLACE TRIGGER TRG_SALARY_VALIDATION
BEFORE UPDATE OF SALARY ON EMPLOYEE
FOR EACH ROW
BEGIN
    IF :NEW.SALARY < :OLD.SALARY THEN
        RAISE_APPLICATION_ERROR(-20006, 'Salary cannot be reduced. Current: ' || :OLD.SALARY || ', Attempted: ' || :NEW.SALARY);
    END IF;
END;
/

-- 6. Audit salary updates into SALARY_AUDIT table
CREATE TABLE SALARY_AUDIT (
    AUDITID NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    EMPID VARCHAR2(10),
    OLDSALARY NUMBER(10,2),
    NEWSALARY NUMBER(10,2),
    CHANGEDATE DATE DEFAULT SYSDATE,
    CHANGEDBY VARCHAR2(50) DEFAULT USER
);
/

CREATE OR REPLACE TRIGGER TRG_SALARY_AUDIT
AFTER UPDATE OF SALARY ON EMPLOYEE
FOR EACH ROW
BEGIN
    INSERT INTO SALARY_AUDIT (EMPID, OLDSALARY, NEWSALARY, CHANGEDATE, CHANGEDBY)
    VALUES (:OLD.EMPID, :OLD.SALARY, :NEW.SALARY, SYSDATE, USER);
END;
/

-- 7. Update order status to SHIPPED upon shipment creation
CREATE OR REPLACE TRIGGER TRG_ORDER_SHIP_STATUS
AFTER INSERT ON SHIPMENT
FOR EACH ROW
DECLARE
    v_orderid VARCHAR2(10);
BEGIN
    SELECT ORDERID INTO v_orderid
    FROM GOODS_MOVEMENT
    WHERE MOVEMENTID = :NEW.MOVEMENTID;

    UPDATE ORDERS
    SET STATUS = 'SHIPPED'
    WHERE ORDERID = v_orderid;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        NULL;
END;
/

-- 8. Prevent deleting customers who have existing orders
CREATE OR REPLACE TRIGGER TRG_CUSTOMER_DELETE_CHECK
BEFORE DELETE ON CUSTOMER
FOR EACH ROW
DECLARE
    v_order_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_order_count
    FROM ORDERS
    WHERE CUSTID = :OLD.CUSTID;

    IF v_order_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20007, 'Cannot delete customer ' || :OLD.CUSTID || ' because they have ' || v_order_count || ' associated orders.');
    END IF;
END;
/

-- 9. Prevent negative refund amounts on goods return
CREATE OR REPLACE TRIGGER TRG_RETURN_AMOUNT_VALIDATION
BEFORE INSERT OR UPDATE ON GOODS_RETURN
FOR EACH ROW
BEGIN
    IF :NEW.REFUNDAMT < 0 THEN
        RAISE_APPLICATION_ERROR(-20008, 'Refund amount cannot be negative.');
    END IF;
END;
/

-- 10. Default inspection result to PENDING if not specified
CREATE OR REPLACE TRIGGER TRG_INSPECTION_RESULT
BEFORE INSERT ON INSPECTION
FOR EACH ROW
BEGIN
    IF :NEW.RESULT IS NULL OR TRIM(:NEW.RESULT) = '' THEN
        :NEW.RESULT := 'PENDING';
    END IF;
END;
/


-- =========================================================
-- SECTION 2: 3 PL/SQL CURSORS
-- =========================================================

-- CURSOR 1: Customer Cursor
-- Explicit cursor to fetch and display customer records row by row
SET SERVEROUTPUT ON;
DECLARE
    CURSOR cur_customer IS
        SELECT CUSTID, CNAME, CITY
        FROM CUSTOMER
        ORDER BY CUSTID;

    v_custid CUSTOMER.CUSTID%TYPE;
    v_cname  CUSTOMER.CNAME%TYPE;
    v_city   CUSTOMER.CITY%TYPE;
BEGIN
    DBMS_OUTPUT.PUT_LINE('--- CUSTOMER CURSOR OUTPUT ---');
    OPEN cur_customer;
    LOOP
        FETCH cur_customer INTO v_custid, v_cname, v_city;
        EXIT WHEN cur_customer%NOTFOUND;
        DBMS_OUTPUT.PUT_LINE('Customer: ' || v_custid || ' | Name: ' || v_cname || ' | City: ' || v_city);
    END LOOP;
    CLOSE cur_customer;
END;
/

-- CURSOR 2: Parameterized Order Cursor
-- Parameterized cursor accepting Customer ID as input
DECLARE
    CURSOR cur_orders (p_custid VARCHAR2) IS
        SELECT ORDERID, ORDERDATE, STATUS
        FROM ORDERS
        WHERE CUSTID = p_custid
        ORDER BY ORDERID;

    v_orderid   ORDERS.ORDERID%TYPE;
    v_orderdate ORDERS.ORDERDATE%TYPE;
    v_status    ORDERS.STATUS%TYPE;
    v_target_cust VARCHAR2(10) := 'C-01';
BEGIN
    DBMS_OUTPUT.PUT_LINE('--- PARAMETERIZED ORDER CURSOR FOR ' || v_target_cust || ' ---');
    OPEN cur_orders(v_target_cust);
    LOOP
        FETCH cur_orders INTO v_orderid, v_orderdate, v_status;
        EXIT WHEN cur_orders%NOTFOUND;
        DBMS_OUTPUT.PUT_LINE('Order: ' || v_orderid || ' | Date: ' || TO_CHAR(v_orderdate, 'YYYY-MM-DD') || ' | Status: ' || v_status);
    END LOOP;
    CLOSE cur_orders;
END;
/

-- CURSOR 3: Order Total Cursor
-- Calculates total order amount (Quantity * Price) using aggregation cursor
DECLARE
    CURSOR cur_order_total IS
        SELECT ORDERID, SUM(QTY * PRICE) AS TOTAL_AMOUNT
        FROM ORDER_ITEM
        GROUP BY ORDERID
        ORDER BY ORDERID;

    v_orderid ORDERS.ORDERID%TYPE;
    v_total   NUMBER(10,2);
BEGIN
    DBMS_OUTPUT.PUT_LINE('--- ORDER TOTAL CURSOR OUTPUT ---');
    OPEN cur_order_total;
    LOOP
        FETCH cur_order_total INTO v_orderid, v_total;
        EXIT WHEN cur_order_total%NOTFOUND;
        DBMS_OUTPUT.PUT_LINE('Order ID: ' || v_orderid || ' | Total Amount: Rs. ' || TO_CHAR(v_total, '999,999.99'));
    END LOOP;
    CLOSE cur_order_total;
END;
/
