package com.larv.pharmacy.inventory;

/** Row content plus the drug name (used by batch listing endpoints). */
public record StockBatchRow(StockBatch batch, String drugName) {
}
