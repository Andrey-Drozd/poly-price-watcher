package com.polymarket.pricewatcher.market.service;

import com.polymarket.pricewatcher.price.model.TrackedMarket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TrackedMarketStartupLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TrackedMarketStartupLogger.class);

    private final ConfiguredTrackedMarketService configuredTrackedMarketService;

    public TrackedMarketStartupLogger(ConfiguredTrackedMarketService configuredTrackedMarketService) {
        this.configuredTrackedMarketService = configuredTrackedMarketService;
    }

    @Override
    public void run(ApplicationArguments args) {
        var trackedMarkets = configuredTrackedMarketService.getTrackedMarkets();
        log.info("Loaded {} tracked Polymarket markets for static configuration", trackedMarkets.size());

        for (TrackedMarket trackedMarket : trackedMarkets) {
            log.info(
                    "Tracked market loaded: assetId={}, marketId={}, outcome={}, slug={}",
                    trackedMarket.assetId(),
                    trackedMarket.marketId(),
                    trackedMarket.outcome(),
                    trackedMarket.marketSlug()
            );
        }
    }
}
