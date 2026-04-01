package com.polymarket.pricewatcher.price.service;

import com.polymarket.pricewatcher.price.model.PriceSource;
import com.polymarket.pricewatcher.price.model.TrackedPrice;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PriceObservationPolicy {

    public boolean shouldIgnore(TrackedPrice previousPrice, PriceSource observedSource, Instant observedAt) {
        if (observedAt.isBefore(previousPrice.observedAt())) {
            return true;
        }

        return observedAt.equals(previousPrice.observedAt())
                && previousPrice.priceSource().isHigherPriorityThan(observedSource);
    }
}
