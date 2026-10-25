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

    @PostMapping("/tables/{table}")
    public ResponseEntity<?> insert(
        @PathVariable String table,
        @RequestBody Map<String, Object> values
    ) {
        try {
            int count = db.insert(table, values);
            return ResponseEntity.ok(Map.of(
                "message", "Record inserted successfully.",
                "affectedRows", count
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage() != null ? e.getMessage() : "Insert failed."
            ));
        }
    }

    @PutMapping("/tables/{table}")
    public ResponseEntity<?> update(
        @PathVariable String table,
        @RequestParam Map<String, String> where,
        @RequestBody Map<String, Object> values
    ) {
        try {
            int count = db.update(table, values, where);
            return ResponseEntity.ok(Map.of(
                "message", "Updated successfully.",
                "affectedRows", count
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage() != null ? e.getMessage() : "Update failed."
            ));
        }
    }

    @DeleteMapping("/tables/{table}")
    public ResponseEntity<?> delete(
        @PathVariable String table,
        @RequestParam Map<String, String> where
    ) {
        try {
            int count = db.delete(table, where);
            return ResponseEntity.ok(Map.of(
                "message", "Deleted successfully (" + count + " related records updated/removed across all tables).",
                "affectedRows", count
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage() != null ? e.getMessage() : "Delete failed."
            ));
        }
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
    // CURSOR 4: STOCK SHORTAGE ALERT CURSOR
    // =========================

    @GetMapping("/cursors/stock-alerts")
    public Map<String, Object> stockAlertCursor() {

        return Map.of(
            "cursor", "Low Stock Alert Cursor",
            "rows", db.stockAlertCursor()
        );
    }

    // =========================
    // CURSOR 5: DRIVER DELIVERY DUTIES CURSOR
    // =========================

    @GetMapping("/cursors/driver-duties")
    public Map<String, Object> driverDutiesCursor(
        @RequestParam(required = false, defaultValue = "D-05") String driverId
    ) {

        return Map.of(
            "cursor", "Driver Delivery Duty Cursor",
            "driverId", driverId,
            "rows", db.driverDutiesCursor(driverId)
        );
    }

    // =========================
    // HEALTH
    // =========================

    @GetMapping("/health")
    public Map<String, Object> health() {
        try {
            db.tables();
            String engine = db.getDatabaseEngineName();
            return Map.of(
                "status", "UP",
                "database", engine != null ? engine : "Neo4j Graph Database"
            );
        } catch (Exception e) {
            return Map.of(
                "status", "DOWN",
                "database", "Neo4j Graph Database",
                "error",
                e.getMessage() == null
                    ? "Connection failed."
                    : e.getMessage()
            );
        }
    }
}