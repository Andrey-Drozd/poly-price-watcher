package com.polymarket.pricewatcher.market.service;

import com.polymarket.pricewatcher.config.MarketSelectionMode;
import com.polymarket.pricewatcher.config.PolymarketProperties;
import com.polymarket.pricewatcher.price.model.TrackedMarket;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguredTrackedMarketServiceTest {

    @Test
    void shouldLoadTrackedMarketsFromStaticConfiguration() {
        ConfiguredTrackedMarketService service = new ConfiguredTrackedMarketService(properties(List.of(
                market("asset-1", "market-1", "condition-1", "slug-1", "Question 1?", "Yes"),
                market("asset-2", "market-2", "condition-2", "slug-2", "Question 2?", "No"),
                market("asset-3", "market-3", "condition-3", "slug-3", "Question 3?", "Yes")
        )));

        List<TrackedMarket> trackedMarkets = service.getTrackedMarkets();

        assertThat(trackedMarkets).hasSize(3);
        assertThat(service.getTrackedAssetIds()).containsExactly("asset-1", "asset-2", "asset-3");
        assertThat(trackedMarkets.get(0).marketQuestion()).isEqualTo("Question 1?");
    }

    @Test
    void shouldRejectConfigurationWithLessThanThreeMarkets() {
        assertThatThrownBy(() -> new ConfiguredTrackedMarketService(properties(List.of(
                market("asset-1", "market-1", "condition-1", "slug-1", "Question 1?", "Yes"),
                market("asset-2", "market-2", "condition-2", "slug-2", "Question 2?", "No")
        )))).isInstanceOf(IllegalStateException.class)
                .hasMessage("At least 3 tracked markets must be configured");
    }

    @Test
    void shouldRejectDuplicateAssetIds() {
        assertThatThrownBy(() -> new ConfiguredTrackedMarketService(properties(List.of(
                market("asset-1", "market-1", "condition-1", "slug-1", "Question 1?", "Yes"),
                market("asset-1", "market-2", "condition-2", "slug-2", "Question 2?", "No"),
                market("asset-3", "market-3", "condition-3", "slug-3", "Question 3?", "Yes")
        )))).isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate tracked market assetId: asset-1");
    }

    @Test
    void shouldRejectDuplicateMarketIds() {
        assertThatThrownBy(() -> new ConfiguredTrackedMarketService(properties(List.of(
                market("asset-1", "market-1", "condition-1", "slug-1", "Question 1?", "Yes"),
                market("asset-2", "market-1", "condition-2", "slug-2", "Question 2?", "No"),
                market("asset-3", "market-3", "condition-3", "slug-3", "Question 3?", "Yes")
        )))).isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate tracked market marketId: market-1");
    }

    @Test
    void shouldRejectDuplicateMarketSlugs() {
        assertThatThrownBy(() -> new ConfiguredTrackedMarketService(properties(List.of(
                market("asset-1", "market-1", "condition-1", "slug-1", "Question 1?", "Yes"),
                market("asset-2", "market-2", "condition-2", "slug-1", "Question 2?", "No"),
                market("asset-3", "market-3", "condition-3", "slug-3", "Question 3?", "Yes")
        )))).isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate tracked market marketSlug: slug-1");
    }

    private PolymarketProperties properties(List<PolymarketProperties.ConfiguredMarket> trackedMarkets) {
        PolymarketProperties properties = new PolymarketProperties();
        properties.getWebsocket().setUrl("ws://localhost/test");
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
