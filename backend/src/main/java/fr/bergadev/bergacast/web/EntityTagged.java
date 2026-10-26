package fr.bergadev.bergacast.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a handler method whose successful (and {@code 304 Not Modified}) responses carry an {@code ETag} header, so
 * that the OpenAPI document lists it.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EntityTagged {}
