package com.larv.pharmacy.drug;

import com.larv.pharmacy.audit.AuditAction;
import com.larv.pharmacy.audit.AuditService;
import com.larv.pharmacy.common.dto.PageResponse;
import com.larv.pharmacy.common.exception.BusinessRuleException;
import com.larv.pharmacy.common.exception.DrugNotFoundException;
import com.larv.pharmacy.common.exception.ResourceNotFoundException;
import com.larv.pharmacy.common.util.MoneyUtil;
import com.larv.pharmacy.common.util.PaginationUtil;
import com.larv.pharmacy.drug.dto.CreateDrugRequest;
import com.larv.pharmacy.drug.dto.DrugResponse;
import com.larv.pharmacy.drug.dto.UpdateDrugRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class DrugService {

    private static final Set<String> SORTABLE = Set.of("id", "name", "genericName", "barcode",
            "currentQuantity", "sellingPrice", "createdAt", "updatedAt");

    private final DrugRepository drugRepository;
    private final CategoryRepository categoryRepository;
    private final AuditService auditService;

    public DrugService(DrugRepository drugRepository, CategoryRepository categoryRepository,
                       AuditService auditService) {
        this.drugRepository = drugRepository;
        this.categoryRepository = categoryRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<DrugResponse> list(String search, Long categoryId, DosageForm dosageForm,
                                           Boolean active, Boolean lowStock, String barcode,
                                           Pageable pageable) {
        Pageable sanitized = PaginationUtil.sanitize(pageable, SORTABLE, "name", Sort.Direction.ASC);
        Page<Drug> page = drugRepository.findAll(buildSpec(search, categoryId, dosageForm, active, lowStock, barcode),
                sanitized);
        return PageResponse.from(page, DrugResponse::from);
    }

    @Transactional(readOnly = true)
    public DrugResponse get(Long id) {
        return DrugResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public DrugResponse getByBarcode(String barcode) {
        return drugRepository.findByBarcode(barcode)
                .map(DrugResponse::from)
                .orElseThrow(() -> new DrugNotFoundException("Drug not found for barcode: " + barcode));
    }

    @Transactional
    public DrugResponse create(CreateDrugRequest request) {
        Drug drug = new Drug();
        apply(drug, request.name(), request.genericName(), request.barcode(), request.manufacturer(),
                request.category(), request.categoryId(), request.dosageForm(), request.strength(),
                request.unit(), request.sellingPrice(), request.description(), request.minimumStockLevel());
        drug.setActive(true);
        drug.setCurrentQuantity(0);
        Drug saved = drugRepository.save(drug);

        auditService.record(AuditAction.CREATE_DRUG, "Drug", saved.getId(),
                "Created drug '" + saved.getName() + "'");
        return DrugResponse.from(saved);
    }

    @Transactional
    public DrugResponse update(Long id, UpdateDrugRequest request) {
        Drug drug = findOrThrow(id);
        apply(drug, request.name(), request.genericName(), request.barcode(), request.manufacturer(),
                request.category(), request.categoryId(), request.dosageForm(), request.strength(),
                request.unit(), request.sellingPrice(), request.description(), request.minimumStockLevel());
        if (request.active() != null) {
            drug.setActive(request.active());
        }
        Drug saved = drugRepository.save(drug);

        auditService.record(AuditAction.UPDATE_DRUG, "Drug", saved.getId(),
                "Updated drug '" + saved.getName() + "'");
        return DrugResponse.from(saved);
    }

    /**
     * Soft delete: the drug is deactivated so historical sales and inventory
     * references remain intact. Hard deletion would destroy accounting history.
     */
    @Transactional
    public void deactivate(Long id) {
        Drug drug = findOrThrow(id);
        drug.setActive(false);
        drugRepository.save(drug);

        auditService.record(AuditAction.DELETE_DRUG, "Drug", drug.getId(),
                "Deactivated drug '" + drug.getName() + "'");
    }

    private void apply(Drug drug, String name, String genericName, String barcode, String manufacturer,
                       String categoryName, Long categoryId, DosageForm dosageForm, String strength,
                       String unit, java.math.BigDecimal sellingPrice, String description,
                       Integer minimumStockLevel) {
        drug.setName(name.trim());
        drug.setGenericName(trimToNull(genericName));
        drug.setManufacturer(trimToNull(manufacturer));
        drug.setStrength(trimToNull(strength));
        drug.setUnit(trimToNull(unit));
        drug.setDescription(trimToNull(description));
        drug.setDosageForm(dosageForm);
        drug.setMinimumStockLevel(minimumStockLevel == null ? 0 : minimumStockLevel);

        String normalizedBarcode = trimToNull(barcode);
        if (normalizedBarcode != null) {
            boolean duplicated = drug.getId() == null
                    ? drugRepository.existsByBarcode(normalizedBarcode)
                    : drugRepository.existsByBarcodeAndIdNot(normalizedBarcode, drug.getId());
            if (duplicated) {
                throw new BusinessRuleException("BARCODE_ALREADY_EXISTS",
                        "A drug with barcode '" + normalizedBarcode + "' already exists");
            }
        }
        drug.setBarcode(normalizedBarcode);
        drug.setSellingPrice(sellingPrice == null ? null : MoneyUtil.round2(sellingPrice));
        drug.setCategory(categoryId != null ? findCategoryOrThrow(categoryId) : resolveCategory(categoryName));
    }

    private Category findCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category", categoryId));
    }

    private Category resolveCategory(String categoryName) {
        String normalized = trimToNull(categoryName);
        if (normalized == null) {
            return null;
        }
        return categoryRepository.findByNameIgnoreCase(normalized)
                .orElseGet(() -> categoryRepository.save(new Category(normalized)));
    }

    private Drug findOrThrow(Long id) {
        return drugRepository.findByIdWithCategory(id)
                .or(() -> drugRepository.findById(id))
                .orElseThrow(() -> new DrugNotFoundException(id));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Specification<Drug> buildSpec(String search, Long categoryId, DosageForm dosageForm,
                                          Boolean active, Boolean lowStock, String barcode) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String trimmed = search.trim();
                String pattern = "%" + PaginationUtil.escapeLike(trimmed.toLowerCase(Locale.ROOT)) + "%";
                String exact = PaginationUtil.escapeLike(trimmed);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("genericName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("barcode")), exact, '\\')));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (dosageForm != null) {
                predicates.add(cb.equal(root.get("dosageForm"), dosageForm));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (Boolean.TRUE.equals(lowStock)) {
                predicates.add(cb.lessThanOrEqualTo(root.get("currentQuantity"), root.get("minimumStockLevel")));
            }
            if (barcode != null && !barcode.isBlank()) {
                predicates.add(cb.equal(root.get("barcode"), barcode.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
