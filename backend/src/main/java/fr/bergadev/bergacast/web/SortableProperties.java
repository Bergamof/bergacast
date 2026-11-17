package fr.bergadev.bergacast.web;

import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Restricts the {@code sort} of a collection to the properties its documentation announces: sorting by an arbitrary
 * entity property would reveal the order of internal fields (hashes, idempotency keys) and make the contract false.
 */
public final class SortableProperties {

    private SortableProperties() {}

    /**
     * Returns {@code pageable} unchanged when it sorts only by {@code allowed} properties.
     *
     * @throws BadRequestException on any other property
     */
    public static Pageable restrict(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new BadRequestException("This collection can only be sorted by: " + new TreeSet<>(allowed));
            }
        }
        return pageable;
    }
}
