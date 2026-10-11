package com.buildback.service;

import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.buildback.dto.RegisterRequest;
import com.buildback.dto.UserResponse;
import com.buildback.entity.User;
import com.buildback.exception.EmailAlreadyUsedException;
import com.buildback.repository.UserRepository;

/**
 * Business rules for user accounts. Controllers call this class;
 * this class calls the repository. It never returns the User entity to the web layer.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a BUYER or SELLER account. The request was already validated and
     * its email normalized (see RegisterRequest).
     */
    public UserResponse register(RegisterRequest request) {
        // 1. Friendly check: gives a clear 409 in the normal case
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyUsedException();
        }

        // 2. Store only the BCrypt hash, never the plain password
        User user = new User(
                request.name(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role().toRole());

        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // 3. Two sign-ups with the same email at the same moment can both pass
            //    step 1. The UNIQUE constraint in MySQL then rejects the second one.
            if (isDuplicateEmail(e)) {
                throw new EmailAlreadyUsedException();
            }
            throw e; // a different database problem: do not pretend it is a duplicate
        }
    }

    public Optional<UserResponse> findById(Long id) {
        return userRepository.findById(id).map(UserResponse::from);
    }

    /** True only when the error came from the uk_users_email constraint. */
    static boolean isDuplicateEmail(DataIntegrityViolationException e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && mentionsEmailConstraint(violation.getConstraintName())) {
                return true;
            }
            if (mentionsEmailConstraint(cause.getMessage())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static boolean mentionsEmailConstraint(String text) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(User.EMAIL_UNIQUE_CONSTRAINT);
    }
}
