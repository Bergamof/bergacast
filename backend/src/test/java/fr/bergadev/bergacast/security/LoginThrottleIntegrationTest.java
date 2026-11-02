package fr.bergadev.bergacast.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.bergadev.bergacast.support.ApiTestClient;
import fr.bergadev.bergacast.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Each test uses its own username: the throttle state is shared by the whole application context. */
@IntegrationTest
class LoginThrottleIntegrationTest {

    private static final String PASSWORD = "a-strong-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTestClient api;

    @Test
    void refusesLoginsWith429AfterThreeFailures() throws Exception {
        String username = "unknown-" + UUID.randomUUID();
        for (int attempt = 0; attempt < 3; attempt++) {
            logIn(username, "wrong-password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER));
        }

        logIn(username, "wrong-password")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "10"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    void refusesEvenTheRightPasswordWhileBlocked() throws Exception {
        String username = createAdministrator();
        for (int attempt = 0; attempt < 3; attempt++) {
            logIn(username, "wrong-password").andExpect(status().isUnauthorized());
        }

        logIn(username, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void forgetsEarlierFailuresAfterASuccessfulLogin() throws Exception {
        String username = createAdministrator();
        logIn(username, "wrong-password").andExpect(status().isUnauthorized());
        logIn(username, "wrong-password").andExpect(status().isUnauthorized());
        logIn(username, PASSWORD).andExpect(status().isOk());

        logIn(username, "wrong-password").andExpect(status().isUnauthorized());
        logIn(username, "wrong-password").andExpect(status().isUnauthorized());
        logIn(username, PASSWORD).andExpect(status().isOk());
    }

    private String createAdministrator() throws Exception {
        String username = "throttled-" + UUID.randomUUID();
        mockMvc.perform(post("/api/v1/admin-users")
                        .header(HttpHeaders.AUTHORIZATION, api.adminAuthorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, PASSWORD)))
                .andExpect(status().isCreated());
        return username;
    }

    private ResultActions logIn(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/access-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(username, password)));
    }

    private static String credentials(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }
}
