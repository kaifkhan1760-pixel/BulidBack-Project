package com.buildback.controller;

import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Connection check used by the React "Check Connection" button.
 * GET http://localhost:8081/api/health
 * CORS for this endpoint is handled globally in CorsConfig.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> checkHealth() {
        try {
            // Simplest possible query: proves we can talk to MySQL
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            return ResponseEntity.ok(Map.of(
                    "application", "BuildBack",
                    "backend", "Running",
                    "database", "Connected"));

        } catch (DataAccessException e) {
            // Backend is running but MySQL did not answer.
            // Do not send SQL errors or stack traces to the browser.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "application", "BuildBack",
                    "backend", "Running",
                    "database", "Unavailable",
                    "message", "The backend could not reach the MySQL database."));
        }
    }
}
