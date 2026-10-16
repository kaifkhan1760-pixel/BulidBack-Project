package com.buildback.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.buildback.entity.Role;
import com.buildback.entity.User;
import com.buildback.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * End-to-end tests of registration, login, sessions, roles, CSRF and CORS.
 * Runs against the in-memory H2 database (profile "test"), never MySQL.
 * Every request goes through the real Spring Security filter chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    private static final String FRONTEND = "http://localhost:5173";
    private static final String PASSWORD = "Secret123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---------- helpers (they behave like the React api client) ----------

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    /** GET /api/auth/csrf for this session and return the token value. */
    private String csrfToken(MockHttpSession session) throws Exception {
        String body = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request,
            MockHttpSession session) throws Exception {
        return request.session(session).header("X-CSRF-TOKEN", csrfToken(session));
    }

    /** A valid mobile number, typed the way people often type it. */
    private static final String PHONE = "98765 43210";

    private static String registerJson(String name, String email, String password, String role) {
        return registerJson(name, email, PHONE, password, role);
    }

    private static String registerJson(String name, String email, String phone, String password, String role) {
        return """
                {"name": %s, "email": %s, "phone": %s, "password": %s, "role": %s}
                """.formatted(quote(name), quote(email), quote(phone), quote(password), quote(role));
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private ResultActions register(String name, String email, String password, String role) throws Exception {
        return register(name, email, PHONE, password, role);
    }

    private ResultActions register(String name, String email, String phone, String password, String role)
            throws Exception {
        MockHttpSession session = new MockHttpSession();
        return mockMvc.perform(withCsrf(post("/api/auth/register"), session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(name, email, phone, password, role)));
    }

    private ResultActions login(MockHttpSession session, String email, String password) throws Exception {
        return mockMvc.perform(withCsrf(post("/api/auth/login"), session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email)
                .param("password", password));
    }

    /** Same as clicking the link in the confirmation email (that flow is tested in EmailVerificationIntegrationTest). */
    private void confirmEmail(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.markEmailVerified();
        userRepository.save(user);
    }

    /** Registers a user, confirms the email and returns a logged-in session. */
    private MockHttpSession registerAndLogin(String role) throws Exception {
        String email = uniqueEmail(role.toLowerCase());
        register("Test " + role, email, PASSWORD, role).andExpect(status().isCreated());
        confirmEmail(email);
        MockHttpSession session = new MockHttpSession();
        login(session, email, PASSWORD).andExpect(status().isOk());
        return session;
    }

    // ---------- 1. health ----------

    @Test
    void healthCheckIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database").value("Connected"));
    }

    // ---------- 2. registration ----------

    @Test
    void buyerRegistrationStoresHashedPasswordAndReturnsSafeUser() throws Exception {
        String email = uniqueEmail("buyer");

        register("  Ali Khan  ", "  " + email.toUpperCase() + " ", PASSWORD, "BUYER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Ali Khan"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.phone").value("+919876543210")) // stored in one format
                .andExpect(jsonPath("$.role").value("BUYER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist());

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(saved.getRole()).isEqualTo(Role.BUYER);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void sellerRegistrationWorks() throws Exception {
        register("Sara Seller", uniqueEmail("seller"), PASSWORD, "SELLER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SELLER"));
    }

    @Test
    void clientCannotChooseIdHashOrCreatedAt() throws Exception {
        String email = uniqueEmail("sneaky");
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(withCsrf(post("/api/auth/register"), session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id": 999999, "name": "Sneaky", "email": "%s", "phone": "9876543210", "password": "%s",
                         "role": "BUYER", "passwordHash": "plain", "createdAt": "2000-01-01T00:00:00Z"}
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getId()).isNotEqualTo(999999L);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(saved.getCreatedAt().toString()).doesNotStartWith("2000");
    }

    // ---------- 3. duplicates ----------

    @Test
    void duplicateEmailIncludingCaseAndSpaceVariantsReturns409() throws Exception {
        String email = uniqueEmail("dup");
        register("First User", email, PASSWORD, "BUYER").andExpect(status().isCreated());

        register("Second User", email, PASSWORD, "SELLER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.email").exists());

        register("Third User", "  " + email.toUpperCase() + "  ", PASSWORD, "BUYER")
                .andExpect(status().isConflict());
    }

    // ---------- 4. invalid input and ADMIN ----------

    @Test
    void invalidInputReturnsFieldErrors() throws Exception {
        register("A", "not-an-email", "12345", "short", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.phone").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.role").exists());
    }

    private void expectFieldError(ResultActions result, String field) throws Exception {
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors." + field).exists());
    }

    @Test
    void namesMustBeLettersOnly() throws Exception {
        expectFieldError(register("8948r84r7894", uniqueEmail("n"), PASSWORD, "BUYER"), "name");
        expectFieldError(register("Kaif123", uniqueEmail("n"), PASSWORD, "BUYER"), "name");
        expectFieldError(register("Kaif @ Khan", uniqueEmail("n"), PASSWORD, "BUYER"), "name");
        expectFieldError(register("<script>", uniqueEmail("n"), PASSWORD, "BUYER"), "name");
        expectFieldError(register("-Kaif", uniqueEmail("n"), PASSWORD, "BUYER"), "name");

        // Real names with dots, apostrophes, hyphens and non-English letters are fine
        register("R. K. D'Souza", uniqueEmail("n"), PASSWORD, "BUYER").andExpect(status().isCreated());
        register("Patel Tiles & Sanitary", uniqueEmail("n"), PASSWORD, "SELLER").andExpect(status().isCreated());
        register("Anne-Marie   Shah", uniqueEmail("n"), PASSWORD, "BUYER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Anne-Marie Shah")); // extra spaces removed
        register("\u0915\u0948\u092b \u0916\u093e\u0928", uniqueEmail("n"), PASSWORD, "BUYER")
                .andExpect(status().isCreated()); // Hindi script
    }

    @Test
    void emailNeedsAProperDomain() throws Exception {
        expectFieldError(register("Test User", "dcdncjdnc", PASSWORD, "BUYER"), "email");
        expectFieldError(register("Test User", "name@gmail", PASSWORD, "BUYER"), "email");
        expectFieldError(register("Test User", "name@.com", PASSWORD, "BUYER"), "email");
        expectFieldError(register("Test User", "na..me@gmail.com", PASSWORD, "BUYER"), "email");
        register("Test User", "first.last+" + UUID.randomUUID() + "@mail.co.in", PASSWORD, "BUYER")
                .andExpect(status().isCreated());
    }

    @Test
    void passwordsMustMixCaseAndNumbers() throws Exception {
        expectFieldError(register("Test User", uniqueEmail("p"), "dncdncidci", "BUYER"), "password"); // the screenshot
        expectFieldError(register("Test User", uniqueEmail("p"), "alllowercase1", "BUYER"), "password");
        expectFieldError(register("Test User", uniqueEmail("p"), "ALLUPPERCASE1", "BUYER"), "password");
        expectFieldError(register("Test User", uniqueEmail("p"), "NoNumbersHere", "BUYER"), "password");
        expectFieldError(register("Test User", uniqueEmail("p"), " Spaced1Out ", "BUYER"), "password");
        String email = "same" + UUID.randomUUID().toString().substring(0, 6) + "Aa1@example.com";
        expectFieldError(register("Test User", email, email.toUpperCase().replace("A1@", "a1@"), "BUYER"), "password");
        register("Test User", uniqueEmail("p"), "Strong Pass 2026", "BUYER").andExpect(status().isCreated());
    }

    @Test
    void phoneMustBeAnIndianMobileAndIsStoredInOneFormat() throws Exception {
        expectFieldError(register("Test User", uniqueEmail("m"), "12345", PASSWORD, "BUYER"), "phone");
        expectFieldError(register("Test User", uniqueEmail("m"), "5876543210", PASSWORD, "BUYER"), "phone"); // starts with 5
        expectFieldError(register("Test User", uniqueEmail("m"), "98765abc10", PASSWORD, "BUYER"), "phone");
        expectFieldError(register("Test User", uniqueEmail("m"), null, PASSWORD, "BUYER"), "phone");

        for (String typed : new String[] { "+91 98765 43210", "098765-43210", "919876543210", "(+91) 98765.43210" }) {
            register("Test User", uniqueEmail("m"), typed, PASSWORD, "BUYER")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.phone").value("+919876543210"));
        }
    }

    @Test
    void adminAndUnknownRolesAreRejected() throws Exception {
        register("Wannabe Admin", uniqueEmail("admin"), PASSWORD, "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").exists());

        register("Unknown Role", uniqueEmail("boss"), PASSWORD, "BOSS")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").exists());
    }

    @Test
    void passwordOver72BytesIsRejected() throws Exception {
        // 37 characters but 74 UTF-8 bytes ("é" is 2 bytes)
        String longPassword = "é".repeat(37);
        register("Long Password", uniqueEmail("long"), longPassword, "BUYER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(withCsrf(post("/api/auth/register"), session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    // ---------- 5 + 7. login, session, /me, session ID rotation ----------

    @Test
    void loginRotatesSessionIdAndMeWorksOnNextRequest() throws Exception {
        String email = uniqueEmail("login");
        register("Login User", email, PASSWORD, "BUYER").andExpect(status().isCreated());
        confirmEmail(email);

        MockHttpSession session = new MockHttpSession();
        String sessionIdBeforeLogin = session.getId();

        login(session, "  " + email.toUpperCase(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("BUYER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        assertThat(session.getId()).isNotEqualTo(sessionIdBeforeLogin);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Login User"));
    }

    // ---------- 6. generic login failure ----------

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameGeneric401() throws Exception {
        String email = uniqueEmail("generic");
        register("Generic User", email, PASSWORD, "BUYER").andExpect(status().isCreated());

        String wrongPassword = login(new MockHttpSession(), email, "WrongPass999")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = login(new MockHttpSession(), uniqueEmail("nobody"), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownEmail).contains("Invalid email or password");
    }

    @Test
    void overlongPasswordAtLoginIsA401NotAServerError() throws Exception {
        String email = uniqueEmail("overlong");
        register("Overlong", email, PASSWORD, "BUYER").andExpect(status().isCreated());

        login(new MockHttpSession(), email, "x".repeat(100))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 8. 401 vs 403 ----------

    @Test
    void anonymousRequestsToProtectedRoutesGet401() throws Exception {
        for (String url : new String[] { "/api/auth/me", "/api/buyer/dashboard",
                "/api/seller/dashboard", "/api/admin/dashboard", "/api/not-a-route", "/login" }) {
            mockMvc.perform(get(url))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        }
    }

    @Test
    void eachRoleOnlyReachesItsOwnDashboard() throws Exception {
        MockHttpSession buyer = registerAndLogin("BUYER");
        mockMvc.perform(get("/api/buyer/dashboard").session(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dashboard").value("BUYER"))
                .andExpect(jsonPath("$.user.role").value("BUYER"));
        mockMvc.perform(get("/api/seller/dashboard").session(buyer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/dashboard").session(buyer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/not-a-route").session(buyer)).andExpect(status().isForbidden());

        MockHttpSession seller = registerAndLogin("SELLER");
        mockMvc.perform(get("/api/seller/dashboard").session(seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dashboard").value("SELLER"));
        mockMvc.perform(get("/api/buyer/dashboard").session(seller)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/dashboard").session(seller)).andExpect(status().isForbidden());
    }

    // ---------- 9. CSRF ----------

    @Test
    void postsWithoutOrWithInvalidCsrfTokenAreRejected() throws Exception {
        String body = registerJson("No Token", uniqueEmail("nocsrf"), PASSWORD, "BUYER");

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Security token missing or expired. Please try again."));

        MockHttpSession session = new MockHttpSession();
        csrfToken(session); // session has a real token, but we send a fake one
        mockMvc.perform(post("/api/auth/register").session(session).header("X-CSRF-TOKEN", "fake-token")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "a@b.com").param("password", PASSWORD))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());
    }

    @Test
    void csrfTokenChangesAfterLoginAndLogout() throws Exception {
        String email = uniqueEmail("rotate");
        register("Rotate User", email, PASSWORD, "BUYER").andExpect(status().isCreated());
        confirmEmail(email);

        MockHttpSession session = new MockHttpSession();
        String tokenBeforeLogin = csrfToken(session);
        mockMvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", tokenBeforeLogin)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email).param("password", PASSWORD))
                .andExpect(status().isOk());

        // The pre-login token no longer works...
        mockMvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", tokenBeforeLogin))
                .andExpect(status().isForbidden());
        // ...but a freshly fetched one does (first mutation after login)
        mockMvc.perform(withCsrf(post("/api/auth/logout"), session))
                .andExpect(status().isNoContent());

        // First mutation after logout with a fresh token works too
        MockHttpSession afterLogout = new MockHttpSession();
        mockMvc.perform(withCsrf(post("/api/auth/register"), afterLogout)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("After Logout", uniqueEmail("after"), PASSWORD, "SELLER")))
                .andExpect(status().isCreated());
    }

    @Test
    void csrfEndpointIsNotCached() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isString());
    }

    // ---------- 10. CORS ----------

    @Test
    void preflightFromFrontendIsAllowedWithCredentials() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                .header("Origin", FRONTEND)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mockMvc.perform(get("/api/auth/csrf").header("Origin", FRONTEND))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void otherOriginsAreRejected() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    // ---------- 11. logout ----------

    @Test
    void logoutInvalidatesSessionSoItCannotBeReused() throws Exception {
        MockHttpSession session = registerAndLogin("BUYER");
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

        mockMvc.perform(withCsrf(post("/api/auth/logout"), session))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }
}
