package fr.bergadev.bergacast.web;

import java.util.List;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.EmbeddedWrappers;

/** Builds HAL collections that always contain their {@code _embedded} collection, even when it is empty. */
public final class CollectionModels {

    private static final EmbeddedWrappers WRAPPERS = new EmbeddedWrappers(false);

    private CollectionModels() {}

    /**
     * @param contentType type of the represented items, whose {@code @Relation} names the embedded collection
     */
    public static <R extends RepresentationModel<?>> CollectionModel<R> of(
            List<R> content, Class<?> contentType, Link... links) {
        if (content.isEmpty()) {
            @SuppressWarnings("unchecked")
            CollectionModel<R> empty = (CollectionModel<R>)
                    (CollectionModel<?>) CollectionModel.of(List.of(WRAPPERS.emptyCollectionOf(contentType)), links);
            return empty;
        }
        return CollectionModel.of(content, links);
    }
}
