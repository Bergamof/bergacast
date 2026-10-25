package fr.bergadev.bergacast.episode;

import io.swagger.v3.oas.annotations.media.Schema;

/** OpenAPI description of the multipart body of an episode upload (the parts are read by the controller). */
@Schema(
        name = "EpisodeUpload",
        description = "Multipart body of an episode publication: a JSON `metadata` part and a binary `audio` part.",
        requiredProperties = {"metadata", "audio"})
record EpisodeUploadForm(
        @Schema(
                description = "Metadata of the episode, as a JSON object. Preferably sent with the "
                        + "application/json content type (see the operation description for the accepted "
                        + "alternatives).")
        EpisodeInput metadata,

        @Schema(
                type = "string",
                format = "binary",
                description = "Audio file, stored as is. Its part must declare a concrete audio/* content "
                        + "type (e.g. audio/mpeg), which is served back on download and chooses the extension "
                        + "of the public audio URL: mp3 (audio/mpeg), m4a (audio/mp4, audio/x-m4a), aac, ogg, "
                        + "opus, wav, flac or webm; for other audio types, the extension of the uploaded file "
                        + "name, else bin. At most BERGACAST_MAX_UPLOAD_SIZE (500MB by default).")
        String audio) {}
