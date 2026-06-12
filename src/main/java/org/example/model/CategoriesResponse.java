package org.example.model;

import java.util.List;

public record CategoriesResponse(List<CategoryEntry> categories) {

    public record CategoryEntry(String category, String type, String primaryCategory) {}
}
