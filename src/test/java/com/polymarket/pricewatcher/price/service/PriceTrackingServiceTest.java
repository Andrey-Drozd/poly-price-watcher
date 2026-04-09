package com.polymarket.pricewatcher.price.service;

import com.polymarket.pricewatcher.price.entity.PriceChangeEvent;
import com.polymarket.pricewatcher.price.model.PriceSource;
import com.polymarket.pricewatcher.price.model.PriceTrackingResult;
import com.polymarket.pricewatcher.price.model.PriceTrackingStatus;
import com.polymarket.pricewatcher.price.model.TrackedMarket;
import com.polymarket.pricewatcher.price.repository.PriceChangeEventRepository;
import com.polymarket.pricewatcher.price.store.InMemoryPriceStateStore;
import com.polymarket.pricewatcher.price.store.PriceStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceTrackingServiceTest {

    private final PriceChangeEventRepository repository = mock(PriceChangeEventRepository.class);
    private final PriceStateStore priceStateStore = new InMemoryPriceStateStore();
    private final PriceObservationPolicy priceObservationPolicy = new PriceObservationPolicy();
    private final PriceTrackingLockManager priceTrackingLockManager = new PriceTrackingLockManager();

    private PriceTrackingService priceTrackingService;

    @BeforeEach
    void setUp() {
        priceTrackingService = new PriceTrackingService(
                priceStateStore,
                repository,
                priceObservationPolicy,
                priceTrackingLockManager
        );
    }

    @Test
    void registerPriceShouldInitializeStateWithoutPersistingEvent() {
        TrackedMarket market = trackedMarket();

        PriceTrackingResult result = priceTrackingService.registerPrice(
                market,
                new BigDecimal("0.5100"),
                PriceSource.BEST_BID_ASK,
                Instant.parse("2026-04-01T10:15:30Z")
        );

        assertThat(result.status()).isEqualTo(PriceTrackingStatus.INITIALIZED);
        assertThat(priceStateStore.findByAssetId(market.assetId())).isPresent();
        assertThat(priceStateStore.findByAssetId(market.assetId()).orElseThrow().price()).isEqualByComparingTo("0.51000000");
        assertThat(priceStateStore.findByAssetId(market.assetId()).orElseThrow().priceSource()).isEqualTo(PriceSource.BEST_BID_ASK);
        verify(repository, never()).save(any());
    }

    @Test
    void registerPriceShouldSkipPersistingWhenPriceDidNotChange() {
        TrackedMarket market = trackedMarket();
        priceTrackingService.registerPrice(
                market,
                new BigDecimal("0.5100"),
                PriceSource.BEST_BID_ASK,
                Instant.parse("2026-04-01T10:15:30Z")
        );

        PriceTrackingResult result = priceTrackingService.registerPrice(
                market,
                new BigDecimal("0.51"),
                PriceSource.LAST_TRADE,
                Instant.parse("2026-04-01T10:16:30Z")
        );

        assertThat(result.status()).isEqualTo(PriceTrackingStatus.UNCHANGED);
        verify(repository, never()).save(any());
    }

    @Test
    void registerPriceShouldIgnoreOlderObservation() {
        TrackedMarket market = trackedMarket();
        priceTrackingService.registerPrice(
                market,
                new BigDecimal("0.5100"),
                PriceSource.BEST_BID_ASK,
                Instant.parse("2026-04-01T10:16:30Z")
        );

        PriceTrackingResult result = priceTrackingService.registerPrice(
                market,
                new BigDecimal("0.4900"),
                PriceSource.LAST_TRADE,
                Instant.parse("2026-04-01T10:15:30Z")
        );

        assertThat(result.status()).isEqualTo(PriceTrackingStatus.STALE);
        assertThat(priceStateStore.findByAssetId(market.assetId()).orElseThrow().price()).isEqualByComparingTo("0.51000000");
        verify(repository, never()).save(any());
    }

    @Test
    void registerPriceShouldIgnoreLowerPrioritySourceForSameTimestamp() {
        TrackedMarket market = trackedMarket();
        Instant observedAt = Instant.parse("2026-04-01T10:15:30Z");
        priceTrackingService.registerPrice(
                market,
                new BigDecimal("0.5100"),
                PriceSource.BEST_BID_ASK,
                observedAt
        );

        PriceTrackingResult result = priceTrackingService.registerPrice(
                market,
                new BigDecimal("0.4900"),
                PriceSource.LAST_TRADE,
                observedAt
        );

        assertThat(result.status()).isEqualTo(PriceTrackingStatus.STALE);
        assertThat(priceStateStore.findByAssetId(market.assetId()).orElseThrow().price()).isEqualByComparingTo("0.51000000");
        assertThat(priceStateStore.findByAssetId(market.assetId()).orElseThrow().priceSource()).isEqualTo(PriceSource.BEST_BID_ASK);
        verify(repository, never()).save(any());
    }

    @Test
    void registerPriceShouldUpdateStoredSourceWhenSameTimestampHasHigherPrioritySource() {
        TrackedMarket market = trackedMarket();
        Instant observedAt = Instant.parse("2026-04-01T10:15:30Z");
        priceTrackingService.registerPrice(
                market,
                new BigDecimal("0.5100"),
                PriceSource.LAST_TRADE,
                observedAt
        );

        PriceTrackingResult result = priceTrackingService.registerPrice(
                market,
                new BigDecimal("0.5100"),
                PriceSource.BEST_BID_ASK,
                observedAt
        );

        assertThat(result.status()).isEqualTo(PriceTrackingStatus.UNCHANGED);
        assertThat(priceStateStore.findByAssetId(market.assetId()).orElseThrow().priceSource()).isEqualTo(PriceSource.BEST_BID_ASK);
        verify(repository, never()).save(any());
    }

    @Test
    void registerPriceShouldPersistEventWhenPriceChanged() {
        TrackedMarket market = trackedMarket();
        priceTrackingService.registerPrice(
                market,
                new BigDecimal("0.5100"),
                PriceSource.BEST_BID_ASK,
                Instant.parse("2026-04-01T10:15:30Z")
        );

        PriceChangeEvent persistedEvent = mock(PriceChangeEvent.class);
        when(persistedEvent.getId()).thenReturn(42L);
        when(repository.save(any(PriceChangeEvent.class))).thenReturn(persistedEvent);

        PriceTrackingResult result = priceTrackingService.registerPrice(
                market,
                new BigDecimal("0.525"),
                PriceSource.PRICE_CHANGE,
                Instant.parse("2026-04-01T10:17:30Z")
        );

        ArgumentCaptor<PriceChangeEvent> eventCaptor = ArgumentCaptor.forClass(PriceChangeEvent.class);
        verify(repository).save(eventCaptor.capture());
        PriceChangeEvent savedEvent = eventCaptor.getValue();

        assertThat(result.status()).isEqualTo(PriceTrackingStatus.CHANGED);
        assertThat(result.eventId()).isEqualTo(42L);
        assertThat(savedEvent.getAssetId()).isEqualTo(market.assetId());
        assertThat(savedEvent.getPreviousPrice()).isEqualByComparingTo("0.51000000");
        assertThat(savedEvent.getCurrentPrice()).isEqualByComparingTo("0.52500000");
        assertThat(savedEvent.getPriceSource()).isEqualTo(PriceSource.PRICE_CHANGE);
        assertThat(priceStateStore.findByAssetId(market.assetId()).orElseThrow().price()).isEqualByComparingTo("0.52500000");
        assertThat(priceStateStore.findByAssetId(market.assetId()).orElseThrow().priceSource()).isEqualTo(PriceSource.PRICE_CHANGE);
    }

    @Test
    void registerPriceShouldSerializeConcurrentUpdatesForSameAsset() throws Exception {
        TrackedMarket market = trackedMarket();
        priceTrackingService.registerPrice(
                market,
                new BigDecimal("0.5100"),
                PriceSource.BEST_BID_ASK,
                Instant.parse("2026-04-01T10:15:30Z")
        );

        PriceChangeEvent persistedEvent = mock(PriceChangeEvent.class);
        when(persistedEvent.getId()).thenReturn(42L);

        // Block repository.save() so the first thread holds the lock
        CountDownLatch repositoryEntered = new CountDownLatch(1);
        CountDownLatch allowRepositoryReturn = new CountDownLatch(1);
        when(repository.save(any(PriceChangeEvent.class))).thenAnswer(invocation -> {
            repositoryEntered.countDown();
            assertThat(allowRepositoryReturn.await(5, TimeUnit.SECONDS)).isTrue();
            return persistedEvent;
        });

        ExecutorService thread1 = Executors.newSingleThreadExecutor();
        ExecutorService thread2 = Executors.newSingleThreadExecutor();
        try {
            // Thread 1: acquires lock, enters repository.save(), blocks
            Future<PriceTrackingResult> changedFuture = thread1.submit(() -> priceTrackingService.registerPrice(
                    market,
                    new BigDecimal("0.525"),
                    PriceSource.PRICE_CHANGE,
                    Instant.parse("2026-04-01T10:17:30Z")
            ));

            // Wait until Thread 1 is inside repository.save() with the lock held
            assertThat(repositoryEntered.await(2, TimeUnit.SECONDS)).isTrue();

            // Thread 2: will try to acquire the same lock — must block until Thread 1 releases
            CountDownLatch thread2Started = new CountDownLatch(1);
            Future<PriceTrackingResult> staleFuture = thread2.submit(() -> {
                thread2Started.countDown();
                return priceTrackingService.registerPrice(
                        market,
                        new BigDecimal("0.520"),
                        PriceSource.LAST_TRADE,
                        Instant.parse("2026-04-01T10:16:30Z")
                );
            });

            // Wait for Thread 2 to enter registerPrice and attempt to acquire the lock
            assertThat(thread2Started.await(2, TimeUnit.SECONDS)).isTrue();

            // Thread 2 should NOT complete yet since the lock is held by Thread 1
            assertThat(staleFuture.isDone()).isFalse();

            // Release Thread 1 — lock is freed, Thread 2 can proceed
            allowRepositoryReturn.countDown();

            // Both must complete with expected statuses
            assertThat(changedFuture.get(5, TimeUnit.SECONDS).status()).isEqualTo(PriceTrackingStatus.CHANGED);
            assertThat(staleFuture.get(5, TimeUnit.SECONDS).status()).isEqualTo(PriceTrackingStatus.STALE);
            verify(repository).save(any(PriceChangeEvent.class));
        } finally {
            thread1.shutdownNow();
            thread2.shutdownNow();
        }
    }

    @Test
    void registerPriceShouldRejectOutOfRangePrice() {
        assertThatThrownBy(() -> priceTrackingService.registerPrice(
                trackedMarket(),
                new BigDecimal("1.1000"),
                PriceSource.BEST_BID_ASK,
                Instant.parse("2026-04-01T10:15:30Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("price must be between 0 and 1 inclusive");
    }

    private TrackedMarket trackedMarket() {
        return new TrackedMarket(
                "asset-123",
                "market-321",
                "condition-999",
                "trump-wins-2028",
                "Will Trump win in 2028?",
                "Yes"
        );
    }
}
