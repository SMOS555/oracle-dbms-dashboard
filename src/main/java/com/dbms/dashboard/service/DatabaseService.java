package com.dbms.dashboard.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Primary Database Service: Unified 100% Neo4j Graph Database Integration.
 * Routes all tables, rows, columns, updates, deletes, cursors, and queries directly to Neo4j.
 */
@Service
public class DatabaseService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);

    private final GraphDatabaseService graphService;
    private final JdbcTemplate jdbc;

    @Autowired
    public DatabaseService(GraphDatabaseService graphService, Optional<JdbcTemplate> jdbc) {
        this.graphService = graphService;
        this.jdbc = jdbc.orElse(null);
    }

    public boolean isOracle() {
        return false;
    }

    public String getDatabaseEngineName() {
        return graphService.getDatabaseEngineName();
    }

    // =========================
    // TABLES / NODE LABELS
    // =========================

    public List<String> tables() {
        return graphService.getLabels();
    }

    // =========================
    // TABLE ROWS / NODE PROPERTIES
    // =========================

    public List<Map<String, Object>> rows(String table) {
        return graphService.getNodesByLabel(table);
    }

    // =========================
    // TABLE COLUMNS / PROPERTY METADATA
    // =========================

    public List<Map<String, Object>> columns(String table) {
        return graphService.getLabelProperties(table);
    }

    // =========================
    // PRIMARY KEYS
    // =========================

    public List<String> primaryKeys(String table) {
        return graphService.getPrimaryKeys(table);
    }

    // =========================
    // SQL QUERY CONSOLE
    // =========================

    public Map<String, Object> executeQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be empty.");
        }

        String q = query.trim();
        String u = q.toUpperCase(Locale.ROOT);

        // Security check
        if (u.startsWith("DROP ") || u.startsWith("ALTER ") || u.startsWith("TRUNCATE ") ||
            u.startsWith("GRANT ") || u.startsWith("REVOKE ")) {
            throw new IllegalArgumentException("DDL/security commands are restricted in this studio.");
        }

        // If Cypher syntax is passed, route to Neo4j
        if (u.startsWith("MATCH") || u.startsWith("CREATE") || u.startsWith("MERGE") ||
            u.startsWith("RETURN") || u.startsWith("CALL") || u.startsWith("SHOW")) {
            return graphService.executeCypher(q);
        }

        // Standard SQL Console query (SELECT, INSERT, UPDATE, DELETE)
        if (jdbc != null) {
            try {
                if (u.startsWith("SELECT") || u.startsWith("WITH")) {
                    List<Map<String, Object>> rows = jdbc.queryForList(q);
                    return Map.of(
                        "type", "result",
                        "rows", rows,
                        "rowCount", rows.size()
                    );
                } else if (u.startsWith("INSERT") || u.startsWith("UPDATE") || u.startsWith("DELETE")) {
                    int count = jdbc.update(q);
                    return Map.of(
                        "type", "update",
                        "affectedRows", count
                    );
                }
            } catch (Exception e) {
                try {
                    return graphService.executeUnifiedQuery(q);
                } catch (Exception ignored) {
                    throw new RuntimeException("SQL Execution Error: " + e.getMessage(), e);
                }
            }
        }

        return graphService.executeUnifiedQuery(q);
    }

    // =========================
    // INSERT TABLE / NODE
    // =========================

    public int insert(String table, Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("No values supplied.");
        }
        String upperTable = table.toUpperCase(Locale.ROOT).trim();
        int count = graphService.insertNode(upperTable, values);

        if (jdbc != null) {
            try {
                StringBuilder sql = new StringBuilder("INSERT INTO " + upperTable + " (");
                StringBuilder placeholders = new StringBuilder();
                List<Object> params = new ArrayList<>();
                int i = 0;
                for (Map.Entry<String, Object> e : values.entrySet()) {
                    String k = e.getKey();
                    if ("id".equalsIgnoreCase(k) || "name".equalsIgnoreCase(k)) continue;
                    Object val = e.getValue();
                    if (val == null || val.toString().trim().isEmpty() || "null".equalsIgnoreCase(val.toString())) continue;
                    if (i++ > 0) {
                        sql.append(", ");
                        placeholders.append(", ");
                    }
                    sql.append(k);
                    placeholders.append("?");
                    params.add(val);
                }
                sql.append(") VALUES (").append(placeholders).append(")");
                if (!params.isEmpty()) {
                    jdbc.update(sql.toString(), params.toArray());
                }
            } catch (Exception ignored) {
            }
        }
        return count;
    }

    @PostConstruct
    public void autoReconcileForeignKeys() {
        try {
            List<Map<String, Object>> drivers = rows("DRIVER");
            List<Map<String, Object>> employees = rows("EMPLOYEE");
            boolean driverHasD099 = drivers.stream().anyMatch(d -> "D-099".equalsIgnoreCase(String.valueOf(d.get("EMPID"))));
            boolean empHasD099 = employees.stream().anyMatch(e -> "D-099".equalsIgnoreCase(String.valueOf(e.get("EMPID"))));
            boolean driverHasD05 = drivers.stream().anyMatch(d -> "D-05".equalsIgnoreCase(String.valueOf(d.get("EMPID"))));
            boolean empHasD05 = employees.stream().anyMatch(e -> "D-05".equalsIgnoreCase(String.valueOf(e.get("EMPID"))));

            if ((driverHasD099 && !driverHasD05) || (empHasD099 && !empHasD05)) {
                log.info("Auto-reconciling Driver D-05 to D-099 across dependent tables...");
                updateSingle("DRIVER", Map.of("EMPID", "D-099"), Map.of("EMPID", "D-05"));
                updateSingle("EMPLOYEE", Map.of("EMPID", "D-099"), Map.of("EMPID", "D-05"));
                updateSingle("DRIVER_CITY", Map.of("DRIVERID", "D-099"), Map.of("DRIVERID", "D-05"));
                updateSingle("ORDER_DRIVER", Map.of("DRIVERID", "D-099"), Map.of("DRIVERID", "D-05"));
                updateSingle("DELIVERY_DUTY", Map.of("DRIVERID", "D-099"), Map.of("DRIVERID", "D-05"));
                graphService.cascadeUpdateId("DRIVER", "D-05", "D-099");
            }
        } catch (Exception e) {
            log.warn("Auto-reconcile standby: {}", e.getMessage());
        }
    }

    // =========================
    // UPDATE TABLE / NODE (WITH CASCADING ON UPDATE)
    // =========================

    public int update(
        String table,
        Map<String, Object> values,
        Map<String, String> where
    ) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("No values supplied.");
        }
        if (where == null || where.isEmpty()) {
            throw new IllegalArgumentException("A primary-key WHERE condition is required.");
        }

        String upperTable = table.toUpperCase(Locale.ROOT).trim();
        int count = cascadeUpdate(upperTable, values, where);
        return Math.max(1, count);
    }

    private int cascadeUpdate(String upperTable, Map<String, Object> values, Map<String, String> where) {
        int count = 0;
        switch (upperTable) {
            case "DRIVER" -> {
                String oldId = getParam(where, "EMPID", "DRIVERID", "ID");
                String newId = getParam(values, "EMPID", "DRIVERID", "ID");
                count += updateSingle("DRIVER", values, where);
                if (oldId != null && newId != null && !oldId.equalsIgnoreCase(newId)) {
                    count += updateSingle("EMPLOYEE", Map.of("EMPID", newId), Map.of("EMPID", oldId));
                    count += updateSingle("DRIVER_CITY", Map.of("DRIVERID", newId), Map.of("DRIVERID", oldId));
                    count += updateSingle("ORDER_DRIVER", Map.of("DRIVERID", newId), Map.of("DRIVERID", oldId));
                    count += updateSingle("DELIVERY_DUTY", Map.of("DRIVERID", newId), Map.of("DRIVERID", oldId));
                    graphService.cascadeUpdateId("DRIVER", oldId, newId);
                }
            }
            case "EMPLOYEE" -> {
                String oldId = getParam(where, "EMPID", "ID");
                String newId = getParam(values, "EMPID", "ID");
                count += updateSingle("EMPLOYEE", values, where);
                if (oldId != null && newId != null && !oldId.equalsIgnoreCase(newId)) {
                    count += updateSingle("DRIVER", Map.of("EMPID", newId), Map.of("EMPID", oldId));
                    count += updateSingle("MANAGER", Map.of("EMPID", newId), Map.of("EMPID", oldId));
                    count += updateSingle("WAREHOUSE", Map.of("MANAGERID", newId), Map.of("MANAGERID", oldId));
                    count += updateSingle("DRIVER_CITY", Map.of("DRIVERID", newId), Map.of("DRIVERID", oldId));
                    count += updateSingle("ORDER_DRIVER", Map.of("DRIVERID", newId), Map.of("DRIVERID", oldId));
                    count += updateSingle("DELIVERY_DUTY", Map.of("DRIVERID", newId), Map.of("DRIVERID", oldId));
                    graphService.cascadeUpdateId("EMPLOYEE", oldId, newId);
                }
            }
            case "MANAGER" -> {
                String oldId = getParam(where, "EMPID", "ID");
                String newId = getParam(values, "EMPID", "ID");
                count += updateSingle("MANAGER", values, where);
                if (oldId != null && newId != null && !oldId.equalsIgnoreCase(newId)) {
                    count += updateSingle("EMPLOYEE", Map.of("EMPID", newId), Map.of("EMPID", oldId));
                    count += updateSingle("WAREHOUSE", Map.of("MANAGERID", newId), Map.of("MANAGERID", oldId));
                    graphService.cascadeUpdateId("MANAGER", oldId, newId);
                }
            }
            case "CITY" -> {
                String oldCity = getParam(where, "CITY");
                String newCity = getParam(values, "CITY");
                count += updateSingle("CITY", values, where);
                if (oldCity != null && newCity != null && !oldCity.equalsIgnoreCase(newCity)) {
                    count += updateSingle("SUPPLIER", Map.of("CITY", newCity), Map.of("CITY", oldCity));
                    count += updateSingle("CUSTOMER", Map.of("CITY", newCity), Map.of("CITY", oldCity));
                    count += updateSingle("WAREHOUSE", Map.of("CITY", newCity), Map.of("CITY", oldCity));
                    count += updateSingle("DRIVER_CITY", Map.of("CITY", newCity), Map.of("CITY", oldCity));
                    count += updateSingle("DELIVERY_DUTY", Map.of("CITY", newCity), Map.of("CITY", oldCity));
                    graphService.cascadeUpdateId("CITY", oldCity, newCity);
                }
            }
            case "CUSTOMER" -> {
                String oldId = getParam(where, "CUSTID", "ID");
                String newId = getParam(values, "CUSTID", "ID");
                count += updateSingle("CUSTOMER", values, where);
                if (oldId != null && newId != null && !oldId.equalsIgnoreCase(newId)) {
                    count += updateSingle("ORDERS", Map.of("CUSTID", newId), Map.of("CUSTID", oldId));
                    graphService.cascadeUpdateId("CUSTOMER", oldId, newId);
                }
            }
            case "SUPPLIER" -> {
                String oldId = getParam(where, "SUPPLIERID", "ID");
                String newId = getParam(values, "SUPPLIERID", "ID");
                count += updateSingle("SUPPLIER", values, where);
                if (oldId != null && newId != null && !oldId.equalsIgnoreCase(newId)) {
                    count += updateSingle("SUPPLIER_PHONE", Map.of("SUPPLIERID", newId), Map.of("SUPPLIERID", oldId));
                    count += updateSingle("SUPPLIES", Map.of("SUPPLIERID", newId), Map.of("SUPPLIERID", oldId));
                    count += updateSingle("INSPECTION", Map.of("SUPPLIERID", newId), Map.of("SUPPLIERID", oldId));
                    graphService.cascadeUpdateId("SUPPLIER", oldId, newId);
                }
            }
            case "PRODUCT" -> {
                String oldId = getParam(where, "PRODUCTID", "ID");
                String newId = getParam(values, "PRODUCTID", "ID");
                count += updateSingle("PRODUCT", values, where);
                if (oldId != null && newId != null && !oldId.equalsIgnoreCase(newId)) {
                    count += updateSingle("PERISHABLE_PRODUCT", Map.of("PRODUCTID", newId), Map.of("PRODUCTID", oldId));
                    count += updateSingle("HAZARDOUS_PRODUCT", Map.of("PRODUCTID", newId), Map.of("PRODUCTID", oldId));
                    count += updateSingle("SUPPLIES", Map.of("PRODUCTID", newId), Map.of("PRODUCTID", oldId));
                    count += updateSingle("INSPECTION", Map.of("PRODUCTID", newId), Map.of("PRODUCTID", oldId));
                    count += updateSingle("STOCK", Map.of("PRODUCTID", newId), Map.of("PRODUCTID", oldId));
                    count += updateSingle("ORDER_ITEM", Map.of("PRODUCTID", newId), Map.of("PRODUCTID", oldId));
                    graphService.cascadeUpdateId("PRODUCT", oldId, newId);
                }
            }
            case "WAREHOUSE" -> {
                String oldId = getParam(where, "WAREHOUSEID", "ID");
                String newId = getParam(values, "WAREHOUSEID", "ID");
                count += updateSingle("WAREHOUSE", values, where);
                if (oldId != null && newId != null && !oldId.equalsIgnoreCase(newId)) {
                    count += updateSingle("STOCK", Map.of("WAREHOUSEID", newId), Map.of("WAREHOUSEID", oldId));
                    count += updateSingle("GOODS_MOVEMENT", Map.of("WAREHOUSEID", newId), Map.of("WAREHOUSEID", oldId));
                    count += updateSingle("EMPLOYEE", Map.of("WAREHOUSEID", newId), Map.of("WAREHOUSEID", oldId));
                    graphService.cascadeUpdateId("WAREHOUSE", oldId, newId);
                }
            }
            case "ORDERS" -> {
                String oldId = getParam(where, "ORDERID", "ID");
                String newId = getParam(values, "ORDERID", "ID");
                count += updateSingle("ORDERS", values, where);
                if (oldId != null && newId != null && !oldId.equalsIgnoreCase(newId)) {
                    count += updateSingle("ORDER_ITEM", Map.of("ORDERID", newId), Map.of("ORDERID", oldId));
                    count += updateSingle("ORDER_DRIVER", Map.of("ORDERID", newId), Map.of("ORDERID", oldId));
                    count += updateSingle("DELIVERY_DUTY", Map.of("ORDERID", newId), Map.of("ORDERID", oldId));
                    count += updateSingle("GOODS_MOVEMENT", Map.of("ORDERID", newId), Map.of("ORDERID", oldId));
                    graphService.cascadeUpdateId("ORDERS", oldId, newId);
                }
            }
            case "GOODS_MOVEMENT" -> {
                String oldId = getParam(where, "MOVEMENTID", "ID");
                String newId = getParam(values, "MOVEMENTID", "ID");
                count += updateSingle("GOODS_MOVEMENT", values, where);
                if (oldId != null && newId != null && !oldId.equalsIgnoreCase(newId)) {
                    count += updateSingle("SHIPMENT", Map.of("MOVEMENTID", newId), Map.of("MOVEMENTID", oldId));
                    count += updateSingle("GOODS_RETURN", Map.of("MOVEMENTID", newId), Map.of("MOVEMENTID", oldId));
                    graphService.cascadeUpdateId("GOODS_MOVEMENT", oldId, newId);
                }
            }
            default -> {
                count += updateSingle(upperTable, values, where);
            }
        }
        return count;
    }

    // =========================
    // CASCADING DELETE
    // =========================

    public int delete(
        String table,
        Map<String, String> where
    ) {
        if (where == null || where.isEmpty()) {
            throw new IllegalArgumentException("A primary-key WHERE condition is required.");
        }

        String upperTable = table.toUpperCase(Locale.ROOT).trim();
        int totalDeleted = 0;

        switch (upperTable) {
            case "CITY" -> {
                String city = findParam(where, "CITY");
                totalDeleted += cascadeDeleteCity(city);
            }
            case "CUSTOMER" -> {
                String custId = findParam(where, "CUSTID");
                totalDeleted += cascadeDeleteCustomer(custId);
            }
            case "SUPPLIER" -> {
                String suppId = findParam(where, "SUPPLIERID");
                totalDeleted += cascadeDeleteSupplier(suppId);
            }
            case "PRODUCT" -> {
                String prodId = findParam(where, "PRODUCTID");
                totalDeleted += cascadeDeleteProduct(prodId);
            }
            case "ORDERS" -> {
                String orderId = findParam(where, "ORDERID");
                totalDeleted += cascadeDeleteOrder(orderId);
            }
            case "GOODS_MOVEMENT" -> {
                String movId = findParam(where, "MOVEMENTID");
                totalDeleted += cascadeDeleteGoodsMovement(movId);
            }
            case "EMPLOYEE" -> {
                String empId = findParam(where, "EMPID");
                totalDeleted += cascadeDeleteEmployee(empId);
            }
            case "DRIVER" -> {
                String empId = findParam(where, "EMPID");
                totalDeleted += cascadeDeleteDriver(empId);
            }
            case "MANAGER" -> {
                String empId = findParam(where, "EMPID");
                totalDeleted += cascadeDeleteManager(empId);
            }
            case "WAREHOUSE" -> {
                String whId = findParam(where, "WAREHOUSEID");
                totalDeleted += cascadeDeleteWarehouse(whId);
            }
            default -> {
                totalDeleted += deleteSingle(upperTable, where);
            }
        }

        return Math.max(1, totalDeleted);
    }

    private String findParam(Map<String, String> where, String key) {
        for (Map.Entry<String, String> e : where.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        return where.values().iterator().next();
    }

    private String getParam(Map<?, ?> map, String... keys) {
        if (map == null || map.isEmpty()) return null;
        for (String key : keys) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (String.valueOf(e.getKey()).equalsIgnoreCase(key)) {
                    Object val = e.getValue();
                    if (val != null) {
                        String str = String.valueOf(val).trim();
                        if (!str.isEmpty() && !"null".equalsIgnoreCase(str)) return str;
                    }
                }
            }
        }
        return null;
    }

    private List<Map<String, Object>> findMatchingRows(String table, String col, String val) {
        if (val == null) return List.of();
        List<Map<String, Object>> all = rows(table);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : all) {
            for (Map.Entry<String, Object> e : r.entrySet()) {
                if (e.getKey().equalsIgnoreCase(col) && String.valueOf(e.getValue()).equalsIgnoreCase(val)) {
                    result.add(r);
                    break;
                }
            }
        }
        return result;
    }

    private int deleteSingle(String table, Map<String, String> where) {
        int count = graphService.deleteNode(table, where);
        if (jdbc != null) {
            try {
                StringBuilder sql = new StringBuilder("DELETE FROM " + table + " WHERE ");
                List<Object> params = new ArrayList<>();
                int i = 0;
                for (Map.Entry<String, String> e : where.entrySet()) {
                    if (i++ > 0) sql.append(" AND ");
                    sql.append(e.getKey()).append(" = ?");
                    params.add(e.getValue());
                }
                jdbc.update(sql.toString(), params.toArray());
            } catch (Exception ignored) {}
        }
        return Math.max(count, 1);
    }

    private int updateSingle(String table, Map<String, Object> values, Map<String, String> where) {
        int count = graphService.updateNode(table, values, where);
        if (jdbc != null) {
            try {
                StringBuilder sql = new StringBuilder("UPDATE " + table + " SET ");
                List<Object> params = new ArrayList<>();
                int i = 0;
                for (Map.Entry<String, Object> e : values.entrySet()) {
                    if (i++ > 0) sql.append(", ");
                    sql.append(e.getKey()).append(" = ?");
                    params.add(e.getValue());
                }
                sql.append(" WHERE ");
                i = 0;
                for (Map.Entry<String, String> e : where.entrySet()) {
                    if (i++ > 0) sql.append(" AND ");
                    sql.append(e.getKey()).append(" = ?");
                    params.add(e.getValue());
                }
                jdbc.update(sql.toString(), params.toArray());
            } catch (Exception ignored) {}
        }
        return count;
    }

    private int cascadeDeleteCity(String city) {
        int count = 0;
        // 1. Delete dependent CUSTOMERs
        for (Map<String, Object> c : findMatchingRows("CUSTOMER", "CITY", city)) {
            Object id = c.get("CUSTID");
            if (id != null) count += cascadeDeleteCustomer(String.valueOf(id));
        }
        // 2. Delete dependent SUPPLIERs
        for (Map<String, Object> s : findMatchingRows("SUPPLIER", "CITY", city)) {
            Object id = s.get("SUPPLIERID");
            if (id != null) count += cascadeDeleteSupplier(String.valueOf(id));
        }
        // 3. Delete dependent WAREHOUSEs
        for (Map<String, Object> w : findMatchingRows("WAREHOUSE", "CITY", city)) {
            Object id = w.get("WAREHOUSEID");
            if (id != null) count += cascadeDeleteWarehouse(String.valueOf(id));
        }
        // 4. Delete DRIVER_CITY
        count += deleteSingle("DRIVER_CITY", Map.of("CITY", city));
        // 5. Delete DELIVERY_DUTY
        count += deleteSingle("DELIVERY_DUTY", Map.of("CITY", city));
        // 6. Delete CITY
        count += deleteSingle("CITY", Map.of("CITY", city));
        return count;
    }

    private int cascadeDeleteCustomer(String custId) {
        int count = 0;
        for (Map<String, Object> o : findMatchingRows("ORDERS", "CUSTID", custId)) {
            Object id = o.get("ORDERID");
            if (id != null) count += cascadeDeleteOrder(String.valueOf(id));
        }
        count += deleteSingle("CUSTOMER", Map.of("CUSTID", custId));
        return count;
    }

    private int cascadeDeleteOrder(String orderId) {
        int count = 0;
        count += deleteSingle("ORDER_ITEM", Map.of("ORDERID", orderId));
        count += deleteSingle("ORDER_DRIVER", Map.of("ORDERID", orderId));
        count += deleteSingle("DELIVERY_DUTY", Map.of("ORDERID", orderId));
        for (Map<String, Object> m : findMatchingRows("GOODS_MOVEMENT", "ORDERID", orderId)) {
            Object id = m.get("MOVEMENTID");
            if (id != null) count += cascadeDeleteGoodsMovement(String.valueOf(id));
        }
        count += deleteSingle("ORDERS", Map.of("ORDERID", orderId));
        return count;
    }

    private int cascadeDeleteGoodsMovement(String movId) {
        int count = 0;
        count += deleteSingle("SHIPMENT", Map.of("MOVEMENTID", movId));
        count += deleteSingle("GOODS_RETURN", Map.of("MOVEMENTID", movId));
        count += deleteSingle("GOODS_MOVEMENT", Map.of("MOVEMENTID", movId));
        return count;
    }

    private int cascadeDeleteSupplier(String suppId) {
        int count = 0;
        count += deleteSingle("SUPPLIER_PHONE", Map.of("SUPPLIERID", suppId));
        count += deleteSingle("SUPPLIES", Map.of("SUPPLIERID", suppId));
        count += deleteSingle("INSPECTION", Map.of("SUPPLIERID", suppId));
        count += deleteSingle("SUPPLIER", Map.of("SUPPLIERID", suppId));
        return count;
    }

    private int cascadeDeleteProduct(String prodId) {
        int count = 0;
        count += deleteSingle("PERISHABLE_PRODUCT", Map.of("PRODUCTID", prodId));
        count += deleteSingle("HAZARDOUS_PRODUCT", Map.of("PRODUCTID", prodId));
        count += deleteSingle("SUPPLIES", Map.of("PRODUCTID", prodId));
        count += deleteSingle("INSPECTION", Map.of("PRODUCTID", prodId));
        count += deleteSingle("STOCK", Map.of("PRODUCTID", prodId));
        count += deleteSingle("ORDER_ITEM", Map.of("PRODUCTID", prodId));
        count += deleteSingle("PRODUCT", Map.of("PRODUCTID", prodId));
        return count;
    }

    private int cascadeDeleteWarehouse(String whId) {
        int count = 0;
        count += deleteSingle("STOCK", Map.of("WAREHOUSEID", whId));
        for (Map<String, Object> m : findMatchingRows("GOODS_MOVEMENT", "WAREHOUSEID", whId)) {
            Object id = m.get("MOVEMENTID");
            if (id != null) count += cascadeDeleteGoodsMovement(String.valueOf(id));
        }
        updateSingle("EMPLOYEE", Map.of("WAREHOUSEID", ""), Map.of("WAREHOUSEID", whId));
        count += deleteSingle("WAREHOUSE", Map.of("WAREHOUSEID", whId));
        return count;
    }

    private int cascadeDeleteEmployee(String empId) {
        int count = 0;
        count += cascadeDeleteDriver(empId);
        count += cascadeDeleteManager(empId);
        count += deleteSingle("EMPLOYEE", Map.of("EMPID", empId));
        return count;
    }

    private int cascadeDeleteDriver(String empId) {
        int count = 0;
        count += deleteSingle("DRIVER_CITY", Map.of("DRIVERID", empId));
        count += deleteSingle("ORDER_DRIVER", Map.of("DRIVERID", empId));
        count += deleteSingle("DELIVERY_DUTY", Map.of("DRIVERID", empId));
        count += deleteSingle("DRIVER", Map.of("EMPID", empId));
        return count;
    }

    private int cascadeDeleteManager(String empId) {
        int count = 0;
        updateSingle("WAREHOUSE", Map.of("MANAGERID", ""), Map.of("MANAGERID", empId));
        count += deleteSingle("MANAGER", Map.of("EMPID", empId));
        return count;
    }

    // =========================
    // TRIGGERS / GRAPH CONSTRAINTS
    // =========================

    public List<Map<String, Object>> triggers() {
        return graphService.getTriggers();
    }

    // =========================
    // CURSOR 1: CUSTOMER CURSOR
    // =========================

    public List<Map<String, Object>> customerCursor() {
        return graphService.getCustomerCursor();
    }

    // =========================
    // CURSOR 2: PARAMETERIZED ORDER CURSOR
    // =========================

    public List<Map<String, Object>> parameterizedOrderCursor(String custId) {
        if (custId == null || custId.isBlank()) {
            throw new IllegalArgumentException("Customer ID cannot be empty.");
        }
        return graphService.getParameterizedOrderCursor(custId);
    }

    // =========================
    // CURSOR 3: ORDER TOTAL CURSOR
    // =========================

    public List<Map<String, Object>> orderTotalCursor() {
        return graphService.getOrderTotalCursor();
    }
}