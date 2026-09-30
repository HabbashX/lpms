package com.larv.pharmacy.drug;

import com.larv.pharmacy.common.dto.PageResponse;
import com.larv.pharmacy.drug.dto.CreateDrugRequest;
import com.larv.pharmacy.drug.dto.DrugResponse;
import com.larv.pharmacy.drug.dto.UpdateDrugRequest;
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
@RequestMapping("/api/v1/drugs")
@Tag(name = "Drugs", description = "Medicine catalog: search, filtering, barcode lookup")
public class DrugController {

    private final DrugService drugService;

    public DrugController(DrugService drugService) {
        this.drugService = drugService;
    }

    @GetMapping
    @Operation(summary = "List drugs",
            description = "Supports pagination, sorting, free-text search, category/dosage-form/active filters, "
                    + "barcode filter and low-stock filtering.")
    public PageResponse<DrugResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) DosageForm dosageForm,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean lowStock,
            @RequestParam(required = false) String barcode,
            Pageable pageable) {
        return drugService.list(search, categoryId, dosageForm, active, lowStock, barcode, pageable);
    }

    @GetMapping("/barcode/{barcode}")
    @Operation(summary = "Look up a drug by barcode")
    public DrugResponse getByBarcode(@PathVariable String barcode) {
        return drugService.getByBarcode(barcode);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a drug by id")
    public DrugResponse get(@PathVariable Long id) {
        return drugService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a drug (ADMIN/PHARMACIST)")
    public DrugResponse create(@Valid @RequestBody CreateDrugRequest request) {
        return drugService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a drug (ADMIN/PHARMACIST)")
    public DrugResponse update(@PathVariable Long id, @Valid @RequestBody UpdateDrugRequest request) {
        return drugService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a drug (ADMIN/PHARMACIST)",
            description = "Soft delete: the drug is marked inactive so historical data stays intact.")
    public void delete(@PathVariable Long id) {
        drugService.deactivate(id);
    }
}
