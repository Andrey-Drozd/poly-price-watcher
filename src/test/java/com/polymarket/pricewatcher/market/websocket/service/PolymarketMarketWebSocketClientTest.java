package com.polymarket.pricewatcher.market.websocket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.pricewatcher.config.MarketSelectionMode;
import com.polymarket.pricewatcher.config.PolymarketProperties;
import com.polymarket.pricewatcher.market.service.ConfiguredTrackedMarketService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PolymarketMarketWebSocketClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBuildSubscriptionPayloadUsingConfiguredAssetsAndMarketType() throws Exception {
        ConfiguredTrackedMarketService trackedMarketService = new ConfiguredTrackedMarketService(properties(List.of(
                market("asset-1", "market-1", "condition-1", "slug-1", "Question 1?", "Yes"),
                market("asset-2", "market-2", "condition-2", "slug-2", "Question 2?", "No"),
                market("asset-3", "market-3", "condition-3", "slug-3", "Question 3?", "Yes")
        )));
        PolymarketMarketWebSocketClient client = new PolymarketMarketWebSocketClient(
                objectMapper,
                properties(trackedMarketService.getTrackedMarkets().stream()
                        .map(trackedMarket -> market(
                                trackedMarket.assetId(),
                                trackedMarket.marketId(),
                                trackedMarket.conditionId(),
                                trackedMarket.marketSlug(),
                                trackedMarket.marketQuestion(),
                                trackedMarket.outcome()
                        ))
                        .toList()),
                trackedMarketService,
                mock(PolymarketMarketMessageHandler.class)
        );

        JsonNode payload = objectMapper.readTree(client.subscriptionMessage(trackedMarketService.getTrackedAssetIds()));

        assertThat(payload.path("type").asText()).isEqualTo("market");
        assertThat(payload.path("custom_feature_enabled").asBoolean()).isTrue();
        assertThat(payload.path("assets_ids")).hasSize(3);
        assertThat(payload.path("assets_ids").get(0).asText()).isEqualTo("asset-1");
        assertThat(client.heartbeatMessage()).isEqualTo("PING");
    }

    private PolymarketProperties properties(List<PolymarketProperties.ConfiguredMarket> trackedMarkets) {
        PolymarketProperties properties = new PolymarketProperties();
        properties.getWebsocket().setEnabled(true);
        properties.getWebsocket().setUrl("ws://localhost/test");
        properties.getWebsocket().setPingInterval(Duration.ofSeconds(10));
        properties.getWebsocket().setReconnectDelay(Duration.ofSeconds(1));
        properties.getWebsocket().setCustomFeatureEnabled(true);
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
