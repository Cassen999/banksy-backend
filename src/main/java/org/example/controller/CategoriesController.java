package org.example.controller;

import org.example.model.CategoriesResponse;
import org.example.repository.PlaidCategoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CategoriesController {

    private final PlaidCategoryRepository categoryRepository;

    public CategoriesController(PlaidCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/categories")
    public ResponseEntity<CategoriesResponse> getCategories() {
        List<CategoriesResponse.CategoryEntry> entries = categoryRepository.findAll().stream()
                .map(c -> new CategoriesResponse.CategoryEntry(
                        c.getCategory(), c.getCategoryType(), c.getPrimaryCategory()))
                .toList();
        return ResponseEntity.ok(new CategoriesResponse(entries));
    }
}
