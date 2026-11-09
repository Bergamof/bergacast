CREATE TABLE admin_user (
    id            uuid         PRIMARY KEY,
    username      varchar(100) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    created_at    timestamptz  NOT NULL
);

CREATE TABLE podcast (
    id                      uuid          PRIMARY KEY,
    slug                    varchar(64)   NOT NULL UNIQUE,
    title                   varchar(255)  NOT NULL,
    description             text          NOT NULL,
    author                  varchar(255),
    owner_name              varchar(255),
    owner_email             varchar(255),
    language                varchar(35)   NOT NULL,
    category                varchar(255),
    explicit                boolean       NOT NULL DEFAULT false,
    image_url               varchar(2048),
    website_url             varchar(2048),
    max_episodes            integer       CHECK (max_episodes >= 1),
    evict_oldest_by         varchar(32)   NOT NULL,
    sort_by                 varchar(32)   NOT NULL,
    sort_direction          varchar(4)    NOT NULL,
    include_future_episodes boolean       NOT NULL DEFAULT false,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    version                 bigint        NOT NULL
);

-- Audio content lives in a PostgreSQL large object referenced by content_oid.
CREATE TABLE audio_file (
    id             uuid         PRIMARY KEY,
    content_oid    oid          NOT NULL,
    mime_type      varchar(127) NOT NULL,
    size_bytes     bigint       NOT NULL CHECK (size_bytes >= 0),
    sha256         varchar(64)  NOT NULL,
    file_extension varchar(16)  NOT NULL
);

CREATE TABLE episode (
    id               uuid         PRIMARY KEY,
    podcast_id       uuid         NOT NULL REFERENCES podcast (id) ON DELETE CASCADE,
    audio_file_id    uuid         NOT NULL UNIQUE REFERENCES audio_file (id),
    guid             varchar(255) NOT NULL,
    title            varchar(255) NOT NULL,
    description      text,
    publication_date timestamptz  NOT NULL,
    added_at         timestamptz  NOT NULL,
    duration_seconds integer      CHECK (duration_seconds >= 0),
    season           integer      CHECK (season >= 1),
    episode_number   integer      CHECK (episode_number >= 1),
    episode_type     varchar(16)  NOT NULL,
    explicit         boolean,
    idempotency_key  varchar(255),
    version          bigint       NOT NULL,
    CONSTRAINT episode_guid_unique UNIQUE (podcast_id, guid),
    CONSTRAINT episode_idempotency_key_unique UNIQUE (podcast_id, idempotency_key)
);

CREATE INDEX episode_podcast_publication_date_idx ON episode (podcast_id, publication_date DESC);

CREATE TABLE api_token (
    id           uuid         PRIMARY KEY,
    podcast_id   uuid         NOT NULL REFERENCES podcast (id) ON DELETE CASCADE,
    name         varchar(100) NOT NULL,
    token_hash   varchar(64)  NOT NULL UNIQUE,
    token_prefix varchar(8)   NOT NULL,
    created_at   timestamptz  NOT NULL,
    last_used_at timestamptz
);

CREATE INDEX api_token_podcast_idx ON api_token (podcast_id);

-- Deleting an episode (directly or through the podcast cascade) deletes its audio file row...
CREATE FUNCTION delete_episode_audio_file() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    DELETE FROM audio_file WHERE id = OLD.audio_file_id;
    RETURN OLD;
END;
$$;

CREATE TRIGGER episode_delete_audio_file
    AFTER DELETE ON episode
    FOR EACH ROW EXECUTE FUNCTION delete_episode_audio_file();

-- ...and deleting an audio file row unlinks its large object, so no orphan content is left behind.
CREATE FUNCTION unlink_audio_large_object() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    PERFORM lo_unlink(OLD.content_oid);
    RETURN OLD;
END;
$$;

CREATE TRIGGER audio_file_unlink_large_object
    AFTER DELETE ON audio_file
    FOR EACH ROW EXECUTE FUNCTION unlink_audio_large_object();
