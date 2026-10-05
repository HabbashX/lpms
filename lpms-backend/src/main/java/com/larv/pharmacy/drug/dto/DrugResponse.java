package com.larv.pharmacy.drug.dto;

import com.larv.pharmacy.drug.DosageForm;
import com.larv.pharmacy.drug.Drug;

import java.time.Instant;

public record DrugResponse(
        Long id,
        String name,
        String genericName,
        String barcode,
        String manufacturer,
        Long categoryId,
        String category,
        DosageForm dosageForm,
        String strength,
        String unit,
        java.math.BigDecimal sellingPrice,
        String description,
        int minimumStockLevel,
        int currentQuantity,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static DrugResponse from(Drug drug) {
        return new DrugResponse(
                drug.getId(),
                drug.getName(),
                drug.getGenericName(),
                drug.getBarcode(),
                drug.getManufacturer(),
                drug.getCategory() == null ? null : drug.getCategory().getId(),
                drug.getCategory() == null ? null : drug.getCategory().getName(),
                drug.getDosageForm(),
                drug.getStrength(),
                drug.getUnit(),
                drug.getSellingPrice() == null ? null : drug.getSellingPrice().setScale(2, java.math.RoundingMode.HALF_UP),
                drug.getDescription(),
                drug.getMinimumStockLevel(),
                drug.getCurrentQuantity(),
                drug.isActive(),
                drug.getCreatedAt(),
                drug.getUpdatedAt());
    }
}
