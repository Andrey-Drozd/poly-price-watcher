package com.polymarket.pricewatcher.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "polymarket")
public class PolymarketProperties {

    private final Websocket websocket = new Websocket();
    private final Markets markets = new Markets();

    public Websocket getWebsocket() {
        return websocket;
    }

    public Markets getMarkets() {
        return markets;
    }

    public static class Websocket {

        private boolean enabled;

        @NotBlank
        private String url;

        @NotNull
        private Duration pingInterval;

        @NotNull
        private Duration reconnectDelay;

        private boolean customFeatureEnabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Duration getPingInterval() {
            return pingInterval;
        }

        public void setPingInterval(Duration pingInterval) {
            this.pingInterval = pingInterval;
        }

        public Duration getReconnectDelay() {
            return reconnectDelay;
        }

        public void setReconnectDelay(Duration reconnectDelay) {
            this.reconnectDelay = reconnectDelay;
        }

        public boolean isCustomFeatureEnabled() {
            return customFeatureEnabled;
        }

        public void setCustomFeatureEnabled(boolean customFeatureEnabled) {
            this.customFeatureEnabled = customFeatureEnabled;
        }

    }

    public static class Markets {

        @NotNull
        private MarketSelectionMode mode = MarketSelectionMode.STATIC;

        @Valid
        private List<ConfiguredMarket> tracked = new ArrayList<>();

        @Min(3)
        private int minTrackedCount = 3;

        public MarketSelectionMode getMode() {
            return mode;
        }

        public void setMode(MarketSelectionMode mode) {
            this.mode = mode;
        }

        public List<ConfiguredMarket> getTracked() {
            return tracked;
        }

        public void setTracked(List<ConfiguredMarket> tracked) {
            this.tracked = tracked;
        }

        public int getMinTrackedCount() {
            return minTrackedCount;
        }

        public void setMinTrackedCount(int minTrackedCount) {
            this.minTrackedCount = minTrackedCount;
        }
    }

    public static class ConfiguredMarket {

        @NotBlank
        private String assetId;

        @NotBlank
        private String marketId;

        @NotBlank
        private String conditionId;

        @NotBlank
        private String marketSlug;

        @NotBlank
        private String marketQuestion;

        @NotBlank
        private String outcome;

        public String getAssetId() {
            return assetId;
        }

        public void setAssetId(String assetId) {
            this.assetId = assetId;
        }

        public String getMarketId() {
            return marketId;
        }

        public void setMarketId(String marketId) {
            this.marketId = marketId;
        }

        public String getConditionId() {
            return conditionId;
        }

        public void setConditionId(String conditionId) {
            this.conditionId = conditionId;
        }

        public String getMarketSlug() {
            return marketSlug;
        }

        public void setMarketSlug(String marketSlug) {
            this.marketSlug = marketSlug;
        }

        public String getMarketQuestion() {
            return marketQuestion;
        }

        public void setMarketQuestion(String marketQuestion) {
            this.marketQuestion = marketQuestion;
        }

        public String getOutcome() {
            return outcome;
        }

        public void setOutcome(String outcome) {
            this.outcome = outcome;
        }
    }
}
