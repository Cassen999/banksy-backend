package org.example.model;

import java.time.LocalDate;

public record ScheduledDepositDto(
        String merchantName,
        String description,
        String frequency,
        LocalDate firstDate,
        LocalDate lastDate,
        LocalDate predictedNextDate,
        RecurringResponse.AmountDto averageAmount,
        RecurringResponse.AmountDto lastAmount,
        Boolean isActive,
        RecurringResponse.PersonalFinanceCategoryDto personalFinanceCategory,
        String status
) {}
