package com.buildback.config;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.buildback.entity.Role;
import com.buildback.entity.User;
import com.buildback.repository.UserRepository;
import com.buildback.service.EmailUtils;

/**
 * Creates the first ADMIN account once, at startup. There is no public
 * "make admin" endpoint, so this is the only way an admin can exist.
 *
 * OFF by default. Only runs when app.admin.bootstrap.enabled=true
 * (environment variable ADMIN_BOOTSTRAP_ENABLED=true in Eclipse).
 *
 * Safety rules:
 *  - creates the admin only if no account uses that email
 *  - never changes an existing password and never promotes a buyer/seller
 *  - never writes the email or password to the log
 */
@Component
@ConditionalOnProperty(name = "app.admin.bootstrap.enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String name;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.bootstrap.name}") String name,
            @Value("${app.admin.bootstrap.email}") String email,
            @Value("${app.admin.bootstrap.password}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.name = name == null ? "" : name.trim();
        this.email = EmailUtils.normalize(email);
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        validateSettings();

        if (userRepository.existsByEmail(email)) {
            log.info("Admin bootstrap: an account with the configured email already exists. Nothing was changed.");
            return;
        }

        userRepository.save(new User(name, email, passwordEncoder.encode(password), Role.ADMIN));
        log.info("Admin bootstrap: admin account created. Now set ADMIN_BOOTSTRAP_ENABLED=false "
                + "and remove ADMIN_PASSWORD from the run configuration.");
    }

    /** Stop startup with a clear message instead of creating a broken admin. */
    private void validateSettings() {
        if (email == null || !email.contains("@") || email.length() > 254) {
            throw new IllegalStateException("Admin bootstrap is enabled but ADMIN_EMAIL is missing or invalid.");
        }
        if (name.length() < 2 || name.length() > 100) {
            throw new IllegalStateException("Admin bootstrap: ADMIN_NAME must be 2 to 100 characters.");
        }
        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "Admin bootstrap is enabled but ADMIN_PASSWORD is missing, shorter than 8 characters or longer than 72 bytes.");
        }
    }
}
