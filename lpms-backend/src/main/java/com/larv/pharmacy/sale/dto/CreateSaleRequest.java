package com.larv.pharmacy.sale.dto;

import com.larv.pharmacy.common.domain.PaymentMethod;
import com.larv.pharmacy.common.domain.TransferProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code POST /sales} body.
 *
 * <p>{@link #transfer} is required exactly when {@code paymentMethod} is
 * {@link PaymentMethod#BANK_TRANSFER}, and must be absent otherwise. That pairing is
 * enforced in {@code SaleService} rather than with annotations alone, because it depends
 * on {@code paymentMethod} and bean validation cannot express "required if".</p>
 */
public record CreateSaleRequest(
        Long customerId,

        @NotNull(message = "paymentMethod is required")
        PaymentMethod paymentMethod,

        @NotEmpty(message = "A sale must contain at least one item")
        List<@Valid CreateSaleItemRequest> items,

        @PositiveOrZero(message = "discount must not be negative")
        BigDecimal discount,

        @PositiveOrZero(message = "amountPaid must not be negative")
        BigDecimal amountPaid,

        @Valid TransferDetails transfer) {

    /**
     * Where a bank transfer payment is sent. {@code accountIdentifier} is the provider's
     * own identifier: a PayPal email, a Bank of Palestine account number, or a Jawwal
     * Pay phone number. See {@link TransferProvider#identifierLabel()}.
     */
    public record TransferDetails(
            @NotNull(message = "transfer.provider is required for a bank transfer")
            TransferProvider provider,

            @NotBlank(message = "transfer.accountName is required for a bank transfer")
            @Size(max = 120, message = "transfer.accountName must be 120 characters or fewer")
            String accountName,

            @NotBlank(message = "transfer.accountIdentifier is required for a bank transfer")
            @Size(max = 120, message = "transfer.accountIdentifier must be 120 characters or fewer")
            String accountIdentifier) {
    }
}
