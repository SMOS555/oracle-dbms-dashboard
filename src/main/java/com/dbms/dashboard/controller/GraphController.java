package com.dbms.dashboard.controller;

import com.dbms.dashboard.service.GraphDatabaseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Modern Graph Database (Neo4j) interactions.
 */
@RestController
@RequestMapping("/api/graph")
@CrossOrigin(origins = "*")
public class GraphController {

    private final GraphDatabaseService graphService;

    public GraphController(GraphDatabaseService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(graphService.getStatus());
    }

    @GetMapping("/data")
    public ResponseEntity<Map<String, Object>> getGraphData() {
        return ResponseEntity.ok(graphService.getGraphData());
    }

    @PostMapping("/cypher")
    public ResponseEntity<?> executeCypher(@RequestBody Map<String, String> payload) {
        try {
            String query = payload.get("query");
            return ResponseEntity.ok(graphService.executeCypher(query));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage() != null ? e.getMessage() : "Error executing Cypher query"
            ));
        }
    }

    @GetMapping("/trace")
    public ResponseEntity<Map<String, Object>> traceSupplyChain(
        @RequestParam(defaultValue = "SUPPLIER_S01") String supplierId
    ) {
        return ResponseEntity.ok(graphService.traceSupplyChain(supplierId));
    }

    @GetMapping("/bottlenecks")
    public ResponseEntity<Map<String, Object>> getBottlenecks() {
        return ResponseEntity.ok(graphService.getBottlenecks());
    }

    @PostMapping("/connect")
    public ResponseEntity<?> connectLive(@RequestBody Map<String, String> credentials) {
        String uri = credentials.get("uri");
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (uri == null || uri.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "URI and password are required."));
        }

        Map<String, Object> result = graphService.connectWithCredentials(uri, username != null ? username : "neo4j", password);
        return ResponseEntity.ok(result);
    }
}
