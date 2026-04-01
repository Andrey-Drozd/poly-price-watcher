package com.polymarket.pricewatcher.price.entity;

import com.polymarket.pricewatcher.price.model.PriceSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_change_events")
public class PriceChangeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false, length = 128, updatable = false)
    private String assetId;

    @Column(name = "market_id", length = 64, updatable = false)
    private String marketId;

    @Column(name = "condition_id", length = 128, updatable = false)
    private String conditionId;

    @Column(name = "market_slug", length = 255, updatable = false)
    private String marketSlug;

    @Column(name = "market_question", length = 500, updatable = false)
    private String marketQuestion;

    @Column(name = "outcome", length = 64, updatable = false)
    private String outcome;

    @Column(name = "previous_price", nullable = false, precision = 19, scale = 8, updatable = false)
    private BigDecimal previousPrice;

    @Column(name = "current_price", nullable = false, precision = 19, scale = 8, updatable = false)
    private BigDecimal currentPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_source", nullable = false, length = 32, updatable = false)
    private PriceSource priceSource;

    @Column(name = "observed_at", nullable = false, updatable = false)
    private Instant observedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected PriceChangeEvent() {
    }

    public PriceChangeEvent(
            String assetId,
            String marketId,
            String conditionId,
            String marketSlug,
            String marketQuestion,
            String outcome,
            BigDecimal previousPrice,
            BigDecimal currentPrice,
            PriceSource priceSource,
            Instant observedAt
    ) {
        this.assetId = assetId;
        this.marketId = marketId;
        this.conditionId = conditionId;
        this.marketSlug = marketSlug;
        this.marketQuestion = marketQuestion;
        this.outcome = outcome;
        this.previousPrice = previousPrice;
        this.currentPrice = currentPrice;
        this.priceSource = priceSource;
        this.observedAt = observedAt;
    }

    public Long getId() {
        return id;
    }

    public String getAssetId() {
        return assetId;
    }

    public String getMarketId() {
        return marketId;
    }

    public String getConditionId() {
        return conditionId;
    }

    public String getMarketSlug() {
        return marketSlug;
    }

    public String getMarketQuestion() {
        return marketQuestion;
    }

    public String getOutcome() {
        return outcome;
    }

    public BigDecimal getPreviousPrice() {
        return previousPrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public PriceSource getPriceSource() {
        return priceSource;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
