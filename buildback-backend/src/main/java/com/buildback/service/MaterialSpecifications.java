package com.buildback.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.buildback.entity.Material;
import com.buildback.entity.MaterialStatus;

import jakarta.persistence.criteria.Predicate;

/**
 * Builds the WHERE part of material queries with the JPA Criteria API.
 * User input is passed as query parameters, never glued into SQL text,
 * so SQL injection is not possible.
 */
public final class MaterialSpecifications {

    /** Escape character for LIKE. "!" works the same in MySQL and H2. */
    private static final char LIKE_ESCAPE = '!';

    private MaterialSpecifications() {
    }

    /** Public catalogue: ACTIVE only, plus every filter the buyer chose (combined with AND). */
    public static Specification<Material> publicSearch(MaterialSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("status"), MaterialStatus.ACTIVE));

            if (c.keyword() != null) {
                // "%" and "_" typed by the user are searched literally, not as wildcards
                String pattern = "%" + escapeLike(c.keyword().toLowerCase(Locale.ROOT)) + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("description")), pattern, LIKE_ESCAPE)));
            }
            if (c.category() != null) {
                where.add(cb.equal(root.get("category"), c.category()));
            }
            if (c.city() != null) {
                where.add(cb.equal(cb.lower(root.get("city")), c.city().toLowerCase(Locale.ROOT)));
            }
            if (c.minPrice() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("unitPrice"), c.minPrice()));
            }
            if (c.maxPrice() != null) {
                where.add(cb.lessThanOrEqualTo(root.get("unitPrice"), c.maxPrice()));
            }
            if (c.inStockOnly()) {
                where.add(cb.greaterThan(root.get("availableQuantity"), 0));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /** A seller's own listings, optionally only ACTIVE or only ARCHIVED. */
    public static Specification<Material> ownedBy(Long sellerId, MaterialStatus status) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("seller").get("id"), sellerId));
            if (status != null) {
                where.add(cb.equal(root.get("status"), status));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /** Makes the user's %, _ and ! characters literal inside a LIKE pattern. */
    public static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
