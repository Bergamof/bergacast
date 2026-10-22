package fr.bergadev.bergacast.token;

import fr.bergadev.bergacast.podcast.Podcast;
import fr.bergadev.bergacast.podcast.PodcastService;
import fr.bergadev.bergacast.web.ResourceNotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Management and verification of podcast API tokens. */
@Service
@Transactional
public class ApiTokenService {

    private final ApiTokenRepository apiTokenRepository;
    private final PodcastService podcastService;
    private final Clock clock;

    ApiTokenService(ApiTokenRepository apiTokenRepository, PodcastService podcastService, Clock clock) {
        this.apiTokenRepository = apiTokenRepository;
        this.podcastService = podcastService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ApiToken> list(String slug) {
        return apiTokenRepository.findByPodcastOrderByCreatedAtAsc(podcastService.getBySlug(slug));
    }

    public CreatedApiToken create(String slug, String name) {
        Podcast podcast = podcastService.getBySlug(slug);
        String plainToken = ApiTokens.generate();
        ApiToken apiToken = new ApiToken(
                podcast, name, ApiTokens.hash(plainToken), ApiTokens.displayedPrefix(plainToken), clock.instant());
        return new CreatedApiToken(apiTokenRepository.save(apiToken), plainToken);
    }

    public void delete(String slug, UUID tokenId) {
        ApiToken apiToken = apiTokenRepository
                .findByIdAndPodcast(tokenId, podcastService.getBySlug(slug))
                .orElseThrow(
                        () -> new ResourceNotFoundException("No API token " + tokenId + " for podcast '" + slug + "'"));
        apiTokenRepository.delete(apiToken);
    }

    /** Returns the identity of a valid plain token and records its use, or empty when the token is unknown. */
    public Optional<AuthenticatedApiToken> authenticate(String plainToken) {
        return apiTokenRepository.findByTokenHash(ApiTokens.hash(plainToken)).map(apiToken -> {
            apiToken.markUsed(clock.instant());
            return new AuthenticatedApiToken(
                    apiToken.getId(), apiToken.getPodcast().getSlug());
        });
    }
}
