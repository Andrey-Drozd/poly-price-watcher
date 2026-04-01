package com.polymarket.pricewatcher.price.store;

import com.polymarket.pricewatcher.price.model.TrackedPrice;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemoryPriceStateStore implements PriceStateStore {

    private final ConcurrentMap<String, TrackedPrice> stateByAssetId = new ConcurrentHashMap<>();

    @Override
    public Optional<TrackedPrice> findByAssetId(String assetId) {
        return Optional.ofNullable(stateByAssetId.get(assetId));
    }

    @Override
    public void save(TrackedPrice trackedPrice) {
        stateByAssetId.put(trackedPrice.assetId(), trackedPrice);
    }
}
