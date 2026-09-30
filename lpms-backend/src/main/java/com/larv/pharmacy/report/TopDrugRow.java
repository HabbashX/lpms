package com.larv.pharmacy.report;

import java.math.BigDecimal;

/** Aggregated top-selling drug row. */
public record TopDrugRow(Long drugId, String drugName, Long quantity, BigDecimal revenue) {
}
