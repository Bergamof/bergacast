package fr.bergadev.bergacast.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.info.BuildProperties;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Entry point of the REST API, advertising the server version and the top-level resources (HATEOAS). */
@RestController
@Tag(name = "Root")
public class RootController {

    private final ApiRootResponse root;

    RootController(BuildProperties buildProperties) {
        this.root = new ApiRootResponse(buildProperties.getVersion());
    }

    @GetMapping(
            path = ApiPaths.BASE,
            produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @Operation(
            operationId = "getApiRoot",
            summary = "Get the server version and the top-level resources",
            description = "Public. Returns the version of the server and links to the top-level resources "
                    + "(podcasts, adminUsers, accessTokens), from which clients can navigate the API.")
    public EntityModel<ApiRootResponse> root() {
        return EntityModel.of(root)
                .add(ApiLinks.link(IanaLinkRelations.SELF, ApiPaths.BASE))
                .add(ApiLinks.link(LinkRelation.of("podcasts"), ApiPaths.PODCASTS))
                .add(ApiLinks.link(LinkRelation.of("adminUsers"), ApiPaths.ADMIN_USERS))
                .add(ApiLinks.link(LinkRelation.of("accessTokens"), ApiPaths.ACCESS_TOKENS));
    }
}
