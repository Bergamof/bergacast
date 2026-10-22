package io.github.bergamof.bergacast.token;

import io.github.bergamof.bergacast.podcast.Podcast;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Persistence of {@link ApiToken}s. */
interface ApiTokenRepository extends JpaRepository<ApiToken, UUID> {

    List<ApiToken> findByPodcastOrderByCreatedAtAsc(Podcast podcast);

    Optional<ApiToken> findByIdAndPodcast(UUID id, Podcast podcast);

    @Query("select t from ApiToken t join fetch t.podcast where t.tokenHash = :tokenHash")
    Optional<ApiToken> findByTokenHash(String tokenHash);
}
