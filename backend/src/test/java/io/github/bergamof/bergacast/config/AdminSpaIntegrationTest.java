package io.github.bergamof.bergacast.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.bergamof.bergacast.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Uses the stand-in SPA of {@code src/test/resources/static/admin}. */
@IntegrationTest
class AdminSpaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void redirectsTheRootToTheAdministration() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/"));
        mockMvc.perform(get("/admin")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/"));
    }

    @Test
    void servesTheIndexForTheRootAndClientRoutes() throws Exception {
        for (String path : new String[] {"/admin/index.html", "/admin/podcasts/my-show/episodes"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Bergacast admin")));
        }
    }

    @Test
    void forwardsTheSpaRootToItsIndex() throws Exception {
        mockMvc.perform(get("/admin/")).andExpect(status().isOk()).andExpect(forwardedUrl("/admin/index.html"));
    }

    @Test
    void servesAssetsAndDoesNotFallBackForMissingFiles() throws Exception {
        mockMvc.perform(get("/admin/_app/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("bergacast")));
        mockMvc.perform(get("/admin/_app/missing.js")).andExpect(status().isNotFound());
    }
}
