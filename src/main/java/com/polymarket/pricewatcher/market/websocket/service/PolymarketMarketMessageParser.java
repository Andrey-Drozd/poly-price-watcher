package com.polymarket.pricewatcher.market.websocket.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.pricewatcher.market.websocket.model.ObservedMarketPrice;
import com.polymarket.pricewatcher.price.model.PriceSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class PolymarketMarketMessageParser {

    private static final BigDecimal TWO = BigDecimal.valueOf(2L);
    private static final Logger log = LoggerFactory.getLogger(PolymarketMarketMessageParser.class);

    private final ObjectMapper objectMapper;

    public PolymarketMarketMessageParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ObservedMarketPrice> parse(String payload) {
        if (!StringUtils.hasText(payload) || "PONG".equalsIgnoreCase(payload.trim())) {
            log.trace("Ignoring empty or heartbeat-only Polymarket payload");
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || root.isNull() || root.isMissingNode()) {
                return List.of();
            }

            if (root.isArray()) {
                List<ObservedMarketPrice> observedPrices = new ArrayList<>();
                for (JsonNode node : root) {
                    observedPrices.addAll(parseNode(node));
                }
                return List.copyOf(observedPrices);
            }

            return parseNode(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to parse Polymarket WebSocket payload", exception);
        }
    }

    private List<ObservedMarketPrice> parseNode(JsonNode node) {
        if (node.isObject() && node.isEmpty()) {
            log.trace("Ignoring empty Polymarket event object");
            return List.of();
        }

        String eventType = text(node, "event_type").orElse("");
        return switch (eventType) {
            case "best_bid_ask" -> parseBestBidAsk(node).stream().toList();
            case "last_trade_price" -> parseLastTradePrice(node).stream().toList();
            case "price_change" -> parsePriceChanges(node);
            case "book" -> parseOrderBook(node).stream().toList();
            default -> {
                log.trace("Ignoring unsupported Polymarket event type: {}", eventType);
                yield List.of();
            }
        };
    }

    private Optional<ObservedMarketPrice> parseBestBidAsk(JsonNode node) {
        Optional<BigDecimal> bestBid = decimal(node, "best_bid");
        Optional<BigDecimal> bestAsk = decimal(node, "best_ask");
        if (bestBid.isEmpty() || bestAsk.isEmpty()) {
            return Optional.empty();
        }

        return parseObservedPrice(
                text(node, "asset_id"),
                Optional.of(midpoint(bestBid.get(), bestAsk.get())),
                timestamp(node),
                PriceSource.BEST_BID_ASK
        );
    }

    private Optional<ObservedMarketPrice> parseLastTradePrice(JsonNode node) {
        return parseObservedPrice(
                text(node, "asset_id"),
                decimal(node, "price"),
                timestamp(node),
                PriceSource.LAST_TRADE
        );
    }

    private List<ObservedMarketPrice> parsePriceChanges(JsonNode node) {
        JsonNode priceChanges = node.path("price_changes");
        if (!priceChanges.isArray()) {
            log.trace("Ignoring price_change payload without price_changes array");
            return List.of();
        }

        Instant observedAt = timestamp(node).orElse(null);
        if (observedAt == null) {
            log.trace("Ignoring price_change payload without valid timestamp");
            return List.of();
        }

        List<ObservedMarketPrice> observedPrices = new ArrayList<>();
        for (JsonNode priceChange : priceChanges) {
            Optional<BigDecimal> midpoint = decimal(priceChange, "best_bid")
                    .flatMap(bestBid -> decimal(priceChange, "best_ask").map(bestAsk -> midpoint(bestBid, bestAsk)));
            Optional<BigDecimal> price = decimal(priceChange, "price");
            if (price.isEmpty()) {
                price = midpoint;
            }
            parseObservedPrice(
                    text(priceChange, "asset_id"),
                    price,
                    Optional.of(observedAt),
                    PriceSource.PRICE_CHANGE
            ).ifPresent(observedPrices::add);
        }

        return List.copyOf(observedPrices);
    }

    private Optional<ObservedMarketPrice> parseOrderBook(JsonNode node) {
        Optional<BigDecimal> bestBid = bestPrice(node.path("bids"), Comparator.naturalOrder());
        Optional<BigDecimal> bestAsk = bestPrice(node.path("asks"), Comparator.reverseOrder());
        if (bestBid.isEmpty() || bestAsk.isEmpty()) {
            log.trace("Ignoring order book payload without both bid and ask levels");
            return Optional.empty();
        }

        return parseObservedPrice(
                text(node, "asset_id"),
                Optional.of(midpoint(bestBid.get(), bestAsk.get())),
                timestamp(node),
                PriceSource.ORDER_BOOK
        );
    }

    private Optional<BigDecimal> bestPrice(JsonNode levels, Comparator<BigDecimal> comparator) {
        if (!levels.isArray()) {
            return Optional.empty();
        }

        BigDecimal selectedPrice = null;
        for (JsonNode level : levels) {
            Optional<BigDecimal> price = decimal(level, "price");
            if (price.isEmpty()) {
                continue;
            }

            if (selectedPrice == null || comparator.compare(price.get(), selectedPrice) > 0) {
                selectedPrice = price.get();
            }
        }

        return Optional.ofNullable(selectedPrice);
    }

    private Optional<ObservedMarketPrice> parseObservedPrice(
            Optional<String> assetId,
            Optional<BigDecimal> price,
            Optional<Instant> observedAt,
            PriceSource priceSource
    ) {
        if (assetId.isEmpty() || price.isEmpty() || observedAt.isEmpty()) {
            log.trace(
                    "Ignoring {} payload without required fields: assetIdPresent={}, pricePresent={}, observedAtPresent={}",
                    priceSource,
                    assetId.isPresent(),
                    price.isPresent(),
                    observedAt.isPresent()
            );
            return Optional.empty();
        }

        return Optional.of(new ObservedMarketPrice(
                assetId.get(),
                price.get(),
                priceSource,
                observedAt.get()
        ));
    }

    private Optional<String> text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (!value.isTextual() || !StringUtils.hasText(value.asText())) {
            return Optional.empty();
        }
        return Optional.of(value.asText());
    }

    private Optional<BigDecimal> decimal(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull() || !StringUtils.hasText(value.asText())) {
            return Optional.empty();
        }

        try {
            return Optional.of(new BigDecimal(value.asText()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private Optional<Instant> timestamp(JsonNode node) {
        JsonNode value = node.path("timestamp");
        if (value.isMissingNode() || value.isNull() || !StringUtils.hasText(value.asText())) {
            return Optional.empty();
        }

        try {
            return Optional.of(Instant.ofEpochMilli(Long.parseLong(value.asText())));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private BigDecimal midpoint(BigDecimal left, BigDecimal right) {
        return left.add(right).divide(TWO, 8, RoundingMode.HALF_UP);
    }
}
