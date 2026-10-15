package com.dbms.dashboard.service;

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
    // UNIFIED QUERY CONSOLE (CYPHER & SQL)
    // =========================

    public Map<String, Object> executeQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be empty.");
        }

        String u = query.trim().toUpperCase(Locale.ROOT);

        // Security check
        if (u.startsWith("DROP ") || u.startsWith("ALTER ") || u.startsWith("TRUNCATE ") ||
            u.startsWith("GRANT ") || u.startsWith("REVOKE ")) {
            throw new IllegalArgumentException("DDL/security commands are restricted in this studio.");
        }

        return graphService.executeUnifiedQuery(query);
    }

    // =========================
    // UPDATE TABLE / NODE
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

        return graphService.updateNode(table, values, where);
    }

    // =========================
    // DELETE ROW / NODE
    // =========================

    public int delete(
        String table,
        Map<String, String> where
    ) {
        if (where == null || where.isEmpty()) {
            throw new IllegalArgumentException("A primary-key WHERE condition is required.");
        }

        return graphService.deleteNode(table, where);
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