// =========================================================
// NEO4J GRAPH DATABASE SEED SCRIPT
// Supply Chain & Logistics Network for DBMS Lab
// Compatible with Neo4j AuraDB (Cloud Free Tier) & Neo4j Desktop
// =========================================================

// Clear existing database
MATCH (n) DETACH DELETE n;

// Create Constraints & Indexes
CREATE CONSTRAINT unique_supplier IF NOT EXISTS FOR (s:Supplier) REQUIRE s.id IS UNIQUE;
CREATE CONSTRAINT unique_product IF NOT EXISTS FOR (p:Product) REQUIRE p.id IS UNIQUE;
CREATE CONSTRAINT unique_warehouse IF NOT EXISTS FOR (w:Warehouse) REQUIRE w.id IS UNIQUE;
CREATE CONSTRAINT unique_customer IF NOT EXISTS FOR (c:Customer) REQUIRE c.id IS UNIQUE;
CREATE CONSTRAINT unique_order IF NOT EXISTS FOR (o:Order) REQUIRE o.id IS UNIQUE;
CREATE CONSTRAINT unique_driver IF NOT EXISTS FOR (d:Driver) REQUIRE d.id IS UNIQUE;

// 1. Suppliers
CREATE (s1:Supplier {id: 'S01', name: 'Apex Agro Supplies', city: 'Chennai', phone: '9845012345', rating: 4.8})
CREATE (s3:Supplier {id: 'S03', name: 'Metro Wholesale Mills', city: 'Mumbai', phone: '9820011122', rating: 4.6})
CREATE (s5:Supplier {id: 'S05', name: 'Delta Cold & Chemicals', city: 'Chennai', phone: '9840199887', rating: 4.9});

// 2. Products (Standard, Perishable, Hazardous)
CREATE (p1:Product {id: 'P-100', name: 'Basmati Rice', category: 'Staples', unitPrice: 480.00})
CREATE (p2:Product {id: 'P-104', name: 'Sunflower Oil', category: 'Oils', unitPrice: 132.00})
CREATE (p3:Product {id: 'P-108', name: 'Sona Masoori', category: 'Staples', unitPrice: 445.00})
CREATE (p4:Product:Perishable {id: 'P-210', name: 'Frozen Peas', category: 'Frozen', unitPrice: 96.00, shelfLifeDays: 90, storageTempC: '-18C'})
CREATE (p5:Product:Hazardous {id: 'P-301', name: 'Industrial Solvent', category: 'Chemicals', unitPrice: 1250.00, hazardClass: 'Class 3 Flammable', handlingNote: 'Keep ventilated'});

// 3. Warehouses
CREATE (w1:Warehouse {id: 'W-01', name: 'Chennai Central Hub', city: 'Chennai', capacity: 50000})
CREATE (w2:Warehouse {id: 'W-02', name: 'Mumbai Port Depot', city: 'Mumbai', capacity: 75000});

// 4. Customers
CREATE (c1:Customer {id: 'C-01', name: 'Shama Retail', street: '12 MG Rd', city: 'Chennai', phone: '9840011223'})
CREATE (c3:Customer {id: 'C-03', name: 'Metro Mart', street: '5 Link Rd', city: 'Mumbai', phone: '9820044556'})
CREATE (c5:Customer {id: 'C-05', name: 'Anand Stores', street: '7 Anna Salai', city: 'Chennai', phone: '9845567788'});

// 5. Orders
CREATE (o1:Order {id: 'O-101', date: '2026-04-02', status: 'SHIPPED', amount: 16200.00})
CREATE (o2:Order {id: 'O-102', date: '2026-04-05', status: 'NEW', amount: 2880.00})
CREATE (o3:Order {id: 'O-103', date: '2026-04-09', status: 'SHIPPED', amount: 7200.00})
CREATE (o4:Order {id: 'O-104', date: '2026-04-11', status: 'NEW', amount: 5340.00})
CREATE (o5:Order {id: 'O-105', date: '2026-04-14', status: 'SHIPPED', amount: 7140.00})
CREATE (o6:Order {id: 'O-106', date: '2026-04-16', status: 'NEW', amount: 8640.00});

// 6. Employees, Drivers & Managers
CREATE (d1:Employee:Driver {id: 'D-05', name: 'Ramesh Kumar', role: 'Driver', licenseNo: 'TN-02-2021-998', expiry: '2028-12-31', salary: 32000})
CREATE (d2:Employee:Driver {id: 'D-09', name: 'Suresh Patil', role: 'Driver', licenseNo: 'MH-01-2020-412', expiry: '2027-08-15', salary: 34000})
CREATE (d3:Employee:Driver {id: 'D-11', name: 'Venkatesh Rao', role: 'Driver', licenseNo: 'TN-05-2022-771', expiry: '2029-05-20', salary: 31000})
CREATE (m1:Employee:Manager {id: 'M-01', name: 'Kavita Sharma', role: 'Warehouse Director', level: 'L3', bonus: 15000, salary: 85000});

// Relationships: SUPPLIES (Supplier -> Product)
CREATE (s1)-[:SUPPLIES {leadTimeDays: 4}]->(p1)
CREATE (s1)-[:SUPPLIES {leadTimeDays: 5}]->(p3)
CREATE (s3)-[:SUPPLIES {leadTimeDays: 3}]->(p2)
CREATE (s5)-[:SUPPLIES {leadTimeDays: 2, coldChain: true}]->(p4)
CREATE (s5)-[:SUPPLIES {leadTimeDays: 7, hazmatCertified: true}]->(p5);

// Relationships: STOCKS (Warehouse -> Product)
CREATE (w1)-[:STOCKS {quantity: 1200}]->(p1)
CREATE (w1)-[:STOCKS {quantity: 850}]->(p3)
CREATE (w1)-[:STOCKS {quantity: 400}]->(p4)
CREATE (w2)-[:STOCKS {quantity: 2500}]->(p2)
CREATE (w2)-[:STOCKS {quantity: 150}]->(p5);

// Relationships: PLACED (Customer -> Order)
CREATE (c1)-[:PLACED]->(o1)
CREATE (c3)-[:PLACED]->(o2)
CREATE (c1)-[:PLACED]->(o3)
CREATE (c5)-[:PLACED]->(o4)
CREATE (c3)-[:PLACED]->(o5)
CREATE (c5)-[:PLACED]->(o6);

// Relationships: CONTAINS (Order -> Product)
CREATE (o1)-[:CONTAINS {qty: 20, price: 480.00}]->(p1)
CREATE (o1)-[:CONTAINS {qty: 50, price: 132.00}]->(p2)
CREATE (o2)-[:CONTAINS {qty: 30, price: 96.00}]->(p4)
CREATE (o3)-[:CONTAINS {qty: 15, price: 480.00}]->(p1)
CREATE (o4)-[:CONTAINS {qty: 12, price: 445.00}]->(p3)
CREATE (o5)-[:CONTAINS {qty: 25, price: 132.00}]->(p2)
CREATE (o5)-[:CONTAINS {qty: 40, price: 96.00}]->(p4)
CREATE (o6)-[:CONTAINS {qty: 18, price: 480.00}]->(p1);

// Relationships: DELIVERS (Driver -> Order)
CREATE (d1)-[:DELIVERS {assignedCity: 'Chennai'}]->(o1)
CREATE (d2)-[:DELIVERS {assignedCity: 'Mumbai'}]->(o2)
CREATE (d1)-[:DELIVERS {assignedCity: 'Chennai'}]->(o3)
CREATE (d1)-[:DELIVERS {assignedCity: 'Chennai'}]->(o4)
CREATE (d2)-[:DELIVERS {assignedCity: 'Mumbai'}]->(o5)
CREATE (d3)-[:DELIVERS {assignedCity: 'Chennai'}]->(o6);

// Relationships: MANAGES (Manager -> Warehouse)
CREATE (m1)-[:MANAGES {since: '2024'}]->(w1);

// Return graph verification summary
MATCH (n) RETURN labels(n) AS EntityType, count(n) AS Count;
