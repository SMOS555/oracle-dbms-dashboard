package com.dbms.dashboard.service;

import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for Modern Graph Database Architecture (Neo4j).
 * Functions as the sole primary database engine for the application:
 * - Direct Bolt protocol connectivity to Neo4j AuraDB Cloud.
 * - Automatic graph seeding on initial empty AuraDB instance.
 * - Complete 22-entity node label and relationship modeling.
 * - High-fidelity in-memory graph engine fallback.
 */
@Service
public class GraphDatabaseService {

    private static final Logger log = LoggerFactory.getLogger(GraphDatabaseService.class);

    @Value("${neo4j.uri:}")
    private String neo4jUri;

    @Value("${neo4j.username:neo4j}")
    private String neo4jUsername;

    @Value("${neo4j.password:}")
    private String neo4jPassword;

    private Driver driver;
    private boolean isLiveConnected = false;
    private String connectionMessage = "Initialized with In-Memory Neo4j Graph Engine";

    public static final List<String> CANONICAL_LABELS = List.of(
        "CITY",
        "CUSTOMER",
        "DELIVERY_DUTY",
        "DRIVER",
        "DRIVER_CITY",
        "EMPLOYEE",
        "GOODS_MOVEMENT",
        "GOODS_RETURN",
        "HAZARDOUS_PRODUCT",
        "INSPECTION",
        "MANAGER",
        "ORDERS",
        "ORDER_DRIVER",
        "ORDER_ITEM",
        "PERISHABLE_PRODUCT",
        "PRODUCT",
        "SHIPMENT",
        "STOCK",
        "SUPPLIER",
        "SUPPLIER_PHONE",
        "SUPPLIES",
        "WAREHOUSE"
    );

    // In-memory graph model for Supply Chain Network (fallback & instant response)
    private final List<Map<String, Object>> defaultNodes = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> defaultLinks = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        initDefaultGraph();
        tryConnectNeo4j();
    }

    @PreDestroy
    public void close() {
        if (driver != null) {
            try {
                driver.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void tryConnectNeo4j() {
        String uri = System.getenv("NEO4J_URI");
        if (uri == null || uri.isBlank()) {
            uri = neo4jUri;
        }

        String user = System.getenv("NEO4J_USERNAME");
        if (user == null || user.isBlank()) {
            user = neo4jUsername;
        }

        String pass = System.getenv("NEO4J_PASSWORD");
        if (pass == null || pass.isBlank()) {
            pass = neo4jPassword;
        }

        if (uri != null && !uri.isBlank() && pass != null && !pass.isBlank()) {
            try {
                driver = GraphDatabase.driver(uri, AuthTokens.basic(user, pass));
                driver.verifyConnectivity();
                isLiveConnected = true;
                connectionMessage = "Connected live to Neo4j AuraDB (" + uri + ")";
                log.info("Successfully connected to live Neo4j AuraDB: {}", uri);
                autoSeedIfEmpty();
                deduplicateLiveGraph();
            } catch (Exception e) {
                isLiveConnected = false;
                connectionMessage = "Fallback to Built-in Neo4j Engine (AuraDB standby: " + e.getMessage() + ")";
                log.warn("Neo4j AuraDB connection standby: {}", e.getMessage());
            }
        } else {
            isLiveConnected = false;
            connectionMessage = "Running on Built-in Neo4j Graph Engine (Ready for live Neo4j AuraDB credentials)";
        }
    }

    public synchronized Map<String, Object> connectWithCredentials(String uri, String username, String password) {
        try {
            if (driver != null) {
                try { driver.close(); } catch (Exception ignored) {}
            }
            driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
            driver.verifyConnectivity();
            isLiveConnected = true;
            connectionMessage = "Connected live to Neo4j (" + uri + ")";
            autoSeedIfEmpty();
            deduplicateLiveGraph();
            return Map.of("success", true, "message", connectionMessage, "live", true);
        } catch (Exception e) {
            isLiveConnected = false;
            connectionMessage = "Connection failed: " + e.getMessage() + ". Retaining built-in graph engine.";
            return Map.of("success", false, "message", e.getMessage(), "live", false);
        }
    }

    /**
     * If the connected Neo4j database is empty (0 nodes), auto-seeds all 22 entities and relationships.
     */
    public Map<String, Object> seedLiveDatabase() {
        if (!isLiveConnected || driver == null) {
            initDefaultGraph();
            return Map.of("success", true, "message", "Seeded in-memory graph with all 22 entities.", "source", "In-Memory Graph Engine");
        }
        try (Session session = driver.session()) {
            seedLiveNeo4j(session);
            return Map.of("success", true, "message", "Successfully seeded live Neo4j AuraDB with all 22 entities and relationships.", "source", "Live Neo4j AuraDB");
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage() != null ? e.getMessage() : "Seeding failed");
        }
    }

    private void autoSeedIfEmpty() {
        if (!isLiveConnected || driver == null) return;
        try (Session session = driver.session()) {
            int count = session.run("MATCH (n) RETURN count(n) AS c").single().get("c").asInt();
            int labelCount = 0;
            try {
                labelCount = session.run("CALL db.labels() YIELD label RETURN count(label) AS c").single().get("c").asInt();
            } catch (Exception ignored) {}

            if (count == 0 || labelCount < 18) {
                log.info("Neo4j database has {} nodes and {} labels. Auto-seeding all 22 entities...", count, labelCount);
                seedLiveNeo4j(session);
                log.info("Neo4j automated graph seeding completed successfully.");
            }
        } catch (Exception e) {
            log.warn("Auto-seeding check encountered exception: {}", e.getMessage());
        }
    }

    private void seedLiveNeo4j(Session session) {
        // 1. Cities
        session.run("UNWIND [" +
            "{city: 'Chennai', state: 'Tamil Nadu'}," +
            "{city: 'Mumbai', state: 'Maharashtra'}," +
            "{city: 'Bangalore', state: 'Karnataka'}," +
            "{city: 'Delhi', state: 'Delhi NCR'}," +
            "{city: 'Hyderabad', state: 'Telangana'}" +
            "] AS c MERGE (n:CITY {CITY: c.city, STATE: c.state, id: c.city, name: c.city})");

        // 2. Suppliers
        session.run("UNWIND [" +
            "{id: 'S01', name: 'Apex Agro Supplies', city: 'Chennai', rating: 4.8, phone: '9845012345'}," +
            "{id: 'S03', name: 'Metro Wholesale Mills', city: 'Mumbai', rating: 4.6, phone: '9820011122'}," +
            "{id: 'S05', name: 'Delta Cold & Chemicals', city: 'Chennai', rating: 4.9, phone: '9840199887'}" +
            "] AS s MERGE (n:SUPPLIER {SUPPLIERID: s.id, SNAME: s.name, CITY: s.city, RATING: s.rating, PHONE: s.phone, id: s.id, name: s.name})");

        // 3. Products
        session.run("UNWIND [" +
            "{id: 'P-100', name: 'Basmati Rice', category: 'Staples', unitPrice: 480.00}," +
            "{id: 'P-104', name: 'Sunflower Oil', category: 'Oils', unitPrice: 132.00}," +
            "{id: 'P-108', name: 'Sona Masoori', category: 'Staples', unitPrice: 445.00}," +
            "{id: 'P-210', name: 'Frozen Peas', category: 'Frozen', unitPrice: 96.00}," +
            "{id: 'P-301', name: 'Industrial Solvent', category: 'Chemicals', unitPrice: 1250.00}" +
            "] AS p MERGE (n:PRODUCT {PRODUCTID: p.id, PNAME: p.name, CATEGORY: p.category, UNITPRICE: p.unitPrice, id: p.id, name: p.name})");

        // 4. Product Subtypes
        session.run("MERGE (n:PERISHABLE_PRODUCT {PRODUCTID: 'P-210', SHELFLIFEDAYS: 90, STORAGETEMPC: '-18°C', id: 'P-210', name: 'Frozen Peas [Perishable]'})");
        session.run("MERGE (n:HAZARDOUS_PRODUCT {PRODUCTID: 'P-301', HAZARDCLASS: 'Class 3 Flammable', HANDLINGNOTE: 'Keep away from sparks and high temperature', id: 'P-301', name: 'Industrial Solvent [Hazmat]'})");

        // 5. Warehouses
        session.run("UNWIND [" +
            "{id: 'W-01', name: 'Chennai Central Hub', city: 'Chennai', capacity: 50000, managerId: 'M-01'}," +
            "{id: 'W-02', name: 'Mumbai Port Depot', city: 'Mumbai', capacity: 75000, managerId: 'M-02'}" +
            "] AS w MERGE (n:WAREHOUSE {WAREHOUSEID: w.id, WNAME: w.name, CITY: w.city, CAPACITY: w.capacity, MANAGERID: w.managerId, id: w.id, name: w.name})");

        // 6. Customers
        session.run("UNWIND [" +
            "{id: 'C-01', name: 'Shama Retail', street: '12 MG Rd', city: 'Chennai', phone: '9840011223'}," +
            "{id: 'C-03', name: 'Metro Mart', street: '5 Link Rd', city: 'Mumbai', phone: '9820044556'}," +
            "{id: 'C-05', name: 'Anand Stores', street: '7 Anna Salai', city: 'Chennai', phone: '9845567788'}" +
            "] AS c MERGE (n:CUSTOMER {CUSTID: c.id, CNAME: c.name, STREET: c.street, CITY: c.city, PHONE: c.phone, id: c.id, name: c.name})");

        // 7. Orders
        session.run("UNWIND [" +
            "{id: 'O-101', date: '2026-04-02', status: 'SHIPPED', custId: 'C-01', amount: 16200}," +
            "{id: 'O-102', date: '2026-04-05', status: 'NEW', custId: 'C-03', amount: 2880}," +
            "{id: 'O-103', date: '2026-04-09', status: 'SHIPPED', custId: 'C-01', amount: 7200}," +
            "{id: 'O-104', date: '2026-04-11', status: 'NEW', custId: 'C-05', amount: 5340}," +
            "{id: 'O-105', date: '2026-04-14', status: 'SHIPPED', custId: 'C-03', amount: 7140}," +
            "{id: 'O-106', date: '2026-04-16', status: 'NEW', custId: 'C-05', amount: 8640}" +
            "] AS o MERGE (n:ORDERS {ORDERID: o.id, ORDERDATE: o.date, STATUS: o.status, CUSTID: o.custId, AMOUNT: o.amount, id: o.id, name: 'Order #' + o.id})");

        // 8. Order Items
        session.run("UNWIND [" +
            "{orderId: 'O-101', productId: 'P-100', qty: 20, price: 480.00}," +
            "{orderId: 'O-101', productId: 'P-104', qty: 50, price: 132.00}," +
            "{orderId: 'O-102', productId: 'P-210', qty: 30, price: 96.00}," +
            "{orderId: 'O-103', productId: 'P-100', qty: 15, price: 480.00}," +
            "{orderId: 'O-104', productId: 'P-108', qty: 12, price: 445.00}," +
            "{orderId: 'O-105', productId: 'P-104', qty: 25, price: 132.00}," +
            "{orderId: 'O-105', productId: 'P-210', qty: 40, price: 96.00}," +
            "{orderId: 'O-106', productId: 'P-100', qty: 18, price: 480.00}" +
            "] AS oi MERGE (n:ORDER_ITEM {ORDERID: oi.orderId, PRODUCTID: oi.productId, QTY: oi.qty, PRICE: oi.price, id: oi.orderId + '_' + oi.productId})");

        // 9. Employees, Drivers, Managers
        session.run("UNWIND [" +
            "{id: 'D-05', name: 'Ramesh Kumar', role: 'Driver', salary: 32000, warehouseId: 'W-01', supervisorId: 'M-01'}," +
            "{id: 'D-09', name: 'Suresh Patil', role: 'Driver', salary: 34000, warehouseId: 'W-02', supervisorId: 'M-01'}," +
            "{id: 'D-11', name: 'Venkatesh Rao', role: 'Driver', salary: 31000, warehouseId: 'W-01', supervisorId: 'M-01'}," +
            "{id: 'M-01', name: 'Kavita Sharma', role: 'Manager', salary: 85000, warehouseId: 'W-01'}" +
            "] AS e MERGE (n:EMPLOYEE {EMPID: e.id}) SET n.ENAME = e.name, n.ROLE = e.role, n.SALARY = e.salary, n.WAREHOUSEID = e.warehouseId, n.SUPERVISORID = e.supervisorId, n.id = e.id, n.name = e.name");

        session.run("MERGE (n:DRIVER {EMPID: 'D-05', LICENSENO: 'TN-02-2021-998', EXPIRY: '2028-12-31', id: 'D-05', name: 'Ramesh Kumar (Driver)'})");
        session.run("MERGE (n:DRIVER {EMPID: 'D-09', LICENSENO: 'MH-01-2020-412', EXPIRY: '2027-08-15', id: 'D-09', name: 'Suresh Patil (Driver)'})");
        session.run("MERGE (n:DRIVER {EMPID: 'D-11', LICENSENO: 'TN-05-2022-771', EXPIRY: '2029-05-20', id: 'D-11', name: 'Venkatesh Rao (Driver)'})");
        session.run("MERGE (n:MANAGER {EMPID: 'M-01', LEVELNO: 'L3', BONUS: 15000.00, id: 'M-01', name: 'Kavita Sharma (Manager)'})");

        // 10. Secondary Entities
        session.run("MERGE (n:INSPECTION {INSPID: 'INSP-01', SUPPLIERID: 'S01', PRODUCTID: 'P-100', INSPDATE: '2026-03-15', RESULT: 'PASSED_GRADE_A', id: 'INSP-01'})");
        session.run("MERGE (n:INSPECTION {INSPID: 'INSP-02', SUPPLIERID: 'S05', PRODUCTID: 'P-210', INSPDATE: '2026-03-20', RESULT: 'PASSED_COLD_CHAIN', id: 'INSP-02'})");

        session.run("MERGE (n:GOODS_MOVEMENT {MOVEMENTID: 'MOV-01', ORDERID: 'O-101', WAREHOUSEID: 'W-01', MOVEDATE: '2026-04-03', STATUS: 'DISPATCHED', id: 'MOV-01'})");
        session.run("MERGE (n:GOODS_MOVEMENT {MOVEMENTID: 'MOV-02', ORDERID: 'O-103', WAREHOUSEID: 'W-01', MOVEDATE: '2026-04-10', STATUS: 'DELIVERED', id: 'MOV-02'})");
        session.run("MERGE (n:GOODS_MOVEMENT {MOVEMENTID: 'MOV-03', ORDERID: 'O-105', WAREHOUSEID: 'W-02', MOVEDATE: '2026-04-15', STATUS: 'DELIVERED', id: 'MOV-03'})");

        session.run("MERGE (n:SHIPMENT {MOVEMENTID: 'MOV-01', DELIVERYDATE: '2026-04-04', id: 'MOV-01'})");
        session.run("MERGE (n:SHIPMENT {MOVEMENTID: 'MOV-02', DELIVERYDATE: '2026-04-12', id: 'MOV-02'})");
        session.run("MERGE (n:GOODS_RETURN {MOVEMENTID: 'MOV-03', REASON: 'Packaging damaged during secondary transit', REFUNDAMT: 1250.00, id: 'MOV-03'})");

        session.run("MERGE (n:SUPPLIER_PHONE {SUPPLIERID: 'S01', PHONE: '9845012345', id: 'S01_P1'})");
        session.run("MERGE (n:SUPPLIER_PHONE {SUPPLIERID: 'S03', PHONE: '9820011122', id: 'S03_P1'})");
        session.run("MERGE (n:DRIVER_CITY {DRIVERID: 'D-05', CITY: 'Chennai', id: 'D05_Chennai'})");
        session.run("MERGE (n:DRIVER_CITY {DRIVERID: 'D-09', CITY: 'Mumbai', id: 'D09_Mumbai'})");
        session.run("MERGE (n:ORDER_DRIVER {ORDERID: 'O-101', DRIVERID: 'D-05', id: 'O101_D05'})");
        session.run("MERGE (n:ORDER_DRIVER {ORDERID: 'O-102', DRIVERID: 'D-09', id: 'O102_D09'})");
        session.run("MERGE (n:DELIVERY_DUTY {ORDERID: 'O-101', DRIVERID: 'D-05', CITY: 'Chennai', id: 'DD_1'})");
        session.run("MERGE (n:DELIVERY_DUTY {ORDERID: 'O-102', DRIVERID: 'D-09', CITY: 'Mumbai', id: 'DD_2'})");
        session.run("MERGE (n:STOCK {WAREHOUSEID: 'W-01', PRODUCTID: 'P-100', QUANTITY: 1200, id: 'ST_1'})");
        session.run("MERGE (n:STOCK {WAREHOUSEID: 'W-02', PRODUCTID: 'P-104', QUANTITY: 2500, id: 'ST_2'})");
        session.run("MERGE (n:SUPPLIES {SUPPLIERID: 'S01', PRODUCTID: 'P-100', LEADTIMEDAYS: 4, id: 'SUP_1'})");

        // 11. Interconnecting Graph Relationships
        session.run("MATCH (s:SUPPLIER {SUPPLIERID: 'S01'}), (p:PRODUCT {PRODUCTID: 'P-100'}) MERGE (s)-[:SUPPLIES {leadTimeDays: 4}]->(p)");
        session.run("MATCH (s:SUPPLIER {SUPPLIERID: 'S01'}), (p:PRODUCT {PRODUCTID: 'P-108'}) MERGE (s)-[:SUPPLIES {leadTimeDays: 5}]->(p)");
        session.run("MATCH (s:SUPPLIER {SUPPLIERID: 'S03'}), (p:PRODUCT {PRODUCTID: 'P-104'}) MERGE (s)-[:SUPPLIES {leadTimeDays: 3}]->(p)");
        session.run("MATCH (s:SUPPLIER {SUPPLIERID: 'S05'}), (p:PRODUCT {PRODUCTID: 'P-210'}) MERGE (s)-[:SUPPLIES {leadTimeDays: 2, coldChain: true}]->(p)");
        session.run("MATCH (s:SUPPLIER {SUPPLIERID: 'S05'}), (p:PRODUCT {PRODUCTID: 'P-301'}) MERGE (s)-[:SUPPLIES {leadTimeDays: 7, hazmatCertified: true}]->(p)");

        session.run("MATCH (w:WAREHOUSE {WAREHOUSEID: 'W-01'}), (p:PRODUCT {PRODUCTID: 'P-100'}) MERGE (w)-[:STOCKS {quantity: 1200}]->(p)");
        session.run("MATCH (w:WAREHOUSE {WAREHOUSEID: 'W-01'}), (p:PRODUCT {PRODUCTID: 'P-108'}) MERGE (w)-[:STOCKS {quantity: 850}]->(p)");
        session.run("MATCH (w:WAREHOUSE {WAREHOUSEID: 'W-01'}), (p:PRODUCT {PRODUCTID: 'P-210'}) MERGE (w)-[:STOCKS {quantity: 400}]->(p)");
        session.run("MATCH (w:WAREHOUSE {WAREHOUSEID: 'W-02'}), (p:PRODUCT {PRODUCTID: 'P-104'}) MERGE (w)-[:STOCKS {quantity: 2500}]->(p)");
        session.run("MATCH (w:WAREHOUSE {WAREHOUSEID: 'W-02'}), (p:PRODUCT {PRODUCTID: 'P-301'}) MERGE (w)-[:STOCKS {quantity: 150}]->(p)");

        session.run("MATCH (c:CUSTOMER {CUSTID: 'C-01'}), (o:ORDERS {ORDERID: 'O-101'}) MERGE (c)-[:PLACED]->(o)");
        session.run("MATCH (c:CUSTOMER {CUSTID: 'C-03'}), (o:ORDERS {ORDERID: 'O-102'}) MERGE (c)-[:PLACED]->(o)");
        session.run("MATCH (c:CUSTOMER {CUSTID: 'C-01'}), (o:ORDERS {ORDERID: 'O-103'}) MERGE (c)-[:PLACED]->(o)");
        session.run("MATCH (c:CUSTOMER {CUSTID: 'C-05'}), (o:ORDERS {ORDERID: 'O-104'}) MERGE (c)-[:PLACED]->(o)");
        session.run("MATCH (c:CUSTOMER {CUSTID: 'C-03'}), (o:ORDERS {ORDERID: 'O-105'}) MERGE (c)-[:PLACED]->(o)");
        session.run("MATCH (c:CUSTOMER {CUSTID: 'C-05'}), (o:ORDERS {ORDERID: 'O-106'}) MERGE (c)-[:PLACED]->(o)");

        session.run("MATCH (o:ORDERS {ORDERID: 'O-101'}), (p:PRODUCT {PRODUCTID: 'P-100'}) MERGE (o)-[:CONTAINS {qty: 20, price: 480}]->(p)");
        session.run("MATCH (o:ORDERS {ORDERID: 'O-101'}), (p:PRODUCT {PRODUCTID: 'P-104'}) MERGE (o)-[:CONTAINS {qty: 50, price: 132}]->(p)");
        session.run("MATCH (o:ORDERS {ORDERID: 'O-102'}), (p:PRODUCT {PRODUCTID: 'P-210'}) MERGE (o)-[:CONTAINS {qty: 30, price: 96}]->(p)");
        session.run("MATCH (o:ORDERS {ORDERID: 'O-103'}), (p:PRODUCT {PRODUCTID: 'P-100'}) MERGE (o)-[:CONTAINS {qty: 15, price: 480}]->(p)");
        session.run("MATCH (o:ORDERS {ORDERID: 'O-104'}), (p:PRODUCT {PRODUCTID: 'P-108'}) MERGE (o)-[:CONTAINS {qty: 12, price: 445}]->(p)");
        session.run("MATCH (o:ORDERS {ORDERID: 'O-105'}), (p:PRODUCT {PRODUCTID: 'P-104'}) MERGE (o)-[:CONTAINS {qty: 25, price: 132}]->(p)");
        session.run("MATCH (o:ORDERS {ORDERID: 'O-105'}), (p:PRODUCT {PRODUCTID: 'P-210'}) MERGE (o)-[:CONTAINS {qty: 40, price: 96}]->(p)");
        session.run("MATCH (o:ORDERS {ORDERID: 'O-106'}), (p:PRODUCT {PRODUCTID: 'P-100'}) MERGE (o)-[:CONTAINS {qty: 18, price: 480}]->(p)");

        session.run("MATCH (d:DRIVER {EMPID: 'D-05'}), (o:ORDERS {ORDERID: 'O-101'}) MERGE (d)-[:DELIVERS {assignedCity: 'Chennai'}]->(o)");
        session.run("MATCH (d:DRIVER {EMPID: 'D-09'}), (o:ORDERS {ORDERID: 'O-102'}) MERGE (d)-[:DELIVERS {assignedCity: 'Mumbai'}]->(o)");
        session.run("MATCH (d:DRIVER {EMPID: 'D-05'}), (o:ORDERS {ORDERID: 'O-103'}) MERGE (d)-[:DELIVERS {assignedCity: 'Chennai'}]->(o)");
        session.run("MATCH (d:DRIVER {EMPID: 'D-05'}), (o:ORDERS {ORDERID: 'O-104'}) MERGE (d)-[:DELIVERS {assignedCity: 'Chennai'}]->(o)");
        session.run("MATCH (d:DRIVER {EMPID: 'D-09'}), (o:ORDERS {ORDERID: 'O-105'}) MERGE (d)-[:DELIVERS {assignedCity: 'Mumbai'}]->(o)");
        session.run("MATCH (d:DRIVER {EMPID: 'D-11'}), (o:ORDERS {ORDERID: 'O-106'}) MERGE (d)-[:DELIVERS {assignedCity: 'Chennai'}]->(o)");

        session.run("MATCH (m:MANAGER {EMPID: 'M-01'}), (w:WAREHOUSE {WAREHOUSEID: 'W-01'}) MERGE (m)-[:MANAGES {since: '2024'}]->(w)");
        session.run("MATCH (d:DRIVER), (e:EMPLOYEE {EMPID: d.EMPID}) MERGE (d)-[:IS_A]->(e)");
        session.run("MATCH (m:MANAGER), (e:EMPLOYEE {EMPID: m.EMPID}) MERGE (m)-[:IS_A]->(e)");
        session.run("MATCH (pp:PERISHABLE_PRODUCT), (p:PRODUCT {PRODUCTID: pp.PRODUCTID}) MERGE (pp)-[:IS_A]->(p)");
        session.run("MATCH (hp:HAZARDOUS_PRODUCT), (p:PRODUCT {PRODUCTID: hp.PRODUCTID}) MERGE (hp)-[:IS_A]->(p)");
        session.run("MATCH (sh:SHIPMENT), (gm:GOODS_MOVEMENT {MOVEMENTID: sh.MOVEMENTID}) MERGE (sh)-[:IS_A]->(gm)");
        session.run("MATCH (gr:GOODS_RETURN), (gm:GOODS_MOVEMENT {MOVEMENTID: gr.MOVEMENTID}) MERGE (gr)-[:IS_A]->(gm)");
    }

    /**
     * Purges duplicate nodes across all 22 labels in live Neo4j AuraDB by primary key.
     */
    public void deduplicateLiveGraph() {
        if (!isLiveConnected || driver == null) return;
        try (Session session = driver.session()) {
            for (String label : CANONICAL_LABELS) {
                List<String> pks = getPrimaryKeys(label);
                if (pks.isEmpty()) continue;
                String firstPk = pks.get(0);
                String cypher = "MATCH (n:" + label + ") " +
                                "WHERE n." + firstPk + " IS NOT NULL " +
                                "WITH n." + firstPk + " AS pk, collect(n) AS nodes " +
                                "WHERE size(nodes) > 1 " +
                                "FOREACH (n IN tail(nodes) | DETACH DELETE n)";
                try {
                    session.run(cypher);
                } catch (Exception ignored) {}
            }
            log.info("Live Neo4j graph deduplication complete across all 22 labels.");
        } catch (Exception e) {
            log.warn("Live Neo4j graph deduplication standby: {}", e.getMessage());
        }
    }

    public boolean isLiveConnected() {
        return isLiveConnected;
    }

    public String getDatabaseEngineName() {
        return isLiveConnected ? "Neo4j AuraDB Cloud (Bolt Protocol)" : "Neo4j Graph Database (In-Memory Engine)";
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("technology", "Graph Database (Neo4j)");
        status.put("liveConnected", isLiveConnected);
        status.put("message", connectionMessage);
        status.put("nodeCount", isLiveConnected ? queryLiveCount("MATCH (n) RETURN count(n) AS c") : defaultNodes.size());
        status.put("relationshipCount", isLiveConnected ? queryLiveCount("MATCH ()-[r]->() RETURN count(r) AS c") : defaultLinks.size());
        status.put("engine", getDatabaseEngineName());
        return status;
    }

    private int queryLiveCount(String cypher) {
        try (Session session = driver.session()) {
            return session.run(cypher).single().get("c").asInt();
        } catch (Exception e) {
            return 0;
        }
    }

    // ==========================================
    // UNIFIED DATA EXPLORER & CRUD (100% NEO4J)
    // ==========================================

    public List<String> getLabels() {
        Set<String> all = new LinkedHashSet<>(CANONICAL_LABELS);
        if (isLiveConnected && driver != null) {
            try (Session session = driver.session()) {
                Result result = session.run("CALL db.labels() YIELD label RETURN label ORDER BY label");
                while (result.hasNext()) {
                    String l = result.next().get("label").asString();
                    boolean exists = false;
                    for (String can : CANONICAL_LABELS) {
                        if (can.equalsIgnoreCase(l)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        all.add(l.toUpperCase(Locale.ROOT));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>(all);
    }

    public static final Map<String, List<Map<String, Object>>> CANONICAL_TABLE_COLUMNS = new LinkedHashMap<>();
    static {
        CANONICAL_TABLE_COLUMNS.put("CITY", List.of(
            Map.of("name", "CITY", "type", "STRING", "length", 50, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "STATE", "type", "STRING", "length", 50, "nullable", false, "isPrimaryKey", false)
        ));
        CANONICAL_TABLE_COLUMNS.put("SUPPLIER", List.of(
            Map.of("name", "SUPPLIERID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "SNAME", "type", "STRING", "length", 100, "nullable", true, "isPrimaryKey", false),
            Map.of("name", "CITY", "type", "STRING", "length", 50, "nullable", true, "isPrimaryKey", false),
            Map.of("name", "RATING", "type", "NUMBER", "length", 10, "nullable", true, "isPrimaryKey", false),
            Map.of("name", "PHONE", "type", "STRING", "length", 20, "nullable", true, "isPrimaryKey", false)
        ));
        CANONICAL_TABLE_COLUMNS.put("SUPPLIER_PHONE", List.of(
            Map.of("name", "SUPPLIERID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "PHONE", "type", "STRING", "length", 20, "nullable", false, "isPrimaryKey", true)
        ));
        CANONICAL_TABLE_COLUMNS.put("PRODUCT", List.of(
            Map.of("name", "PRODUCTID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "PNAME", "type", "STRING", "length", 100, "nullable", false, "isPrimaryKey", false),
            Map.of("name", "CATEGORY", "type", "STRING", "length", 50, "nullable", false, "isPrimaryKey", false),
            Map.of("name", "UNITPRICE", "type", "NUMBER", "length", 10, "nullable", true, "isPrimaryKey", false)
        ));
        CANONICAL_TABLE_COLUMNS.put("PERISHABLE_PRODUCT", List.of(
            Map.of("name", "PRODUCTID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "SHELFLIFEDAYS", "type", "NUMBER", "length", 10, "nullable", true, "isPrimaryKey", false),
            Map.of("name", "STORAGETEMPC", "type", "STRING", "length", 50, "nullable", true, "isPrimaryKey", false)
        ));
        CANONICAL_TABLE_COLUMNS.put("HAZARDOUS_PRODUCT", List.of(
            Map.of("name", "PRODUCTID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "HAZARDCLASS", "type", "STRING", "length", 100, "nullable", true, "isPrimaryKey", false),
            Map.of("name", "HANDLINGNOTE", "type", "STRING", "length", 200, "nullable", true, "isPrimaryKey", false)
        ));
        CANONICAL_TABLE_COLUMNS.put("SUPPLIES", List.of(
            Map.of("name", "SUPPLIERID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "PRODUCTID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "LEADTIMEDAYS", "type", "NUMBER", "length", 10, "nullable", true, "isPrimaryKey", false)
        ));
        CANONICAL_TABLE_COLUMNS.put("INSPECTION", List.of(
            Map.of("name", "INSPID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "SUPPLIERID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", false),
            Map.of("name", "PRODUCTID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", false),
            Map.of("name", "INSPDATE", "type", "DATE", "length", 20, "nullable", false, "isPrimaryKey", false),
            Map.of("name", "RESULT", "type", "STRING", "length", 50, "nullable", true, "isPrimaryKey", false)
        ));
        CANONICAL_TABLE_COLUMNS.put("WAREHOUSE", List.of(
            Map.of("name", "WAREHOUSEID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "WNAME", "type", "STRING", "length", 100, "nullable", false, "isPrimaryKey", false),
            Map.of("name", "CITY", "type", "STRING", "length", 50, "nullable", true, "isPrimaryKey", false),
            Map.of("name", "CAPACITY", "type", "NUMBER", "length", 10, "nullable", true, "isPrimaryKey", false),
            Map.of("name", "MANAGERID", "type", "STRING", "length", 10, "nullable", true, "isPrimaryKey", false)
        ));
        CANONICAL_TABLE_COLUMNS.put("STOCK", List.of(
            Map.of("name", "WAREHOUSEID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "PRODUCTID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "QUANTITY", "type", "NUMBER", "length", 10, "nullable", true, "isPrimaryKey", false)
        ));
        CANONICAL_TABLE_COLUMNS.put("EMPLOYEE", List.of(
            Map.of("name", "EMPID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "ENAME", "type", "STRING", "length", 100, "nullable", true, "isPrimaryKey", false),
            Map.of("name", "ROLE", "type", "STRING", "length", 50, "nullable", true, "isPrimaryKey", false),
            Map.of("name", "SALARY", "type", "NUMBER", "length", 12, "nullable", true, "isPrimaryKey", false),
            Map.of("name", "WAREHOUSEID", "type", "STRING", "length", 10, "nullable", true, "isPrimaryKey", false),
            Map.of("name", "SUPERVISORID", "type", "STRING", "length", 10, "nullable", true, "isPrimaryKey", false)
        ));
        CANONICAL_TABLE_COLUMNS.put("MANAGER", List.of(
            Map.of("name", "EMPID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "LEVELNO", "type", "STRING", "length", 50, "nullable", true, "isPrimaryKey", false),
            Map.of("name", "BONUS", "type", "NUMBER", "length", 10, "nullable", true, "isPrimaryKey", false)
        ));
        CANONICAL_TABLE_COLUMNS.put("DRIVER", List.of(
            Map.of("name", "EMPID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "LICENSENO", "type", "STRING", "length", 50, "nullable", true, "isPrimaryKey", false),
            Map.of("name", "EXPIRY", "type", "DATE", "length", 20, "nullable", true, "isPrimaryKey", false)
        ));
        CANONICAL_TABLE_COLUMNS.put("DRIVER_CITY", List.of(
            Map.of("name", "DRIVERID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "CITY", "type", "STRING", "length", 50, "nullable", false, "isPrimaryKey", true)
        ));
        CANONICAL_TABLE_COLUMNS.put("CUSTOMER", List.of(
            Map.of("name", "CUSTID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "CNAME", "type", "STRING", "length", 100, "nullable", false, "isPrimaryKey", false),
            Map.of("name", "STREET", "type", "STRING", "length", 100, "nullable", true, "isPrimaryKey", false),
            Map.of("name", "CITY", "type", "STRING", "length", 50, "nullable", true, "isPrimaryKey", false),
            Map.of("name", "PHONE", "type", "STRING", "length", 20, "nullable", true, "isPrimaryKey", false)
        ));
        CANONICAL_TABLE_COLUMNS.put("ORDERS", List.of(
            Map.of("name", "ORDERID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "ORDERDATE", "type", "DATE", "length", 20, "nullable", false, "isPrimaryKey", false),
            Map.of("name", "STATUS", "type", "STRING", "length", 30, "nullable", true, "isPrimaryKey", false),
            Map.of("name", "CUSTID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", false),
            Map.of("name", "AMOUNT", "type", "NUMBER", "length", 10, "nullable", true, "isPrimaryKey", false)
        ));
        CANONICAL_TABLE_COLUMNS.put("ORDER_ITEM", List.of(
            Map.of("name", "ORDERID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "PRODUCTID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "QTY", "type", "NUMBER", "length", 10, "nullable", false, "isPrimaryKey", false),
            Map.of("name", "PRICE", "type", "NUMBER", "length", 10, "nullable", false, "isPrimaryKey", false)
        ));
        CANONICAL_TABLE_COLUMNS.put("ORDER_DRIVER", List.of(
            Map.of("name", "ORDERID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "DRIVERID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true)
        ));
        CANONICAL_TABLE_COLUMNS.put("DELIVERY_DUTY", List.of(
            Map.of("name", "ORDERID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "DRIVERID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", false),
            Map.of("name", "CITY", "type", "STRING", "length", 50, "nullable", false, "isPrimaryKey", true)
        ));
        CANONICAL_TABLE_COLUMNS.put("GOODS_MOVEMENT", List.of(
            Map.of("name", "MOVEMENTID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "ORDERID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", false),
            Map.of("name", "WAREHOUSEID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", false),
            Map.of("name", "MOVEDATE", "type", "DATE", "length", 20, "nullable", true, "isPrimaryKey", false),
            Map.of("name", "STATUS", "type", "STRING", "length", 30, "nullable", true, "isPrimaryKey", false)
        ));
        CANONICAL_TABLE_COLUMNS.put("SHIPMENT", List.of(
            Map.of("name", "MOVEMENTID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "DELIVERYDATE", "type", "DATE", "length", 20, "nullable", true, "isPrimaryKey", false)
        ));
        CANONICAL_TABLE_COLUMNS.put("GOODS_RETURN", List.of(
            Map.of("name", "MOVEMENTID", "type", "STRING", "length", 10, "nullable", false, "isPrimaryKey", true),
            Map.of("name", "REASON", "type", "STRING", "length", 200, "nullable", true, "isPrimaryKey", false),
            Map.of("name", "REFUNDAMT", "type", "NUMBER", "length", 10, "nullable", true, "isPrimaryKey", false)
        ));
    }

    public static Object getPropertyCaseInsensitive(Map<String, ?> map, String key) {
        if (map == null || key == null) return null;
        Object val = map.get(key);
        if (val != null) return val;
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public List<Map<String, Object>> getNodesByLabel(String label) {
        String cleanLabel = (label == null) ? "SUPPLIER" : label.trim();
        List<Map<String, Object>> rows = new ArrayList<>();

        if (isLiveConnected && driver != null) {
            try (Session session = driver.session()) {
                String cypher = "MATCH (n) WHERE any(l IN labels(n) WHERE toUpper(l) = toUpper($label)) " +
                                "RETURN properties(n) AS props, id(n) AS _id LIMIT 250";
                Result result = session.run(cypher, Map.of("label", cleanLabel));
                while (result.hasNext()) {
                    Record rec = result.next();
                    Map<String, Object> props = new LinkedHashMap<>(rec.get("props").asMap());
                    props.remove("id");
                    props.remove("name");
                    rows.add(props);
                }
            } catch (Exception e) {
                log.warn("Querying live Neo4j for label {} failed: {}. Falling back to in-memory graph.", cleanLabel, e.getMessage());
            }
        }

        if (rows.isEmpty()) {
            // In-memory fallback
            rows = defaultNodes.stream()
                .filter(n -> {
                    String type = String.valueOf(n.get("type"));
                    return type.equalsIgnoreCase(cleanLabel);
                })
                .map(n -> {
                    Map<String, Object> props = new LinkedHashMap<>((Map<String, Object>) n.get("properties"));
                    props.remove("id");
                    props.remove("name");
                    return props;
                })
                .collect(Collectors.toList());
        }

        // Deduplicate rows by primary key(s)
        List<String> pks = getPrimaryKeys(cleanLabel.toUpperCase(Locale.ROOT));
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> deduped = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            List<String> keyParts = new ArrayList<>();
            for (String pk : pks) {
                Object v = getPropertyCaseInsensitive(row, pk);
                if (v != null) keyParts.add(String.valueOf(v).trim().toUpperCase(Locale.ROOT));
            }
            String compositeKey = keyParts.isEmpty() ? row.toString() : String.join("||", keyParts);
            if (seen.add(compositeKey)) {
                deduped.add(row);
            }
        }
        return deduped;
    }

    public List<Map<String, Object>> getLabelProperties(String label) {
        String upper = (label == null) ? "" : label.toUpperCase(Locale.ROOT).trim();
        if (CANONICAL_TABLE_COLUMNS.containsKey(upper)) {
            return CANONICAL_TABLE_COLUMNS.get(upper);
        }

        List<Map<String, Object>> rows = getNodesByLabel(label);
        if (rows.isEmpty()) {
            return List.of(
                Map.of("name", "ID", "type", "STRING", "length", 50, "nullable", false, "isPrimaryKey", true),
                Map.of("name", "NAME", "type", "STRING", "length", 100, "nullable", true, "isPrimaryKey", false)
            );
        }

        Map<String, String> types = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                if ("id".equalsIgnoreCase(key) || "name".equalsIgnoreCase(key)) continue;
                if (!types.containsKey(key)) {
                    Object val = entry.getValue();
                    if (val instanceof Number) {
                        types.put(key, "NUMBER");
                    } else if (val instanceof Boolean) {
                        types.put(key, "BOOLEAN");
                    } else {
                        types.put(key, "STRING");
                    }
                }
            }
        }

        List<Map<String, Object>> cols = new ArrayList<>();
        types.forEach((colName, colType) -> {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("name", colName);
            col.put("type", colType);
            col.put("length", 100);
            col.put("nullable", true);
            col.put("isPrimaryKey", false);
            cols.add(col);
        });
        return cols;
    }

    public List<String> getPrimaryKeys(String label) {
        String upper = (label == null) ? "" : label.toUpperCase(Locale.ROOT).trim();
        if (CANONICAL_TABLE_COLUMNS.containsKey(upper)) {
            return CANONICAL_TABLE_COLUMNS.get(upper).stream()
                .filter(c -> Boolean.TRUE.equals(c.get("isPrimaryKey")))
                .map(c -> String.valueOf(c.get("name")))
                .collect(Collectors.toList());
        }

        return switch (upper) {
            case "CITY" -> List.of("CITY");
            case "SUPPLIER" -> List.of("SUPPLIERID");
            case "SUPPLIER_PHONE" -> List.of("SUPPLIERID", "PHONE");
            case "PRODUCT", "PERISHABLE_PRODUCT", "HAZARDOUS_PRODUCT" -> List.of("PRODUCTID");
            case "SUPPLIES" -> List.of("SUPPLIERID", "PRODUCTID");
            case "INSPECTION" -> List.of("INSPID");
            case "WAREHOUSE" -> List.of("WAREHOUSEID");
            case "STOCK" -> List.of("WAREHOUSEID", "PRODUCTID");
            case "EMPLOYEE", "MANAGER", "DRIVER" -> List.of("EMPID");
            case "DRIVER_CITY" -> List.of("DRIVERID", "CITY");
            case "CUSTOMER" -> List.of("CUSTID");
            case "ORDERS" -> List.of("ORDERID");
            case "ORDER_ITEM" -> List.of("ORDERID", "PRODUCTID");
            case "ORDER_DRIVER" -> List.of("ORDERID", "DRIVERID");
            case "DELIVERY_DUTY" -> List.of("ORDERID", "CITY");
            case "GOODS_MOVEMENT", "SHIPMENT", "GOODS_RETURN" -> List.of("MOVEMENTID");
            default -> List.of("id");
        };
    }

    public Map<String, Object> cleanAndCastValues(String label, Map<String, Object> values) {
        Map<String, Object> cleaned = new LinkedHashMap<>();
        if (values == null || values.isEmpty()) return cleaned;

        String upperLabel = (label == null) ? "" : label.toUpperCase(Locale.ROOT).trim();
        List<Map<String, Object>> canonicalCols = CANONICAL_TABLE_COLUMNS.getOrDefault(upperLabel, List.of());
        Map<String, String> colTypes = new HashMap<>();
        for (Map<String, Object> col : canonicalCols) {
            colTypes.put(String.valueOf(col.get("name")).toUpperCase(Locale.ROOT), String.valueOf(col.get("type")));
        }

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) continue;
            String upperKey = key.trim().toUpperCase(Locale.ROOT);
            if ("ID".equals(upperKey) || "NAME".equals(upperKey)) {
                // Ignore internal visual aliases unless table has that exact column
                if (!colTypes.containsKey(upperKey)) continue;
            }

            Object val = entry.getValue();
            if (val == null) continue;
            String strVal = String.valueOf(val).trim();
            if (strVal.isEmpty() || "null".equalsIgnoreCase(strVal)) continue;

            String type = colTypes.getOrDefault(upperKey, "STRING");
            if ("NUMBER".equals(type)) {
                try {
                    if (strVal.contains(".")) {
                        cleaned.put(upperKey, Double.parseDouble(strVal));
                    } else {
                        cleaned.put(upperKey, Long.parseLong(strVal));
                    }
                } catch (Exception ex) {
                    cleaned.put(upperKey, strVal);
                }
            } else {
                cleaned.put(upperKey, strVal);
            }
        }
        return cleaned;
    }

    public int insertNode(String label, Map<String, Object> values) {
        if (values == null || values.isEmpty()) return 0;
        String cleanLabel = (label == null) ? "Node" : label.toUpperCase(Locale.ROOT).trim();
        Map<String, Object> cleanVals = cleanAndCastValues(cleanLabel, values);
        if (cleanVals.isEmpty()) return 0;

        List<String> pks = getPrimaryKeys(cleanLabel);
        Map<String, Object> pkParams = new HashMap<>();
        StringBuilder matchKeys = new StringBuilder();
        int kIdx = 0;
        for (String pk : pks) {
            Object v = cleanVals.get(pk);
            if (v != null) {
                if (kIdx++ > 0) matchKeys.append(", ");
                String paramName = "pk_" + pk;
                matchKeys.append(pk).append(": $").append(paramName);
                pkParams.put(paramName, v);
            }
        }

        if (isLiveConnected && driver != null) {
            try (Session session = driver.session()) {
                String cypher;
                Map<String, Object> params = new HashMap<>(pkParams);
                params.put("vals", cleanVals);
                if (matchKeys.length() > 0) {
                    cypher = "MERGE (n:" + cleanLabel + " {" + matchKeys + "}) SET n += $vals RETURN count(n) AS c";
                } else {
                    cypher = "CREATE (n:" + cleanLabel + ") SET n = $vals RETURN count(n) AS c";
                }
                session.run(cypher, params);
            } catch (Exception e) {
                log.error("Live Neo4j insert failed: {}", e.getMessage());
            }
        }

        // Always also handle in-memory store with deduplication
        String pkVal = extractPrimaryKeyValue(cleanLabel, cleanVals);
        String nodeId = cleanLabel + "_" + pkVal;
        String displayLabel = cleanLabel + " [" + pkVal + "]";
        if (cleanVals.containsKey("PNAME")) displayLabel = String.valueOf(cleanVals.get("PNAME")) + " [" + pkVal + "]";
        else if (cleanVals.containsKey("SNAME")) displayLabel = String.valueOf(cleanVals.get("SNAME")) + " [" + pkVal + "]";
        else if (cleanVals.containsKey("CNAME")) displayLabel = String.valueOf(cleanVals.get("CNAME")) + " [" + pkVal + "]";
        else if (cleanVals.containsKey("ENAME")) displayLabel = String.valueOf(cleanVals.get("ENAME")) + " [" + pkVal + "]";
        else if (cleanVals.containsKey("WNAME")) displayLabel = String.valueOf(cleanVals.get("WNAME")) + " [" + pkVal + "]";
        else if (cleanVals.containsKey("CITY")) displayLabel = String.valueOf(cleanVals.get("CITY"));

        boolean replaced = false;
        for (Map<String, Object> node : defaultNodes) {
            if (cleanLabel.equalsIgnoreCase(String.valueOf(node.get("type")))) {
                Map<String, Object> props = (Map<String, Object>) node.get("properties");
                boolean allPkMatch = !pks.isEmpty();
                for (String pk : pks) {
                    Object existingPk = getPropertyCaseInsensitive(props, pk);
                    Object newPk = getPropertyCaseInsensitive(cleanVals, pk);
                    if (existingPk == null || newPk == null || !String.valueOf(existingPk).trim().equalsIgnoreCase(String.valueOf(newPk).trim())) {
                        allPkMatch = false;
                        break;
                    }
                }
                if (allPkMatch) {
                    props.putAll(cleanVals);
                    node.put("label", displayLabel);
                    replaced = true;
                    break;
                }
            }
        }

        if (!replaced) {
            addNode(nodeId, displayLabel, cleanLabel, cleanVals);
            autoLinkNode(nodeId, cleanLabel, cleanVals);
        }
        return 1;
    }

    private String extractPrimaryKeyValue(String label, Map<String, Object> vals) {
        List<String> pks = getPrimaryKeys(label);
        List<String> parts = new ArrayList<>();
        for (String pk : pks) {
            Object v = getPropertyCaseInsensitive(vals, pk);
            if (v != null) parts.add(String.valueOf(v).trim());
        }
        if (parts.isEmpty()) {
            return vals.values().stream().findFirst().map(String::valueOf).orElse(UUID.randomUUID().toString().substring(0, 8));
        }
        return String.join("_", parts);
    }

    private void autoLinkNode(String nodeId, String label, Map<String, Object> vals) {
        switch (label) {
            case "SUPPLIER" -> {
                if (vals.containsKey("CITY")) {
                    addLink(nodeId, "CITY_" + vals.get("CITY"), "LOCATED_IN", Map.of());
                }
            }
            case "WAREHOUSE" -> {
                if (vals.containsKey("CITY")) {
                    addLink(nodeId, "CITY_" + vals.get("CITY"), "LOCATED_IN", Map.of());
                }
            }
            case "CUSTOMER" -> {
                if (vals.containsKey("CITY")) {
                    addLink(nodeId, "CITY_" + vals.get("CITY"), "LOCATED_IN", Map.of());
                }
            }
            case "ORDERS" -> {
                if (vals.containsKey("CUSTID")) {
                    addLink("CUSTOMER_" + vals.get("CUSTID"), nodeId, "PLACED", Map.of());
                }
            }
            case "ORDER_ITEM" -> {
                if (vals.containsKey("ORDERID") && vals.containsKey("PRODUCTID")) {
                    addLink("ORDER_" + vals.get("ORDERID"), "PRODUCT_" + vals.get("PRODUCTID"), "CONTAINS", Map.of());
                }
            }
            case "STOCK" -> {
                if (vals.containsKey("WAREHOUSEID") && vals.containsKey("PRODUCTID")) {
                    addLink("WAREHOUSE_" + vals.get("WAREHOUSEID"), "PRODUCT_" + vals.get("PRODUCTID"), "STOCKS", Map.of());
                }
            }
            case "SUPPLIES" -> {
                if (vals.containsKey("SUPPLIERID") && vals.containsKey("PRODUCTID")) {
                    addLink("SUPPLIER_" + vals.get("SUPPLIERID"), "PRODUCT_" + vals.get("PRODUCTID"), "SUPPLIES", Map.of());
                }
            }
            default -> {}
        }
    }

    public int updateNode(String label, Map<String, Object> values, Map<String, String> where) {
        if (values == null || values.isEmpty()) return 0;
        String cleanLabel = (label == null) ? "Node" : label.toUpperCase(Locale.ROOT).trim();
        Map<String, Object> cleanVals = cleanAndCastValues(cleanLabel, values);
        if (cleanVals.isEmpty()) return 0;

        if (isLiveConnected && driver != null) {
            try (Session session = driver.session()) {
                StringBuilder whereClause = new StringBuilder();
                Map<String, Object> params = new HashMap<>();
                params.put("label", cleanLabel);
                params.put("vals", cleanVals);

                int i = 0;
                for (Map.Entry<String, String> e : where.entrySet()) {
                    String pKey = "w_" + i++;
                    String kUp = e.getKey().toUpperCase(Locale.ROOT);
                    String kLow = e.getKey().toLowerCase(Locale.ROOT);
                    whereClause.append(" AND (toString(n.").append(kUp).append(") = $").append(pKey)
                               .append(" OR toString(n.").append(kLow).append(") = $").append(pKey).append(")");
                    params.put(pKey, e.getValue());
                }

                String cypher = "MATCH (n) WHERE any(l IN labels(n) WHERE toUpper(l) = toUpper($label)) " +
                                whereClause + " SET n += $vals RETURN count(n) AS c";
                int c = session.run(cypher, params).single().get("c").asInt();
                if (c > 0) {
                    updateInMemoryNode(cleanLabel, cleanVals, where);
                    return c;
                }
            } catch (Exception e) {
                log.error("Live Neo4j update failed: {}", e.getMessage());
            }
        }

        return updateInMemoryNode(cleanLabel, cleanVals, where);
    }

    private int updateInMemoryNode(String cleanLabel, Map<String, Object> cleanVals, Map<String, String> where) {
        int updated = 0;
        for (Map<String, Object> n : defaultNodes) {
            String type = String.valueOf(n.get("type"));
            if (type.equalsIgnoreCase(cleanLabel)) {
                Map<String, Object> props = (Map<String, Object>) n.get("properties");
                boolean match = true;
                for (Map.Entry<String, String> w : where.entrySet()) {
                    Object pVal = props.get(w.getKey().toUpperCase(Locale.ROOT));
                    if (pVal == null) {
                        pVal = props.get(w.getKey().toLowerCase(Locale.ROOT));
                    }
                    if (pVal == null && props.containsKey(w.getKey())) {
                        pVal = props.get(w.getKey());
                    }
                    if (pVal == null || !String.valueOf(pVal).equalsIgnoreCase(w.getValue())) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    props.putAll(cleanVals);
                    String pkVal = extractPrimaryKeyValue(cleanLabel, props);
                    if (props.containsKey("PNAME")) n.put("label", props.get("PNAME") + " [" + pkVal + "]");
                    else if (props.containsKey("SNAME")) n.put("label", props.get("SNAME") + " [" + pkVal + "]");
                    else if (props.containsKey("CNAME")) n.put("label", props.get("CNAME") + " [" + pkVal + "]");
                    else if (props.containsKey("ENAME")) n.put("label", props.get("ENAME") + " [" + pkVal + "]");
                    else if (props.containsKey("WNAME")) n.put("label", props.get("WNAME") + " [" + pkVal + "]");
                    else if (props.containsKey("CITY")) n.put("label", String.valueOf(props.get("CITY")));
                    updated++;
                }
            }
        }
        return updated;
    }

    /**
     * Cascades primary key / identifier updates across all referencing nodes & links in both live Neo4j and memory.
     */
    public void cascadeUpdateId(String label, String oldId, String newId) {
        if (oldId == null || newId == null || oldId.equalsIgnoreCase(newId)) return;
        String upper = label.toUpperCase(Locale.ROOT).trim();

        if (isLiveConnected && driver != null) {
            try (Session session = driver.session()) {
                switch (upper) {
                    case "DRIVER", "EMPLOYEE" -> {
                        session.run("MATCH (d:DRIVER) WHERE d.EMPID = $oldId SET d.EMPID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (e:EMPLOYEE) WHERE e.EMPID = $oldId SET e.EMPID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (dc:DRIVER_CITY) WHERE dc.DRIVERID = $oldId SET dc.DRIVERID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (od:ORDER_DRIVER) WHERE od.DRIVERID = $oldId SET od.DRIVERID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (dd:DELIVERY_DUTY) WHERE dd.DRIVERID = $oldId SET dd.DRIVERID = $newId", Map.of("oldId", oldId, "newId", newId));
                    }
                    case "CITY" -> {
                        session.run("MATCH (c:CITY) WHERE c.CITY = $oldId SET c.CITY = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (s:SUPPLIER) WHERE s.CITY = $oldId SET s.CITY = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (c:CUSTOMER) WHERE c.CITY = $oldId SET c.CITY = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (w:WAREHOUSE) WHERE w.CITY = $oldId SET w.CITY = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (dc:DRIVER_CITY) WHERE dc.CITY = $oldId SET dc.CITY = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (dd:DELIVERY_DUTY) WHERE dd.CITY = $oldId SET dd.CITY = $newId", Map.of("oldId", oldId, "newId", newId));
                    }
                    case "CUSTOMER" -> {
                        session.run("MATCH (c:CUSTOMER) WHERE c.CUSTID = $oldId SET c.CUSTID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (o:ORDERS) WHERE o.CUSTID = $oldId SET o.CUSTID = $newId", Map.of("oldId", oldId, "newId", newId));
                    }
                    case "SUPPLIER" -> {
                        session.run("MATCH (s:SUPPLIER) WHERE s.SUPPLIERID = $oldId SET s.SUPPLIERID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (sp:SUPPLIER_PHONE) WHERE sp.SUPPLIERID = $oldId SET sp.SUPPLIERID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (sup:SUPPLIES) WHERE sup.SUPPLIERID = $oldId SET sup.SUPPLIERID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (i:INSPECTION) WHERE i.SUPPLIERID = $oldId SET i.SUPPLIERID = $newId", Map.of("oldId", oldId, "newId", newId));
                    }
                    case "PRODUCT" -> {
                        session.run("MATCH (p:PRODUCT) WHERE p.PRODUCTID = $oldId SET p.PRODUCTID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (pp:PERISHABLE_PRODUCT) WHERE pp.PRODUCTID = $oldId SET pp.PRODUCTID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (hp:HAZARDOUS_PRODUCT) WHERE hp.PRODUCTID = $oldId SET hp.PRODUCTID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (oi:ORDER_ITEM) WHERE oi.PRODUCTID = $oldId SET oi.PRODUCTID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (s:STOCK) WHERE s.PRODUCTID = $oldId SET s.PRODUCTID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (sup:SUPPLIES) WHERE sup.PRODUCTID = $oldId SET sup.PRODUCTID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (i:INSPECTION) WHERE i.PRODUCTID = $oldId SET i.PRODUCTID = $newId", Map.of("oldId", oldId, "newId", newId));
                    }
                    case "WAREHOUSE" -> {
                        session.run("MATCH (w:WAREHOUSE) WHERE w.WAREHOUSEID = $oldId SET w.WAREHOUSEID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (s:STOCK) WHERE s.WAREHOUSEID = $oldId SET s.WAREHOUSEID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (gm:GOODS_MOVEMENT) WHERE gm.WAREHOUSEID = $oldId SET gm.WAREHOUSEID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (e:EMPLOYEE) WHERE e.WAREHOUSEID = $oldId SET e.WAREHOUSEID = $newId", Map.of("oldId", oldId, "newId", newId));
                    }
                    case "ORDERS" -> {
                        session.run("MATCH (o:ORDERS) WHERE o.ORDERID = $oldId SET o.ORDERID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (oi:ORDER_ITEM) WHERE oi.ORDERID = $oldId SET oi.ORDERID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (od:ORDER_DRIVER) WHERE od.ORDERID = $oldId SET od.ORDERID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (dd:DELIVERY_DUTY) WHERE dd.ORDERID = $oldId SET dd.ORDERID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (gm:GOODS_MOVEMENT) WHERE gm.ORDERID = $oldId SET gm.ORDERID = $newId", Map.of("oldId", oldId, "newId", newId));
                    }
                    case "GOODS_MOVEMENT" -> {
                        session.run("MATCH (gm:GOODS_MOVEMENT) WHERE gm.MOVEMENTID = $oldId SET gm.MOVEMENTID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (sh:SHIPMENT) WHERE sh.MOVEMENTID = $oldId SET sh.MOVEMENTID = $newId", Map.of("oldId", oldId, "newId", newId));
                        session.run("MATCH (gr:GOODS_RETURN) WHERE gr.MOVEMENTID = $oldId SET gr.MOVEMENTID = $newId", Map.of("oldId", oldId, "newId", newId));
                    }
                }
            } catch (Exception e) {
                log.warn("cascadeUpdateId Neo4j query error: {}", e.getMessage());
            }
        }

        // In-memory update
        for (Map<String, Object> node : defaultNodes) {
            Map<String, Object> props = (Map<String, Object>) node.get("properties");
            for (Map.Entry<String, Object> prop : props.entrySet()) {
                if (String.valueOf(prop.getValue()).equalsIgnoreCase(oldId)) {
                    props.put(prop.getKey(), newId);
                }
            }
            String currId = String.valueOf(node.get("id"));
            if (currId.contains(oldId.replace("-", "").toUpperCase(Locale.ROOT)) || currId.contains(oldId)) {
                String updatedId = currId.replace(oldId, newId).replace(oldId.replace("-", ""), newId.replace("-", ""));
                node.put("id", updatedId);
                for (Map<String, Object> link : defaultLinks) {
                    if (currId.equals(link.get("source"))) link.put("source", updatedId);
                    if (currId.equals(link.get("target"))) link.put("target", updatedId);
                }
            }
        }
    }

    public int deleteNode(String label, Map<String, String> where) {
        if (where == null || where.isEmpty()) return 0;
        String cleanLabel = (label == null) ? "Node" : label.toUpperCase(Locale.ROOT).trim();

        if (isLiveConnected) {
            try (Session session = driver.session()) {
                StringBuilder whereClause = new StringBuilder();
                Map<String, Object> params = new HashMap<>();
                params.put("label", cleanLabel);

                int i = 0;
                for (Map.Entry<String, String> e : where.entrySet()) {
                    String pKey = "w_" + i++;
                    whereClause.append(" AND (toString(n.").append(e.getKey().toUpperCase(Locale.ROOT)).append(") = $").append(pKey).append(")");
                    params.put(pKey, e.getValue());
                }

                String cypher = "MATCH (n) WHERE any(l IN labels(n) WHERE toUpper(l) = toUpper($label)) " +
                                whereClause + " DETACH DELETE n RETURN count(n) AS c";
                session.run(cypher, params);
            } catch (Exception e) {
                log.error("Live Neo4j delete failed: {}", e.getMessage());
            }
        }

        // In-memory removal
        List<Map<String, Object>> toRemove = new ArrayList<>();
        for (Map<String, Object> n : defaultNodes) {
            String type = String.valueOf(n.get("type"));
            if (type.equalsIgnoreCase(cleanLabel)) {
                Map<String, Object> props = (Map<String, Object>) n.get("properties");
                boolean match = true;
                for (Map.Entry<String, String> w : where.entrySet()) {
                    Object pVal = props.get(w.getKey().toUpperCase(Locale.ROOT));
                    if (pVal == null && props.containsKey(w.getKey())) {
                        pVal = props.get(w.getKey());
                    }
                    if (pVal == null || !String.valueOf(pVal).equalsIgnoreCase(w.getValue())) {
                        match = false;
                        break;
                    }
                }
                if (match) toRemove.add(n);
            }
        }
        toRemove.forEach(n -> {
            String id = (String) n.get("id");
            defaultLinks.removeIf(l -> String.valueOf(l.get("source")).equals(id) || String.valueOf(l.get("target")).equals(id));
            defaultNodes.remove(n);
        });
        return toRemove.size();
    }

    // ==========================================
    // UNIFIED QUERY EVALUATOR (CYPHER & SQL BRIDGE)
    // ==========================================

    public Map<String, Object> executeUnifiedQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be empty.");
        }

        String trimmed = query.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);

        // Native Cypher execution
        if (upper.startsWith("MATCH") || upper.startsWith("CREATE") || upper.startsWith("MERGE") ||
            upper.startsWith("RETURN") || upper.startsWith("CALL") || upper.startsWith("SHOW") ||
            upper.startsWith("DETACH") || upper.startsWith("DROP")) {
            return executeCypher(trimmed);
        }

        long start = System.currentTimeMillis();

        // SQL Bridge to Neo4j
        if (upper.startsWith("SELECT")) {
            return executeSqlSelect(trimmed, start);
        }

        if (upper.startsWith("INSERT")) {
            return executeSqlInsert(trimmed, start);
        }

        if (upper.startsWith("UPDATE")) {
            return executeSqlUpdate(trimmed, start);
        }

        if (upper.startsWith("DELETE")) {
            return executeSqlDelete(trimmed, start);
        }

        // Fallback Cypher execution
        return executeCypher(trimmed);
    }

    private Map<String, Object> executeSqlSelect(String sql, long startTime) {
        Pattern p = Pattern.compile("(?i)SELECT\\s+(.+?)\\s+FROM\\s+([A-Za-z0-9_]+)(?:\\s+WHERE\\s+(.+?))?(?:\\s+ORDER\\s+BY\\s+(.+?))?(?:\\s+LIMIT\\s+(\\d+))?$");
        Matcher m = p.matcher(sql);
        if (!m.find()) {
            Pattern simpleP = Pattern.compile("(?i)SELECT\\s+(.+?)\\s+FROM\\s+([A-Za-z0-9_]+)");
            Matcher sm = simpleP.matcher(sql);
            if (!sm.find()) {
                throw new IllegalArgumentException("Invalid SELECT query syntax.");
            }
            String table = sm.group(2).toUpperCase(Locale.ROOT).trim();
            List<Map<String, Object>> rows = getNodesByLabel(table);
            List<String> cols = rows.isEmpty() ? getCanonicalColumnNames(table) : new ArrayList<>(rows.get(0).keySet());
            long duration = System.currentTimeMillis() - startTime;
            return Map.of(
                "type", "result",
                "columns", cols,
                "rows", rows,
                "rowCount", rows.size(),
                "durationMs", Math.max(1, duration),
                "source", "Neo4j Graph Engine (" + table + ")"
            );
        }

        String colStr = m.group(1).trim();
        String table = m.group(2).toUpperCase(Locale.ROOT).trim();
        String whereClause = m.group(3);
        String orderByClause = m.group(4);
        String limitStr = m.group(5);

        List<Map<String, Object>> rows = new ArrayList<>(getNodesByLabel(table));

        if (whereClause != null && !whereClause.isBlank()) {
            rows = filterRows(rows, whereClause.trim());
        }

        if (orderByClause != null && !orderByClause.isBlank()) {
            sortRows(rows, orderByClause.trim());
        }

        if (limitStr != null && !limitStr.isBlank()) {
            try {
                int limit = Integer.parseInt(limitStr.trim());
                if (limit >= 0 && limit < rows.size()) {
                    rows = new ArrayList<>(rows.subList(0, limit));
                }
            } catch (Exception ignored) {}
        }

        List<String> cols;
        if ("*".equals(colStr)) {
            cols = rows.isEmpty() ? getCanonicalColumnNames(table) : new ArrayList<>(rows.get(0).keySet());
        } else {
            String[] reqCols = colStr.split(",");
            cols = new ArrayList<>();
            for (String c : reqCols) {
                cols.add(c.trim().toUpperCase(Locale.ROOT));
            }
            List<Map<String, Object>> projected = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Map<String, Object> newRow = new LinkedHashMap<>();
                for (String c : cols) {
                    Object val = r.get(c);
                    if (val == null) val = r.get(c.toLowerCase(Locale.ROOT));
                    newRow.put(c, val);
                }
                projected.add(newRow);
            }
            rows = projected;
        }

        long duration = System.currentTimeMillis() - startTime;
        return Map.of(
            "type", "result",
            "columns", cols,
            "rows", rows,
            "rowCount", rows.size(),
            "durationMs", Math.max(1, duration),
            "source", "Neo4j Graph Engine (" + table + ")"
        );
    }

    private List<Map<String, Object>> filterRows(List<Map<String, Object>> rows, String where) {
        String[] conditions = where.split("(?i)\\s+AND\\s+");
        List<Map<String, Object>> filtered = new ArrayList<>();

        for (Map<String, Object> r : rows) {
            boolean matchAll = true;
            for (String cond : conditions) {
                if (!evaluateCondition(r, cond.trim())) {
                    matchAll = false;
                    break;
                }
            }
            if (matchAll) {
                filtered.add(r);
            }
        }
        return filtered;
    }

    private boolean evaluateCondition(Map<String, Object> row, String cond) {
        if (cond.contains("!=") || cond.contains("<>")) {
            String op = cond.contains("!=") ? "!=" : "<>";
            String[] parts = cond.split(Pattern.quote(op), 2);
            String key = parts[0].trim().toUpperCase(Locale.ROOT);
            String val = unquote(parts[1].trim());
            Object actual = getRowValue(row, key);
            if (actual == null) return !"null".equalsIgnoreCase(val);
            return !String.valueOf(actual).trim().equalsIgnoreCase(val);
        } else if (cond.contains("=")) {
            String[] parts = cond.split("=", 2);
            String key = parts[0].trim().toUpperCase(Locale.ROOT);
            String val = unquote(parts[1].trim());
            Object actual = getRowValue(row, key);
            if (actual == null) return "null".equalsIgnoreCase(val);
            return String.valueOf(actual).trim().equalsIgnoreCase(val);
        } else if (cond.toUpperCase(Locale.ROOT).contains(" LIKE ")) {
            String[] parts = cond.split("(?i)\\s+LIKE\\s+", 2);
            String key = parts[0].trim().toUpperCase(Locale.ROOT);
            String val = unquote(parts[1].trim()).replace("%", ".*");
            Object actual = getRowValue(row, key);
            if (actual == null) return false;
            return String.valueOf(actual).matches("(?i)" + val);
        }
        return true;
    }

    private Object getRowValue(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        return null;
    }

    private String unquote(String s) {
        if (s == null) return "";
        s = s.trim();
        if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private void sortRows(List<Map<String, Object>> rows, String orderBy) {
        String[] parts = orderBy.trim().split("\\s+");
        String col = parts[0].trim();
        boolean desc = parts.length > 1 && "DESC".equalsIgnoreCase(parts[1].trim());
        rows.sort((a, b) -> {
            Object va = getRowValue(a, col);
            Object vb = getRowValue(b, col);
            if (va == null && vb == null) return 0;
            if (va == null) return desc ? 1 : -1;
            if (vb == null) return desc ? -1 : 1;
            int cmp = String.valueOf(va).compareToIgnoreCase(String.valueOf(vb));
            return desc ? -cmp : cmp;
        });
    }

    private List<String> getCanonicalColumnNames(String table) {
        List<Map<String, Object>> colMeta = CANONICAL_TABLE_COLUMNS.get(table.toUpperCase(Locale.ROOT));
        if (colMeta != null && !colMeta.isEmpty()) {
            List<String> list = new ArrayList<>();
            for (Map<String, Object> m : colMeta) {
                list.add(String.valueOf(m.get("name")));
            }
            return list;
        }
        return List.of("RESULT");
    }

    private Map<String, Object> executeSqlInsert(String sql, long startTime) {
        Pattern p = Pattern.compile("(?i)INSERT\\s+INTO\\s+([A-Za-z0-9_]+)\\s*\\((.+?)\\)\\s*VALUES\\s*\\((.+?)\\)");
        Matcher m = p.matcher(sql);
        if (!m.find()) {
            throw new IllegalArgumentException("Invalid INSERT SQL syntax. Format: INSERT INTO <table> (col1, col2) VALUES (val1, val2)");
        }
        String table = m.group(1).toUpperCase(Locale.ROOT).trim();
        String[] cols = m.group(2).split(",");
        String[] vals = m.group(3).split(",");
        if (cols.length != vals.length) {
            throw new IllegalArgumentException("Column count (" + cols.length + ") does not match value count (" + vals.length + ").");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < cols.length; i++) {
            map.put(cols[i].trim().toUpperCase(Locale.ROOT), unquote(vals[i].trim()));
        }
        int count = insertNode(table, map);
        long duration = System.currentTimeMillis() - startTime;
        return Map.of(
            "type", "update",
            "affectedRows", count,
            "durationMs", Math.max(1, duration),
            "source", "Neo4j Graph Database"
        );
    }

    private Map<String, Object> executeSqlUpdate(String sql, long startTime) {
        Pattern p = Pattern.compile("(?i)UPDATE\\s+([A-Za-z0-9_]+)\\s+SET\\s+(.+?)(?:\\s+WHERE\\s+(.+?))?$");
        Matcher m = p.matcher(sql);
        if (!m.find()) {
            throw new IllegalArgumentException("Invalid UPDATE SQL syntax. Format: UPDATE <table> SET col=val WHERE col=val");
        }
        String table = m.group(1).toUpperCase(Locale.ROOT).trim();
        String setPart = m.group(2).trim();
        String wherePart = m.group(3);

        Map<String, Object> values = new LinkedHashMap<>();
        for (String pair : setPart.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                values.put(kv[0].trim().toUpperCase(Locale.ROOT), unquote(kv[1].trim()));
            }
        }

        Map<String, String> where = new LinkedHashMap<>();
        if (wherePart != null && !wherePart.isBlank()) {
            for (String pair : wherePart.split("(?i)\\s+AND\\s+")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    where.put(kv[0].trim().toUpperCase(Locale.ROOT), unquote(kv[1].trim()));
                }
            }
        }

        int count = updateNode(table, values, where);
        long duration = System.currentTimeMillis() - startTime;
        return Map.of(
            "type", "update",
            "affectedRows", count,
            "durationMs", Math.max(1, duration),
            "source", "Neo4j Graph Database"
        );
    }

    private Map<String, Object> executeSqlDelete(String sql, long startTime) {
        Pattern p = Pattern.compile("(?i)DELETE\\s+(?:FROM\\s+)?([A-Za-z0-9_]+)(?:\\s+WHERE\\s+(.+?))?$");
        Matcher m = p.matcher(sql);
        if (!m.find()) {
            throw new IllegalArgumentException("Invalid DELETE SQL syntax. Format: DELETE FROM <table> WHERE col=val");
        }
        String table = m.group(1).toUpperCase(Locale.ROOT).trim();
        String wherePart = m.group(2);

        Map<String, String> where = new LinkedHashMap<>();
        if (wherePart != null && !wherePart.isBlank()) {
            for (String pair : wherePart.split("(?i)\\s+AND\\s+")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    where.put(kv[0].trim().toUpperCase(Locale.ROOT), unquote(kv[1].trim()));
                }
            }
        }

        int count = deleteNode(table, where);
        long duration = System.currentTimeMillis() - startTime;
        return Map.of(
            "type", "update",
            "affectedRows", count,
            "durationMs", Math.max(1, duration),
            "source", "Neo4j Graph Database"
        );
    }

    public Map<String, Object> executeCypher(String cypher) {
        if (cypher == null || cypher.isBlank()) {
            throw new IllegalArgumentException("Cypher query cannot be empty.");
        }

        long start = System.currentTimeMillis();

        if (isLiveConnected) {
            try (Session session = driver.session()) {
                Result result = session.run(cypher);
                List<Map<String, Object>> records = new ArrayList<>();
                List<String> columns = result.keys();
                while (result.hasNext()) {
                    Record rec = result.next();
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String col : columns) {
                        Object val = rec.get(col).asObject();
                        row.put(col, val);
                    }
                    records.add(row);
                }
                long duration = System.currentTimeMillis() - start;
                return Map.of(
                    "type", "result",
                    "columns", columns,
                    "rows", records,
                    "rowCount", records.size(),
                    "durationMs", Math.max(1, duration),
                    "source", "Live Neo4j AuraDB Instance"
                );
            } catch (Exception e) {
                throw new RuntimeException("Neo4j Cypher Execution Error: " + e.getMessage(), e);
            }
        }

        // In-memory Cypher evaluator
        String upper = cypher.toUpperCase(Locale.ROOT).trim();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> columns = new ArrayList<>();

        if (upper.contains("SUPPLIER")) {
            columns = List.of("SupplierID", "Name", "City", "ProductsSupplied");
            for (Map<String, Object> node : defaultNodes) {
                if ("SUPPLIER".equalsIgnoreCase(String.valueOf(node.get("type")))) {
                    Map<String, Object> p = (Map<String, Object>) node.get("properties");
                    long suppliesCount = defaultLinks.stream()
                        .filter(l -> l.get("source").equals(node.get("id")) && "SUPPLIES".equals(l.get("label")))
                        .count();
                    rows.add(Map.of(
                        "SupplierID", p.getOrDefault("SUPPLIERID", p.get("id")),
                        "Name", p.getOrDefault("SNAME", p.get("name")),
                        "City", p.getOrDefault("CITY", p.get("city")),
                        "ProductsSupplied", suppliesCount
                    ));
                }
            }
        } else if (upper.contains("PRODUCT")) {
            columns = List.of("ProductID", "Name", "Category", "UnitPrice");
            for (Map<String, Object> node : defaultNodes) {
                if (String.valueOf(node.get("type")).toUpperCase().contains("PRODUCT")) {
                    Map<String, Object> p = (Map<String, Object>) node.get("properties");
                    rows.add(Map.of(
                        "ProductID", p.getOrDefault("PRODUCTID", p.get("id")),
                        "Name", p.getOrDefault("PNAME", p.get("name")),
                        "Category", p.getOrDefault("CATEGORY", p.get("category")),
                        "UnitPrice", p.getOrDefault("UNITPRICE", p.get("unitPrice"))
                    ));
                }
            }
        } else if (upper.contains("ORDER") || upper.contains("CUSTOMER")) {
            columns = List.of("OrderID", "Customer", "Date", "Status", "Amount");
            for (Map<String, Object> node : defaultNodes) {
                if ("ORDERS".equalsIgnoreCase(String.valueOf(node.get("type")))) {
                    Map<String, Object> p = (Map<String, Object>) node.get("properties");
                    rows.add(Map.of(
                        "OrderID", p.getOrDefault("ORDERID", p.get("id")),
                        "Customer", p.getOrDefault("CUSTID", "Retail Partner"),
                        "Date", p.getOrDefault("ORDERDATE", p.get("date")),
                        "Status", p.getOrDefault("STATUS", p.get("status")),
                        "Amount", p.getOrDefault("AMOUNT", p.get("amount"))
                    ));
                }
            }
        } else {
            columns = List.of("ID", "Label", "Type", "ConnectedEdges");
            for (Map<String, Object> node : defaultNodes) {
                long edges = defaultLinks.stream()
                    .filter(l -> l.get("source").equals(node.get("id")) || l.get("target").equals(node.get("id")))
                    .count();
                rows.add(Map.of(
                    "ID", node.get("id"),
                    "Label", node.get("label"),
                    "Type", node.get("type"),
                    "ConnectedEdges", edges
                ));
            }
        }

        long duration = System.currentTimeMillis() - start;
        return Map.of(
            "type", "result",
            "columns", columns,
            "rows", rows,
            "rowCount", rows.size(),
            "durationMs", Math.max(1, duration),
            "source", "Built-in Neo4j Engine"
        );
    }

    // ==========================================
    // GRAPH TELEMETRY & WORKBENCH VISUALIZATION
    // ==========================================

    public List<Map<String, Object>> getSupplyChainNodes() {
        Set<String> supplyPrefixes = Set.of("SUPPLIER_S", "PRODUCT_P", "WAREHOUSE_W", "CUSTOMER_C", "ORDER_O", "DRIVER_D", "MANAGER_M");
        return defaultNodes.stream()
            .filter(n -> {
                String id = String.valueOf(n.get("id"));
                return supplyPrefixes.stream().anyMatch(id::startsWith);
            })
            .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getSupplyChainLinks() {
        Set<String> validIds = getSupplyChainNodes().stream().map(n -> String.valueOf(n.get("id"))).collect(Collectors.toSet());
        return defaultLinks.stream()
            .filter(l -> validIds.contains(String.valueOf(l.get("source"))) && validIds.contains(String.valueOf(l.get("target"))))
            .collect(Collectors.toList());
    }

    public Map<String, Object> getGraphData() {
        if (isLiveConnected) {
            try {
                Map<String, Object> live = fetchLiveGraphData();
                List<?> liveNodes = (List<?>) live.get("nodes");
                if (liveNodes != null && !liveNodes.isEmpty()) {
                    return live;
                }
            } catch (Exception ignored) {
            }
        }
        return Map.of(
            "nodes", getSupplyChainNodes(),
            "links", getSupplyChainLinks()
        );
    }

    private Map<String, Object> fetchLiveGraphData() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> links = new ArrayList<>();
        Set<String> nodeIds = new HashSet<>();

        try (Session session = driver.session()) {
            String cypher = "MATCH (n) WHERE any(l IN labels(n) WHERE toUpper(l) IN ['SUPPLIER','PRODUCT','PERISHABLE_PRODUCT','HAZARDOUS_PRODUCT','WAREHOUSE','MANAGER','CUSTOMER','ORDERS','ORDER','DRIVER']) " +
                            "OPTIONAL MATCH (n)-[r]->(m) WHERE any(l IN labels(m) WHERE toUpper(l) IN ['SUPPLIER','PRODUCT','PERISHABLE_PRODUCT','HAZARDOUS_PRODUCT','WAREHOUSE','MANAGER','CUSTOMER','ORDERS','ORDER','DRIVER']) " +
                            "RETURN n, r, m LIMIT 300";
            Result result = session.run(cypher);
            while (result.hasNext()) {
                Record record = result.next();
                Node n = record.get("n").asNode();
                String nId = addLiveNode(n, nodes, nodeIds);

                if (!record.get("m").isNull() && !record.get("r").isNull()) {
                    Node m = record.get("m").asNode();
                    Relationship r = record.get("r").asRelationship();
                    String mId = addLiveNode(m, nodes, nodeIds);

                    if (nId != null && mId != null) {
                        Map<String, Object> link = new LinkedHashMap<>();
                        link.put("source", nId);
                        link.put("target", mId);
                        link.put("label", r.type());
                        link.put("properties", r.asMap());
                        links.add(link);
                    }
                }
            }
        }

        if (links.isEmpty() || nodes.isEmpty()) {
            return Map.of("nodes", getSupplyChainNodes(), "links", getSupplyChainLinks());
        }

        return Map.of("nodes", nodes, "links", links);
    }

    private String addLiveNode(Node n, List<Map<String, Object>> nodes, Set<String> nodeIds) {
        if (!n.labels().iterator().hasNext() && n.asMap().isEmpty()) {
            return null;
        }

        String label = n.labels().iterator().hasNext() ? n.labels().iterator().next() : "Node";
        String upperLabel = label.toUpperCase(Locale.ROOT);
        if (!Set.of("SUPPLIER","PRODUCT","PERISHABLE_PRODUCT","HAZARDOUS_PRODUCT","WAREHOUSE","MANAGER","CUSTOMER","ORDERS","ORDER","DRIVER").contains(upperLabel)) {
            return null;
        }
        Map<String, Object> props = n.asMap();
        String idStr = props.containsKey("id") ? String.valueOf(props.get("id")) :
                       props.containsKey("SUPPLIERID") ? String.valueOf(props.get("SUPPLIERID")) :
                       props.containsKey("PRODUCTID") ? String.valueOf(props.get("PRODUCTID")) :
                       props.containsKey("CUSTID") ? String.valueOf(props.get("CUSTID")) :
                       props.containsKey("ORDERID") ? String.valueOf(props.get("ORDERID")) :
                       props.containsKey("EMPID") ? String.valueOf(props.get("EMPID")) :
                       String.valueOf(n.id());

        if (!nodeIds.contains(idStr)) {
            nodeIds.add(idStr);
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", idStr);
            node.put("type", label);
            String name = props.containsKey("name") ? String.valueOf(props.get("name")) :
                          props.containsKey("SNAME") ? String.valueOf(props.get("SNAME")) :
                          props.containsKey("PNAME") ? String.valueOf(props.get("PNAME")) :
                          props.containsKey("CNAME") ? String.valueOf(props.get("CNAME")) :
                          props.containsKey("ENAME") ? String.valueOf(props.get("ENAME")) :
                          label + " #" + idStr;
            node.put("label", name);
            node.put("properties", props);
            nodes.add(node);
        }
        return idStr;
    }

    public Map<String, Object> traceSupplyChain(String supplierId) {
        String targetSupplier = (supplierId == null || supplierId.isBlank()) ? "SUPPLIER_S01" : supplierId.trim().toUpperCase(Locale.ROOT);
        if (!targetSupplier.startsWith("SUPPLIER_")) {
            targetSupplier = "SUPPLIER_" + targetSupplier;
        }

        final String supId = targetSupplier;
        Set<String> visitedNodeIds = new HashSet<>();
        List<Map<String, Object>> pathNodes = new ArrayList<>();
        List<Map<String, Object>> pathLinks = new ArrayList<>();

        defaultNodes.stream().filter(n -> n.get("id").equals(supId)).findFirst().ifPresent(n -> {
            pathNodes.add(n);
            visitedNodeIds.add(supId);
        });

        List<String> productIds = new ArrayList<>();
        for (Map<String, Object> link : defaultLinks) {
            if (link.get("source").equals(supId) && "SUPPLIES".equals(link.get("label"))) {
                String pId = (String) link.get("target");
                productIds.add(pId);
                pathLinks.add(link);
                defaultNodes.stream().filter(n -> n.get("id").equals(pId)).findFirst().ifPresent(n -> {
                    if (visitedNodeIds.add(pId)) pathNodes.add(n);
                });
            }
        }

        List<String> warehouseIds = new ArrayList<>();
        for (Map<String, Object> link : defaultLinks) {
            if ("STOCKS".equals(link.get("label")) && productIds.contains(link.get("target"))) {
                String wId = (String) link.get("source");
                warehouseIds.add(wId);
                pathLinks.add(link);
                defaultNodes.stream().filter(n -> n.get("id").equals(wId)).findFirst().ifPresent(n -> {
                    if (visitedNodeIds.add(wId)) pathNodes.add(n);
                });
            }
        }

        List<String> orderIds = new ArrayList<>();
        for (Map<String, Object> link : defaultLinks) {
            if ("CONTAINS".equals(link.get("label")) && productIds.contains(link.get("target"))) {
                String oId = (String) link.get("source");
                orderIds.add(oId);
                pathLinks.add(link);
                defaultNodes.stream().filter(n -> n.get("id").equals(oId)).findFirst().ifPresent(n -> {
                    if (visitedNodeIds.add(oId)) pathNodes.add(n);
                });
            }
        }

        for (Map<String, Object> link : defaultLinks) {
            if ("PLACED".equals(link.get("label")) && orderIds.contains(link.get("target"))) {
                String cId = (String) link.get("source");
                pathLinks.add(link);
                defaultNodes.stream().filter(n -> n.get("id").equals(cId)).findFirst().ifPresent(n -> {
                    if (visitedNodeIds.add(cId)) pathNodes.add(n);
                });
            }
            if ("DELIVERS".equals(link.get("label")) && orderIds.contains(link.get("target"))) {
                String dId = (String) link.get("source");
                pathLinks.add(link);
                defaultNodes.stream().filter(n -> n.get("id").equals(dId)).findFirst().ifPresent(n -> {
                    if (visitedNodeIds.add(dId)) pathNodes.add(n);
                });
            }
        }

        return Map.of(
            "supplierId", targetSupplier,
            "nodes", pathNodes,
            "links", pathLinks,
            "hops", 4,
            "traceSummary", "Neo4j Graph Trace across Supplier -> Product -> Warehouse -> Order -> Customer -> Driver"
        );
    }

    public Map<String, Object> getBottlenecks() {
        Map<String, List<String>> productSuppliers = new HashMap<>();
        for (Map<String, Object> link : defaultLinks) {
            if ("SUPPLIES".equals(link.get("label"))) {
                String pId = (String) link.get("target");
                String sId = (String) link.get("source");
                productSuppliers.computeIfAbsent(pId, k -> new ArrayList<>()).add(sId);
            }
        }

        List<Map<String, Object>> singleSourceRisks = new ArrayList<>();
        productSuppliers.forEach((pId, suppliers) -> {
            if (suppliers.size() == 1) {
                Map<String, Object> pNode = defaultNodes.stream().filter(n -> n.get("id").equals(pId)).findFirst().orElse(Map.of());
                singleSourceRisks.add(Map.of(
                    "productId", pId,
                    "productName", pNode.getOrDefault("label", pId),
                    "soleSupplier", suppliers.get(0),
                    "riskLevel", "HIGH",
                    "recommendation", "Diversify supplier network in Neo4j to avoid stockout vulnerability."
                ));
            }
        });

        Map<String, Integer> driverOrders = new HashMap<>();
        for (Map<String, Object> link : defaultLinks) {
            if ("DELIVERS".equals(link.get("label"))) {
                String dId = (String) link.get("source");
                driverOrders.put(dId, driverOrders.getOrDefault(dId, 0) + 1);
            }
        }

        return Map.of(
            "singleSourceVulnerabilities", singleSourceRisks,
            "driverWorkloadCentrality", driverOrders,
            "totalNodesAnalyzed", defaultNodes.size(),
            "totalRelationshipsAnalyzed", defaultLinks.size(),
            "graphDensity", String.format("%.3f", (double) defaultLinks.size() / Math.max(1, (defaultNodes.size() * (defaultNodes.size() - 1))))
        );
    }

    // ==========================================
    // GRAPH CURSORS & RULES
    // ==========================================

    public List<Map<String, Object>> getCustomerCursor() {
        List<Map<String, Object>> customers = getNodesByLabel("CUSTOMER");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> c : customers) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("CUSTID", c.getOrDefault("CUSTID", c.get("id")));
            row.put("CNAME", c.getOrDefault("CNAME", c.get("name")));
            row.put("CITY", c.getOrDefault("CITY", c.get("city")));
            result.add(row);
        }
        result.sort(Comparator.comparing(m -> String.valueOf(m.get("CUSTID"))));
        return result;
    }

    public List<Map<String, Object>> getParameterizedOrderCursor(String custId) {
        List<Map<String, Object>> orders = getNodesByLabel("ORDERS");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> o : orders) {
            String cId = String.valueOf(o.getOrDefault("CUSTID", o.get("custId")));
            if (cId.equalsIgnoreCase(custId)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ORDERID", o.getOrDefault("ORDERID", o.get("id")));
                row.put("ORDERDATE", o.getOrDefault("ORDERDATE", o.get("date")));
                row.put("STATUS", o.getOrDefault("STATUS", o.get("status")));
                result.add(row);
            }
        }
        return result;
    }

    public List<Map<String, Object>> getOrderTotalCursor() {
        List<Map<String, Object>> items = getNodesByLabel("ORDER_ITEM");
        Map<String, Double> totals = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String orderId = String.valueOf(item.getOrDefault("ORDERID", item.get("orderId")));
            double qty = Double.parseDouble(String.valueOf(item.getOrDefault("QTY", 1)));
            double price = Double.parseDouble(String.valueOf(item.getOrDefault("PRICE", 0)));
            totals.put(orderId, totals.getOrDefault(orderId, 0.0) + (qty * price));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        totals.forEach((orderId, total) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ORDERID", orderId);
            row.put("TOTAL_AMOUNT", total);
            result.add(row);
        });
        return result;
    }

    public List<Map<String, Object>> getTriggers() {
        return List.of(
            Map.of(
                "TRIGGER_NAME", "TRG_NEO4J_STOCK_INTEGRITY",
                "STATUS", "ENABLED",
                "TRIGGERING_EVENT", "CREATE OR UPDATE (:STOCK)",
                "TRIGGER_TYPE", "BEFORE GRAPH COMMIT"
            ),
            Map.of(
                "TRIGGER_NAME", "TRG_NEO4J_ORDER_LIFECYCLE",
                "STATUS", "ENABLED",
                "TRIGGERING_EVENT", "UPDATE (:ORDERS)",
                "TRIGGER_TYPE", "AFTER GRAPH EVENT"
            ),
            Map.of(
                "TRIGGER_NAME", "TRG_NEO4J_COLD_CHAIN_ALERT",
                "STATUS", "ENABLED",
                "TRIGGERING_EVENT", "MERGE (:PERISHABLE_PRODUCT)",
                "TRIGGER_TYPE", "GRAPH CONSTRAINT"
            ),
            Map.of(
                "TRIGGER_NAME", "TRG_NEO4J_FLEET_AUDIT",
                "STATUS", "ENABLED",
                "TRIGGERING_EVENT", "CREATE ([:DELIVERS])",
                "TRIGGER_TYPE", "AFTER RELATIONSHIP COMMIT"
            )
        );
    }

    // ==========================================
    // INITIALIZE ALL 22 ENTITIES IN-MEMORY
    // ==========================================

    private void initDefaultGraph() {
        defaultNodes.clear();
        defaultLinks.clear();

        // 1. CITY
        addNode("CITY_Chennai", "Chennai", "CITY", Map.of("CITY", "Chennai", "STATE", "Tamil Nadu", "id", "Chennai", "name", "Chennai"));
        addNode("CITY_Mumbai", "Mumbai", "CITY", Map.of("CITY", "Mumbai", "STATE", "Maharashtra", "id", "Mumbai", "name", "Mumbai"));
        addNode("CITY_Bangalore", "Bangalore", "CITY", Map.of("CITY", "Bangalore", "STATE", "Karnataka", "id", "Bangalore", "name", "Bangalore"));

        // 2. SUPPLIER
        addNode("SUPPLIER_S01", "Apex Agro Supplies [S01]", "SUPPLIER", Map.of(
            "SUPPLIERID", "S01", "SNAME", "Apex Agro Supplies", "CITY", "Chennai", "RATING", 4.8, "PHONE", "9845012345", "id", "S01", "name", "Apex Agro Supplies"
        ));
        addNode("SUPPLIER_S03", "Metro Wholesale Mills [S03]", "SUPPLIER", Map.of(
            "SUPPLIERID", "S03", "SNAME", "Metro Wholesale Mills", "CITY", "Mumbai", "RATING", 4.6, "PHONE", "9820011122", "id", "S03", "name", "Metro Wholesale Mills"
        ));
        addNode("SUPPLIER_S05", "Delta Cold & Chemicals [S05]", "SUPPLIER", Map.of(
            "SUPPLIERID", "S05", "SNAME", "Delta Cold & Chemicals", "CITY", "Chennai", "RATING", 4.9, "PHONE", "9840199887", "id", "S05", "name", "Delta Cold & Chemicals"
        ));

        // 3. SUPPLIER_PHONE
        addNode("SP_S01_1", "S01 Phone 1", "SUPPLIER_PHONE", Map.of("SUPPLIERID", "S01", "PHONE", "9845012345", "id", "S01_P1"));
        addNode("SP_S01_2", "S01 Phone 2", "SUPPLIER_PHONE", Map.of("SUPPLIERID", "S01", "PHONE", "08022334455", "id", "S01_P2"));
        addNode("SP_S03_1", "S03 Phone 1", "SUPPLIER_PHONE", Map.of("SUPPLIERID", "S03", "PHONE", "9820011122", "id", "S03_P1"));
        addNode("SP_S05_1", "S05 Phone 1", "SUPPLIER_PHONE", Map.of("SUPPLIERID", "S05", "PHONE", "9840199887", "id", "S05_P1"));

        // 4. PRODUCT
        addNode("PRODUCT_P100", "Basmati Rice [P-100]", "PRODUCT", Map.of(
            "PRODUCTID", "P-100", "PNAME", "Basmati Rice", "CATEGORY", "Staples", "UNITPRICE", 480.00, "id", "P-100", "name", "Basmati Rice"
        ));
        addNode("PRODUCT_P104", "Sunflower Oil [P-104]", "PRODUCT", Map.of(
            "PRODUCTID", "P-104", "PNAME", "Sunflower Oil", "CATEGORY", "Oils", "UNITPRICE", 132.00, "id", "P-104", "name", "Sunflower Oil"
        ));
        addNode("PRODUCT_P108", "Sona Masoori [P-108]", "PRODUCT", Map.of(
            "PRODUCTID", "P-108", "PNAME", "Sona Masoori Rice", "CATEGORY", "Staples", "UNITPRICE", 445.00, "id", "P-108", "name", "Sona Masoori Rice"
        ));
        addNode("PRODUCT_P210", "Frozen Peas [P-210]", "PRODUCT", Map.of(
            "PRODUCTID", "P-210", "PNAME", "Frozen Peas", "CATEGORY", "Frozen", "UNITPRICE", 96.00, "id", "P-210", "name", "Frozen Peas"
        ));
        addNode("PRODUCT_P301", "Industrial Solvent [P-301]", "PRODUCT", Map.of(
            "PRODUCTID", "P-301", "PNAME", "Industrial Solvent", "CATEGORY", "Chemicals", "UNITPRICE", 1250.00, "id", "P-301", "name", "Industrial Solvent"
        ));

        // 5. PERISHABLE_PRODUCT & HAZARDOUS_PRODUCT
        addNode("PERISHABLE_P210", "Frozen Peas [Perishable]", "PERISHABLE_PRODUCT", Map.of(
            "PRODUCTID", "P-210", "SHELFLIFEDAYS", 90, "STORAGETEMPC", "-18°C", "id", "P-210", "name", "Frozen Peas [Perishable]"
        ));
        addNode("HAZARDOUS_P301", "Solvent [Hazmat]", "HAZARDOUS_PRODUCT", Map.of(
            "PRODUCTID", "P-301", "HAZARDCLASS", "Class 3 Flammable", "HANDLINGNOTE", "Keep away from sparks", "id", "P-301", "name", "Industrial Solvent [Hazmat]"
        ));

        // 6. SUPPLIES
        addNode("SUPPLIES_1", "Supply S01 -> P100", "SUPPLIES", Map.of("SUPPLIERID", "S01", "PRODUCTID", "P-100", "LEADTIMEDAYS", 4, "id", "SUP_1"));
        addNode("SUPPLIES_2", "Supply S03 -> P104", "SUPPLIES", Map.of("SUPPLIERID", "S03", "PRODUCTID", "P-104", "LEADTIMEDAYS", 3, "id", "SUP_2"));
        addNode("SUPPLIES_3", "Supply S05 -> P210", "SUPPLIES", Map.of("SUPPLIERID", "S05", "PRODUCTID", "P-210", "LEADTIMEDAYS", 2, "id", "SUP_3"));

        // 7. INSPECTION
        addNode("INSP_01", "Inspection #01", "INSPECTION", Map.of(
            "INSPID", "INSP-01", "SUPPLIERID", "S01", "PRODUCTID", "P-100", "INSPDATE", "2026-03-15", "RESULT", "PASSED_GRADE_A", "id", "INSP-01"
        ));
        addNode("INSP_02", "Inspection #02", "INSPECTION", Map.of(
            "INSPID", "INSP-02", "SUPPLIERID", "S05", "PRODUCTID", "P-210", "INSPDATE", "2026-03-20", "RESULT", "PASSED_COLD_CHAIN", "id", "INSP-02"
        ));

        // 8. WAREHOUSE
        addNode("WAREHOUSE_W01", "Chennai Central Hub [W-01]", "WAREHOUSE", Map.of(
            "WAREHOUSEID", "W-01", "WNAME", "Chennai Central Hub", "CITY", "Chennai", "CAPACITY", 50000, "MANAGERID", "M-01", "id", "W-01", "name", "Chennai Central Hub"
        ));
        addNode("WAREHOUSE_W02", "Mumbai Port Depot [W-02]", "WAREHOUSE", Map.of(
            "WAREHOUSEID", "W-02", "WNAME", "Mumbai Port Depot", "CITY", "Mumbai", "CAPACITY", 75000, "MANAGERID", "M-02", "id", "W-02", "name", "Mumbai Port Depot"
        ));

        // 9. STOCK
        addNode("STOCK_1", "Stock W01 P100", "STOCK", Map.of("WAREHOUSEID", "W-01", "PRODUCTID", "P-100", "QUANTITY", 1200, "id", "ST_1"));
        addNode("STOCK_2", "Stock W01 P210", "STOCK", Map.of("WAREHOUSEID", "W-01", "PRODUCTID", "P-210", "QUANTITY", 400, "id", "ST_2"));
        addNode("STOCK_3", "Stock W02 P104", "STOCK", Map.of("WAREHOUSEID", "W-02", "PRODUCTID", "P-104", "QUANTITY", 2500, "id", "ST_3"));

        // 10. EMPLOYEE, MANAGER, DRIVER
        addNode("EMP_M01", "Kavita Sharma [M-01]", "EMPLOYEE", Map.of(
            "EMPID", "M-01", "ENAME", "Kavita Sharma", "ROLE", "Manager", "SALARY", 85000.00, "WAREHOUSEID", "W-01", "id", "M-01", "name", "Kavita Sharma"
        ));
        addNode("EMP_D05", "Ramesh Kumar [D-05]", "EMPLOYEE", Map.of(
            "EMPID", "D-05", "ENAME", "Ramesh Kumar", "ROLE", "Driver", "SALARY", 32000.00, "WAREHOUSEID", "W-01", "id", "D-05", "name", "Ramesh Kumar"
        ));
        addNode("EMP_D09", "Suresh Patil [D-09]", "EMPLOYEE", Map.of(
            "EMPID", "D-09", "ENAME", "Suresh Patil", "ROLE", "Driver", "SALARY", 34000.00, "WAREHOUSEID", "W-02", "id", "D-09", "name", "Suresh Patil"
        ));
        addNode("EMP_D11", "Venkatesh Rao [D-11]", "EMPLOYEE", Map.of(
            "EMPID", "D-11", "ENAME", "Venkatesh Rao", "ROLE", "Driver", "SALARY", 31000.00, "WAREHOUSEID", "W-01", "id", "D-11", "name", "Venkatesh Rao"
        ));

        addNode("MANAGER_M01", "Manager M-01 (Kavita)", "MANAGER", Map.of("EMPID", "M-01", "LEVELNO", "L3", "BONUS", 15000.00, "id", "M-01"));
        addNode("DRIVER_D05", "Driver D-05 (Ramesh)", "DRIVER", Map.of("EMPID", "D-05", "LICENSENO", "TN-02-2021-998", "EXPIRY", "2028-12-31", "id", "D-05"));
        addNode("DRIVER_D09", "Driver D-09 (Suresh)", "DRIVER", Map.of("EMPID", "D-09", "LICENSENO", "MH-01-2020-412", "EXPIRY", "2027-08-15", "id", "D-09"));
        addNode("DRIVER_D11", "Driver D-11 (Venkatesh)", "DRIVER", Map.of("EMPID", "D-11", "LICENSENO", "TN-05-2022-771", "EXPIRY", "2029-05-20", "id", "D-11"));

        // 11. DRIVER_CITY
        addNode("DC_1", "D05 Chennai", "DRIVER_CITY", Map.of("DRIVERID", "D-05", "CITY", "Chennai", "id", "DC_1"));
        addNode("DC_2", "D09 Mumbai", "DRIVER_CITY", Map.of("DRIVERID", "D-09", "CITY", "Mumbai", "id", "DC_2"));

        // 12. CUSTOMER
        addNode("CUSTOMER_C01", "Shama Retail [C-01]", "CUSTOMER", Map.of(
            "CUSTID", "C-01", "CNAME", "Shama Retail", "STREET", "12 MG Rd", "CITY", "Chennai", "PHONE", "9840011223", "id", "C-01", "name", "Shama Retail"
        ));
        addNode("CUSTOMER_C03", "Metro Mart [C-03]", "CUSTOMER", Map.of(
            "CUSTID", "C-03", "CNAME", "Metro Mart", "STREET", "5 Link Rd", "CITY", "Mumbai", "PHONE", "9820044556", "id", "C-03", "name", "Metro Mart"
        ));
        addNode("CUSTOMER_C05", "Anand Stores [C-05]", "CUSTOMER", Map.of(
            "CUSTID", "C-05", "CNAME", "Anand Stores", "STREET", "7 Anna Salai", "CITY", "Chennai", "PHONE", "9845567788", "id", "C-05", "name", "Anand Stores"
        ));

        // 13. ORDERS
        addNode("ORDER_O101", "Order #O-101", "ORDERS", Map.of(
            "ORDERID", "O-101", "ORDERDATE", "2026-04-02", "STATUS", "SHIPPED", "CUSTID", "C-01", "AMOUNT", 16200.00, "id", "O-101", "name", "Order #O-101"
        ));
        addNode("ORDER_O102", "Order #O-102", "ORDERS", Map.of(
            "ORDERID", "O-102", "ORDERDATE", "2026-04-05", "STATUS", "NEW", "CUSTID", "C-03", "AMOUNT", 2880.00, "id", "O-102", "name", "Order #O-102"
        ));
        addNode("ORDER_O103", "Order #O-103", "ORDERS", Map.of(
            "ORDERID", "O-103", "ORDERDATE", "2026-04-09", "STATUS", "SHIPPED", "CUSTID", "C-01", "AMOUNT", 7200.00, "id", "O-103", "name", "Order #O-103"
        ));
        addNode("ORDER_O104", "Order #O-104", "ORDERS", Map.of(
            "ORDERID", "O-104", "ORDERDATE", "2026-04-11", "STATUS", "NEW", "CUSTID", "C-05", "AMOUNT", 5340.00, "id", "O-104", "name", "Order #O-104"
        ));
        addNode("ORDER_O105", "Order #O-105", "ORDERS", Map.of(
            "ORDERID", "O-105", "ORDERDATE", "2026-04-14", "STATUS", "SHIPPED", "CUSTID", "C-03", "AMOUNT", 7140.00, "id", "O-105", "name", "Order #O-105"
        ));
        addNode("ORDER_O106", "Order #O-106", "ORDERS", Map.of(
            "ORDERID", "O-106", "ORDERDATE", "2026-04-16", "STATUS", "NEW", "CUSTID", "C-05", "AMOUNT", 8640.00, "id", "O-106", "name", "Order #O-106"
        ));

        // 14. ORDER_ITEM
        addNode("OI_1", "Item O101 P100", "ORDER_ITEM", Map.of("ORDERID", "O-101", "PRODUCTID", "P-100", "QTY", 20, "PRICE", 480.00, "id", "OI_1"));
        addNode("OI_2", "Item O101 P104", "ORDER_ITEM", Map.of("ORDERID", "O-101", "PRODUCTID", "P-104", "QTY", 50, "PRICE", 132.00, "id", "OI_2"));
        addNode("OI_3", "Item O102 P210", "ORDER_ITEM", Map.of("ORDERID", "O-102", "PRODUCTID", "P-210", "QTY", 30, "PRICE", 96.00, "id", "OI_3"));
        addNode("OI_4", "Item O105 P104", "ORDER_ITEM", Map.of("ORDERID", "O-105", "PRODUCTID", "P-104", "QTY", 25, "PRICE", 132.00, "id", "OI_4"));

        // 15. ORDER_DRIVER & DELIVERY_DUTY
        addNode("OD_1", "Driver O101 D05", "ORDER_DRIVER", Map.of("ORDERID", "O-101", "DRIVERID", "D-05", "id", "OD_1"));
        addNode("OD_2", "Driver O102 D09", "ORDER_DRIVER", Map.of("ORDERID", "O-102", "DRIVERID", "D-09", "id", "OD_2"));

        addNode("DD_1", "Delivery O101 Chennai", "DELIVERY_DUTY", Map.of("ORDERID", "O-101", "DRIVERID", "D-05", "CITY", "Chennai", "id", "DD_1"));
        addNode("DD_2", "Delivery O102 Mumbai", "DELIVERY_DUTY", Map.of("ORDERID", "O-102", "DRIVERID", "D-09", "CITY", "Mumbai", "id", "DD_2"));

        // 16. GOODS_MOVEMENT, SHIPMENT, GOODS_RETURN
        addNode("GM_1", "Movement MOV-01", "GOODS_MOVEMENT", Map.of(
            "MOVEMENTID", "MOV-01", "ORDERID", "O-101", "WAREHOUSEID", "W-01", "MOVEDATE", "2026-04-03", "STATUS", "DISPATCHED", "id", "MOV-01"
        ));
        addNode("GM_2", "Movement MOV-02", "GOODS_MOVEMENT", Map.of(
            "MOVEMENTID", "MOV-02", "ORDERID", "O-103", "WAREHOUSEID", "W-01", "MOVEDATE", "2026-04-10", "STATUS", "DELIVERED", "id", "MOV-02"
        ));
        addNode("GM_3", "Movement MOV-03", "GOODS_MOVEMENT", Map.of(
            "MOVEMENTID", "MOV-03", "ORDERID", "O-105", "WAREHOUSEID", "W-02", "MOVEDATE", "2026-04-15", "STATUS", "DELIVERED", "id", "MOV-03"
        ));

        addNode("SH_1", "Shipment MOV-01", "SHIPMENT", Map.of("MOVEMENTID", "MOV-01", "DELIVERYDATE", "2026-04-04", "id", "MOV-01"));
        addNode("GR_1", "Return MOV-03", "GOODS_RETURN", Map.of("MOVEMENTID", "MOV-03", "REASON", "Packaging damaged", "REFUNDAMT", 1250.00, "id", "MOV-03"));

        // Relationships for Graph Studio
        addLink("SUPPLIER_S01", "PRODUCT_P100", "SUPPLIES", Map.of("leadTimeDays", 4));
        addLink("SUPPLIER_S01", "PRODUCT_P108", "SUPPLIES", Map.of("leadTimeDays", 5));
        addLink("SUPPLIER_S03", "PRODUCT_P104", "SUPPLIES", Map.of("leadTimeDays", 3));
        addLink("SUPPLIER_S05", "PRODUCT_P210", "SUPPLIES", Map.of("leadTimeDays", 2, "coldChain", true));
        addLink("SUPPLIER_S05", "PRODUCT_P301", "SUPPLIES", Map.of("leadTimeDays", 7, "hazmatCertified", true));

        addLink("WAREHOUSE_W01", "PRODUCT_P100", "STOCKS", Map.of("quantity", 1200));
        addLink("WAREHOUSE_W01", "PRODUCT_P108", "STOCKS", Map.of("quantity", 850));
        addLink("WAREHOUSE_W01", "PRODUCT_P210", "STOCKS", Map.of("quantity", 400));
        addLink("WAREHOUSE_W02", "PRODUCT_P104", "STOCKS", Map.of("quantity", 2500));
        addLink("WAREHOUSE_W02", "PRODUCT_P301", "STOCKS", Map.of("quantity", 150));

        addLink("CUSTOMER_C01", "ORDER_O101", "PLACED", Map.of());
        addLink("CUSTOMER_C03", "ORDER_O102", "PLACED", Map.of());
        addLink("CUSTOMER_C01", "ORDER_O103", "PLACED", Map.of());
        addLink("CUSTOMER_C05", "ORDER_O104", "PLACED", Map.of());
        addLink("CUSTOMER_C03", "ORDER_O105", "PLACED", Map.of());
        addLink("CUSTOMER_C05", "ORDER_O106", "PLACED", Map.of());

        addLink("ORDER_O101", "PRODUCT_P100", "CONTAINS", Map.of("qty", 20, "price", 480));
        addLink("ORDER_O101", "PRODUCT_P104", "CONTAINS", Map.of("qty", 50, "price", 132));
        addLink("ORDER_O102", "PRODUCT_P210", "CONTAINS", Map.of("qty", 30, "price", 96));
        addLink("ORDER_O103", "PRODUCT_P100", "CONTAINS", Map.of("qty", 15, "price", 480));
        addLink("ORDER_O104", "PRODUCT_P108", "CONTAINS", Map.of("qty", 12, "price", 445));
        addLink("ORDER_O105", "PRODUCT_P104", "CONTAINS", Map.of("qty", 25, "price", 132));
        addLink("ORDER_O105", "PRODUCT_P210", "CONTAINS", Map.of("qty", 40, "price", 96));
        addLink("ORDER_O106", "PRODUCT_P100", "CONTAINS", Map.of("qty", 18, "price", 480));

        addLink("DRIVER_D05", "ORDER_O101", "DELIVERS", Map.of("assignedCity", "Chennai"));
        addLink("DRIVER_D09", "ORDER_O102", "DELIVERS", Map.of("assignedCity", "Mumbai"));
        addLink("DRIVER_D05", "ORDER_O103", "DELIVERS", Map.of("assignedCity", "Chennai"));
        addLink("DRIVER_D05", "ORDER_O104", "DELIVERS", Map.of("assignedCity", "Chennai"));
        addLink("DRIVER_D09", "ORDER_O105", "DELIVERS", Map.of("assignedCity", "Mumbai"));
        addLink("DRIVER_D11", "ORDER_O106", "DELIVERS", Map.of("assignedCity", "Chennai"));

        addLink("MANAGER_M01", "WAREHOUSE_W01", "MANAGES", Map.of("since", "2024"));
    }

    private void addNode(String id, String label, String type, Map<String, Object> properties) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("label", label);
        node.put("type", type);
        Map<String, Object> cleanProps = new LinkedHashMap<>(properties);
        cleanProps.remove("id");
        cleanProps.remove("name");
        node.put("properties", cleanProps);
        defaultNodes.add(node);
    }

    private void addLink(String source, String target, String label, Map<String, Object> properties) {
        Map<String, Object> link = new LinkedHashMap<>();
        link.put("source", source);
        link.put("target", target);
        link.put("label", label);
        link.put("properties", new LinkedHashMap<>(properties));
        defaultLinks.add(link);
    }
}
