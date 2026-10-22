package fr.bergadev.bergacast.audio;

/**
 * Result of streaming content into a large object.
 *
 * @param oid identifier of the large object
 * @param sizeBytes number of bytes written
 * @param sha256 lowercase hexadecimal SHA-256 of the content
 */
record StoredContent(long oid, long sizeBytes, String sha256) {}
