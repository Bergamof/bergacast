package fr.bergadev.bergacast.web;

import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;

/** Builds HAL pages that always contain their {@code _embedded} collection, even when it is empty. */
public final class PagedModels {

    private PagedModels() {}

    public static <T, R extends RepresentationModel<?>> PagedModel<R> of(
            Page<T> page,
            PagedResourcesAssembler<T> pagedAssembler,
            RepresentationModelAssembler<T, R> assembler,
            Class<?> contentType) {
        if (page.isEmpty()) {
            @SuppressWarnings("unchecked")
            PagedModel<R> empty = (PagedModel<R>) pagedAssembler.toEmptyModel(page, contentType);
            return empty;
        }
        return pagedAssembler.toModel(page, assembler);
    }
}
