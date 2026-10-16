package com.buildback.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.buildback.entity.Material;
import com.buildback.entity.MaterialCategory;
import com.buildback.entity.MaterialStatus;
import com.buildback.entity.MaterialUnit;
import com.buildback.entity.Role;
import com.buildback.entity.User;
import com.fasterxml.jackson.annotation.JsonFormat;

/** Read-only views for the admin pages. Password hashes are never included. */
public final class AdminResponses {

    private AdminResponses() {
    }

    public record Summary(
            long buyers, long sellers, long admins,
            long activeListings, long archivedListings,
            long pendingOrders, long paidOrders, long collectedOrders, long cancelledOrders, long expiredOrders,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal paidRevenue) {
    }

    public record UserRow(Long id, String name, String email, String phone, Role role, Instant createdAt) {
        public static UserRow from(User u) {
            return new UserRow(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(), u.getCreatedAt());
        }
    }

    public record MaterialRow(
            Long id, String title, MaterialCategory category,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice, MaterialUnit unit,
            int availableQuantity, int reservedQuantity, String city, MaterialStatus status,
            String sellerName, String sellerEmail, Instant createdAt) {
        public static MaterialRow from(Material m) {
            return new MaterialRow(m.getId(), m.getTitle(), m.getCategory(), m.getUnitPrice(), m.getUnit(),
                    m.getAvailableQuantity(), m.getReservedQuantity(), m.getCity(), m.getStatus(),
                    m.getSeller().getName(), m.getSeller().getEmail(), m.getCreatedAt());
        }
    }
}
