package com.polymarket.pricewatcher.price.model;

public enum PriceSource {
    BEST_BID_ASK(4),
    ORDER_BOOK(3),
    PRICE_CHANGE(2),
    LAST_TRADE(1);

    private final int priority;

    PriceSource(int priority) {
        this.priority = priority;
    }

    public boolean isHigherPriorityThan(PriceSource other) {
        return this.priority > other.priority;
    }
}
