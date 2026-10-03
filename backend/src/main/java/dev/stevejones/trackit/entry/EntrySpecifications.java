package dev.stevejones.trackit.entry;

import dev.stevejones.trackit.media.MediaItem;
import dev.stevejones.trackit.media.MediaType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/** Composable filters and ordering for the library view. */
public final class EntrySpecifications {

    static final String MEDIA_ITEM_PREFIX = "mediaItem.";

    private EntrySpecifications() {
    }

    public static Specification<Entry> ownedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Entry> hasType(MediaType mediaType) {
        if (mediaType == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("mediaItem").get("mediaType"), mediaType);
    }

    public static Specification<Entry> hasStatus(EntryStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Entry> ratedAtLeast(Integer minRating) {
        if (minRating == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("rating"), minRating);
    }

    public static Specification<Entry> titleContains(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String pattern = "%" + text.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("mediaItem").get("title")), pattern);
    }

    /**
     * Fetches the media item in the same query (one select, not one per row) and
     * applies the ordering.
     *
     * <p>Ordering lives here rather than in the {@code Pageable} for two reasons:
     * Spring Data's {@code Sort} cannot express null precedence against a
     * criteria query, and doing it by hand lets the sort reuse the fetch join
     * instead of adding a second one. Unrated and unfinished entries therefore
     * sort last in both directions instead of crowding the top of a descending
     * list. Callers must pass an unsorted {@code Pageable}, or Spring Data
     * replaces this {@code orderBy}.
     */
    public static Specification<Entry> withMediaItemOrderedBy(String property, Sort.Direction direction) {
        return (root, query, cb) -> {
            if (query == null || isCountQuery(query.getResultType())) {
                // A fetch join and an ORDER BY are both invalid in a count query.
                return null;
            }

            Join<Entry, MediaItem> mediaItem = asJoin(root);
            Path<?> path = resolve(root, mediaItem, property);
            query.orderBy(orders(cb, root, path, direction));
            return null;
        };
    }

    private static List<Order> orders(
            CriteriaBuilder cb, Root<Entry> root, Path<?> path, Sort.Direction direction) {

        // CASE WHEN path IS NULL THEN 1 ELSE 0 END, ascending: nulls last
        // whichever way the real sort runs. Postgres would otherwise put them
        // first on DESC.
        Expression<Integer> nullsLast = cb.<Integer>selectCase()
                .when(path.isNull(), 1)
                .otherwise(0)
                .as(Integer.class);

        return List.of(
                cb.asc(nullsLast),
                direction.isAscending() ? cb.asc(path) : cb.desc(path),
                // Stable paging when the sort key ties.
                cb.desc(root.get("id")));
    }

    private static Path<?> resolve(Root<Entry> root, Join<Entry, MediaItem> mediaItem, String property) {
        if (property.startsWith(MEDIA_ITEM_PREFIX)) {
            return mediaItem.get(property.substring(MEDIA_ITEM_PREFIX.length()));
        }
        return root.get(property);
    }

    /**
     * Hibernate's fetch is backed by a join, so casting lets the ORDER BY above
     * reuse it rather than triggering a second join on the same association.
     */
    @SuppressWarnings("unchecked")
    private static Join<Entry, MediaItem> asJoin(Root<Entry> root) {
        // Fetch and Join are unrelated types in the JPA API even though every
        // Hibernate Fetch is a Join underneath, hence the hop through Object.
        return (Join<Entry, MediaItem>) (Object) root.fetch("mediaItem", JoinType.INNER);
    }

    private static boolean isCountQuery(Class<?> resultType) {
        return resultType == Long.class || resultType == long.class;
    }
}
