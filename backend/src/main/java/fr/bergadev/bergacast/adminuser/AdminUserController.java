package fr.bergadev.bergacast.adminuser;

import fr.bergadev.bergacast.web.ApiLinks;
import fr.bergadev.bergacast.web.ApiMediaTypes;
import fr.bergadev.bergacast.web.ApiPaths;
import fr.bergadev.bergacast.web.PagedModels;
import fr.bergadev.bergacast.web.SecuritySchemes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administration of administrators. */
@RestController
@RequestMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@Tag(name = "Admin users")
@SecurityRequirement(name = SecuritySchemes.ADMIN_JWT)
class AdminUserController {

    private static final RepresentationModelAssembler<AdminUser, EntityModel<AdminUserResponse>> ASSEMBLER =
            adminUser -> EntityModel.of(
                    new AdminUserResponse(adminUser.getId(), adminUser.getUsername(), adminUser.getCreatedAt()),
                    ApiLinks.link(IanaLinkRelations.SELF, ApiPaths.ADMIN_USER, adminUser.getId()));

    private final AdminUserService adminUserService;
    private final PagedResourcesAssembler<AdminUser> pagedAssembler;

    AdminUserController(AdminUserService adminUserService, PagedResourcesAssembler<AdminUser> pagedAssembler) {
        this.adminUserService = adminUserService;
        this.pagedAssembler = pagedAssembler;
    }

    @GetMapping(ApiPaths.ADMIN_USERS)
    @Operation(
            operationId = "listAdminUsers",
            summary = "List administrators",
            description =
                    "Lists the administrators. Sorted by username by default; sortable by username or " + "createdAt.")
    PagedModel<EntityModel<AdminUserResponse>> list(
            @ParameterObject @PageableDefault(sort = "username") Pageable pageable) {
        return PagedModels.of(adminUserService.list(pageable), pagedAssembler, ASSEMBLER, AdminUserResponse.class);
    }

    @GetMapping(ApiPaths.ADMIN_USER)
    @Operation(
            operationId = "getAdminUser",
            summary = "Get an administrator",
            description = "Returns the administrator identified by its id. Passwords are never returned.")
    EntityModel<AdminUserResponse> get(@PathVariable UUID adminUserId) {
        return ASSEMBLER.toModel(adminUserService.get(adminUserId));
    }

    @PostMapping(path = ApiPaths.ADMIN_USERS, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createAdminUser",
            summary = "Create an administrator",
            description = "Creates an administrator, who can then log in with POST /api/v1/access-tokens. The "
                    + "password " + PasswordPolicy.DESCRIPTION + " (UTF-8).")
    @ApiResponse(responseCode = "201", description = "Administrator created")
    @ApiResponse(responseCode = "409", description = "Username already taken", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid content, listed in errors", content = @Content)
    ResponseEntity<EntityModel<AdminUserResponse>> create(@Valid @RequestBody CreateAdminUserRequest request) {
        AdminUser created = adminUserService.create(request.username(), request.password());
        return ResponseEntity.created(ApiLinks.uri(ApiPaths.ADMIN_USER, created.getId()))
                .body(ASSEMBLER.toModel(created));
    }

    @PatchMapping(
            path = ApiPaths.ADMIN_USER,
            consumes = {ApiMediaTypes.MERGE_PATCH_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @Operation(
            operationId = "changeAdminUserPassword",
            summary = "Change the password of an administrator",
            description = "JSON Merge Patch (application/merge-patch+json, or plain application/json) whose only "
                    + "member is the new password, the only modifiable field. Access tokens already issued stay "
                    + "valid until they expire.")
    @ApiResponse(responseCode = "200", description = "Password changed")
    @ApiResponse(responseCode = "422", description = "Invalid password, listed in errors", content = @Content)
    EntityModel<AdminUserResponse> changePassword(
            @PathVariable UUID adminUserId, @Valid @RequestBody ChangePasswordRequest request) {
        return ASSEMBLER.toModel(adminUserService.changePassword(adminUserId, request.password()));
    }

    @DeleteMapping(ApiPaths.ADMIN_USER)
    @Operation(
            operationId = "deleteAdminUser",
            summary = "Delete an administrator",
            description = "Deletes an administrator. Administrators cannot delete themselves, and the last "
                    + "administrator cannot be deleted. Access tokens already issued to them stay valid until they "
                    + "expire.")
    @ApiResponse(responseCode = "204", description = "Administrator deleted")
    @ApiResponse(responseCode = "409", description = "Deleting oneself or the last administrator", content = @Content)
    ResponseEntity<Void> delete(@PathVariable UUID adminUserId, Authentication authentication) {
        adminUserService.delete(adminUserId, UUID.fromString(authentication.getName()));
        return ResponseEntity.noContent().build();
    }
}
