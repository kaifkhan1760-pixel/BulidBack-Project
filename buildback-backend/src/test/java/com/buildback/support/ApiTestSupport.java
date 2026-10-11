package com.buildback.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.buildback.entity.Role;
import com.buildback.entity.User;
import com.buildback.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;

/**
 * Shared helpers for API tests: log in as a fresh user of any role and send
 * JSON with a real CSRF token, exactly like the React client does.
 */
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "Secret123!";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper jsonMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    public record TestUser(Long id, MockHttpSession session, String name, String email) {
    }

    protected static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected String csrf(MockHttpSession session) throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/auth/csrf").session(session))
                .andReturn().getResponse().getContentAsString(), "$.token");
    }

    protected TestUser loggedIn(Role role) throws Exception {
        String name = role.name().charAt(0) + role.name().substring(1).toLowerCase() + " " + unique();
        String email = role.name().toLowerCase() + "-" + unique() + "@example.com";
        User user = userRepository.save(new User(name, email, passwordEncoder.encode(PASSWORD), role));

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", csrf(session))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email).param("password", PASSWORD))
                .andExpect(status().isOk());
        return new TestUser(user.getId(), session, name, email);
    }

    /** POST with JSON body (null = no body) and a fresh CSRF token. */
    protected ResultActions postJson(TestUser user, String url, Object body) throws Exception {
        var request = post(url).session(user.session()).header("X-CSRF-TOKEN", csrf(user.session()));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    protected ResultActions getAs(TestUser user, String url) throws Exception {
        return mockMvc.perform(get(url).session(user.session()));
    }

    protected static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put((String) keyValues[i], keyValues[i + 1]);
        }
        return result;
    }

    protected static long idOf(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    protected static <T> T read(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }
}
