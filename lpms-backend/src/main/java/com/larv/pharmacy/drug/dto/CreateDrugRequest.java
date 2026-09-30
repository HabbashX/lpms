package com.larv.pharmacy.drug.dto;

import com.larv.pharmacy.drug.DosageForm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateDrugRequest(
        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @Size(max = 150) String genericName,
        @Size(max = 50) String barcode,
        @Size(max = 150) String manufacturer,
        @Size(max = 100) String category,
        DosageForm dosageForm,
        @Size(max = 50) String strength,
        @Size(max = 30) String unit,
        @Size(max = 500) String description,

        @PositiveOrZero(message = "minimumStockLevel must not be negative")
        Integer minimumStockLevel) {
}
