package com.polymarket.pricewatcher.price.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class PriceTrackingLockManager {

    private final ConcurrentMap<String, ReentrantLock> locksByAssetId = new ConcurrentHashMap<>();

    public ReentrantLock acquire(String assetId) {
        ReentrantLock lock = locksByAssetId.computeIfAbsent(assetId, ignored -> new ReentrantLock());
        lock.lock();
        return lock;
    }
}
