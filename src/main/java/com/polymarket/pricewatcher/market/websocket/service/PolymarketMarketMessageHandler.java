package com.polymarket.pricewatcher.market.websocket.service;

import com.polymarket.pricewatcher.market.service.ConfiguredTrackedMarketService;
import com.polymarket.pricewatcher.market.websocket.model.ObservedMarketPrice;
import com.polymarket.pricewatcher.price.model.PriceTrackingResult;
import com.polymarket.pricewatcher.price.model.PriceTrackingStatus;
import com.polymarket.pricewatcher.price.model.TrackedMarket;
import com.polymarket.pricewatcher.price.service.PriceTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toUnmodifiableMap;

@Component
public class PolymarketMarketMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(PolymarketMarketMessageHandler.class);

    private final PolymarketMarketMessageParser messageParser;
    private final PriceTrackingService priceTrackingService;
    private final Map<String, TrackedMarket> trackedMarketsByAssetId;

    public PolymarketMarketMessageHandler(
            PolymarketMarketMessageParser messageParser,
            PriceTrackingService priceTrackingService,
            ConfiguredTrackedMarketService trackedMarketService
    ) {
        this.messageParser = messageParser;
        this.priceTrackingService = priceTrackingService;
        this.trackedMarketsByAssetId = trackedMarketService.getTrackedMarkets().stream()
                .collect(toUnmodifiableMap(TrackedMarket::assetId, Function.identity()));
    }

    public void handlePayload(String payload) {
        try {
            List<ObservedMarketPrice> observedPrices = messageParser.parse(payload);
            for (ObservedMarketPrice observedPrice : observedPrices) {
                handleObservedPrice(observedPrice);
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to process Polymarket WebSocket payload: {}", abbreviate(payload), exception);
        }
    }

    private void handleObservedPrice(ObservedMarketPrice observedPrice) {
        TrackedMarket trackedMarket = trackedMarketsByAssetId.get(observedPrice.assetId());
        if (trackedMarket == null) {
            log.debug("Ignoring WebSocket update for untracked assetId={}", observedPrice.assetId());
            return;
        }

        PriceTrackingResult result = priceTrackingService.registerPrice(
                trackedMarket,
                observedPrice.price(),
                observedPrice.priceSource(),
                observedPrice.observedAt()
        );

        if (result.status() == PriceTrackingStatus.CHANGED) {
            log.info(
                    "Tracked price changed: assetId={}, marketId={}, price={}, source={}, eventId={}",
                    trackedMarket.assetId(),
                    trackedMarket.marketId(),
                    observedPrice.price(),
                    observedPrice.priceSource(),
                    result.eventId()
            );
            return;
        }

        log.debug(
                "Tracked price {}: assetId={}, marketId={}, price={}, source={}",
                result.status().name().toLowerCase(),
                trackedMarket.assetId(),
                trackedMarket.marketId(),
                observedPrice.price(),
                observedPrice.priceSource()
        );
    }

    private String abbreviate(String payload) {
        if (payload == null) {
            return "<null>";
        }
        String normalized = payload.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 240) {
            return normalized;
        }
        return normalized.substring(0, 237) + "...";
    }
}
