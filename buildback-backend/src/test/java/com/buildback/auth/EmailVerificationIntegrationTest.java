package com.buildback.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.buildback.repository.EmailVerificationTokenRepository;
import com.buildback.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * The whole confirmation flow, end to end: register -> the email (saved in the test
 * outbox folder because no mail server is configured) -> open its link -> log in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailVerificationIntegrationTest {

    private static final String PASSWORD = "Secret123!";
    private static final Pattern LINK = Pattern.compile("/verify-email#token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Value("${app.mail.outbox-dir}")
    private String outboxDir;

    private String csrf(MockHttpSession session) throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/auth/csrf").session(session))
                .andReturn().getResponse().getContentAsString(), "$.token");
    }

    private ResultActions postJson(String path, String json) throws Exception {
        MockHttpSession session = new MockHttpSession();
        return mockMvc.perform(post(path).session(session).header("X-CSRF-TOKEN", csrf(session))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void register(String email) throws Exception {
        postJson("/api/auth/register", """
                {"name": "Mail Tester", "email": "%s", "phone": "9876543210", "password": "%s", "role": "BUYER"}
                """.formatted(email, PASSWORD)).andExpect(status().isCreated());
    }

    private ResultActions login(String email) throws Exception {
        MockHttpSession session = new MockHttpSession();
        return mockMvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", csrf(session))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", email).param("password", PASSWORD));
    }

    /** The codes from every saved email addressed to this person, oldest first. */
    private List<String> codesSentTo(String email) throws IOException {
        Path folder = Path.of(outboxDir);
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.sorted().map(file -> {
                try {
                    return Files.readString(file);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }).filter(html -> html.contains("To: " + email)).map(html -> {
                Matcher m = LINK.matcher(html);
                return m.find() ? m.group(1) : null;
            }).filter(code -> code != null).toList();
        }
    }

    private static String uniqueEmail() {
        return "mail-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void registerEmailLinkThenLogin() throws Exception {
        String email = uniqueEmail();
        register(email);

        // Not confirmed yet: the right password gives a clear 403 with a code React understands
        login(email).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        List<String> codes = codesSentTo(email);
        assertThat(codes).hasSize(1);
        // Only the hash is stored, never the code itself
        assertThat(tokenRepository.findAll()).noneMatch(t -> t.toString().contains(codes.get(0)));

        postJson("/api/auth/verify-email", "{\"token\": \"%s\"}".formatted(codes.get(0)))
                .andExpect(status().isOk());
        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerified()).isTrue();
        login(email).andExpect(status().isOk());

        // A link works only once
        postJson("/api/auth/verify-email", "{\"token\": \"%s\"}".formatted(codes.get(0)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.token").exists());
    }

    @Test
    void wrongPasswordOnUnconfirmedAccountStaysAGeneric401() throws Exception {
        String email = uniqueEmail();
        register(email);
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", csrf(session))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", email).param("password", "Wrong123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void fakeOrEmptyCodesAreRejected() throws Exception {
        postJson("/api/auth/verify-email", "{\"token\": \"made-up-code\"}").andExpect(status().isBadRequest());
        postJson("/api/auth/verify-email", "{\"token\": \"\"}").andExpect(status().isBadRequest());
        postJson("/api/auth/verify-email", "{}").andExpect(status().isBadRequest());
    }

    @Test
    void resendLooksTheSameForEveryoneAndWaitsBetweenEmails() throws Exception {
        String email = uniqueEmail();
        register(email);

        String unknown = postJson("/api/auth/resend-verification", "{\"email\": \"nobody-%s@example.com\"}"
                .formatted(UUID.randomUUID())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String known = postJson("/api/auth/resend-verification", "{\"email\": \"%s\"}".formatted(email))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(known).isEqualTo(unknown);

        // Asked again within 60 seconds of the sign-up email: no extra email
        assertThat(codesSentTo(email)).hasSize(1);
    }
}
