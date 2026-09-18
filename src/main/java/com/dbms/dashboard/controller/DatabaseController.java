package com.dbms.dashboard.controller;

import com.dbms.dashboard.model.QueryRequest;
import com.dbms.dashboard.service.DatabaseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class DatabaseController {

    private final DatabaseService db;

    public DatabaseController(DatabaseService db) {
        this.db = db;
    }

    @GetMapping("/tables")
    public Map<String, Object> tables() {
        return Map.of(
            "tables", db.tables()
        );
    }

    @GetMapping("/tables/{table}/columns")
    public Map<String, Object> columns(@PathVariable String table) {
        return Map.of(
            "table", table.toUpperCase(),
            "columns", db.columns(table),
            "primaryKeys", db.primaryKeys(table)
        );
    }

    @GetMapping("/tables/{table}/rows")
    public Map<String, Object> rows(@PathVariable String table) {
        return Map.of(
            "table", table.toUpperCase(),
            "rows", db.rows(table)
        );
    }

    @PutMapping("/tables/{table}")
    public Map<String, Object> update(
        @PathVariable String table,
        @RequestParam Map<String, String> where,
        @RequestBody Map<String, Object> values
    ) {
        int count = db.update(table, values, where);

        return Map.of(
            "message", "Updated successfully.",
            "affectedRows", count
        );
    }

    @DeleteMapping("/tables/{table}")
    public Map<String, Object> delete(
        @PathVariable String table,
        @RequestParam Map<String, String> where
    ) {
        int count = db.delete(table, where);

        return Map.of(
            "message", "Deleted successfully.",
            "affectedRows", count
        );
    }

    // =========================
    // SQL CONSOLE
    // =========================

    @PostMapping("/query")
    public ResponseEntity<?> query(
        @RequestBody QueryRequest request
    ) {
        try {
            return ResponseEntity.ok(
                db.executeQuery(request.sql())
            );

        } catch (Exception e) {

            return ResponseEntity.badRequest().body(
                Map.of(
                    "error",
                    e.getMessage() == null
                        ? "Database error."
                        : e.getMessage()
                )
            );
        }
    }

    // =========================
    // TRIGGERS
    // =========================

    @GetMapping("/triggers")
    public Map<String, Object> triggers() {

        return Map.of(
            "triggers", db.triggers()
        );
    }

    // =========================
    // CURSOR 1
    // =========================

    @GetMapping("/cursors/customers")
    public Map<String, Object> customerCursor() {

        return Map.of(
            "cursor", "Customer Cursor",
            "rows", db.customerCursor()
        );
    }

    // =========================
    // CURSOR 2
    // =========================

    @GetMapping("/cursors/orders")
    public Map<String, Object> parameterizedOrderCursor(
        @RequestParam String custId
    ) {

        return Map.of(
            "cursor", "Parameterized Order Cursor",
            "customerId", custId,
            "rows", db.parameterizedOrderCursor(custId)
        );
    }

    // =========================
    // CURSOR 3
    // =========================

    @GetMapping("/cursors/order-totals")
    public Map<String, Object> orderTotalCursor() {

        return Map.of(
            "cursor", "Order Total Cursor",
            "rows", db.orderTotalCursor()
        );
    }

    // =========================
    // HEALTH
    // =========================

    @GetMapping("/health")
    public Map<String, Object> health() {

        try {

            db.tables();

            return Map.of(
                "status", "UP",
                "database", "Oracle"
            );

        } catch (Exception e) {

            return Map.of(
                "status", "DOWN",
                "database", "Oracle",
                "error",
                e.getMessage() == null
                    ? "Connection failed."
                    : e.getMessage()
            );
        }
    }
}