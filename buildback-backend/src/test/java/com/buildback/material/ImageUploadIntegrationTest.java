package com.buildback.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.buildback.entity.Role;
import com.buildback.entity.User;
import com.buildback.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;

/** Listing photo upload: who may upload, which files are accepted, and serving them back. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImageUploadIntegrationTest {

    private static final String PASSWORD = "Secret123!";

    /** The 8-byte PNG signature followed by a few bytes: enough for type detection. */
    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13 };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String csrf(MockHttpSession session) throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/auth/csrf").session(session))
                .andReturn().getResponse().getContentAsString(), "$.token");
    }

    private MockHttpSession loggedIn(Role role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        userRepository.save(new User("Test " + role, email, passwordEncoder.encode(PASSWORD), role));
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", csrf(session))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email).param("password", PASSWORD))
                .andExpect(status().isOk());
        return session;
    }

    private ResultActions upload(MockHttpSession session, MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/api/seller/images").file(file).session(session)
                .header("X-CSRF-TOKEN", csrf(session)));
    }

    @Test
    void sellerUploadsPngAndAnyoneCanViewIt() throws Exception {
        MockHttpSession seller = loggedIn(Role.SELLER);

        // The browser says "text/plain" and the name says .txt, but the bytes are a PNG: we trust the bytes
        String body = upload(seller, new MockMultipartFile("file", "photo.txt", "text/plain", PNG))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.url");
        assertThat(url).matches("/api/images/[0-9a-f]{32}\\.png");

        // Public, no login needed
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));

        // The uploaded photo can be used in a listing
        Map<String, Object> listing = new LinkedHashMap<>();
        listing.put("title", "White emulsion paint");
        listing.put("description", "Two unopened 20 litre cans of interior emulsion.");
        listing.put("category", "PAINT");
        listing.put("unitPrice", "1200.00");
        listing.put("availableQuantity", 2);
        listing.put("unit", "CAN");
        listing.put("city", "Pune");
        listing.put("imageUrls", List.of(url));
        mockMvc.perform(post("/api/seller/materials").session(seller).header("X-CSRF-TOKEN", csrf(seller))
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(listing)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value(url));
    }

    @Test
    void nonImageFilesAreRejected() throws Exception {
        MockHttpSession seller = loggedIn(Role.SELLER);
        byte[] svg = "<svg onload=alert(1)></svg>".getBytes();
        upload(seller, new MockMultipartFile("file", "x.png", "image/png", svg))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.file").exists());
        upload(seller, new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/seller/images").session(seller).header("X-CSRF-TOKEN", csrf(seller)))
                .andExpect(status().isBadRequest()); // no file at all
    }

    @Test
    void onlySellersCanUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG);
        upload(loggedIn(Role.BUYER), file).andExpect(status().isForbidden());
        MockHttpSession anonymous = new MockHttpSession();
        upload(anonymous, file).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownOrMalformedNamesAreNotFound() throws Exception {
        mockMvc.perform(get("/api/images/0123456789abcdef0123456789abcdef.jpg")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/images/application.properties")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/images/..%2F..%2Fpom.xml")).andExpect(status().is4xxClientError());
    }
}
