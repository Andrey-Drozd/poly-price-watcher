package com.polymarket.pricewatcher.market.websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.pricewatcher.market.websocket.model.ObservedMarketPrice;
import com.polymarket.pricewatcher.price.model.PriceSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolymarketMarketMessageParserTest {

    private final PolymarketMarketMessageParser parser = new PolymarketMarketMessageParser(new ObjectMapper());

    @Test
    void shouldParseBestBidAskMessageIntoMidpointPrice() {
        List<ObservedMarketPrice> prices = parser.parse("""
                {
                  "event_type": "best_bid_ask",
                  "asset_id": "asset-123",
                  "best_bid": "0.73",
                  "best_ask": "0.77",
                  "timestamp": "1766789469958"
                }
                """);

        assertThat(prices).singleElement().satisfies(price -> {
            assertThat(price.assetId()).isEqualTo("asset-123");
            assertThat(price.price()).isEqualByComparingTo("0.75000000");
            assertThat(price.priceSource()).isEqualTo(PriceSource.BEST_BID_ASK);
            assertThat(price.observedAt()).isEqualTo(Instant.ofEpochMilli(1766789469958L));
        });
    }

    @Test
    void shouldParseOrderBookMessageIntoMidpointPrice() {
        List<ObservedMarketPrice> prices = parser.parse("""
                {
                  "event_type": "book",
                  "asset_id": "asset-123",
                  "bids": [
                    {"price": "0.48", "size": "30"},
                    {"price": "0.50", "size": "15"}
                  ],
                  "asks": [
                    {"price": "0.52", "size": "25"},
                    {"price": "0.54", "size": "10"}
                  ],
                  "timestamp": "1757908892351"
                }
                """);

        assertThat(prices).singleElement().satisfies(price -> {
            assertThat(price.price()).isEqualByComparingTo("0.51000000");
            assertThat(price.priceSource()).isEqualTo(PriceSource.ORDER_BOOK);
        });
    }

    @Test
    void shouldParseOrderBookUsingHighestBidAndLowestAskFromUnsortedLevels() {
        List<ObservedMarketPrice> prices = parser.parse("""
                {
                  "event_type": "book",
                  "asset_id": "asset-123",
                  "bids": [
                    {"price": "0.48", "size": "30"},
                    {"price": "0.50", "size": "15"},
                    {"price": "0.49", "size": "10"}
                  ],
                  "asks": [
                    {"price": "0.56", "size": "25"},
                    {"price": "0.54", "size": "10"},
                    {"price": "0.55", "size": "5"}
                  ],
                  "timestamp": "1757908892351"
                }
                """);

        assertThat(prices).singleElement().satisfies(price -> {
            assertThat(price.price()).isEqualByComparingTo("0.52000000");
            assertThat(price.priceSource()).isEqualTo(PriceSource.ORDER_BOOK);
        });
    }

    @Test
    void shouldPreferBestBidAskMidpointInsidePriceChangeMessage() {
        List<ObservedMarketPrice> prices = parser.parse("""
                {
                  "event_type": "price_change",
                  "timestamp": "1757908892351",
                  "price_changes": [
                    {
                      "asset_id": "asset-123",
                      "price": "0.5",
                      "best_bid": "0.5",
                      "best_ask": "0.6"
                    }
                  ]
                }
                """);

        assertThat(prices).singleElement().satisfies(price -> {
            assertThat(price.price()).isEqualByComparingTo("0.5");
            assertThat(price.priceSource()).isEqualTo(PriceSource.PRICE_CHANGE);
        });
    }

    @Test
    void shouldFallbackToTradePriceWhenPriceChangeDoesNotContainBestBidAndAsk() {
        List<ObservedMarketPrice> prices = parser.parse("""
                {
                  "event_type": "price_change",
                  "timestamp": "1757908892351",
                  "price_changes": [
                    {
                      "asset_id": "asset-123",
                      "price": "0.41"
                    }
                  ]
                }
                """);

        assertThat(prices).singleElement().satisfies(price ->
                assertThat(price.price()).isEqualByComparingTo("0.41"));
    }

    @Test
    void shouldUseExplicitPriceFromPriceChangeWhenBidAskWouldProduceDifferentMidpoint() {
        List<ObservedMarketPrice> prices = parser.parse("""
                {
                  "event_type": "price_change",
                  "timestamp": "1757908892351",
                  "price_changes": [
                    {
                      "asset_id": "asset-123",
                      "price": "0.5",
                      "best_bid": "0",
                      "best_ask": "0.5"
                    }
                  ]
                }
                """);

        assertThat(prices).singleElement().satisfies(price -> {
            assertThat(price.price()).isEqualByComparingTo("0.5");
            assertThat(price.priceSource()).isEqualTo(PriceSource.PRICE_CHANGE);
        });
    }

    @Test
    void shouldParseLastTradePriceMessage() {
        List<ObservedMarketPrice> prices = parser.parse("""
                {
                  "event_type": "last_trade_price",
                  "asset_id": "asset-123",
                  "price": "0.456",
                  "timestamp": "1750428146322"
                }
                """);

        assertThat(prices).singleElement().satisfies(price -> {
            assertThat(price.price()).isEqualByComparingTo("0.456");
            assertThat(price.priceSource()).isEqualTo(PriceSource.LAST_TRADE);
        });
    }

    @Test
    void shouldParseArrayPayloadWithMultipleSupportedEvents() {
        List<ObservedMarketPrice> prices = parser.parse("""
                [
                  {
                    "event_type": "last_trade_price",
                    "asset_id": "asset-123",
                    "price": "0.456",
                    "timestamp": "1750428146322"
                  },
                  {
                    "event_type": "best_bid_ask",
                    "asset_id": "asset-456",
                    "best_bid": "0.20",
                    "best_ask": "0.30",
                    "timestamp": "1766789469958"
                  }
                ]
                """);

        assertThat(prices).hasSize(2);
        assertThat(prices.get(0).assetId()).isEqualTo("asset-123");
        assertThat(prices.get(0).priceSource()).isEqualTo(PriceSource.LAST_TRADE);
        assertThat(prices.get(1).assetId()).isEqualTo("asset-456");
        assertThat(prices.get(1).price()).isEqualByComparingTo("0.25000000");
        assertThat(prices.get(1).priceSource()).isEqualTo(PriceSource.BEST_BID_ASK);
    }

    @Test
    void shouldIgnorePongAndUnknownEvents() {
        assertThat(parser.parse("PONG")).isEmpty();
        assertThat(parser.parse("{\"event_type\":\"tick_size_change\"}")).isEmpty();
        assertThat(parser.parse("{}")).isEmpty();
    }

    @Test
    void shouldRejectMalformedJsonPayload() {
        assertThatThrownBy(() -> parser.parse("{invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Failed to parse Polymarket WebSocket payload");
    }
}
