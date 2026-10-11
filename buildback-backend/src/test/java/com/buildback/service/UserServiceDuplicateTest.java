package com.buildback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.buildback.entity.Role;
import com.buildback.entity.User;
import com.buildback.repository.UserRepository;

/**
 * Two sign-ups at the same moment can both pass the "email exists?" check.
 * The database UNIQUE constraint then rejects the second INSERT. These tests make
 * real database errors and check that only the email constraint counts as "duplicate".
 */
@SpringBootTest
@ActiveProfiles("test")
class UserServiceDuplicateTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void uniqueEmailViolationIsRecognisedAsDuplicate() {
        String email = "race-" + UUID.randomUUID() + "@example.com";
        userRepository.saveAndFlush(new User("First", email, "hash", Role.BUYER));

        // Skip the friendly check on purpose, like a second request arriving at the same time
        DataIntegrityViolationException error = catchThrowableOfType(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(new User("Second", email, "hash", Role.SELLER)));

        assertThat(error).isNotNull();
        assertThat(UserService.isDuplicateEmail(error)).isTrue();
    }

    @Test
    void otherIntegrityErrorsAreNotTreatedAsDuplicates() {
        // name is NOT NULL, so this fails for a different reason
        DataIntegrityViolationException error = catchThrowableOfType(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(
                        new User(null, "x-" + UUID.randomUUID() + "@example.com", "hash", Role.BUYER)));

        assertThat(error).isNotNull();
        assertThat(UserService.isDuplicateEmail(error)).isFalse();
    }
}
