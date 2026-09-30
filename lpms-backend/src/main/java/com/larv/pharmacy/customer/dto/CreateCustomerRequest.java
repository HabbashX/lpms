package com.larv.pharmacy.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @Size(max = 30) String phone,
        @Size(max = 255) String address,
        @Size(max = 500) String notes) {
}
