package com.larv.pharmacy.sale.dto;

import com.larv.pharmacy.common.domain.PaymentMethod;
import com.larv.pharmacy.common.domain.TransferProvider;
import com.larv.pharmacy.sale.SaleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SaleResponse(
        Long id,
        Instant createdAt,
        Long customerId,
        String customerName,
        String createdBy,
        PaymentMethod paymentMethod,
        SaleStatus status,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        BigDecimal amountPaid,
        BigDecimal amountDue,
        BigDecimal cost,
        BigDecimal profit,
  BigDecimal refundedTotal,
  List<SaleItemResponse> items,
  TransferProvider transferProvider,
  String transferAccountName,
  String transferAccountIdentifier) {

  /** The complete bank transfer destination, or null for any non-transfer payment. */
  public CreateSaleRequest.TransferDetails transfer() {
    if (transferProvider == null) {
      return null;
    }
    return new CreateSaleRequest.TransferDetails(
      transferProvider, transferAccountName, transferAccountIdentifier);
  }
}
