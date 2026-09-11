-- =========================================================
-- FINAL DBMS LAB SEED DATA
-- Only values present in the supplied handwritten sheets
-- =========================================================

-- =========================================================
-- CITY
-- =========================================================

INSERT INTO CITY (CITY, STATE)
VALUES ('Chennai', 'Tamil Nadu');

INSERT INTO CITY (CITY, STATE)
VALUES ('Mumbai', 'Maharashtra');


-- =========================================================
-- SUPPLIER
-- Supplier names/cities are not provided in the sheets.
-- =========================================================

INSERT INTO SUPPLIER (SUPPLIERID)
VALUES ('S01');

INSERT INTO SUPPLIER (SUPPLIERID)
VALUES ('S03');

INSERT INTO SUPPLIER (SUPPLIERID)
VALUES ('S05');


-- =========================================================
-- SUPPLIER_PHONE
-- =========================================================

INSERT INTO SUPPLIER_PHONE (SUPPLIERID, PHONE)
VALUES ('S01', '9845012345');

INSERT INTO SUPPLIER_PHONE (SUPPLIERID, PHONE)
VALUES ('S01', '08022334455');

INSERT INTO SUPPLIER_PHONE (SUPPLIERID, PHONE)
VALUES ('S03', '9820011122');

INSERT INTO SUPPLIER_PHONE (SUPPLIERID, PHONE)
VALUES ('S05', '9840199887');

INSERT INTO SUPPLIER_PHONE (SUPPLIERID, PHONE)
VALUES ('S05', '04424851212');


-- =========================================================
-- PRODUCT
-- =========================================================

INSERT INTO PRODUCT (PRODUCTID, PNAME, CATEGORY, UNITPRICE)
VALUES ('P-100', 'Basmati Rice', 'Staples', 480);

INSERT INTO PRODUCT (PRODUCTID, PNAME, CATEGORY, UNITPRICE)
VALUES ('P-104', 'Sunflower Oil', 'Oils', 132);

INSERT INTO PRODUCT (PRODUCTID, PNAME, CATEGORY, UNITPRICE)
VALUES ('P-108', 'Sona Masoori', 'Staples', 445);

INSERT INTO PRODUCT (PRODUCTID, PNAME, CATEGORY, UNITPRICE)
VALUES ('P-210', 'Frozen Peas', 'Frozen', 96);


-- =========================================================
-- CUSTOMER
-- =========================================================

INSERT INTO CUSTOMER (CUSTID, CNAME, STREET, CITY, PHONE)
VALUES ('C-01', 'Shama Retail', '12 MG Rd', 'Chennai', '9840011223');

INSERT INTO CUSTOMER (CUSTID, CNAME, STREET, CITY, PHONE)
VALUES ('C-03', 'Metro Mart', '5 Link Rd', 'Mumbai', '9820044556');

INSERT INTO CUSTOMER (CUSTID, CNAME, STREET, CITY, PHONE)
VALUES ('C-05', 'Anand Stores', '7 Anna Salai', 'Chennai', '9845567788');


-- =========================================================
-- ORDERS
-- =========================================================
-- The handwritten sheet gives day/month only, not a year.
-- 2026 is used here so Oracle receives a complete DATE value.

INSERT INTO ORDERS (ORDERID, ORDERDATE, STATUS, CUSTID)
VALUES ('O-101', DATE '2026-04-02', 'SHIPPED', 'C-01');

INSERT INTO ORDERS (ORDERID, ORDERDATE, STATUS, CUSTID)
VALUES ('O-102', DATE '2026-04-05', 'NEW', 'C-03');

INSERT INTO ORDERS (ORDERID, ORDERDATE, STATUS, CUSTID)
VALUES ('O-103', DATE '2026-04-09', 'SHIPPED', 'C-01');

INSERT INTO ORDERS (ORDERID, ORDERDATE, STATUS, CUSTID)
VALUES ('O-104', DATE '2026-04-11', 'NEW', 'C-05');

INSERT INTO ORDERS (ORDERID, ORDERDATE, STATUS, CUSTID)
VALUES ('O-105', DATE '2026-04-14', 'SHIPPED', 'C-03');

INSERT INTO ORDERS (ORDERID, ORDERDATE, STATUS, CUSTID)
VALUES ('O-106', DATE '2026-04-16', 'NEW', 'C-05');


-- =========================================================
-- ORDER_ITEM
-- =========================================================

INSERT INTO ORDER_ITEM (ORDERID, PRODUCTID, QTY, PRICE)
VALUES ('O-101', 'P-100', 20, 480);

INSERT INTO ORDER_ITEM (ORDERID, PRODUCTID, QTY, PRICE)
VALUES ('O-101', 'P-104', 50, 132);

INSERT INTO ORDER_ITEM (ORDERID, PRODUCTID, QTY, PRICE)
VALUES ('O-102', 'P-210', 30, 96);

INSERT INTO ORDER_ITEM (ORDERID, PRODUCTID, QTY, PRICE)
VALUES ('O-103', 'P-100', 15, 480);

INSERT INTO ORDER_ITEM (ORDERID, PRODUCTID, QTY, PRICE)
VALUES ('O-104', 'P-108', 12, 445);

INSERT INTO ORDER_ITEM (ORDERID, PRODUCTID, QTY, PRICE)
VALUES ('O-105', 'P-104', 25, 132);

INSERT INTO ORDER_ITEM (ORDERID, PRODUCTID, QTY, PRICE)
VALUES ('O-105', 'P-210', 40, 96);

INSERT INTO ORDER_ITEM (ORDERID, PRODUCTID, QTY, PRICE)
VALUES ('O-106', 'P-100', 18, 480);


-- =========================================================
-- DRIVER / EMPLOYEE
-- Only driver IDs are available in the supplied data.
-- Other employee/driver attributes are therefore NULL.
-- =========================================================

INSERT INTO EMPLOYEE (EMPID, ENAME)
VALUES ('D-05', NULL);

INSERT INTO EMPLOYEE (EMPID, ENAME)
VALUES ('D-09', NULL);

INSERT INTO EMPLOYEE (EMPID, ENAME)
VALUES ('D-11', NULL);

INSERT INTO DRIVER (EMPID)
VALUES ('D-05');

INSERT INTO DRIVER (EMPID)
VALUES ('D-09');

INSERT INTO DRIVER (EMPID)
VALUES ('D-11');


-- =========================================================
-- ORDER_DRIVER
-- =========================================================

INSERT INTO ORDER_DRIVER (ORDERID, DRIVERID)
VALUES ('O-101', 'D-05');

INSERT INTO ORDER_DRIVER (ORDERID, DRIVERID)
VALUES ('O-102', 'D-09');

INSERT INTO ORDER_DRIVER (ORDERID, DRIVERID)
VALUES ('O-103', 'D-05');

INSERT INTO ORDER_DRIVER (ORDERID, DRIVERID)
VALUES ('O-104', 'D-05');

INSERT INTO ORDER_DRIVER (ORDERID, DRIVERID)
VALUES ('O-105', 'D-09');

INSERT INTO ORDER_DRIVER (ORDERID, DRIVERID)
VALUES ('O-106', 'D-11');


-- =========================================================
-- DELIVERY_DUTY
-- Primary key = ORDERID + CITY
-- =========================================================

INSERT INTO DELIVERY_DUTY (ORDERID, DRIVERID, CITY)
VALUES ('O-101', 'D-05', 'Chennai');

INSERT INTO DELIVERY_DUTY (ORDERID, DRIVERID, CITY)
VALUES ('O-101', 'D-09', 'Mumbai');

INSERT INTO DELIVERY_DUTY (ORDERID, DRIVERID, CITY)
VALUES ('O-102', 'D-09', 'Mumbai');

INSERT INTO DELIVERY_DUTY (ORDERID, DRIVERID, CITY)
VALUES ('O-103', 'D-05', 'Chennai');

INSERT INTO DELIVERY_DUTY (ORDERID, DRIVERID, CITY)
VALUES ('O-104', 'D-05', 'Chennai');

INSERT INTO DELIVERY_DUTY (ORDERID, DRIVERID, CITY)
VALUES ('O-105', 'D-09', 'Mumbai');

INSERT INTO DELIVERY_DUTY (ORDERID, DRIVERID, CITY)
VALUES ('O-106', 'D-11', 'Chennai');


COMMIT;