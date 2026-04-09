package com.polymarket.pricewatcher.price.repository;

import com.polymarket.pricewatcher.price.entity.PriceChangeEvent;
import com.polymarket.pricewatcher.price.model.PriceSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PriceChangeEventRepositoryTest {

    @Autowired
    private PriceChangeEventRepository repository;

    @Test
    void findHistoryByAssetIdShouldReturnPersistedEventsInStableOrder() {
        repository.save(new PriceChangeEvent(
                "asset-123",
                "market-321",
                "condition-999",
                "trump-wins-2028",
                "Will Trump win in 2028?",
                "Yes",
                new BigDecimal("0.41000000"),
                new BigDecimal("0.43000000"),
                PriceSource.BEST_BID_ASK,
                Instant.parse("2026-04-01T10:15:30Z")
        ));

        repository.save(new PriceChangeEvent(
                "asset-123",
                "market-321",
                "condition-999",
                "trump-wins-2028",
                "Will Trump win in 2028?",
                "Yes",
                new BigDecimal("0.43000000"),
                new BigDecimal("0.45000000"),
                PriceSource.LAST_TRADE,
                Instant.parse("2026-04-01T10:16:30Z")
        ));

        List<PriceChangeEvent> events = repository.findHistoryByAssetId("asset-123");

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getCurrentPrice()).isEqualByComparingTo("0.43000000");
        assertThat(events.get(1).getCurrentPrice()).isEqualByComparingTo("0.45000000");
    }

    @Test
    void findHistoryByAssetIdShouldReturnEmptyForUnknownAsset() {
        List<PriceChangeEvent> events = repository.findHistoryByAssetId("nonexistent-asset");

        assertThat(events).isEmpty();
    }

    @Test
    void findHistoryByAssetIdShouldIsolateDifferentAssets() {
        repository.save(new PriceChangeEvent(
                "asset-1",
                "market-1",
                "condition-1",
                "slug-1",
                "Question 1?",
                "Yes",
                new BigDecimal("0.40000000"),
                new BigDecimal("0.42000000"),
                PriceSource.BEST_BID_ASK,
                Instant.parse("2026-04-01T10:15:30Z")
        ));

        repository.save(new PriceChangeEvent(
                "asset-2",
                "market-2",
                "condition-2",
                "slug-2",
                "Question 2?",
                "No",
                new BigDecimal("0.50000000"),
                new BigDecimal("0.55000000"),
                PriceSource.LAST_TRADE,
                Instant.parse("2026-04-01T10:16:30Z")
        ));

        List<PriceChangeEvent> asset1Events = repository.findHistoryByAssetId("asset-1");
        List<PriceChangeEvent> asset2Events = repository.findHistoryByAssetId("asset-2");

        assertThat(asset1Events).hasSize(1);
        assertThat(asset1Events.get(0).getAssetId()).isEqualTo("asset-1");
        assertThat(asset2Events).hasSize(1);
        assertThat(asset2Events.get(0).getAssetId()).isEqualTo("asset-2");
    }
}
