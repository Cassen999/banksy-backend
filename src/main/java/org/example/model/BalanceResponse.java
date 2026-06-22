package org.example.model;

import java.util.List;

public record BalanceResponse(List<Account> accounts, List<RelinkSignal> relinkRequired) {

    public record Account(
            String accountId,
            String name,
            String type,
            String subtype,
            Double currentBalance,
            Double availableBalance,
            String currency,
            String institutionName,
            String customName
    ) {}
}
