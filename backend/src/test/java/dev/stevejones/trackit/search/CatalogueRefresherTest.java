package dev.stevejones.trackit.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.stevejones.trackit.IntegrationTest;
import dev.stevejones.trackit.auth.AppUser;
import dev.stevejones.trackit.auth.AppUserRepository;
import dev.stevejones.trackit.entry.Entry;
import dev.stevejones.trackit.entry.EntryRepository;
import dev.stevejones.trackit.entry.EntryStatus;
import dev.stevejones.trackit.media.MediaItem;
import dev.stevejones.trackit.media.MediaItemRepository;
import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.media.MetadataSource;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The provider is stubbed: what TMDB returns is TmdbProviderTest's job. */
class CatalogueRefresherTest extends IntegrationTest {

    @Autowired CatalogueRefresher refresher;
    @Autowired MediaItemRepository mediaItems;
    @Autowired EntryRepository entries;
    @Autowired AppUserRepository users;

    @MockitoBean MetadataProviders providers;

    private MetadataProvider provider;
    private AppUser steve;

    @BeforeEach
    void setUp() {
        entries.deleteAll();
        mediaItems.deleteAll();
        users.deleteAll();
        steve = users.save(new AppUser("steve", "irrelevant"));
        provider = Mockito.mock(MetadataProvider.class);
        when(providers.forType(any())).thenReturn(provider);
    }

    private MediaItem tracked(MediaType type, String externalId, Integer year, Instant fetchedAt) {
        MediaItem item = new MediaItem();
        item.setSource(MetadataSource.TMDB);
        item.setMediaType(type);
        item.setExternalId(externalId);
        item.setTitle("Title " + externalId);
        item.setReleaseYear(year);
        item.setMetadataFetchedAt(fetchedAt);
        item = mediaItems.save(item);
        entries.save(new Entry(steve, item, EntryStatus.WANT));
        return item;
    }

    private static MediaItem fetched(MediaType type, String externalId, String title, Integer seasons) {
        MediaItem item = new MediaItem();
        item.setSource(MetadataSource.TMDB);
        item.setMediaType(type);
        item.setExternalId(externalId);
        item.setTitle(title);
        item.setSeasonCount(seasons);
        item.setMetadataFetchedAt(Instant.now());
        return item;
    }

    private static Instant daysAgo(int days) {
        return Instant.now().minus(Duration.ofDays(days));
    }

    @Test
    void storesWhatTheProviderSaysNow() {
        MediaItem show = tracked(MediaType.TV, "95396", 2022, daysAgo(30));
        when(provider.fetchDetails(MediaType.TV, "95396")).thenReturn(fetched(MediaType.TV, "95396", "Severance", 3));

        assertThat(refresher.refresh(show)).isTrue();

        MediaItem stored = mediaItems.findById(show.getId()).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("Severance");
        assertThat(stored.getSeasonCount()).isEqualTo(3);
        assertThat(stored.getMetadataFetchedAt()).isAfter(daysAgo(1));
    }

    @Test
    void skipsATitleFetchedAMomentAgo() {
        MediaItem show = tracked(MediaType.TV, "95396", 2022, Instant.now());

        assertThat(refresher.refresh(show)).isFalse();
        verify(provider, never()).fetchDetails(any(), anyString());
    }

    @Test
    void nightlyRefreshesShowsAndRecentTitlesButNotSettledOldFilms() {
        int thisYear = Year.now().getValue();
        tracked(MediaType.TV, "1", 2008, daysAgo(30));
        tracked(MediaType.MOVIE, "2", thisYear, daysAgo(30));
        tracked(MediaType.MOVIE, "3", null, daysAgo(30));
        tracked(MediaType.MOVIE, "4", 1979, daysAgo(30));
        tracked(MediaType.TV, "5", 2008, daysAgo(1));
        when(provider.fetchDetails(any(), anyString())).thenAnswer(call ->
                fetched(call.getArgument(0), call.getArgument(1), "Fresh", null));

        refresher.refreshStale();

        verify(provider).fetchDetails(MediaType.TV, "1");
        verify(provider).fetchDetails(MediaType.MOVIE, "2");
        verify(provider).fetchDetails(MediaType.MOVIE, "3");
        // A 1979 film's details have settled, and show 5 was fetched yesterday.
        verify(provider, never()).fetchDetails(MediaType.MOVIE, "4");
        verify(provider, never()).fetchDetails(MediaType.TV, "5");
    }

    @Test
    void leavesTitlesNobodyTracksAlone() {
        MediaItem orphan = new MediaItem();
        orphan.setSource(MetadataSource.TMDB);
        orphan.setMediaType(MediaType.TV);
        orphan.setExternalId("1");
        orphan.setTitle("Nobody watches this");
        mediaItems.save(orphan);

        refresher.refreshStale();

        verify(provider, never()).fetchDetails(any(), anyString());
    }

    @Test
    void marksATitleTheProviderNoLongerHasAsCheckedSoItDoesNotBlockTheQueue() {
        MediaItem gone = tracked(MediaType.TV, "1", 2008, null);
        when(provider.fetchDetails(MediaType.TV, "1"))
                .thenThrow(new UnknownTitleException("TMDB doesn't have that title any more."));

        refresher.refreshStale();

        MediaItem stored = mediaItems.findById(gone.getId()).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("Title 1");
        assertThat(stored.getMetadataFetchedAt()).isAfter(daysAgo(1));
    }

    @Test
    void givesUpOnOneProviderWithoutAbandoningTheOther() {
        for (int i = 1; i <= 5; i++) {
            tracked(MediaType.TV, "tv" + i, 2008, daysAgo(30 + i));
        }
        MediaItem game = new MediaItem();
        game.setSource(MetadataSource.IGDB);
        game.setMediaType(MediaType.GAME);
        game.setExternalId("1942");
        game.setTitle("The Witcher 3");
        game.setMetadataFetchedAt(daysAgo(10));
        entries.save(new Entry(steve, mediaItems.save(game), EntryStatus.WANT));

        when(provider.fetchDetails(eq(MediaType.TV), anyString()))
                .thenThrow(new ProviderException("Film and TV search is not configured."));
        when(provider.fetchDetails(MediaType.GAME, "1942")).thenReturn(fetched(MediaType.GAME, "1942", "Fresh", null));

        refresher.refreshStale();

        verify(provider, times(3)).fetchDetails(eq(MediaType.TV), anyString());
        verify(provider).fetchDetails(MediaType.GAME, "1942");
    }
}
