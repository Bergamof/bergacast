package fr.bergadev.bergacast.episode;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OpenAPI description of the multipart body of an episode upload (the parts are read by the controller).
 *
 * @param metadata episode metadata, as JSON
 * @param audio audio file, with an {@code audio/*} content type
 */
@Schema(
        name = "EpisodeUpload",
        requiredProperties = {"metadata", "audio"})
record EpisodeUploadForm(
        EpisodeInput metadata,
        @Schema(type = "string", format = "binary") String audio) {}
