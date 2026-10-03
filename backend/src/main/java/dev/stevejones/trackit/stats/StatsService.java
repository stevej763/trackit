package dev.stevejones.trackit.stats;

import dev.stevejones.trackit.entry.EntryStatus;
import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.stats.StatsRepository.KeyAverage;
import dev.stevejones.trackit.stats.StatsRepository.KeyCount;
import dev.stevejones.trackit.stats.StatsResponse.Average;
import dev.stevejones.trackit.stats.StatsResponse.Bucket;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
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

    private final StatsRepository repository;

    public StatsService(StatsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public StatsResponse forUser(Long userId) {
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
        List<Bucket> finishedByMonth = finishedByMonth(repository.countFinishedByMonth(userId));

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
                averages);
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

    private static List<Bucket> finishedByMonth(List<KeyCount> rows) {
        Map<String, Long> counts = rows.stream()
                .collect(Collectors.toMap(KeyCount::getKey, KeyCount::getTotal));

        Map<String, Bucket> buckets = new LinkedHashMap<>();
        LocalDate start = LocalDate.now().withDayOfMonth(1).minusMonths(MONTHS - 1L);
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
