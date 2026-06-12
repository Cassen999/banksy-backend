package org.example.model;

import java.time.LocalDate;
import java.util.List;

public record RecurringResponse(
        List<TransactionStreamDto> inflowStreams,
        List<TransactionStreamDto> outflowStreams,
        List<RelinkSignal> relinkRequired
) {
    public record TransactionStreamDto(
            String accountId,
            String streamId,
            String merchantName,
            String description,
            String frequency,
            LocalDate firstDate,
            LocalDate lastDate,
            LocalDate predictedNextDate,
            AmountDto averageAmount,
            AmountDto lastAmount,
            Boolean isActive,
            PersonalFinanceCategoryDto personalFinanceCategory,
            String status
    ) {}

    public record AmountDto(Double amount, String isoCurrencyCode) {}

    public record PersonalFinanceCategoryDto(String primary, String detailed) {}
}
