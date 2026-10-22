package io.github.bergamof.bergacast.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Validates request content that Spring MVC does not bind itself, reporting violations like bound bodies. */
@Component
public class RequestValidator {

    private final Validator validator;

    public RequestValidator(Validator validator) {
        this.validator = validator;
    }

    /**
     * @param validationGroups Bean Validation groups to check, the default group when empty
     * @throws UnprocessableContentException listing every violation
     */
    public <T> void validate(T content, Class<?>... validationGroups) {
        Set<ConstraintViolation<T>> violations = validator.validate(content, validationGroups);
        if (!violations.isEmpty()) {
            List<InvalidField> errors = violations.stream()
                    .map(violation ->
                            new InvalidField(violation.getPropertyPath().toString(), violation.getMessage()))
                    .sorted(Comparator.comparing(InvalidField::field).thenComparing(InvalidField::message))
                    .toList();
            throw new UnprocessableContentException(errors);
        }
    }
}
