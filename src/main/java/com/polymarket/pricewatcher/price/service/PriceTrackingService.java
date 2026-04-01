package com.polymarket.pricewatcher.price.service;

import com.polymarket.pricewatcher.price.entity.PriceChangeEvent;
import com.polymarket.pricewatcher.price.model.PriceSource;
import com.polymarket.pricewatcher.price.model.PriceTrackingResult;
import com.polymarket.pricewatcher.price.model.TrackedMarket;
import com.polymarket.pricewatcher.price.model.TrackedPrice;
import com.polymarket.pricewatcher.price.repository.PriceChangeEventRepository;
import com.polymarket.pricewatcher.price.store.PriceStateStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class PriceTrackingService {

    private static final BigDecimal MIN_PRICE = BigDecimal.ZERO;
    private static final BigDecimal MAX_PRICE = BigDecimal.ONE;
    private static final int PRICE_SCALE = 8;

    private final PriceStateStore priceStateStore;
    private final PriceChangeEventRepository priceChangeEventRepository;
    private final PriceObservationPolicy priceObservationPolicy;
    private final PriceTrackingLockManager priceTrackingLockManager;

    public PriceTrackingService(
            PriceStateStore priceStateStore,
            PriceChangeEventRepository priceChangeEventRepository,
            PriceObservationPolicy priceObservationPolicy,
            PriceTrackingLockManager priceTrackingLockManager
    ) {
        this.priceStateStore = priceStateStore;
        this.priceChangeEventRepository = priceChangeEventRepository;
        this.priceObservationPolicy = priceObservationPolicy;
        this.priceTrackingLockManager = priceTrackingLockManager;
    }

    @Transactional
    public PriceTrackingResult registerPrice(
            TrackedMarket trackedMarket,
            BigDecimal observedPrice,
            PriceSource priceSource,
            Instant observedAt
    ) {
        Objects.requireNonNull(trackedMarket, "trackedMarket must not be null");
        Objects.requireNonNull(observedPrice, "observedPrice must not be null");
        Objects.requireNonNull(priceSource, "priceSource must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");

        ReentrantLock assetLock = priceTrackingLockManager.acquire(trackedMarket.assetId());
        boolean releaseAfterTransactionCompletion = false;

        try {
            BigDecimal normalizedPrice = normalizePrice(observedPrice);
            TrackedPrice newTrackedPrice = new TrackedPrice(
                    trackedMarket.assetId(),
                    normalizedPrice,
                    observedAt,
                    priceSource
            );

            Optional<TrackedPrice> previousPrice = priceStateStore.findByAssetId(trackedMarket.assetId());
            if (previousPrice.isEmpty()) {
                priceStateStore.save(newTrackedPrice);
                return PriceTrackingResult.initialized(trackedMarket.assetId());
            }

            TrackedPrice previousTrackedPrice = previousPrice.get();
            if (priceObservationPolicy.shouldIgnore(previousTrackedPrice, priceSource, observedAt)) {
                return PriceTrackingResult.stale(trackedMarket.assetId());
            }

            if (previousTrackedPrice.price().compareTo(normalizedPrice) == 0) {
                priceStateStore.save(newTrackedPrice);
                return PriceTrackingResult.unchanged(trackedMarket.assetId());
            }

            PriceChangeEvent savedEvent = priceChangeEventRepository.save(new PriceChangeEvent(
                    trackedMarket.assetId(),
                    trackedMarket.marketId(),
                    trackedMarket.conditionId(),
                    trackedMarket.marketSlug(),
                    trackedMarket.marketQuestion(),
                    trackedMarket.outcome(),
                    previousTrackedPrice.price(),
                    normalizedPrice,
                    priceSource,
                    observedAt
            ));

            saveStateAfterCommit(newTrackedPrice, assetLock);
            releaseAfterTransactionCompletion = true;
            return PriceTrackingResult.changed(trackedMarket.assetId(), savedEvent.getId());
        } finally {
            if (!releaseAfterTransactionCompletion) {
                assetLock.unlock();
            }
        }
    }

    private void saveStateAfterCommit(TrackedPrice trackedPrice, ReentrantLock assetLock) {
        // Unit tests call the service without an active transaction. In that case
        // we update the in-memory state immediately instead of waiting for afterCommit.
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            try {
                priceStateStore.save(trackedPrice);
            } finally {
                assetLock.unlock();
            }
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                priceStateStore.save(trackedPrice);
            }

            @Override
            public void afterCompletion(int status) {
                assetLock.unlock();
            }
        });
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        BigDecimal normalized = price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        if (normalized.compareTo(MIN_PRICE) < 0 || normalized.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException("price must be between 0 and 1 inclusive");
        }
        return normalized;
    }
}
