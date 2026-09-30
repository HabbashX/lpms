package com.larv.pharmacy.customer;

import com.larv.pharmacy.common.dto.PageResponse;
import com.larv.pharmacy.customer.dto.CreateAdjustmentRequest;
import com.larv.pharmacy.customer.dto.CreateCustomerRequest;
import com.larv.pharmacy.customer.dto.CreatePaymentRequest;
import com.larv.pharmacy.customer.dto.CustomerAccountResponse;
import com.larv.pharmacy.customer.dto.CustomerResponse;
import com.larv.pharmacy.customer.dto.PaymentResponse;
import com.larv.pharmacy.customer.dto.TransactionResponse;
import com.larv.pharmacy.customer.dto.UpdateCustomerRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers", description = "Customer management, debt account and payments")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerAccountService accountService;

    public CustomerController(CustomerService customerService, CustomerAccountService accountService) {
        this.customerService = customerService;
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "List customers", description = "Free-text search matches name or phone.")
    public PageResponse<CustomerResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            Pageable pageable) {
        return customerService.list(search, active, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a customer by id")
    public CustomerResponse get(@PathVariable Long id) {
        return customerService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a customer")
    public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a customer")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCustomerRequest request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a customer",
            description = "Soft delete that preserves the financial ledger and debt history.")
    public void delete(@PathVariable Long id) {
        customerService.deactivate(id);
    }

    @GetMapping("/{id}/account")
    @Operation(summary = "Full customer account page",
            description = "Totals plus the chronological ledger. "
                    + "transactionPage/transactionSize paginate the transaction history.")
    public CustomerAccountResponse account(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int transactionPage,
            @RequestParam(defaultValue = "20") int transactionSize) {
        return accountService.accountPage(id,
                org.springframework.data.domain.PageRequest.of(transactionPage, transactionSize));
    }

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a debt payment",
            description = "Reduces the outstanding debt; payments above the balance are rejected "
                    + "unless customer credit balances are enabled.")
    public PaymentResponse pay(@PathVariable Long id, @Valid @RequestBody CreatePaymentRequest request) {
        return accountService.pay(id, request);
    }

    @PostMapping("/{id}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Manual ledger adjustment (ADMIN/PHARMACIST)",
            description = "Appends an ADJUSTMENT entry (e.g. written-off debt) to the ledger.")
    public TransactionResponse adjust(@PathVariable Long id,
                                      @Valid @RequestBody CreateAdjustmentRequest request) {
        return accountService.adjust(id, request);
    }
}
