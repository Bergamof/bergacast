package fr.bergadev.bergacast.security;

import static fr.bergadev.bergacast.support.ApiTestClient.bearer;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.bergadev.bergacast.support.ApiTestClient;
import fr.bergadev.bergacast.support.IntegrationTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTestClient api;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void issuesAnAccessTokenForValidCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/access-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin-password-123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").isString());
    }

    @Test
    void rejectsInvalidCredentialsWithAProblem() throws Exception {
        mockMvc.perform(post("/api/v1/access-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password-123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void validatesTheLoginBody() throws Exception {
        mockMvc.perform(post("/api/v1/access-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[1].field").value("username"));
    }

    @Test
    void requiresABearerTokenOnTheAdminApi() throws Exception {
        mockMvc.perform(get("/api/v1/podcasts"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.instance").value("/api/v1/podcasts"));
    }

    @Test
    void rejectsAnInvalidJwt() throws Exception {
        mockMvc.perform(get("/api/v1/podcasts").header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsAnExpiredJwt() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("bergacast")
                .subject("00000000-0000-0000-0000-000000000001")
                .issuedAt(past.minusSeconds(900))
                .expiresAt(past)
                .claim("roles", List.of("ADMIN"))
                .build();
        String expired = jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        mockMvc.perform(get("/api/v1/podcasts").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnUnknownApiToken() throws Exception {
        mockMvc.perform(get("/api/v1/some-podcast/episodes").header(HttpHeaders.AUTHORIZATION, bearer("bgc_unknown")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
    }

    @Test
    void forbidsApiTokensOutsideOfTheirPodcastEpisodes() throws Exception {
        String slug = ApiTestClient.uniqueSlug("scoped");
        api.createPodcast(slug);
        String token = bearer(api.createApiToken(slug));

        mockMvc.perform(get("/api/v1/{slug}/episodes", slug).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/podcasts/{slug}", slug).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.instance").value("/api/v1/podcasts/" + slug));
        mockMvc.perform(get("/api/v1/admin-users").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void forbidsApiTokensOnAnotherPodcast() throws Exception {
        String slug = ApiTestClient.uniqueSlug("owner");
        String otherSlug = ApiTestClient.uniqueSlug("other");
        api.createPodcast(slug);
        api.createPodcast(otherSlug);
        String token = bearer(api.createApiToken(slug));

        mockMvc.perform(get("/api/v1/{slug}/episodes", otherSlug).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mockMvc.perform(ApiTestClient.uploadRequest(otherSlug, "{\"title\":\"x\"}", new byte[] {1}, "audio/mpeg")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void exposesTheApiRootPublicly() throws Exception {
        mockMvc.perform(get("/api/v1").accept(MediaType.parseMediaType("application/hal+json")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/hal+json"))
                .andExpect(jsonPath("$._links.self.href").value(endsWith("/api/v1")))
                .andExpect(jsonPath("$._links.podcasts.href").value(endsWith("/api/v1/podcasts")))
                .andExpect(jsonPath("$._links.adminUsers.href").value(endsWith("/api/v1/admin-users")))
                .andExpect(jsonPath("$._links.accessTokens.href").value(endsWith("/api/v1/access-tokens")));
    }

    @Test
    void rendersUnknownRoutesAndMethodsAsProblems() throws Exception {
        String admin = api.adminAuthorization();
        mockMvc.perform(get("/api/v1/unknown/resource/here").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/no/such/page"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.instance").value("/no/such/page"));
        mockMvc.perform(put("/api/v1/podcasts").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void exposesHealthOnly() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }
}
