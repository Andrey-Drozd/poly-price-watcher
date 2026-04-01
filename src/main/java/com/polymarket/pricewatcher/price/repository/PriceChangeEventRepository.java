package com.polymarket.pricewatcher.price.repository;

import com.polymarket.pricewatcher.price.entity.PriceChangeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PriceChangeEventRepository extends JpaRepository<PriceChangeEvent, Long> {

    @Query("""
            select event
            from PriceChangeEvent event
            where event.assetId = :assetId
            order by event.observedAt asc, event.id asc
            """)
    List<PriceChangeEvent> findHistoryByAssetId(String assetId);
}
