package com.larv.pharmacy.drug;

import com.larv.pharmacy.audit.AuditAction;
import com.larv.pharmacy.audit.AuditService;
import com.larv.pharmacy.common.exception.BusinessRuleException;
import com.larv.pharmacy.common.exception.ResourceNotFoundException;
import com.larv.pharmacy.drug.dto.CategoryRequest;
import com.larv.pharmacy.drug.dto.CategoryResponse;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final DrugRepository drugRepository;
    private final AuditService auditService;

    public CategoryService(CategoryRepository categoryRepository, DrugRepository drugRepository,
                           AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.drugRepository = drugRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return CategoryResponse.from(findOrThrow(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.findByNameIgnoreCase(name).isPresent()) {
            throw duplicate(name);
        }
        Category saved = categoryRepository.save(new Category(name));
        auditService.record(AuditAction.CREATE_CATEGORY, "Category", saved.getId(),
                "Created category '" + saved.getName() + "'");
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findOrThrow(id);
        String name = request.name().trim();
        categoryRepository.findByNameIgnoreCase(name)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw duplicate(name);
                });
        category.setName(name);
        Category saved = categoryRepository.save(category);
        auditService.record(AuditAction.UPDATE_CATEGORY, "Category", saved.getId(),
                "Renamed category to '" + saved.getName() + "'");
        return CategoryResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        Category category = findOrThrow(id);
        if (drugRepository.existsByCategoryId(id)) {
            throw new BusinessRuleException("CATEGORY_IN_USE",
                    "Category '" + category.getName() + "' is used by drugs and cannot be deleted");
        }
        categoryRepository.delete(category);
        auditService.record(AuditAction.DELETE_CATEGORY, "Category", id,
                "Deleted category '" + category.getName() + "'");
    }

    private Category findOrThrow(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private static BusinessRuleException duplicate(String name) {
        return new BusinessRuleException("CATEGORY_ALREADY_EXISTS",
                "A category named '" + name + "' already exists");
    }
}
