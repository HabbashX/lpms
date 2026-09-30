package com.larv.pharmacy.inventory;

import com.larv.pharmacy.common.domain.ImmutableEntity;
import com.larv.pharmacy.drug.Drug;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A single purchase of a drug. Prices are stored per batch and never
 * overwritten, so historical purchase prices are always preserved and the
 * weighted-average cost can be recomputed from the remaining stock.
 */
@Entity
@Table(name = "stock_batches", indexes = {
        @Index(name = "idx_stock_batches_drug_id", columnList = "drug_id"),
        @Index(name = "idx_stock_batches_expiration", columnList = "expiration_date"),
        @Index(name = "idx_stock_batches_drug_expiration", columnList = "drug_id, expiration_date"),
        @Index(name = "idx_stock_batches_received_at", columnList = "received_at")
})
public class StockBatch extends ImmutableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "drug_id", nullable = false)
    private Drug drug;

    @Column(name = "quantity_received", nullable = false)
    private int quantityReceived;

    @Column(name = "remaining_quantity", nullable = false)
    private int remainingQuantity;

    @Column(name = "unit_purchase_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPurchasePrice;

    @Column(length = 150)
    private String supplier;

    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected StockBatch() {
    }

    public StockBatch(Drug drug, int quantityReceived, int remainingQuantity, BigDecimal unitPurchasePrice,
                      String supplier, String batchNumber, LocalDate expirationDate, Instant receivedAt) {
        this.drug = drug;
        this.quantityReceived = quantityReceived;
        this.remainingQuantity = remainingQuantity;
        this.unitPurchasePrice = unitPurchasePrice;
        this.supplier = supplier;
        this.batchNumber = batchNumber;
        this.expirationDate = expirationDate;
        this.receivedAt = receivedAt;
    }

    public Long getId() {
        return id;
    }

    public Drug getDrug() {
        return drug;
    }

    public int getQuantityReceived() {
        return quantityReceived;
    }

    public int getRemainingQuantity() {
        return remainingQuantity;
    }

    public void setRemainingQuantity(int remainingQuantity) {
        this.remainingQuantity = remainingQuantity;
    }

    public BigDecimal getUnitPurchasePrice() {
        return unitPurchasePrice;
    }

    public String getSupplier() {
        return supplier;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    /** Value of the remaining units in this batch. */
    public BigDecimal remainingValue() {
        return unitPurchasePrice.multiply(BigDecimal.valueOf(remainingQuantity));
    }
}
