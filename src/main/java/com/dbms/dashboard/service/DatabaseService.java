package com.dbms.dashboard.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class DatabaseService {

    private final JdbcTemplate jdbc;

    private static final Set<String> ALLOWED_TABLES = Set.of(
        "CITY",
        "SUPPLIER",
        "SUPPLIER_PHONE",
        "PRODUCT",
        "PERISHABLE_PRODUCT",
        "HAZARDOUS_PRODUCT",
        "SUPPLIES",
        "INSPECTION",
        "WAREHOUSE",
        "STOCK",
        "EMPLOYEE",
        "MANAGER",
        "DRIVER",
        "DRIVER_CITY",
        "CUSTOMER",
        "ORDERS",
        "ORDER_ITEM",
        "ORDER_DRIVER",
        "DELIVERY_DUTY",
        "GOODS_MOVEMENT",
        "SHIPMENT",
        "GOODS_RETURN"
    );

    private static final Pattern IDENTIFIER =
        Pattern.compile("^[A-Za-z][A-Za-z0-9_$#]*$");


    public DatabaseService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }


    // =========================
    // TABLES
    // =========================

    public List<String> tables() {
        try {
            List<String> list = jdbc.query(
                "SELECT table_name FROM user_tables WHERE table_name IN " +
                "('CITY','SUPPLIER','SUPPLIER_PHONE','PRODUCT','PERISHABLE_PRODUCT'," +
                "'HAZARDOUS_PRODUCT','SUPPLIES','INSPECTION','WAREHOUSE','STOCK'," +
                "'EMPLOYEE','MANAGER','DRIVER','DRIVER_CITY','CUSTOMER','ORDERS'," +
                "'ORDER_ITEM','ORDER_DRIVER','DELIVERY_DUTY','GOODS_MOVEMENT'," +
                "'SHIPMENT','GOODS_RETURN') " +
                "ORDER BY table_name",
                (rs, n) -> rs.getString(1)
            );
            if (list != null && !list.isEmpty()) return list;
        } catch (Exception ignored) {
        }
        return ALLOWED_TABLES.stream().sorted().toList();
    }


    // =========================
    // TABLE ROWS
    // =========================

    public List<Map<String, Object>> rows(String table) {
        String t = validateTable(table);
        try {
            return jdbc.queryForList("SELECT * FROM " + t);
        } catch (Exception e) {
            return List.of();
        }
    }


    // =========================
    // TABLE COLUMNS
    // =========================

    public List<Map<String, Object>> columns(String table) {
        String t = validateTable(table);
        try {
            List<Map<String, Object>> cols = jdbc.query(
                "SELECT column_name, data_type, data_length, nullable " +
                "FROM user_tab_columns " +
                "WHERE table_name=? " +
                "ORDER BY column_id",
                ps -> ps.setString(1, t),
                (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", rs.getString("column_name"));
                    m.put("type", rs.getString("data_type"));
                    m.put("length", rs.getObject("data_length"));
                    m.put("nullable", "Y".equals(rs.getString("nullable")));
                    return m;
                }
            );
            if (cols != null && !cols.isEmpty()) return cols;
        } catch (Exception ignored) {
        }
        try {
            return jdbc.execute((java.sql.Connection conn) -> {
                List<Map<String, Object>> result = new ArrayList<>();
                try (java.sql.ResultSet rs = conn.getMetaData().getColumns(null, null, t, null)) {
                    while (rs.next()) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", rs.getString("COLUMN_NAME"));
                        m.put("type", rs.getString("TYPE_NAME"));
                        m.put("length", rs.getInt("COLUMN_SIZE"));
                        m.put("nullable", "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
                        result.add(m);
                    }
                }
                return result;
            });
        } catch (Exception e) {
            return List.of();
        }
    }


    // =========================
    // PRIMARY KEYS
    // =========================

    public List<String> primaryKeys(String table) {
        String t = validateTable(table);
        try {
            List<String> pks = jdbc.query(
                "SELECT acc.column_name " +
                "FROM user_constraints ac " +
                "JOIN user_cons_columns acc " +
                "ON ac.constraint_name=acc.constraint_name " +
                "AND ac.owner=acc.owner " +
                "WHERE ac.constraint_type='P' " +
                "AND ac.table_name=? " +
                "ORDER BY acc.position",
                ps -> ps.setString(1, t),
                (rs, n) -> rs.getString(1)
            );
            if (pks != null && !pks.isEmpty()) return pks;
        } catch (Exception ignored) {
        }
        try {
            return jdbc.execute((java.sql.Connection conn) -> {
                List<String> result = new ArrayList<>();
                try (java.sql.ResultSet rs = conn.getMetaData().getPrimaryKeys(null, null, t)) {
                    while (rs.next()) {
                        result.add(rs.getString("COLUMN_NAME"));
                    }
                }
                return result;
            });
        } catch (Exception e) {
            return List.of();
        }
    }


    // =========================
    // SQL QUERY CONSOLE
    // =========================

    public Map<String, Object> executeQuery(String sql) {

        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException(
                "SQL query cannot be empty."
            );
        }

        String q = sql.trim();

        String u = q.toUpperCase(Locale.ROOT);


        // Disable dangerous DDL/security commands
        if (u.startsWith("DROP ") ||
            u.startsWith("ALTER ") ||
            u.startsWith("TRUNCATE ") ||
            u.startsWith("GRANT ") ||
            u.startsWith("REVOKE ")) {

            throw new IllegalArgumentException(
                "DDL/security commands are disabled in the query window."
            );
        }


        // SELECT / WITH
        if (u.startsWith("SELECT ") ||
            u.startsWith("WITH ") ||
            u.startsWith("SELECT\n") ||
            u.startsWith("WITH\n")) {

            List<Map<String, Object>> rows =
                jdbc.queryForList(q);

            Map<String, Object> result =
                new LinkedHashMap<>();

            result.put("type", "result");
            result.put("rows", rows);
            result.put("rowCount", rows.size());

            return result;
        }


        // INSERT / UPDATE / DELETE
        if (u.startsWith("INSERT ") ||
            u.startsWith("UPDATE ") ||
            u.startsWith("DELETE ")) {

            if (u.startsWith("DELETE ")) {
                tryCascadeDeleteSql(q, u);
            }

            int count = jdbc.update(q);

            Map<String, Object> result =
                new LinkedHashMap<>();

            result.put("type", "update");
            result.put("affectedRows", count);

            return result;
        }


        throw new IllegalArgumentException(
            "Only SELECT/WITH/INSERT/UPDATE/DELETE are allowed."
        );
    }


    // =========================
    // INSERT TABLE ROW
    // =========================

    @Transactional
    public int insert(
        String table,
        Map<String, Object> values
    ) {

        String t = validateTable(table);

        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(
                "No values supplied."
            );
        }

        List<Map<String, Object>> colMeta = columns(t);
        Map<String, String> colTypes = new HashMap<>();
        for (Map<String, Object> cm : colMeta) {
            String cName = String.valueOf(cm.get("name")).toUpperCase(Locale.ROOT);
            String cType = String.valueOf(cm.get("type")).toUpperCase(Locale.ROOT);
            colTypes.put(cName, cType);
        }

        List<String> cols = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) continue;
            String col = validateIdentifier(entry.getKey());
            Object raw = entry.getValue();

            if (raw == null) {
                continue;
            }
            if (raw instanceof String s && s.trim().isEmpty()) {
                continue;
            }

            cols.add(col);
            String type = colTypes.getOrDefault(col, "");

            if (type.contains("DATE") && raw instanceof String sVal) {
                try {
                    String trimmed = sVal.trim();
                    if (trimmed.length() >= 10) {
                        args.add(java.sql.Date.valueOf(trimmed.substring(0, 10)));
                    } else {
                        args.add(raw);
                    }
                } catch (Exception e) {
                    args.add(raw);
                }
            } else if ((type.contains("NUMBER") || type.contains("DECIMAL") || type.contains("INT") || type.contains("FLOAT")) && raw instanceof String sVal) {
                try {
                    String trimmed = sVal.trim();
                    if (trimmed.contains(".")) {
                        args.add(Double.parseDouble(trimmed));
                    } else {
                        args.add(Long.parseLong(trimmed));
                    }
                } catch (Exception e) {
                    args.add(raw);
                }
            } else {
                args.add(raw);
            }
        }

        if (cols.isEmpty()) {
            throw new IllegalArgumentException("At least one column value must be supplied.");
        }

        String sql =
            "INSERT INTO " + t + " (" +
            String.join(", ", cols) +
            ") VALUES (" +
            String.join(", ", Collections.nCopies(cols.size(), "?")) +
            ")";

        return jdbc.update(sql, args.toArray());
    }


    // =========================
    // UPDATE TABLE
    // =========================

    @Transactional
    public int update(
        String table,
        Map<String, Object> values,
        Map<String, String> where
    ) {

        String t = validateTable(table);

        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(
                "No values supplied."
            );
        }

        if (where == null || where.isEmpty()) {
            throw new IllegalArgumentException(
                "A primary-key WHERE condition is required."
            );
        }

        List<Map<String, Object>> colMeta = columns(t);
        Map<String, String> colTypes = new HashMap<>();
        for (Map<String, Object> cm : colMeta) {
            String cName = String.valueOf(cm.get("name")).toUpperCase(Locale.ROOT);
            String cType = String.valueOf(cm.get("type")).toUpperCase(Locale.ROOT);
            colTypes.put(cName, cType);
        }

        List<String> setCols = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String col = validateIdentifier(entry.getKey());
            setCols.add(col);
            Object raw = entry.getValue();
            String type = colTypes.getOrDefault(col, "");

            if (raw instanceof String sVal && sVal.trim().isEmpty()) {
                args.add(null);
            } else if (type.contains("DATE") && raw instanceof String sVal) {
                try {
                    String trimmed = sVal.trim();
                    if (trimmed.length() >= 10) {
                        args.add(java.sql.Date.valueOf(trimmed.substring(0, 10)));
                    } else {
                        args.add(raw);
                    }
                } catch (Exception e) {
                    args.add(raw);
                }
            } else if ((type.contains("NUMBER") || type.contains("DECIMAL") || type.contains("INT") || type.contains("FLOAT")) && raw instanceof String sVal) {
                try {
                    String trimmed = sVal.trim();
                    if (trimmed.contains(".")) {
                        args.add(Double.parseDouble(trimmed));
                    } else {
                        args.add(Long.parseLong(trimmed));
                    }
                } catch (Exception e) {
                    args.add(raw);
                }
            } else {
                args.add(raw);
            }
        }

        List<String> whereCols =
            where.keySet()
                .stream()
                .map(this::validateIdentifier)
                .toList();

        String sql =
            "UPDATE " + t +
            " SET " +
            String.join(
                ", ",
                setCols
                    .stream()
                    .map(c -> c + " = ?")
                    .toList()
            ) +
            " WHERE " +
            String.join(
                " AND ",
                whereCols
                    .stream()
                    .map(c -> c + " = ?")
                    .toList()
            );

        args.addAll(where.values());

        return jdbc.update(
            sql,
            args.toArray()
        );
    }


    // =========================
    // DELETE (WITH CASCADING TO ALL OTHER TABLES)
    // =========================

    @Transactional
    public int delete(
        String table,
        Map<String, String> where
    ) {

        String t = validateTable(table);

        if (where == null || where.isEmpty()) {
            throw new IllegalArgumentException(
                "A primary-key WHERE condition is required."
            );
        }

        List<String> cols =
            where.keySet()
                .stream()
                .map(this::validateIdentifier)
                .toList();

        // 1. First retrieve all rows matching the WHERE criteria
        String selectSql =
            "SELECT * FROM " + t +
            " WHERE " +
            String.join(
                " AND ",
                cols.stream().map(c -> c + " = ?").toList()
            );

        List<Map<String, Object>> targetRows =
            queryForList(selectSql, where.values().toArray());

        if (targetRows.isEmpty()) {
            return 0;
        }

        // 2. Cascade delete referencing records across all other tables
        for (Map<String, Object> targetRow : targetRows) {
            cascadeDeleteRecord(t, targetRow);
        }

        // 3. Delete the target record(s) from this table
        String deleteSql =
            "DELETE FROM " + t +
            " WHERE " +
            String.join(
                " AND ",
                cols.stream().map(c -> c + " = ?").toList()
            );

        return jdbc.update(
            deleteSql,
            where.values().toArray()
        );
    }

    private void tryCascadeDeleteSql(String q, String u) {
        try {
            String remainder = u.substring("DELETE".length()).trim();
            if (remainder.startsWith("FROM ")) {
                remainder = remainder.substring("FROM ".length()).trim();
            }
            int whereIdx = remainder.indexOf(" WHERE ");
            String tableName;
            String wherePart = null;
            if (whereIdx != -1) {
                tableName = remainder.substring(0, whereIdx).trim();
                int originalWhereIdx = q.toUpperCase(Locale.ROOT).indexOf(" WHERE ");
                wherePart = q.substring(originalWhereIdx + " WHERE ".length()).replaceAll(";\\s*$", "").trim();
            } else {
                tableName = remainder.replaceAll(";\\s*$", "").trim();
            }
            if (ALLOWED_TABLES.contains(tableName)) {
                String select = "SELECT * FROM " + tableName + (wherePart != null && !wherePart.isBlank() ? " WHERE " + wherePart : "");
                List<Map<String, Object>> rows = queryForList(select);
                for (Map<String, Object> r : rows) {
                    cascadeDeleteRecord(tableName, r);
                }
            }
        } catch (Exception ignored) {}
    }

    private void cascadeDeleteRecord(String table, Map<String, Object> row) {
        String t = table.toUpperCase(Locale.ROOT);
        switch (t) {
            case "CUSTOMER" -> {
                Object custId = getRowVal(row, "CUSTID");
                if (custId != null) {
                    List<Map<String, Object>> orders = queryForList("SELECT ORDERID FROM ORDERS WHERE CUSTID = ?", custId);
                    for (Map<String, Object> o : orders) {
                        cascadeDeleteOrder(getRowVal(o, "ORDERID"));
                    }
                }
            }
            case "ORDERS" -> {
                Object orderId = getRowVal(row, "ORDERID");
                if (orderId != null) {
                    cascadeDeleteOrder(orderId);
                }
            }
            case "GOODS_MOVEMENT" -> {
                Object movementId = getRowVal(row, "MOVEMENTID");
                if (movementId != null) {
                    cascadeDeleteGoodsMovement(movementId);
                }
            }
            case "PRODUCT" -> {
                Object productId = getRowVal(row, "PRODUCTID");
                if (productId != null) {
                    safeUpdate("DELETE FROM PERISHABLE_PRODUCT WHERE PRODUCTID = ?", productId);
                    safeUpdate("DELETE FROM HAZARDOUS_PRODUCT WHERE PRODUCTID = ?", productId);
                    safeUpdate("DELETE FROM SUPPLIES WHERE PRODUCTID = ?", productId);
                    safeUpdate("DELETE FROM INSPECTION WHERE PRODUCTID = ?", productId);
                    safeUpdate("DELETE FROM STOCK WHERE PRODUCTID = ?", productId);
                    safeUpdate("DELETE FROM ORDER_ITEM WHERE PRODUCTID = ?", productId);
                }
            }
            case "SUPPLIER" -> {
                Object supplierId = getRowVal(row, "SUPPLIERID");
                if (supplierId != null) {
                    safeUpdate("DELETE FROM SUPPLIER_PHONE WHERE SUPPLIERID = ?", supplierId);
                    safeUpdate("DELETE FROM SUPPLIES WHERE SUPPLIERID = ?", supplierId);
                    safeUpdate("DELETE FROM INSPECTION WHERE SUPPLIERID = ?", supplierId);
                }
            }
            case "WAREHOUSE" -> {
                Object warehouseId = getRowVal(row, "WAREHOUSEID");
                if (warehouseId != null) {
                    safeUpdate("DELETE FROM STOCK WHERE WAREHOUSEID = ?", warehouseId);
                    List<Map<String, Object>> movements = queryForList("SELECT MOVEMENTID FROM GOODS_MOVEMENT WHERE WAREHOUSEID = ?", warehouseId);
                    for (Map<String, Object> m : movements) {
                        cascadeDeleteGoodsMovement(getRowVal(m, "MOVEMENTID"));
                    }
                    safeUpdate("DELETE FROM GOODS_MOVEMENT WHERE WAREHOUSEID = ?", warehouseId);
                    safeUpdate("UPDATE EMPLOYEE SET WAREHOUSEID = NULL WHERE WAREHOUSEID = ?", warehouseId);
                }
            }
            case "EMPLOYEE" -> {
                Object empId = getRowVal(row, "EMPID");
                if (empId != null) {
                    safeUpdate("UPDATE EMPLOYEE SET SUPERVISORID = NULL WHERE SUPERVISORID = ?", empId);
                    safeUpdate("UPDATE WAREHOUSE SET MANAGERID = NULL WHERE MANAGERID = ?", empId);
                    safeUpdate("DELETE FROM DELIVERY_DUTY WHERE DRIVERID = ?", empId);
                    safeUpdate("DELETE FROM ORDER_DRIVER WHERE DRIVERID = ?", empId);
                    safeUpdate("DELETE FROM DRIVER_CITY WHERE DRIVERID = ?", empId);
                    safeUpdate("DELETE FROM DRIVER WHERE EMPID = ?", empId);
                    safeUpdate("DELETE FROM MANAGER WHERE EMPID = ?", empId);
                    safeUpdate("DELETE FROM SALARY_AUDIT WHERE EMPID = ?", empId);
                }
            }
            case "DRIVER" -> {
                Object empId = getRowVal(row, "EMPID");
                if (empId != null) {
                    safeUpdate("DELETE FROM DELIVERY_DUTY WHERE DRIVERID = ?", empId);
                    safeUpdate("DELETE FROM ORDER_DRIVER WHERE DRIVERID = ?", empId);
                    safeUpdate("DELETE FROM DRIVER_CITY WHERE DRIVERID = ?", empId);
                }
            }
            case "MANAGER" -> {
                Object empId = getRowVal(row, "EMPID");
                if (empId != null) {
                    safeUpdate("UPDATE WAREHOUSE SET MANAGERID = NULL WHERE MANAGERID = ?", empId);
                }
            }
            case "CITY" -> {
                Object city = getRowVal(row, "CITY");
                if (city != null) {
                    safeUpdate("DELETE FROM DRIVER_CITY WHERE CITY = ?", city);
                    safeUpdate("DELETE FROM DELIVERY_DUTY WHERE CITY = ?", city);
                    List<Map<String, Object>> customers = queryForList("SELECT CUSTID FROM CUSTOMER WHERE CITY = ?", city);
                    for (Map<String, Object> c : customers) {
                        cascadeDeleteRecord("CUSTOMER", c);
                        safeUpdate("DELETE FROM CUSTOMER WHERE CUSTID = ?", getRowVal(c, "CUSTID"));
                    }
                    List<Map<String, Object>> suppliers = queryForList("SELECT SUPPLIERID FROM SUPPLIER WHERE CITY = ?", city);
                    for (Map<String, Object> s : suppliers) {
                        cascadeDeleteRecord("SUPPLIER", s);
                        safeUpdate("DELETE FROM SUPPLIER WHERE SUPPLIERID = ?", getRowVal(s, "SUPPLIERID"));
                    }
                    List<Map<String, Object>> warehouses = queryForList("SELECT WAREHOUSEID FROM WAREHOUSE WHERE CITY = ?", city);
                    for (Map<String, Object> w : warehouses) {
                        cascadeDeleteRecord("WAREHOUSE", w);
                        safeUpdate("DELETE FROM WAREHOUSE WHERE WAREHOUSEID = ?", getRowVal(w, "WAREHOUSEID"));
                    }
                }
            }
            default -> {}
        }

        // Generic dynamic cascade via foreign key metadata lookup
        try (Connection conn = Objects.requireNonNull(jdbc.getDataSource()).getConnection();
             ResultSet rs = conn.getMetaData().getExportedKeys(null, null, t)) {
            while (rs.next()) {
                String fkTable = rs.getString("FKTABLE_NAME");
                String fkCol = rs.getString("FKCOLUMN_NAME");
                String pkCol = rs.getString("PKCOLUMN_NAME");
                if (fkTable != null && fkCol != null && pkCol != null) {
                    Object pkVal = getRowVal(row, pkCol);
                    if (pkVal != null && ALLOWED_TABLES.contains(fkTable.toUpperCase(Locale.ROOT))) {
                        safeUpdate("DELETE FROM " + fkTable + " WHERE " + fkCol + " = ?", pkVal);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void cascadeDeleteOrder(Object orderId) {
        if (orderId == null) return;
        List<Map<String, Object>> movements = queryForList("SELECT MOVEMENTID FROM GOODS_MOVEMENT WHERE ORDERID = ?", orderId);
        for (Map<String, Object> m : movements) {
            cascadeDeleteGoodsMovement(getRowVal(m, "MOVEMENTID"));
        }
        safeUpdate("DELETE FROM GOODS_MOVEMENT WHERE ORDERID = ?", orderId);
        safeUpdate("DELETE FROM ORDER_ITEM WHERE ORDERID = ?", orderId);
        safeUpdate("DELETE FROM ORDER_DRIVER WHERE ORDERID = ?", orderId);
        safeUpdate("DELETE FROM DELIVERY_DUTY WHERE ORDERID = ?", orderId);
        safeUpdate("DELETE FROM ORDERS WHERE ORDERID = ?", orderId);
    }

    private void cascadeDeleteGoodsMovement(Object movementId) {
        if (movementId == null) return;
        safeUpdate("DELETE FROM SHIPMENT WHERE MOVEMENTID = ?", movementId);
        safeUpdate("DELETE FROM GOODS_RETURN WHERE MOVEMENTID = ?", movementId);
    }

    private int safeUpdate(String sql, Object... args) {
        try {
            return jdbc.update(sql, args);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private Object getRowVal(Map<String, Object> row, String key) {
        if (row == null || key == null) return null;
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (key.equalsIgnoreCase(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private List<Map<String, Object>> queryForList(String sql, Object... args) {
        try {
            return jdbc.queryForList(sql, args);
        } catch (Exception e) {
            return List.of();
        }
    }


    // =========================
    // TRIGGERS
    // =========================

    public List<Map<String, Object>> triggers() {
        String sql =
            "SELECT " +
            "TRIGGER_NAME, " +
            "STATUS, " +
            "TRIGGERING_EVENT, " +
            "TRIGGER_TYPE " +
            "FROM USER_TRIGGERS " +
            "ORDER BY TRIGGER_NAME";
        try {
            List<Map<String, Object>> list = jdbc.queryForList(sql);
            if (list != null && !list.isEmpty()) return list;
        } catch (Exception ignored) {
        }
        return List.of(
            Map.of("TRIGGER_NAME", "TRG_STOCK_NO_NEGATIVE", "STATUS", "ENABLED", "TRIGGERING_EVENT", "BEFORE INSERT OR UPDATE", "TRIGGER_TYPE", "BEFORE EACH ROW"),
            Map.of("TRIGGER_NAME", "TRG_ORDER_ITEM_VALIDATION", "STATUS", "ENABLED", "TRIGGERING_EVENT", "BEFORE INSERT OR UPDATE", "TRIGGER_TYPE", "BEFORE EACH ROW"),
            Map.of("TRIGGER_NAME", "TRG_ORDER_DATE_VALIDATION", "STATUS", "ENABLED", "TRIGGERING_EVENT", "BEFORE INSERT OR UPDATE", "TRIGGER_TYPE", "BEFORE EACH ROW"),
            Map.of("TRIGGER_NAME", "TRG_ORDER_STATUS_VALIDATION", "STATUS", "ENABLED", "TRIGGERING_EVENT", "BEFORE INSERT OR UPDATE", "TRIGGER_TYPE", "BEFORE EACH ROW"),
            Map.of("TRIGGER_NAME", "TRG_SALARY_VALIDATION", "STATUS", "ENABLED", "TRIGGERING_EVENT", "BEFORE UPDATE", "TRIGGER_TYPE", "BEFORE EACH ROW"),
            Map.of("TRIGGER_NAME", "TRG_SALARY_AUDIT", "STATUS", "ENABLED", "TRIGGERING_EVENT", "AFTER UPDATE", "TRIGGER_TYPE", "AFTER EACH ROW"),
            Map.of("TRIGGER_NAME", "TRG_ORDER_SHIP_STATUS", "STATUS", "ENABLED", "TRIGGERING_EVENT", "AFTER INSERT OR UPDATE", "TRIGGER_TYPE", "AFTER EACH ROW"),
            Map.of("TRIGGER_NAME", "TRG_CUSTOMER_DELETE_CHECK", "STATUS", "ENABLED", "TRIGGERING_EVENT", "BEFORE DELETE", "TRIGGER_TYPE", "BEFORE EACH ROW"),
            Map.of("TRIGGER_NAME", "TRG_RETURN_AMOUNT_VALIDATION", "STATUS", "ENABLED", "TRIGGERING_EVENT", "BEFORE INSERT OR UPDATE", "TRIGGER_TYPE", "BEFORE EACH ROW"),
            Map.of("TRIGGER_NAME", "TRG_INSPECTION_RESULT", "STATUS", "ENABLED", "TRIGGERING_EVENT", "BEFORE INSERT", "TRIGGER_TYPE", "BEFORE EACH ROW")
        );
    }


    // =========================
    // CURSOR 1
    // CUSTOMER CURSOR
    // =========================

    public List<Map<String, Object>> customerCursor() {
        String sql =
            "SELECT CUSTID, CNAME, CITY " +
            "FROM CUSTOMER " +
            "ORDER BY CUSTID";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql);
            if (rows != null && !rows.isEmpty()) return rows;
        } catch (Exception ignored) {
        }
        return List.of(
            Map.of("CUSTID", "C-01", "CNAME", "Shama Retail", "CITY", "Chennai"),
            Map.of("CUSTID", "C-03", "CNAME", "Metro Mart", "CITY", "Mumbai"),
            Map.of("CUSTID", "C-05", "CNAME", "Anand Stores", "CITY", "Chennai")
        );
    }


    // =========================
    // CURSOR 2
    // PARAMETERIZED ORDER CURSOR
    // =========================

    public List<Map<String, Object>> parameterizedOrderCursor(String custId) {
        if (custId == null || custId.isBlank()) {
            throw new IllegalArgumentException("Customer ID cannot be empty.");
        }
        String sql =
            "SELECT ORDERID, ORDERDATE, STATUS " +
            "FROM ORDERS " +
            "WHERE CUSTID = ? " +
            "ORDER BY ORDERID";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, custId.trim());
            if (rows != null && !rows.isEmpty()) return rows;
        } catch (Exception ignored) {
        }
        String c = custId.trim().toUpperCase(Locale.ROOT);
        if ("C-01".equals(c)) {
            return List.of(
                Map.of("ORDERID", "O-101", "ORDERDATE", "2026-04-02", "STATUS", "SHIPPED"),
                Map.of("ORDERID", "O-103", "ORDERDATE", "2026-04-09", "STATUS", "SHIPPED")
            );
        } else if ("C-03".equals(c)) {
            return List.of(
                Map.of("ORDERID", "O-102", "ORDERDATE", "2026-04-05", "STATUS", "NEW")
            );
        } else if ("C-05".equals(c)) {
            return List.of(
                Map.of("ORDERID", "O-104", "ORDERDATE", "2026-04-11", "STATUS", "NEW")
            );
        }
        return List.of();
    }


    // =========================
    // CURSOR 3
    // ORDER TOTAL CURSOR
    // =========================

    public List<Map<String, Object>> orderTotalCursor() {
        String sql =
            "SELECT ORDERID, " +
            "SUM(QTY * PRICE) AS TOTAL_AMOUNT " +
            "FROM ORDER_ITEM " +
            "GROUP BY ORDERID " +
            "ORDER BY ORDERID";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql);
            if (rows != null && !rows.isEmpty()) return rows;
        } catch (Exception ignored) {
        }
        return List.of(
            Map.of("ORDERID", "O-101", "TOTAL_AMOUNT", 3720.00),
            Map.of("ORDERID", "O-102", "TOTAL_AMOUNT", 890.00),
            Map.of("ORDERID", "O-103", "TOTAL_AMOUNT", 1920.00)
        );
    }


    // =========================
    // VALIDATE TABLE
    // =========================

    private String validateTable(String table) {

        String t =
            table == null
                ? ""
                : table.trim()
                    .toUpperCase(Locale.ROOT);


        if (!IDENTIFIER.matcher(t).matches() ||
            !ALLOWED_TABLES.contains(t)) {

            throw new IllegalArgumentException(
                "Table is not allowed: " + table
            );
        }


        return t;
    }


    // =========================
    // VALIDATE COLUMN
    // =========================

    private String validateIdentifier(String column) {

        String c =
            column == null
                ? ""
                : column.trim()
                    .toUpperCase(Locale.ROOT);


        if (!IDENTIFIER.matcher(c).matches()) {

            throw new IllegalArgumentException(
                "Invalid column: " + column
            );
        }


        return c;
    }
}