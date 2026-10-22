package io.github.bergamof.bergacast.podcast;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The annotated slug must not be one of {@link Slugs#RESERVED}. {@code null} is considered valid. */
@Documented
@Constraint(validatedBy = NotReservedSlug.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotReservedSlug {

    String message() default "is reserved by the application";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Checks a slug against the reserved list. */
    class Validator implements ConstraintValidator<NotReservedSlug, String> {

        @Override
        public boolean isValid(String slug, ConstraintValidatorContext context) {
            return slug == null || !Slugs.isReserved(slug);
        }
    }
}
