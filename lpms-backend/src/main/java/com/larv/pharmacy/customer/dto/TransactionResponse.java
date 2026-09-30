package com.larv.pharmacy.customer.dto;

import com.larv.pharmacy.common.util.MoneyUtil;
import com.larv.pharmacy.customer.CustomerTransaction;
import com.larv.pharmacy.customer.TransactionDirection;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        Instant date,
        String type,
        String description,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal balance,
        String reference,
        String createdBy) {

    public static TransactionResponse from(CustomerTransaction transaction, String createdByUsername) {
        boolean debit = transaction.getDirection() == TransactionDirection.DEBIT;
        String reference = transaction.getReferenceType() == null
                ? null
                : (transaction.getReferenceId() == null
                        ? transaction.getReferenceType()
                        : transaction.getReferenceType() + " #" + transaction.getReferenceId());
        return new TransactionResponse(
                transaction.getId(),
                transaction.getCreatedAt(),
                transaction.getType().name(),
                transaction.getDescription(),
                debit ? MoneyUtil.round2(transaction.getAmount()) : BigDecimal.ZERO.setScale(2),
                debit ? BigDecimal.ZERO.setScale(2) : MoneyUtil.round2(transaction.getAmount()),
                MoneyUtil.round2(transaction.getBalanceAfter()),
                reference,
                createdByUsername);
    }
}
