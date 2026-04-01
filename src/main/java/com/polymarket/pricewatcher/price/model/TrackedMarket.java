package com.polymarket.pricewatcher.price.model;

import java.util.Objects;

public record TrackedMarket(
        String assetId,
        String marketId,
        String conditionId,
        String marketSlug,
        String marketQuestion,
        String outcome
) {

    public TrackedMarket {
        Objects.requireNonNull(assetId, "assetId must not be null");
        if (assetId.isBlank()) {
            throw new IllegalArgumentException("assetId must not be blank");
        }
    }
}
