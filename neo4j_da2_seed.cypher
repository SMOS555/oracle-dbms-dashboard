// ========================================================
// NEO4J SEED SCRIPT (EXACTLY MATCHING 'da2' BRANCH DATA)
// 22 TABLES - NO EXTRA TABLES, NO EXTRA SAMPLE RECORDS
// ========================================================

// 0. WIPE OLD DATA (OPTIONAL: run if resetting your database)
MATCH (n) DETACH DELETE n;

// 1. CITY (2 records)
UNWIND [
  {city: 'Chennai', state: 'Tamil Nadu'},
  {city: 'Mumbai', state: 'Maharashtra'}
] AS c
MERGE (n:CITY {CITY: c.city})
SET n.STATE = c.state, n.id = c.city, n.name = c.city;

// 2. SUPPLIER (3 records)
UNWIND [
  {id: 'S01'},
  {id: 'S03'},
  {id: 'S05'}
] AS s
MERGE (n:SUPPLIER {SUPPLIERID: s.id})
SET n.id = s.id;

// 3. SUPPLIER_PHONE (5 records)
UNWIND [
  {supplierId: 'S01', phone: '9845012345'},
  {supplierId: 'S01', phone: '08022334455'},
  {supplierId: 'S03', phone: '9820011122'},
  {supplierId: 'S05', phone: '9840199887'},
  {supplierId: 'S05', phone: '04424851212'}
] AS sp
MERGE (n:SUPPLIER_PHONE {SUPPLIERID: sp.supplierId, PHONE: sp.phone})
SET n.id = sp.supplierId + '_' + sp.phone;

// 4. PRODUCT (5 records)
UNWIND [
  {id: 'P-100', name: 'Basmati Rice', category: 'Staples', unitPrice: 480},
  {id: 'P-104', name: 'Sunflower Oil', category: 'Oils', unitPrice: 132},
  {id: 'P-108', name: 'Sona Masoori', category: 'Staples', unitPrice: 445},
  {id: 'P-210', name: 'Frozen Peas', category: 'Frozen', unitPrice: 96},
  {id: 'P-420', name: 'Industrial Sanitizer', category: 'Chemicals', unitPrice: 350.00}
] AS p
MERGE (n:PRODUCT {PRODUCTID: p.id})
SET n.PNAME = p.name, n.CATEGORY = p.category, n.UNITPRICE = p.unitPrice, n.id = p.id, n.name = p.name;

// 5. PERISHABLE_PRODUCT & HAZARDOUS_PRODUCT
MERGE (n:PERISHABLE_PRODUCT {PRODUCTID: 'P-210'})
SET n.SHELFLIFEDAYS = 90, n.STORAGETEMPC = '-18C', n.id = 'P-210';

MERGE (n:HAZARDOUS_PRODUCT {PRODUCTID: 'P-420'})
SET n.HAZARDCLASS = 'Class 3 Flammable Liquid', n.HANDLINGNOTE = 'Keep away from heat and open flame', n.id = 'P-420';

// 6. CUSTOMER (3 records)
UNWIND [
  {id: 'C-01', name: 'Shama Retail', street: '12 MG Rd', city: 'Chennai', phone: '9840011223'},
  {id: 'C-03', name: 'Metro Mart', street: '5 Link Rd', city: 'Mumbai', phone: '9820044556'},
  {id: 'C-05', name: 'Anand Stores', street: '7 Anna Salai', city: 'Chennai', phone: '9845567788'}
] AS c
MERGE (n:CUSTOMER {CUSTID: c.id})
SET n.CNAME = c.name, n.STREET = c.street, n.CITY = c.city, n.PHONE = c.phone, n.id = c.id, n.name = c.name;

// 7. ORDERS (6 records)
UNWIND [
  {id: 'O-101', date: '2026-04-02', status: 'SHIPPED', custId: 'C-01'},
  {id: 'O-102', date: '2026-04-05', status: 'NEW', custId: 'C-03'},
  {id: 'O-103', date: '2026-04-09', status: 'SHIPPED', custId: 'C-01'},
  {id: 'O-104', date: '2026-04-11', status: 'NEW', custId: 'C-05'},
  {id: 'O-105', date: '2026-04-14', status: 'SHIPPED', custId: 'C-03'},
  {id: 'O-106', date: '2026-04-16', status: 'NEW', custId: 'C-05'}
] AS o
MERGE (n:ORDERS {ORDERID: o.id})
SET n.ORDERDATE = o.date, n.STATUS = o.status, n.CUSTID = o.custId, n.id = o.id;

// 8. ORDER_ITEM (8 records)
UNWIND [
  {orderId: 'O-101', productId: 'P-100', qty: 20, price: 480},
  {orderId: 'O-101', productId: 'P-104', qty: 50, price: 132},
  {orderId: 'O-102', productId: 'P-210', qty: 30, price: 96},
  {orderId: 'O-103', productId: 'P-100', qty: 15, price: 480},
  {orderId: 'O-104', productId: 'P-108', qty: 12, price: 445},
  {orderId: 'O-105', productId: 'P-104', qty: 25, price: 132},
  {orderId: 'O-105', productId: 'P-210', qty: 40, price: 96},
  {orderId: 'O-106', productId: 'P-100', qty: 18, price: 480}
] AS oi
MERGE (n:ORDER_ITEM {ORDERID: oi.orderId, PRODUCTID: oi.productId})
SET n.QTY = oi.qty, n.PRICE = oi.price, n.id = oi.orderId + '_' + oi.productId;

// 9. EMPLOYEE, DRIVER, MANAGER (5 records)
UNWIND [
  {id: 'D-05', name: null, role: 'Driver', salary: null},
  {id: 'D-09', name: null, role: 'Driver', salary: null},
  {id: 'D-11', name: null, role: 'Driver', salary: null},
  {id: 'M-01', name: 'Kavita Sharma', role: 'Manager', salary: 85000},
  {id: 'M-02', name: 'Arun Nair', role: 'Manager', salary: 82000}
] AS e
MERGE (n:EMPLOYEE {EMPID: e.id})
SET n.ENAME = e.name, n.ROLE = e.role, n.SALARY = e.salary, n.id = e.id, n.name = e.name;

MERGE (n:DRIVER {EMPID: 'D-05'}) SET n.id = 'D-05';
MERGE (n:DRIVER {EMPID: 'D-09'}) SET n.id = 'D-09';
MERGE (n:DRIVER {EMPID: 'D-11'}) SET n.id = 'D-11';

MERGE (n:MANAGER {EMPID: 'M-01'}) SET n.LEVELNO = 'Senior L3', n.BONUS = 15000.00, n.id = 'M-01';
MERGE (n:MANAGER {EMPID: 'M-02'}) SET n.LEVELNO = 'Regional L2', n.BONUS = 12000.00, n.id = 'M-02';

// 10. WAREHOUSE (3 records)
UNWIND [
  {id: 'W-01', name: 'Central Chennai Depot', city: 'Chennai', capacity: 15000, managerId: 'M-01'},
  {id: 'W-02', name: 'Navi Mumbai Hub', city: 'Mumbai', capacity: 22000, managerId: 'M-02'},
  {id: 'W-03', name: 'Whitefield Logistics Center', city: 'Bengaluru', capacity: 18000, managerId: 'M-01'}
] AS w
MERGE (n:WAREHOUSE {WAREHOUSEID: w.id})
SET n.WNAME = w.name, n.CITY = w.city, n.CAPACITY = w.capacity, n.MANAGERID = w.managerId, n.id = w.id, n.name = w.name;

// 11. ORDER_DRIVER (6 records)
UNWIND [
  {orderId: 'O-101', driverId: 'D-05'},
  {orderId: 'O-102', driverId: 'D-09'},
  {orderId: 'O-103', driverId: 'D-05'},
  {orderId: 'O-104', driverId: 'D-05'},
  {orderId: 'O-105', driverId: 'D-09'},
  {orderId: 'O-106', driverId: 'D-11'}
] AS od
MERGE (n:ORDER_DRIVER {ORDERID: od.orderId, DRIVERID: od.driverId})
SET n.id = od.orderId + '_' + od.driverId;

// 12. DELIVERY_DUTY (8 records)
UNWIND [
  {orderId: 'O-101', driverId: 'D-05', city: 'Chennai'},
  {orderId: 'O-101', driverId: 'D-09', city: 'Mumbai'},
  {orderId: 'O-102', driverId: 'D-09', city: 'Mumbai'},
  {orderId: 'O-103', driverId: 'D-05', city: 'Chennai'},
  {orderId: 'O-104', driverId: 'D-05', city: 'Chennai'},
  {orderId: 'O-105', driverId: 'D-09', city: 'Mumbai'},
  {orderId: 'O-106', driverId: 'D-11', city: 'Chennai'},
  {orderId: 'O-107', driverId: 'D-11', city: 'Bengaluru'}
] AS dd
MERGE (n:DELIVERY_DUTY {ORDERID: dd.orderId, CITY: dd.city})
SET n.DRIVERID = dd.driverId, n.id = dd.orderId + '_' + dd.city;

// 13. DRIVER_CITY (3 records)
UNWIND [
  {driverId: 'D-05', city: 'Chennai'},
  {driverId: 'D-09', city: 'Mumbai'},
  {driverId: 'D-11', city: 'Bengaluru'}
] AS dc
MERGE (n:DRIVER_CITY {DRIVERID: dc.driverId, CITY: dc.city})
SET n.id = dc.driverId + '_' + dc.city;

// 14. STOCK (5 records)
UNWIND [
  {warehouseId: 'W-01', productId: 'P-100', quantity: 450},
  {warehouseId: 'W-01', productId: 'P-104', quantity: 600},
  {warehouseId: 'W-01', productId: 'P-210', quantity: 300},
  {warehouseId: 'W-02', productId: 'P-100', quantity: 800},
  {warehouseId: 'W-02', productId: 'P-108', quantity: 520}
] AS st
MERGE (n:STOCK {WAREHOUSEID: st.warehouseId, PRODUCTID: st.productId})
SET n.QUANTITY = st.quantity, n.id = st.warehouseId + '_' + st.productId;

// 15. SUPPLIES (2 records)
MERGE (n:SUPPLIES {SUPPLIERID: 'S01', PRODUCTID: 'P-100'})
SET n.LEADTIMEDAYS = 3, n.id = 'S01_P100';

MERGE (n:SUPPLIES {SUPPLIERID: 'S05', PRODUCTID: 'P-210'})
SET n.LEADTIMEDAYS = 4, n.id = 'S05_P210';

// 16. INSPECTION (2 records)
MERGE (n:INSPECTION {INSPID: 'INS-01'})
SET n.SUPPLIERID = 'S01', n.PRODUCTID = 'P-100', n.INSPDATE = '2026-04-01', n.RESULT = 'PASSED', n.id = 'INS-01';

MERGE (n:INSPECTION {INSPID: 'INS-02'})
SET n.SUPPLIERID = 'S05', n.PRODUCTID = 'P-210', n.INSPDATE = '2026-04-04', n.RESULT = 'PASSED', n.id = 'INS-02';

// 17. GOODS_MOVEMENT, SHIPMENT, GOODS_RETURN
MERGE (n:GOODS_MOVEMENT {MOVEMENTID: 'M-01'})
SET n.ORDERID = 'O-101', n.WAREHOUSEID = 'W-01', n.MOVEDATE = '2026-04-03', n.STATUS = 'DISPATCHED', n.id = 'M-01';

MERGE (n:GOODS_MOVEMENT {MOVEMENTID: 'M-02'})
SET n.ORDERID = 'O-103', n.WAREHOUSEID = 'W-01', n.MOVEDATE = '2026-04-10', n.STATUS = 'DISPATCHED', n.id = 'M-02';

MERGE (n:GOODS_MOVEMENT {MOVEMENTID: 'M-03'})
SET n.ORDERID = 'O-105', n.WAREHOUSEID = 'W-02', n.MOVEDATE = '2026-04-15', n.STATUS = 'DISPATCHED', n.id = 'M-03';

MERGE (n:SHIPMENT {MOVEMENTID: 'M-01'})
SET n.DELIVERYDATE = '2026-04-04', n.id = 'M-01';

MERGE (n:SHIPMENT {MOVEMENTID: 'M-02'})
SET n.DELIVERYDATE = '2026-04-11', n.id = 'M-02';

MERGE (n:SHIPMENT {MOVEMENTID: 'M-03'})
SET n.DELIVERYDATE = '2026-04-16', n.id = 'M-03';

MERGE (n:GOODS_RETURN {MOVEMENTID: 'M-02'})
SET n.REASON = 'Damaged packaging during transit', n.REFUNDAMT = 480.00, n.id = 'M-02';

// 18. RELATIONSHIPS
MATCH (s:SUPPLIER {SUPPLIERID: 'S01'}), (p:PRODUCT {PRODUCTID: 'P-100'}) MERGE (s)-[:SUPPLIES {leadTimeDays: 3}]->(p);
MATCH (s:SUPPLIER {SUPPLIERID: 'S05'}), (p:PRODUCT {PRODUCTID: 'P-210'}) MERGE (s)-[:SUPPLIES {leadTimeDays: 4}]->(p);

MATCH (w:WAREHOUSE {WAREHOUSEID: 'W-01'}), (p:PRODUCT {PRODUCTID: 'P-100'}) MERGE (w)-[:STOCKS {quantity: 450}]->(p);
MATCH (w:WAREHOUSE {WAREHOUSEID: 'W-01'}), (p:PRODUCT {PRODUCTID: 'P-104'}) MERGE (w)-[:STOCKS {quantity: 600}]->(p);
MATCH (w:WAREHOUSE {WAREHOUSEID: 'W-01'}), (p:PRODUCT {PRODUCTID: 'P-210'}) MERGE (w)-[:STOCKS {quantity: 300}]->(p);
MATCH (w:WAREHOUSE {WAREHOUSEID: 'W-02'}), (p:PRODUCT {PRODUCTID: 'P-100'}) MERGE (w)-[:STOCKS {quantity: 800}]->(p);
MATCH (w:WAREHOUSE {WAREHOUSEID: 'W-02'}), (p:PRODUCT {PRODUCTID: 'P-108'}) MERGE (w)-[:STOCKS {quantity: 520}]->(p);

MATCH (c:CUSTOMER {CUSTID: 'C-01'}), (o:ORDERS {ORDERID: 'O-101'}) MERGE (c)-[:PLACED]->(o);
MATCH (c:CUSTOMER {CUSTID: 'C-03'}), (o:ORDERS {ORDERID: 'O-102'}) MERGE (c)-[:PLACED]->(o);
MATCH (c:CUSTOMER {CUSTID: 'C-01'}), (o:ORDERS {ORDERID: 'O-103'}) MERGE (c)-[:PLACED]->(o);
MATCH (c:CUSTOMER {CUSTID: 'C-05'}), (o:ORDERS {ORDERID: 'O-104'}) MERGE (c)-[:PLACED]->(o);
MATCH (c:CUSTOMER {CUSTID: 'C-03'}), (o:ORDERS {ORDERID: 'O-105'}) MERGE (c)-[:PLACED]->(o);
MATCH (c:CUSTOMER {CUSTID: 'C-05'}), (o:ORDERS {ORDERID: 'O-106'}) MERGE (c)-[:PLACED]->(o);

MATCH (o:ORDERS {ORDERID: 'O-101'}), (p:PRODUCT {PRODUCTID: 'P-100'}) MERGE (o)-[:CONTAINS {qty: 20, price: 480}]->(p);
MATCH (o:ORDERS {ORDERID: 'O-101'}), (p:PRODUCT {PRODUCTID: 'P-104'}) MERGE (o)-[:CONTAINS {qty: 50, price: 132}]->(p);
MATCH (o:ORDERS {ORDERID: 'O-102'}), (p:PRODUCT {PRODUCTID: 'P-210'}) MERGE (o)-[:CONTAINS {qty: 30, price: 96}]->(p);
MATCH (o:ORDERS {ORDERID: 'O-103'}), (p:PRODUCT {PRODUCTID: 'P-100'}) MERGE (o)-[:CONTAINS {qty: 15, price: 480}]->(p);
MATCH (o:ORDERS {ORDERID: 'O-104'}), (p:PRODUCT {PRODUCTID: 'P-108'}) MERGE (o)-[:CONTAINS {qty: 12, price: 445}]->(p);
MATCH (o:ORDERS {ORDERID: 'O-105'}), (p:PRODUCT {PRODUCTID: 'P-104'}) MERGE (o)-[:CONTAINS {qty: 25, price: 132}]->(p);
MATCH (o:ORDERS {ORDERID: 'O-105'}), (p:PRODUCT {PRODUCTID: 'P-210'}) MERGE (o)-[:CONTAINS {qty: 40, price: 96}]->(p);
MATCH (o:ORDERS {ORDERID: 'O-106'}), (p:PRODUCT {PRODUCTID: 'P-100'}) MERGE (o)-[:CONTAINS {qty: 18, price: 480}]->(p);

MATCH (d:DRIVER {EMPID: 'D-05'}), (o:ORDERS {ORDERID: 'O-101'}) MERGE (d)-[:DELIVERS {assignedCity: 'Chennai'}]->(o);
MATCH (d:DRIVER {EMPID: 'D-09'}), (o:ORDERS {ORDERID: 'O-102'}) MERGE (d)-[:DELIVERS {assignedCity: 'Mumbai'}]->(o);
MATCH (d:DRIVER {EMPID: 'D-05'}), (o:ORDERS {ORDERID: 'O-103'}) MERGE (d)-[:DELIVERS {assignedCity: 'Chennai'}]->(o);
MATCH (d:DRIVER {EMPID: 'D-05'}), (o:ORDERS {ORDERID: 'O-104'}) MERGE (d)-[:DELIVERS {assignedCity: 'Chennai'}]->(o);
MATCH (d:DRIVER {EMPID: 'D-09'}), (o:ORDERS {ORDERID: 'O-105'}) MERGE (d)-[:DELIVERS {assignedCity: 'Mumbai'}]->(o);
MATCH (d:DRIVER {EMPID: 'D-11'}), (o:ORDERS {ORDERID: 'O-106'}) MERGE (d)-[:DELIVERS {assignedCity: 'Chennai'}]->(o);

MATCH (m:MANAGER {EMPID: 'M-01'}), (w:WAREHOUSE {WAREHOUSEID: 'W-01'}) MERGE (m)-[:MANAGES]->(w);
MATCH (m:MANAGER {EMPID: 'M-02'}), (w:WAREHOUSE {WAREHOUSEID: 'W-02'}) MERGE (m)-[:MANAGES]->(w);

MATCH (d:DRIVER), (e:EMPLOYEE {EMPID: d.EMPID}) MERGE (d)-[:IS_A]->(e);
MATCH (m:MANAGER), (e:EMPLOYEE {EMPID: m.EMPID}) MERGE (m)-[:IS_A]->(e);
MATCH (pp:PERISHABLE_PRODUCT), (p:PRODUCT {PRODUCTID: pp.PRODUCTID}) MERGE (pp)-[:IS_A]->(p);
MATCH (hp:HAZARDOUS_PRODUCT), (p:PRODUCT {PRODUCTID: hp.PRODUCTID}) MERGE (hp)-[:IS_A]->(p);
MATCH (sh:SHIPMENT), (gm:GOODS_MOVEMENT {MOVEMENTID: sh.MOVEMENTID}) MERGE (sh)-[:IS_A]->(gm);
MATCH (gr:GOODS_RETURN), (gm:GOODS_MOVEMENT {MOVEMENTID: gr.MOVEMENTID}) MERGE (gr)-[:IS_A]->(gm);
