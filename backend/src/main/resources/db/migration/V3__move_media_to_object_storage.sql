-- Audio files and cover images are now objects of the storage (S3), found from their key alone. Their former content
-- columns become optional and are no longer written; they keep the content stored so far until it is exported to the
-- storage (backend/scripts/export-media-to-object-storage.sh), then a later migration unlinks the large objects and
-- drops the columns. The unlink trigger of V1 stays: deleting an episode still frees its former large object.
ALTER TABLE audio_file ALTER COLUMN content_oid DROP NOT NULL;
ALTER TABLE podcast_image ALTER COLUMN content DROP NOT NULL;
