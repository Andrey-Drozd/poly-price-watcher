package com.polymarket.pricewatcher.market.websocket.model;

import com.polymarket.pricewatcher.price.model.PriceSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record ObservedMarketPrice(
        String assetId,
        BigDecimal price,
        PriceSource priceSource,
        Instant observedAt
) {

    public ObservedMarketPrice {
        Objects.requireNonNull(assetId, "assetId must not be null");
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(priceSource, "priceSource must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
    }
}
