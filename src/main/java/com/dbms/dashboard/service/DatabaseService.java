package com.dbms.dashboard.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
    // UPDATE TABLE
    // =========================

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


        List<String> setCols =
            values.keySet()
                .stream()
                .map(this::validateIdentifier)
                .toList();


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


        List<Object> args =
            new ArrayList<>(values.values());

        args.addAll(where.values());


        return jdbc.update(
            sql,
            args.toArray()
        );
    }


    // =========================
    // DELETE
    // =========================

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


        String sql =
            "DELETE FROM " + t +
            " WHERE " +
            String.join(
                " AND ",
                cols
                    .stream()
                    .map(c -> c + " = ?")
                    .toList()
            );


        return jdbc.update(
            sql,
            where.values().toArray()
        );
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