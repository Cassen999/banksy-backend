package org.example.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "plaid_categories")
public class PlaidCategory {

    @Id
    @Column(name = "category", nullable = false, length = 255)
    private String category;

    @Column(name = "category_type", nullable = false, length = 8)
    private String categoryType;

    @Column(name = "primary_category", length = 255)
    private String primaryCategory;

    public String getCategory() { return category; }
    public String getCategoryType() { return categoryType; }
    public String getPrimaryCategory() { return primaryCategory; }
}
