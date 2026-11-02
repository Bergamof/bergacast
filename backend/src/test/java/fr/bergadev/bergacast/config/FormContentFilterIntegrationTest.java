package fr.bergadev.bergacast.config;

import static org.assertj.core.api.Assertions.assertThat;

import fr.bergadev.bergacast.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.filter.FormContentFilter;

@IntegrationTest
class FormContentFilterIntegrationTest {

    @Autowired
    private ApplicationContext context;

    /** It would read whole PUT/PATCH/DELETE form bodies in memory, before authentication and without limit. */
    @Test
    void doesNotParseFormBodiesOfPutPatchAndDeleteRequests() {
        assertThat(context.getBeansOfType(FormContentFilter.class)).isEmpty();
    }
}
