package com.polymarket.pricewatcher.market.websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.pricewatcher.config.MarketSelectionMode;
import com.polymarket.pricewatcher.config.PolymarketProperties;
import com.polymarket.pricewatcher.price.model.PriceSource;
import com.polymarket.pricewatcher.price.model.PriceTrackingResult;
import com.polymarket.pricewatcher.price.model.PriceTrackingStatus;
import com.polymarket.pricewatcher.price.service.PriceTrackingService;
import com.polymarket.pricewatcher.market.service.ConfiguredTrackedMarketService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolymarketMarketMessageHandlerTest {

    private final PolymarketMarketMessageParser parser = new PolymarketMarketMessageParser(new ObjectMapper());
    private final PriceTrackingService priceTrackingService = mock(PriceTrackingService.class);

    @Test
    void shouldForwardTrackedAssetPriceToPriceTrackingService() {
        ConfiguredTrackedMarketService trackedMarketService = new ConfiguredTrackedMarketService(properties(List.of(
                market("asset-1", "market-1", "condition-1", "slug-1", "Question 1?", "Yes"),
                market("asset-2", "market-2", "condition-2", "slug-2", "Question 2?", "No"),
                market("asset-3", "market-3", "condition-3", "slug-3", "Question 3?", "Yes")
        )));
        PolymarketMarketMessageHandler handler = new PolymarketMarketMessageHandler(parser, priceTrackingService, trackedMarketService);

        when(priceTrackingService.registerPrice(any(), any(), any(), any()))
                .thenReturn(new PriceTrackingResult(PriceTrackingStatus.CHANGED, "asset-1", 42L));

        handler.handlePayload("""
                {
                  "event_type": "best_bid_ask",
                  "asset_id": "asset-1",
                  "best_bid": "0.60",
                  "best_ask": "0.70",
                  "timestamp": "1766789469958"
                }
                """);

        verify(priceTrackingService).registerPrice(
                eq(trackedMarketService.getTrackedMarkets().get(0)),
                eq(new BigDecimal("0.65000000")),
                eq(PriceSource.BEST_BID_ASK),
                eq(Instant.ofEpochMilli(1766789469958L))
        );
    }

    @Test
    void shouldIgnoreUntrackedAssetUpdates() {
        ConfiguredTrackedMarketService trackedMarketService = new ConfiguredTrackedMarketService(properties(List.of(
                market("asset-1", "market-1", "condition-1", "slug-1", "Question 1?", "Yes"),
                market("asset-2", "market-2", "condition-2", "slug-2", "Question 2?", "No"),
                market("asset-3", "market-3", "condition-3", "slug-3", "Question 3?", "Yes")
        )));
        PolymarketMarketMessageHandler handler = new PolymarketMarketMessageHandler(parser, priceTrackingService, trackedMarketService);

        handler.handlePayload("""
                {
                  "event_type": "last_trade_price",
                  "asset_id": "unknown-asset",
                  "price": "0.44",
                  "timestamp": "1750428146322"
                }
                """);

        verify(priceTrackingService, never()).registerPrice(any(), any(), any(), any());
    }

    private PolymarketProperties properties(List<PolymarketProperties.ConfiguredMarket> trackedMarkets) {
        PolymarketProperties properties = new PolymarketProperties();
        properties.getWebsocket().setEnabled(false);
        properties.getWebsocket().setUrl("ws://localhost/test");
        properties.getWebsocket().setPingInterval(java.time.Duration.ofSeconds(10));
        properties.getWebsocket().setReconnectDelay(java.time.Duration.ofSeconds(1));
        properties.getMarkets().setMode(MarketSelectionMode.STATIC);
        properties.getMarkets().setMinTrackedCount(3);
        properties.getMarkets().setTracked(trackedMarkets);
        return properties;
    }

    private PolymarketProperties.ConfiguredMarket market(
            String assetId,
            String marketId,
            String conditionId,
            String marketSlug,
            String marketQuestion,
            String outcome
    ) {
        PolymarketProperties.ConfiguredMarket market = new PolymarketProperties.ConfiguredMarket();
        market.setAssetId(assetId);
        market.setMarketId(marketId);
        market.setConditionId(conditionId);
        market.setMarketSlug(marketSlug);
        market.setMarketQuestion(marketQuestion);
        market.setOutcome(outcome);
        return market;
    }
}
