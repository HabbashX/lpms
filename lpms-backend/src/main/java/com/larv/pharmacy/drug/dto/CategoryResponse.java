package com.larv.pharmacy.drug.dto;

import com.larv.pharmacy.drug.Category;

public record CategoryResponse(Long id, String name) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
