package com.buildback.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.buildback.entity.Role;
import com.buildback.entity.User;

/**
 * Spring Data generates the SQL for these methods from their names.
 * Emails passed in must already be normalized (EmailUtils.normalize).
 */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRole(Role role);
}
