package com.polymarket.pricewatcher.market.service;

import com.polymarket.pricewatcher.config.MarketSelectionMode;
import com.polymarket.pricewatcher.config.PolymarketProperties;
import com.polymarket.pricewatcher.price.model.TrackedMarket;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ConfiguredTrackedMarketService {

    private final List<TrackedMarket> trackedMarkets;
    private final List<String> trackedAssetIds;

    public ConfiguredTrackedMarketService(PolymarketProperties polymarketProperties) {
        this.trackedMarkets = List.copyOf(validateAndMap(polymarketProperties));
        this.trackedAssetIds = this.trackedMarkets.stream()
                .map(TrackedMarket::assetId)
                .toList();
    }

    public List<TrackedMarket> getTrackedMarkets() {
        return trackedMarkets;
    }

    public List<String> getTrackedAssetIds() {
        return trackedAssetIds;
    }

    private List<TrackedMarket> validateAndMap(PolymarketProperties properties) {
        if (properties.getMarkets().getMode() != MarketSelectionMode.STATIC) {
            throw new IllegalStateException("Only STATIC market selection mode is supported at the moment");
        }

        List<PolymarketProperties.ConfiguredMarket> configuredMarkets = properties.getMarkets().getTracked();
        if (configuredMarkets.size() < properties.getMarkets().getMinTrackedCount()) {
            throw new IllegalStateException(
                    "At least " + properties.getMarkets().getMinTrackedCount() + " tracked markets must be configured"
            );
        }

        ensureNoDuplicates(configuredMarkets.stream().map(PolymarketProperties.ConfiguredMarket::getAssetId).toList(), "assetId");
        ensureNoDuplicates(configuredMarkets.stream().map(PolymarketProperties.ConfiguredMarket::getMarketId).toList(), "marketId");
        ensureNoDuplicates(configuredMarkets.stream().map(PolymarketProperties.ConfiguredMarket::getMarketSlug).toList(), "marketSlug");

        return configuredMarkets.stream()
                .map(configuredMarket -> new TrackedMarket(
                        configuredMarket.getAssetId(),
                        configuredMarket.getMarketId(),
                        configuredMarket.getConditionId(),
                        configuredMarket.getMarketSlug(),
                        configuredMarket.getMarketQuestion(),
                        configuredMarket.getOutcome()
                ))
                .toList();
    }

    private void ensureNoDuplicates(List<String> values, String fieldName) {
        Set<String> uniqueValues = new LinkedHashSet<>();
        for (String value : values) {
            if (!uniqueValues.add(value)) {
                throw new IllegalStateException("Duplicate tracked market " + fieldName + ": " + value);
            }
        }
    }
}
