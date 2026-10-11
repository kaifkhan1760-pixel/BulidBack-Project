package com.buildback.dto;

import com.buildback.entity.Role;

/**
 * Roles a visitor may choose when signing up.
 * ADMIN is intentionally missing, so {"role":"ADMIN"} cannot even be parsed.
 */
public enum RegistrationRole {
    BUYER,
    SELLER;

    public Role toRole() {
        return switch (this) {
            case BUYER -> Role.BUYER;
            case SELLER -> Role.SELLER;
        };
    }
}
