package com.buildback.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.buildback.entity.EmailVerificationToken;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /** The newest link sent to this user (used to stop "resend" being spammed). */
    Optional<EmailVerificationToken> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    /** When a new link is sent, older unused links stop working. */
    @Modifying
    @Query("delete from EmailVerificationToken t where t.user.id = :userId and t.usedAt is null")
    void deleteUnusedForUser(Long userId);
}
