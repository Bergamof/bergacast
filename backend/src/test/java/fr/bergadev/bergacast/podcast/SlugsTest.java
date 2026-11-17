package fr.bergadev.bergacast.podcast;

import static org.assertj.core.api.Assertions.assertThat;

import fr.bergadev.bergacast.web.ApiPaths;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlugsTest {

    /** The collections of the API share their level with a podcast slug: `/api/v1/{slug}/episodes`. */
    private static final List<String> API_COLLECTIONS = List.of("podcasts", "admin-users", "access-tokens");

    @Test
    void reservesEveryCollectionOfTheApiThatTheEpisodesMappingExcludes() {
        assertThat(API_COLLECTIONS).allSatisfy(collection -> {
            assertThat(Slugs.isReserved(collection)).isTrue();
            assertThat(ApiPaths.EPISODES_MAPPING).contains(collection);
        });
    }

    @Test
    void acceptsAnOrdinarySlug() {
        assertThat(Slugs.isReserved("my-show")).isFalse();
    }
}
