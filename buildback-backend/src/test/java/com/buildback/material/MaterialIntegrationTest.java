package com.buildback.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.buildback.entity.Material;
import com.buildback.entity.MaterialStatus;
import com.buildback.entity.Role;
import com.buildback.entity.User;
import com.buildback.repository.MaterialRepository;
import com.buildback.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;

/**
 * Seller CRUD, ownership, archiving, optimistic locking, validation and the
 * public catalogue search. Runs on the in-memory H2 database (profile "test").
 * Each test uses its own random city / users, so tests do not see each other's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MaterialIntegrationTest {

    private static final String PASSWORD = "Secret123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ======================= helpers =======================

    private record TestUser(MockHttpSession session, String name, String email) {
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String csrf(MockHttpSession session) throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/auth/csrf").session(session))
                .andReturn().getResponse().getContentAsString(), "$.token");
    }

    /** Creates a user straight in the database, then logs in through the real login endpoint. */
    private TestUser loggedIn(Role role) throws Exception {
        String name = role.name().charAt(0) + role.name().substring(1).toLowerCase() + " " + unique();
        String email = role.name().toLowerCase() + "-" + unique() + "@example.com";
        userRepository.save(new User(name, email, passwordEncoder.encode(PASSWORD), role));

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", csrf(session))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email).param("password", PASSWORD))
                .andExpect(status().isOk());
        return new TestUser(session, name, email);
    }

    private Map<String, Object> tiles(String city) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Glossy floor tiles");
        body.put("description", "Leftover 2x2 ft vitrified tiles, never used.");
        body.put("category", "TILES");
        body.put("unitPrice", "45.50");
        body.put("availableQuantity", 120);
        body.put("unit", "PIECE");
        body.put("city", city);
        body.put("imageUrl", "https://example.com/tile.jpg");
        body.put("tileLengthFeet", "2.00");
        body.put("tileWidthFeet", "2.00");
        return body;
    }

    private Map<String, Object> paint(String city) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "White emulsion paint");
        body.put("description", "Two unopened 20 litre cans of interior emulsion.");
        body.put("category", "PAINT");
        body.put("unitPrice", "1200.00");
        body.put("availableQuantity", 2);
        body.put("unit", "CAN");
        body.put("city", city);
        return body;
    }

    private Map<String, Object> with(Map<String, Object> base, Object... keyValues) {
        Map<String, Object> copy = new LinkedHashMap<>(base);
        for (int i = 0; i < keyValues.length; i += 2) {
            copy.put((String) keyValues[i], keyValues[i + 1]);
        }
        return copy;
    }

    private ResultActions create(MockHttpSession session, Object body) throws Exception {
        return mockMvc.perform(post("/api/seller/materials").session(session).header("X-CSRF-TOKEN", csrf(session))
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body)));
    }

    private ResultActions update(MockHttpSession session, long id, Object body) throws Exception {
        return mockMvc.perform(put("/api/seller/materials/" + id).session(session)
                .header("X-CSRF-TOKEN", csrf(session))
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body)));
    }

    private ResultActions archive(MockHttpSession session, long id, long version) throws Exception {
        return mockMvc.perform(delete("/api/seller/materials/" + id).param("version", String.valueOf(version))
                .session(session).header("X-CSRF-TOKEN", csrf(session)));
    }

    private long createdId(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    /** query must already be URL-encoded (e.g. "keyword=100%25" for "100%"). */
    private List<Integer> publicIds(String query) throws Exception {
        return JsonPath.read(mockMvc.perform(get(java.net.URI.create("/api/materials?" + query)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.content[*].id");
    }

    // ======================= 1-2. create and see it =======================

    @Test
    void sellerCreatesListingThatAppearsInInventoryAndCatalogue() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        String city = "City" + unique();

        ResultActions created = create(seller.session(), tiles(city))
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.unitPrice").value("45.50"))
                .andExpect(jsonPath("$.tileLengthFeet").value("2.00"));
        long id = createdId(created);

        mockMvc.perform(get("/api/seller/materials/" + id).session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Glossy floor tiles"));

        assertThat(publicIds("city=" + city)).containsExactly((int) id);

        String detail = mockMvc.perform(get("/api/materials/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerName").value(seller.name()))
                .andExpect(jsonPath("$.unit").value("PIECE"))
                .andExpect(jsonPath("$.seller").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).doesNotContain(seller.email()).doesNotContain("password");

        Material saved = materialRepository.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(MaterialStatus.ACTIVE);
    }

    @Test
    void serverOwnedFieldsInTheBodyAreIgnored() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        Map<String, Object> sneaky = with(tiles("City" + unique()),
                "id", 999999, "status", "ARCHIVED", "version", 77, "sellerId", 1, "createdAt", "2000-01-01T00:00:00Z");

        create(seller.session(), sneaky)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(999999)));
    }

    // ======================= 3-4. ownership and roles =======================

    @Test
    void anotherSellerCannotReadEditOrArchiveByGuessingTheId() throws Exception {
        TestUser owner = loggedIn(Role.SELLER);
        TestUser other = loggedIn(Role.SELLER);
        long id = createdId(create(owner.session(), tiles("City" + unique())));

        mockMvc.perform(get("/api/seller/materials/" + id).session(other.session()))
                .andExpect(status().isNotFound());
        update(other.session(), id, with(tiles("Hacked"), "title", "Hacked title", "version", 0))
                .andExpect(status().isNotFound());
        archive(other.session(), id, 0).andExpect(status().isNotFound());

        Material unchanged = materialRepository.findById(id).orElseThrow();
        assertThat(unchanged.getTitle()).isEqualTo("Glossy floor tiles");
        assertThat(unchanged.getStatus()).isEqualTo(MaterialStatus.ACTIVE);
    }

    @Test
    void anonymousBuyersAndAdminsCannotTouchSellerInventory() throws Exception {
        TestUser owner = loggedIn(Role.SELLER);
        long id = createdId(create(owner.session(), tiles("City" + unique())));

        // anonymous with a valid CSRF token -> 401
        MockHttpSession anonymous = new MockHttpSession();
        mockMvc.perform(post("/api/seller/materials").session(anonymous).header("X-CSRF-TOKEN", csrf(anonymous))
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(tiles("X"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/seller/materials")).andExpect(status().isUnauthorized());

        for (Role role : new Role[] { Role.BUYER, Role.ADMIN }) {
            TestUser user = loggedIn(role);
            create(user.session(), tiles("X")).andExpect(status().isForbidden());
            update(user.session(), id, with(tiles("X"), "version", 0)).andExpect(status().isForbidden());
            archive(user.session(), id, 0).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/seller/materials").session(user.session())).andExpect(status().isForbidden());
        }
        assertThat(materialRepository.findById(id).orElseThrow().getStatus()).isEqualTo(MaterialStatus.ACTIVE);
    }

    // ======================= 5. edits and optimistic locking =======================

    @Test
    void ownerEditsPersistButStaleVersionsGet409() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        String city = "City" + unique();
        long id = createdId(create(seller.session(), tiles(city)));

        update(seller.session(), id, with(tiles(city), "title", "Updated tiles", "unitPrice", "40.00", "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated tiles"))
                .andExpect(jsonPath("$.version").value(1));

        // A second tab still has version 0
        update(seller.session(), id, with(tiles(city), "title", "Old tab overwrite", "version", 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Reload")));
        archive(seller.session(), id, 0).andExpect(status().isConflict());

        Material saved = materialRepository.findById(id).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("Updated tiles");
        assertThat(saved.getUnitPrice()).isEqualByComparingTo("40.00");
        assertThat(saved.getStatus()).isEqualTo(MaterialStatus.ACTIVE);

        update(seller.session(), id, tiles(city)) // no version at all
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").exists());
    }

    // ======================= 6. archive =======================

    @Test
    void archiveKeepsTheRowHidesItPubliclyAndIsIdempotent() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        String city = "City" + unique();
        long id = createdId(create(seller.session(), tiles(city)));

        archive(seller.session(), id, 0).andExpect(status().isNoContent());

        Material row = materialRepository.findById(id).orElseThrow(); // still in the database
        assertThat(row.getStatus()).isEqualTo(MaterialStatus.ARCHIVED);

        mockMvc.perform(get("/api/materials/" + id)).andExpect(status().isNotFound());
        assertThat(publicIds("city=" + city)).isEmpty();

        mockMvc.perform(get("/api/seller/materials/" + id).session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mockMvc.perform(get("/api/seller/materials?status=ARCHIVED").session(seller.session()))
                .andExpect(jsonPath("$.content[0].id").value(id));

        archive(seller.session(), id, 0).andExpect(status().isNoContent()); // repeat is fine
        update(seller.session(), id, with(tiles(city), "version", 1))
                .andExpect(status().isConflict()); // archived = read-only
    }

    // ======================= 7-8. validation and stock =======================

    private void expectFieldError(MockHttpSession session, Object body, String field) throws Exception {
        create(session, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + field).exists());
    }

    @Test
    void invalidValuesAreRejectedWith400AndFieldErrors() throws Exception {
        MockHttpSession s = loggedIn(Role.SELLER).session();
        Map<String, Object> t = tiles("Pune");
        Map<String, Object> p = paint("Pune");

        expectFieldError(s, with(t, "unitPrice", "0"), "unitPrice");
        expectFieldError(s, with(t, "unitPrice", "1.234"), "unitPrice");
        expectFieldError(s, with(t, "unitPrice", "10000000.01"), "unitPrice");
        expectFieldError(s, with(t, "unitPrice", "abc"), "unitPrice");
        expectFieldError(s, with(t, "availableQuantity", -1), "availableQuantity");
        expectFieldError(s, with(t, "availableQuantity", 1_000_001), "availableQuantity");
        expectFieldError(s, with(t, "availableQuantity", 2.5), "availableQuantity");
        expectFieldError(s, with(t, "availableQuantity", 0), "availableQuantity"); // create needs >= 1
        expectFieldError(s, with(t, "category", "WOOD"), "category");
        expectFieldError(s, with(p, "unit", "PIECE"), "unit"); // paint is sold per CAN
        expectFieldError(s, with(t, "tileWidthFeet", null), "tileWidthFeet");
        expectFieldError(s, with(t, "tileLengthFeet", "0"), "tileLengthFeet");
        expectFieldError(s, with(t, "tileLengthFeet", "10.01"), "tileLengthFeet");
        expectFieldError(s, with(t, "tileLengthFeet", "1.255"), "tileLengthFeet");
        expectFieldError(s, with(p, "tileLengthFeet", "2.00"), "tileLengthFeet"); // not tiles
        expectFieldError(s, with(t, "imageUrl", "http://example.com/a.jpg"), "imageUrl");
        expectFieldError(s, with(t, "imageUrl", "javascript:alert(1)"), "imageUrl");
        expectFieldError(s, with(t, "imageUrl", "data:image/png;base64,AAAA"), "imageUrl");
        expectFieldError(s, with(t, "title", "  ab  "), "title");
        expectFieldError(s, with(t, "description", "short"), "description");
        expectFieldError(s, with(t, "city", "   "), "city");

        mockMvc.perform(post("/api/seller/materials").session(s).header("X-CSRF-TOKEN", csrf(s))
                .contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validPaintWithoutImageIsAccepted() throws Exception {
        MockHttpSession s = loggedIn(Role.SELLER).session();
        create(s, with(paint("Pune"), "imageUrl", "   "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").doesNotExist())
                .andExpect(jsonPath("$.tileLengthFeet").doesNotExist());
    }

    @Test
    void updatingQuantityToZeroShowsOutOfStock() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        String city = "City" + unique();
        long id = createdId(create(seller.session(), paint(city)));

        update(seller.session(), id, with(paint(city), "availableQuantity", 0, "version", 0))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/materials/" + id))
                .andExpect(jsonPath("$.availableQuantity").value(0))
                .andExpect(jsonPath("$.inStock").value(false));
        assertThat(publicIds("city=" + city)).containsExactly((int) id);
        assertThat(publicIds("city=" + city + "&inStock=true")).isEmpty();
    }

    // ======================= 9. search, sort, paging =======================

    @Test
    void searchFiltersSortingAndPagingAreDoneCorrectly() throws Exception {
        MockHttpSession s = loggedIn(Role.SELLER).session();
        String city = "Searchcity" + unique();

        long a = createdId(create(s, with(tiles(city), "title", "Matte floor tile", "unitPrice", "40.00",
                "availableQuantity", 10)));
        long b = createdId(create(s, with(tiles(city), "title", "Glossy wall tile 100% new", "unitPrice", "55.00")));
        long c = createdId(create(s, with(paint(city), "title", "Emulsion paint",
                "description", "White interior paint, 20 litre can")));
        long d = createdId(create(s, with(paint(city), "title", "Marine plywood", "category", "PLYWOOD",
                "unit", "SHEET", "unitPrice", "2100.00", "description", "8x4 ft marine grade sheets")));
        long e = createdId(create(s, with(paint(city), "title", "Brass tap", "category", "FITTINGS",
                "unit", "PIECE", "unitPrice", "40.00", "description", "New brass taps, boxed")));
        // b goes out of stock
        update(s, b, with(tiles(city), "title", "Glossy wall tile 100% new", "unitPrice", "55.00",
                "availableQuantity", 0, "version", 0)).andExpect(status().isOk());

        String base = "city=" + city.toUpperCase(); // city match ignores case
        List<Integer> all = publicIds(base + "&sort=priceAsc");
        assertThat(all).containsExactly((int) a, (int) e, (int) b, (int) c, (int) d); // 40, 40 (id order), 55, ...
        assertThat(publicIds(base + "&sort=priceDesc")).containsExactly((int) d, (int) c, (int) b, (int) e, (int) a);
        assertThat(publicIds(base)).containsExactly((int) e, (int) d, (int) c, (int) b, (int) a); // newest

        assertThat(publicIds(base + "&keyword=TILE&sort=priceAsc")).containsExactly((int) a, (int) b);
        assertThat(publicIds(base + "&keyword=interior")).containsExactly((int) c); // matches description
        assertThat(publicIds(base + "&keyword=100%25")).containsExactly((int) b); // literal "%"
        assertThat(publicIds(base + "&keyword=_")).isEmpty(); // "_" is not a wildcard
        assertThat(publicIds(base + "&category=TILES&inStock=true")).containsExactly((int) a);
        assertThat(publicIds(base + "&minPrice=40&maxPrice=55&sort=priceAsc"))
                .containsExactly((int) a, (int) e, (int) b);
        assertThat(publicIds(base + "&minPrice=40.00&maxPrice=40.00&sort=priceAsc"))
                .containsExactly((int) a, (int) e);
        assertThat(publicIds("city=" + city.substring(0, 6))).isEmpty(); // exact city, not "starts with"

        mockMvc.perform(get("/api/materials?" + base + "&size=2&sort=priceAsc"))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
        mockMvc.perform(get("/api/materials?" + base + "&size=2&sort=priceAsc&page=2"))
                .andExpect(jsonPath("$.content[0].id").value(d))
                .andExpect(jsonPath("$.last").value(true));
        mockMvc.perform(get("/api/materials?" + base + "&page=7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    // ======================= 10. bad query parameters =======================

    @Test
    void invalidQueryParametersReturn400() throws Exception {
        String[][] cases = {
                { "size=0", "size" }, { "size=51", "size" }, { "page=-1", "page" }, { "page=abc", "page" },
                { "sort=title", "sort" }, { "minPrice=100&maxPrice=50", "minPrice" }, { "minPrice=-1", "minPrice" },
                { "maxPrice=1.234", "maxPrice" }, { "category=WOOD", "category" }, { "inStock=maybe", "inStock" },
                { "keyword=" + "x".repeat(101), "keyword" } };
        for (String[] c : cases) {
            mockMvc.perform(get("/api/materials?" + c[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors." + c[1]).exists());
        }
        mockMvc.perform(get("/api/materials/999999999")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/materials/abc")).andExpect(status().isBadRequest());
    }

    // ======================= 11. CSRF on every write =======================

    @Test
    void createUpdateAndArchiveAllRequireCsrf() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        long id = createdId(create(seller.session(), tiles("City" + unique())));
        String body = jsonMapper.writeValueAsString(with(tiles("X"), "version", 0));

        mockMvc.perform(post("/api/seller/materials").session(seller.session())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/seller/materials/" + id).session(seller.session())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/seller/materials/" + id).param("version", "0").session(seller.session()))
                .andExpect(status().isForbidden());

        assertThat(materialRepository.findById(id).orElseThrow().getStatus()).isEqualTo(MaterialStatus.ACTIVE);
    }

    // ======================= seller inventory list =======================

    @Test
    void sellerListShowsOnlyOwnListingsWithPagingAndStatusFilter() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        TestUser other = loggedIn(Role.SELLER);
        String city = "City" + unique();
        createdId(create(seller.session(), tiles(city)));
        createdId(create(seller.session(), paint(city)));
        long third = createdId(create(seller.session(), tiles(city)));
        createdId(create(other.session(), tiles(city)));
        archive(seller.session(), third, 0).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/seller/materials?size=2").session(seller.session()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].version").exists());
        mockMvc.perform(get("/api/seller/materials?status=ACTIVE").session(seller.session()))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/seller/materials?status=ARCHIVED").session(seller.session()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/seller/materials?status=SOLD").session(seller.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
    }

    @Test
    void archiveNeedsAValidVersionParameter() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        long id = createdId(create(seller.session(), tiles("City" + unique())));

        mockMvc.perform(delete("/api/seller/materials/" + id).session(seller.session())
                .header("X-CSRF-TOKEN", csrf(seller.session())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").exists());
        mockMvc.perform(delete("/api/seller/materials/" + id).param("version", "abc").session(seller.session())
                .header("X-CSRF-TOKEN", csrf(seller.session())))
                .andExpect(status().isBadRequest());
    }
}
