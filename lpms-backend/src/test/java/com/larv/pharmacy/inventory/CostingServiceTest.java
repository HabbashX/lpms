package com.larv.pharmacy.inventory;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CostingServiceTest {

    private StockBatch batch(int remaining, String unitPrice) {
        return new StockBatch(null, 100, remaining, new BigDecimal(unitPrice),
                "Supplier", "B1", LocalDate.of(2027, 6, 30), Instant.parse("2026-09-01T00:00:00Z"));
    }

    @Test
    void computesWeightedAverageAcrossBatches() {
        List<StockBatch> batches = List.of(batch(10, "2.50"), batch(6, "3.00"));

        CostingService.CostBasis basis = CostingService.calculate(batches);

        assertThat(basis.totalQuantity()).isEqualTo(16);
        assertThat(basis.totalInventoryCost()).isEqualByComparingTo("43.00");
        // 43.00 / 16 = 2.6875 (quantity-weighted, not a plain price average)
        assertThat(basis.weightedAverageCost()).isEqualByComparingTo("2.6875");
        assertThat(basis.weightedAverageCost().scale()).isEqualTo(4);
    }

    @Test
    void emptyStockHasZeroBasis() {
        CostingService.CostBasis basis = CostingService.calculate(List.of());

        assertThat(basis.totalQuantity()).isZero();
        assertThat(basis.totalInventoryCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(basis.weightedAverageCost()).isEqualByComparingTo("0.0000");
        assertThat(basis.weightedAverageCost().scale()).isEqualTo(4);
    }

    @Test
    void singleBatchAverageEqualsItsPrice() {
        CostingService.CostBasis basis = CostingService.calculate(List.of(batch(5, "7.1234")));

        assertThat(basis.totalQuantity()).isEqualTo(5);
        assertThat(basis.totalInventoryCost()).isEqualByComparingTo("35.6170");
        assertThat(basis.weightedAverageCost()).isEqualByComparingTo("7.1234");
    }

    @Test
    void ignoresDepletedBatchesWithZeroRemaining() {
        StockBatch depleted = batch(0, "99.99");
        CostingService.CostBasis basis = CostingService.calculate(List.of(depleted, batch(4, "1.50")));

        assertThat(basis.totalQuantity()).isEqualTo(4);
        assertThat(basis.totalInventoryCost()).isEqualByComparingTo("6.00");
        assertThat(basis.weightedAverageCost()).isEqualByComparingTo("1.5000");
    }
}
