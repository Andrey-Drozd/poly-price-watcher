package com.polymarket.pricewatcher.price.store;

import com.polymarket.pricewatcher.price.model.TrackedPrice;

import java.util.Optional;

public interface PriceStateStore {

    Optional<TrackedPrice> findByAssetId(String assetId);

    void save(TrackedPrice trackedPrice);
}
