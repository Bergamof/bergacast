package fr.bergadev.bergacast.config;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.bergadev.bergacast.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BuildProperties buildProperties;

    @Test
    void publishesTheOpenApiDocumentWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").isString())
                .andExpect(jsonPath("$.info.version").value(buildProperties.getVersion()))
                .andExpect(jsonPath("$.components.securitySchemes", hasKey("adminJwt")))
                .andExpect(jsonPath("$.components.securitySchemes", hasKey("podcastToken")))
                .andExpect(jsonPath("$.components.schemas", hasKey("Problem")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/{slug}/episodes")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/{slug}/episodes'].post.requestBody.content", hasKey("multipart/form-data")));
    }

    @Test
    void publishesTheSwaggerUiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
