package io.github.bergamof.bergacast.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.bergamof.bergacast.support.IntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Writes the OpenAPI document to {@code build/openapi.json} (path given by the build in the
 * {@code bergacast.openapi.output} system property) so that CI can lint it, see the {@code generateOpenApiSpec} task.
 */
@IntegrationTest
class OpenApiSpecExportTest {

    private static final String OUTPUT_PROPERTY = "bergacast.openapi.output";
    private static final String DEFAULT_OUTPUT = "build/openapi.json";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exportsTheOpenApiDocument() throws Exception {
        byte[] document = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        Path output = Path.of(System.getProperty(OUTPUT_PROPERTY, DEFAULT_OUTPUT));
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.write(output, document);

        assertThat(output).isNotEmptyFile();
    }
}
