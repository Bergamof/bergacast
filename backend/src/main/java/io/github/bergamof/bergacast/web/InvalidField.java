package io.github.bergamof.bergacast.web;

/**
 * One validation error exposed in the {@code errors} member of a Problem Details response.
 *
 * @param field path of the invalid property in the request body
 * @param message human readable reason
 */
public record InvalidField(String field, String message) {}
