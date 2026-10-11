package com.buildback.dto;

import com.buildback.entity.Role;
import com.buildback.entity.User;

/**
 * The only user data ever sent to the browser. No password, no hash.
 */
public record UserResponse(Long id, String name, String email, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
