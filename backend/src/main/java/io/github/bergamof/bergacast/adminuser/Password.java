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

/** The annotated password must satisfy the {@link PasswordPolicy}. {@code null} is considered valid. */
@Documented
@Constraint(validatedBy = Password.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Password {

    String message() default PasswordPolicy.DESCRIPTION;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Checks a password against the policy. */
    class Validator implements ConstraintValidator<Password, String> {

        @Override
        public boolean isValid(String password, ConstraintValidatorContext context) {
            return password == null || PasswordPolicy.isSatisfiedBy(password);
        }
    }
}
