package io.github.bergamof.bergacast.adminuser;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * Password policy: at least {@value #MIN_LENGTH} characters and at most {@value #MAX_BYTES} bytes once UTF-8 encoded
 * (bcrypt ignores, and Spring Security rejects, anything longer). {@code null} is considered valid.
 */
@Documented
@Constraint(validatedBy = Password.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Password {

    int MIN_LENGTH = 12;
    int MAX_BYTES = 72;

    String message() default "must contain at least " + MIN_LENGTH + " characters and at most " + MAX_BYTES + " bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Checks a password against the policy. */
    class Validator implements ConstraintValidator<Password, String> {

        @Override
        public boolean isValid(String password, ConstraintValidatorContext context) {
            return password == null
                    || (password.codePointCount(0, password.length()) >= MIN_LENGTH
                            && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES);
        }
    }
}
