package fr.bergadev.bergacast.audio;

/** Outcome of evaluating a {@code Range} request header against a representation of known length. */
sealed interface ByteRange {

    /** No (usable) range: the whole representation is sent with status 200. */
    record Full(long length) implements ByteRange {}

    /**
     * A satisfiable single range, sent with status 206.
     *
     * @param first offset of the first byte
     * @param last offset of the last byte (inclusive)
     * @param completeLength length of the whole representation
     */
    record Partial(long first, long last, long completeLength) implements ByteRange {

        long length() {
            return last - first + 1;
        }

        String contentRange() {
            return "bytes " + first + "-" + last + "/" + completeLength;
        }
    }

    /** A syntactically valid range that does not overlap the representation (status 416). */
    record Unsatisfiable(long completeLength) implements ByteRange {}
}
