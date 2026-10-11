package com.buildback.dto;

import java.util.List;

/**
 * Dashboard answer: who is logged in, which role area this is, and a few real
 * totals from the database (no invented numbers).
 */
public record DashboardResponse(String dashboard, String message, UserResponse user, List<Stat> stats) {

    /** One tile on the dashboard, e.g. ("Paid orders", "3"). */
    public record Stat(String label, String value) {
    }
}
