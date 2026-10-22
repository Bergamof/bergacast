package fr.bergadev.bergacast.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Entry point of the REST API, advertising the top-level resources (HATEOAS). */
@RestController
@Tag(name = "Root", description = "Entry point of the API")
public class RootController {

    @GetMapping(
            path = ApiPaths.BASE,
            produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @Operation(
            operationId = "getApiRoot",
            summary = "List the top-level resources",
            description = "Public. Returns links to the API resources.")
    public RepresentationModel<?> root() {
        return new RepresentationModel<>()
                .add(ApiLinks.link(IanaLinkRelations.SELF, ApiPaths.BASE))
                .add(ApiLinks.link(LinkRelation.of("podcasts"), ApiPaths.PODCASTS))
                .add(ApiLinks.link(LinkRelation.of("adminUsers"), ApiPaths.ADMIN_USERS))
                .add(ApiLinks.link(LinkRelation.of("accessTokens"), ApiPaths.ACCESS_TOKENS));
    }
}
