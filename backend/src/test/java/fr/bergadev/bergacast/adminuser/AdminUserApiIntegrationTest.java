package fr.bergadev.bergacast.adminuser;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.bergadev.bergacast.support.ApiTestClient;
import fr.bergadev.bergacast.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
class AdminUserApiIntegrationTest {

    private static final String PASSWORD = "a-strong-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTestClient api;

    @Autowired
    private JwtDecoder jwtDecoder;

    private String adminToken;

    @BeforeEach
    void logIn() throws Exception {
        adminToken = api.adminToken();
    }

    @Test
    void createsTheInitialAdministratorAtStartup() throws Exception {
        mockMvc.perform(get("/api/v1/admin-users?size=100").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.adminUsers[?(@.username == 'admin')]")
                        .exists())
                .andExpect(
                        jsonPath("$._embedded.adminUsers[0]._links.self.href").isString())
                .andExpect(jsonPath("$._embedded.adminUsers[0].passwordHash").doesNotExist());
    }

    @Test
    void managesAdministrators() throws Exception {
        String username = "user-" + UUID.randomUUID();
        MvcResult created = mockMvc.perform(post("/api/v1/admin-users")
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/v1/admin-users/")))
                .andExpect(jsonPath("$.username").value(username))
                .andReturn();
        String id = api.json(created).get("id").asString();

        mockMvc.perform(post("/api/v1/admin-users")
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, PASSWORD)))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/v1/admin-users/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType("application/merge-patch+json")
                        .content("{\"password\":\"another-strong-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href").isString());
        mockMvc.perform(post("/api/v1/access-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, "another-strong-password")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/admin-users/{id}", id).header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/admin-users/{id}", id).header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void enforcesThePasswordPolicy() throws Exception {
        mockMvc.perform(post("/api/v1/admin-users")
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("short-" + UUID.randomUUID(), "short")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        mockMvc.perform(post("/api/v1/admin-users")
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("long-" + UUID.randomUUID(), "é".repeat(40))))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void forbidsDeletingOneself() throws Exception {
        String ownId = jwtDecoder.decode(adminToken).getSubject();

        mockMvc.perform(delete("/api/v1/admin-users/{id}", ownId).header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An administrator cannot delete their own account"));
    }

    private String admin() {
        return ApiTestClient.bearer(adminToken);
    }

    private static String credentials(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }
}
