package fr.bergadev.bergacast.web;

/**
 * Representation of the API root.
 *
 * @param version semantic version of the running server ({@code 0.0.0-dev} outside of a release build)
 */
public record ApiRootResponse(String version) {}
