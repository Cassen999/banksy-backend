package org.example.model;

import java.time.LocalDate;
import java.util.List;

public record TransactionsResponse(List<Transaction> transactions, int total) {

    public record Transaction(
            LocalDate date,
            String name,
            Double amount,
            String currency,
            List<String> category
    ) {}
}
