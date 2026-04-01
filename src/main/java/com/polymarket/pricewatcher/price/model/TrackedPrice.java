package com.polymarket.pricewatcher.price.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record TrackedPrice(
        String assetId,
        BigDecimal price,
        Instant observedAt,
        PriceSource priceSource
) {

    public TrackedPrice {
        Objects.requireNonNull(assetId, "assetId must not be null");
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        Objects.requireNonNull(priceSource, "priceSource must not be null");
    }
}
