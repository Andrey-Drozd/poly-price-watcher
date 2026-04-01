package com.polymarket.pricewatcher.price.service;

import com.polymarket.pricewatcher.price.model.PriceSource;
import com.polymarket.pricewatcher.price.model.TrackedPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PriceObservationPolicyTest {

    private final PriceObservationPolicy policy = new PriceObservationPolicy();

    @Test
    void shouldIgnoreObservationWhenItIsOlderThanCurrentState() {
        TrackedPrice previousPrice = trackedPrice("0.62", "2026-04-01T10:15:30Z", PriceSource.BEST_BID_ASK);

        boolean ignored = policy.shouldIgnore(
                previousPrice,
                PriceSource.LAST_TRADE,
                Instant.parse("2026-04-01T10:15:29Z")
        );

        assertThat(ignored).isTrue();
    }

    @Test
    void shouldIgnoreObservationWhenSameTimestampHasLowerPrioritySource() {
        Instant observedAt = Instant.parse("2026-04-01T10:15:30Z");
        TrackedPrice previousPrice = trackedPrice("0.62", observedAt.toString(), PriceSource.BEST_BID_ASK);

        boolean ignored = policy.shouldIgnore(previousPrice, PriceSource.LAST_TRADE, observedAt);

        assertThat(ignored).isTrue();
    }

    @Test
    void shouldAcceptObservationWhenSameTimestampHasHigherPrioritySource() {
        Instant observedAt = Instant.parse("2026-04-01T10:15:30Z");
        TrackedPrice previousPrice = trackedPrice("0.62", observedAt.toString(), PriceSource.LAST_TRADE);

        boolean ignored = policy.shouldIgnore(previousPrice, PriceSource.BEST_BID_ASK, observedAt);

        assertThat(ignored).isFalse();
    }

    @Test
    void shouldAcceptObservationWhenSameTimestampHasSamePrioritySource() {
        Instant observedAt = Instant.parse("2026-04-01T10:15:30Z");
        TrackedPrice previousPrice = trackedPrice("0.62", observedAt.toString(), PriceSource.BEST_BID_ASK);

        boolean ignored = policy.shouldIgnore(previousPrice, PriceSource.BEST_BID_ASK, observedAt);

        assertThat(ignored).isFalse();
    }

    private TrackedPrice trackedPrice(String price, String observedAt, PriceSource priceSource) {
        return new TrackedPrice(
                "asset-123",
                new BigDecimal(price),
                Instant.parse(observedAt),
                priceSource
        );
    }
}
