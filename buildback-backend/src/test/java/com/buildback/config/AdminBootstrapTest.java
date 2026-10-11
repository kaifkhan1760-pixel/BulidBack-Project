package com.buildback.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.buildback.entity.Role;
import com.buildback.entity.User;
import com.buildback.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Admin bootstrap switched ON (test values only, separate in-memory database).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_bootstrap_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.admin.bootstrap.enabled=true",
        "app.admin.bootstrap.name=Test Admin",
        "app.admin.bootstrap.email=  Admin@BuildBack.TEST ",
        "app.admin.bootstrap.password=AdminTest-Only-123" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminBootstrapTest {

    private static final String ADMIN_EMAIL = "admin@buildback.test";
    private static final String ADMIN_PASSWORD = "AdminTest-Only-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void createsAdminWithHashedPasswordAndNormalizedEmail() {
        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getPasswordHash()).isNotEqualTo(ADMIN_PASSWORD);
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();
    }

    @Test
    void adminReachesOnlyTheAdminDashboard() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = JsonPath.read(mockMvc.perform(get("/api/auth/csrf").session(session))
                .andReturn().getResponse().getContentAsString(), "$.token");

        mockMvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", ADMIN_EMAIL).param("password", ADMIN_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(get("/api/admin/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dashboard").value("ADMIN"));
        // No automatic admin access to other role areas in this version
        mockMvc.perform(get("/api/buyer/dashboard").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/seller/dashboard").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void runningAgainNeverResetsPassword() {
        new AdminBootstrap(userRepository, passwordEncoder, "Other", ADMIN_EMAIL, "Different-Pass-999").run(null);

        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();
    }

    @Test
    void neverPromotesAnExistingBuyer() {
        userRepository.save(new User("Normal Buyer", "buyer@buildback.test", passwordEncoder.encode("BuyerPass123"),
                Role.BUYER));

        new AdminBootstrap(userRepository, passwordEncoder, "Admin", "buyer@buildback.test", "Admin-Pass-123").run(null);

        assertThat(userRepository.findByEmail("buyer@buildback.test").orElseThrow().getRole()).isEqualTo(Role.BUYER);
    }
}
