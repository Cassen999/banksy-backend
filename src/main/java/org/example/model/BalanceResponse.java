package org.example.model;

import java.util.List;

// Like a TypeScript interface — defines the exact shape of the JSON we return to the frontend.
// Java records are immutable and auto-generate equals/hashCode/toString.
public record

BalanceResponse(List<Account> accounts) {

    public record Account(
            String name,
            String type,
            String subtype,
            Double currentBalance,
            Double availableBalance,
            String currency
    ) {}
}
