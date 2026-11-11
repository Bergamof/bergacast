-- The media were exported to the object storage after V3 (audio files, cover images): their former content is freed.
-- Large objects are not removed with the rows that reference them: they are unlinked explicitly, before the trigger
-- that unlinked them on deletion and the columns go.
SELECT lo_unlink(content_oid) FROM audio_file WHERE content_oid IS NOT NULL;

DROP TRIGGER audio_file_unlink_large_object ON audio_file;
DROP FUNCTION unlink_audio_large_object();

ALTER TABLE audio_file DROP COLUMN content_oid;
ALTER TABLE podcast_image DROP COLUMN content;
