package com.polymarket.pricewatcher.price.model;

public record PriceTrackingResult(
        PriceTrackingStatus status,
        String assetId,
        Long eventId
) {

    public static PriceTrackingResult initialized(String assetId) {
        return new PriceTrackingResult(PriceTrackingStatus.INITIALIZED, assetId, null);
    }

    public static PriceTrackingResult stale(String assetId) {
        return new PriceTrackingResult(PriceTrackingStatus.STALE, assetId, null);
    }

    public static PriceTrackingResult unchanged(String assetId) {
        return new PriceTrackingResult(PriceTrackingStatus.UNCHANGED, assetId, null);
    }

    public static PriceTrackingResult changed(String assetId, Long eventId) {
        return new PriceTrackingResult(PriceTrackingStatus.CHANGED, assetId, eventId);
    }
}
