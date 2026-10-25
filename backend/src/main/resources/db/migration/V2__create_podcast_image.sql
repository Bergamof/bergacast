-- Cover image uploaded for a podcast, at most one per podcast. Its content is bounded by the upload limit (20 MB by
-- default) and read whole, so it is stored inline in a bytea, simpler than a large object (no unlink trigger needed).
CREATE TABLE podcast_image (
    podcast_id uuid         PRIMARY KEY REFERENCES podcast (id) ON DELETE CASCADE,
    content    bytea        NOT NULL,
    mime_type  varchar(127) NOT NULL,
    width      integer      NOT NULL CHECK (width >= 1),
    height     integer      NOT NULL CHECK (height >= 1),
    size_bytes bigint       NOT NULL CHECK (size_bytes >= 0),
    sha256     varchar(64)  NOT NULL
);
