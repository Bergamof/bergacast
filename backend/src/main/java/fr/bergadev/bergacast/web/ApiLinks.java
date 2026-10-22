package fr.bergadev.bergacast.web;

import java.net.URI;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Builds absolute API links relative to the current request (honouring forwarded headers). */
public final class ApiLinks {

    private ApiLinks() {}

    public static Link link(LinkRelation relation, String uriTemplate, Object... uriVariables) {
        return Link.of(uri(uriTemplate, uriVariables).toString(), relation);
    }

    public static URI uri(String uriTemplate, Object... uriVariables) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(uriTemplate)
                .buildAndExpand(uriVariables)
                .toUri();
    }
}
