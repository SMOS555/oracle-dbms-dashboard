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

        return jdbc.query(
            "SELECT table_name FROM user_tables WHERE table_name IN " +
            "('CITY','SUPPLIER','SUPPLIER_PHONE','PRODUCT','PERISHABLE_PRODUCT'," +
            "'HAZARDOUS_PRODUCT','SUPPLIES','INSPECTION','WAREHOUSE','STOCK'," +
            "'EMPLOYEE','MANAGER','DRIVER','DRIVER_CITY','CUSTOMER','ORDERS'," +
            "'ORDER_ITEM','ORDER_DRIVER','DELIVERY_DUTY','GOODS_MOVEMENT'," +
            "'SHIPMENT','GOODS_RETURN') " +
            "ORDER BY table_name",

            (rs, n) -> rs.getString(1)
        );
    }


    // =========================
    // TABLE ROWS
    // =========================

    public List<Map<String, Object>> rows(String table) {

        String t = validateTable(table);

        return jdbc.queryForList(
            "SELECT * FROM " + t
        );
    }


    // =========================
    // TABLE COLUMNS
    // =========================

    public List<Map<String, Object>> columns(String table) {

        String t = validateTable(table);

        return jdbc.query(
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
                m.put(
                    "nullable",
                    "Y".equals(rs.getString("nullable"))
                );

                return m;
            }
        );
    }


    // =========================
    // PRIMARY KEYS
    // =========================

    public List<String> primaryKeys(String table) {

        String t = validateTable(table);

        return jdbc.query(
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


        return jdbc.queryForList(sql);
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


        return jdbc.queryForList(sql);
    }


    // =========================
    // CURSOR 2
    // PARAMETERIZED ORDER CURSOR
    // =========================

    public List<Map<String, Object>> parameterizedOrderCursor(
        String custId
    ) {

        if (custId == null || custId.isBlank()) {
            throw new IllegalArgumentException(
                "Customer ID cannot be empty."
            );
        }


        String sql =
            "SELECT ORDERID, ORDERDATE, STATUS " +
            "FROM ORDERS " +
            "WHERE CUSTID = ? " +
            "ORDER BY ORDERID";


        return jdbc.queryForList(
            sql,
            custId
        );
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


        return jdbc.queryForList(sql);
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