package io.github.bergamof.bergacast.web;

import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Applies a JSON Merge Patch document (RFC 7396) to an immutable view of a resource, then validates the result. */
@Component
public class JsonMergePatch {

    private final JsonMapper jsonMapper;
    private final RequestValidator requestValidator;

    public JsonMergePatch(JsonMapper jsonMapper, RequestValidator requestValidator) {
        this.jsonMapper = jsonMapper;
        this.requestValidator = requestValidator;
    }

    /**
     * Returns {@code current} with {@code patch} merged into it.
     *
     * @param validationGroups Bean Validation groups checked on the result, the default group when empty
     * @throws UnprocessableContentException when the patch is not an object, cannot be mapped or violates constraints
     */
    public <T> T apply(T current, JsonNode patch, Class<T> type, Class<?>... validationGroups) {
        if (!patch.isObject()) {
            throw new UnprocessableContentException("$", "A merge patch document must be a JSON object");
        }
        T patched = map(merge(jsonMapper.valueToTree(current), patch), type);
        requestValidator.validate(patched, validationGroups);
        return patched;
    }

    private JsonNode merge(JsonNode target, JsonNode patch) {
        if (!patch.isObject()) {
            return patch;
        }
        ObjectNode result = target.isObject() ? (ObjectNode) target.deepCopy() : jsonMapper.createObjectNode();
        for (Map.Entry<String, JsonNode> member : patch.properties()) {
            if (member.getValue().isNull()) {
                result.remove(member.getKey());
            } else {
                result.set(member.getKey(), merge(result.path(member.getKey()), member.getValue()));
            }
        }
        return result;
    }

    private <T> T map(JsonNode merged, Class<T> type) {
        try {
            return jsonMapper.treeToValue(merged, type);
        } catch (JacksonException exception) {
            throw new UnprocessableContentException(fieldOf(exception), "Invalid value");
        }
    }

    private static String fieldOf(JacksonException exception) {
        String path = exception.getPathReference();
        return path == null ? "$" : path;
    }
}
