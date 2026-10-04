package dev.stevejones.trackit.stats;

import dev.stevejones.trackit.entry.EntryStatus;
import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.stats.StatsRepository.KeyAverage;
import dev.stevejones.trackit.stats.StatsRepository.GenreRow;
import dev.stevejones.trackit.stats.StatsRepository.KeyCount;
import dev.stevejones.trackit.stats.StatsRepository.RuntimeTotal;
import dev.stevejones.trackit.stats.StatsResponse.Average;
import dev.stevejones.trackit.stats.StatsResponse.Bucket;
import dev.stevejones.trackit.stats.StatsResponse.FilmTime;
import dev.stevejones.trackit.stats.StatsResponse.Genre;
import dev.stevejones.trackit.stats.YearInReview.Highlight;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsService {

    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int MONTHS = 12;
    private static final int LIBRARY_GENRES = 10;
    private static final int YEAR_GENRES = 5;
    private static final int YEAR_BEST = 6;

    private final StatsRepository repository;

    public StatsService(StatsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public StatsResponse forUser(Long userId, ZoneId zone) {
        List<Bucket> byMediaType = fill(
                repository.countByMediaType(userId),
                MediaType.values(),
                Enum::name,
                StatsService::mediaTypeLabel);

        List<Bucket> byStatus = fill(
                repository.countByStatus(userId),
                EntryStatus.values(),
                Enum::name,
                StatsService::statusLabel);

        List<Bucket> ratingHistogram = ratingHistogram(repository.countByRating(userId));
        List<Bucket> finishedByMonth =
                finishedByMonth(repository.countFinishedByMonth(userId, zone.getId()), zone);

        long totalItems = byStatus.stream().mapToLong(Bucket::count).sum();
        long ratedItems = ratingHistogram.stream().mapToLong(Bucket::count).sum();
        Double averageRating = weightedAverage(ratingHistogram, ratedItems);

        List<Average> averages = repository.averageRatingByMediaType(userId).stream()
                .map(row -> new Average(
                        row.getKey(),
                        mediaTypeLabel(MediaType.valueOf(row.getKey())),
                        round(row.getAverage())))
                .toList();

        return new StatsResponse(
                totalItems,
                ratedItems,
                averageRating,
                byMediaType,
                byStatus,
                ratingHistogram,
                finishedByMonth,
                averages,
                filmTime(repository.finishedFilmRuntime(userId)),
                genres(repository.genres(userId, LIBRARY_GENRES)),
                years(userId, zone));
    }

    /** Everything finished in one calendar year, where "year" is the user's, not UTC's. */
    @Transactional(readOnly = true)
    public YearInReview yearInReview(Long userId, int year, ZoneId zone) {
        Instant from = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant();
        Instant to = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant();

        List<Bucket> byMediaType = fill(
                repository.countFinishedByMediaTypeBetween(userId, from, to),
                MediaType.values(),
                Enum::name,
                StatsService::mediaTypeLabel);

        List<Bucket> byMonth = monthsOf(
                repository.countFinishedByMonthBetween(userId, zone.getId(), from, to),
                LocalDate.of(year, 1, 1));

        List<Highlight> best = repository.bestFinishedBetween(userId, from, to, YEAR_BEST).stream()
                .map(row -> new Highlight(
                        row.getEntryId(), row.getTitle(), row.getMediaType(), row.getPosterUrl(), row.getRating()))
                .toList();

        return new YearInReview(
                year,
                years(userId, zone),
                byMediaType.stream().mapToLong(Bucket::count).sum(),
                round(repository.averageRatingFinishedBetween(userId, from, to)),
                byMediaType,
                byMonth,
                filmTime(repository.finishedFilmRuntimeBetween(userId, from, to)),
                genres(repository.genresFinishedBetween(userId, from, to, YEAR_GENRES)),
                best);
    }

    /** Years with a finish, newest first, always including this one so there's somewhere to start. */
    private List<Integer> years(Long userId, ZoneId zone) {
        List<Integer> years = new ArrayList<>(repository.finishedYears(userId, zone.getId()));
        int thisYear = Year.now(zone).getValue();
        if (!years.contains(thisYear)) {
            years.add(thisYear);
            years.sort(Comparator.reverseOrder());
        }
        return List.copyOf(years);
    }

    private static FilmTime filmTime(RuntimeTotal total) {
        return new FilmTime(total.getMinutes(), total.getMissing());
    }

    private static List<Genre> genres(List<GenreRow> rows) {
        return rows.stream()
                .map(row -> new Genre(row.getKey(), row.getTotal(), round(row.getAverage())))
                .toList();
    }

    /** Projects grouped counts onto the full set of enum values, zeros included. */
    private static <E extends Enum<E>> List<Bucket> fill(
            List<KeyCount> rows,
            E[] all,
            Function<E, String> keyOf,
            Function<E, String> labelOf) {

        Map<String, Long> counts = rows.stream()
                .collect(Collectors.toMap(KeyCount::getKey, KeyCount::getTotal));

        List<Bucket> buckets = new ArrayList<>(all.length);
        for (E value : all) {
            String key = keyOf.apply(value);
            buckets.add(new Bucket(key, labelOf.apply(value), counts.getOrDefault(key, 0L)));
        }
        return buckets;
    }

    private static List<Bucket> ratingHistogram(List<KeyCount> rows) {
        Map<String, Long> counts = rows.stream()
                .collect(Collectors.toMap(KeyCount::getKey, KeyCount::getTotal));

        List<Bucket> buckets = new ArrayList<>(10);
        for (int rating = 1; rating <= 10; rating++) {
            String key = String.valueOf(rating);
            buckets.add(new Bucket(key, key, counts.getOrDefault(key, 0L)));
        }
        return buckets;
    }

    private static List<Bucket> finishedByMonth(List<KeyCount> rows, ZoneId zone) {
        // "This month" in the user's zone, matching the query's bucketing.
        return monthsOf(rows, LocalDate.now(zone).withDayOfMonth(1).minusMonths(MONTHS - 1L));
    }

    /** Twelve monthly buckets starting at {@code start}'s month, zeros included. */
    private static List<Bucket> monthsOf(List<KeyCount> rows, LocalDate start) {
        Map<String, Long> counts = rows.stream()
                .collect(Collectors.toMap(KeyCount::getKey, KeyCount::getTotal));

        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (int offset = 0; offset < MONTHS; offset++) {
            LocalDate month = start.plusMonths(offset);
            String key = month.format(MONTH_KEY);
            String label = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.UK);
            buckets.put(key, new Bucket(key, label, counts.getOrDefault(key, 0L)));
        }
        return List.copyOf(buckets.values());
    }

    private static Double weightedAverage(List<Bucket> histogram, long ratedItems) {
        if (ratedItems == 0) {
            return null;
        }
        long weighted = histogram.stream()
                .mapToLong(bucket -> Long.parseLong(bucket.key()) * bucket.count())
                .sum();
        return round((double) weighted / ratedItems);
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10.0) / 10.0;
    }

    private static String mediaTypeLabel(MediaType mediaType) {
        return switch (mediaType) {
            case MOVIE -> "Films";
            case TV -> "TV";
            case GAME -> "Games";
        };
    }

    private static String statusLabel(EntryStatus status) {
        return switch (status) {
            case WANT -> "Want to start";
            case IN_PROGRESS -> "In progress";
            case ON_HOLD -> "On hold";
            case COMPLETED -> "Finished";
            case DROPPED -> "Gave up";
        };
    }
}
