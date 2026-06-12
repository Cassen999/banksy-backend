package org.example.model;

import java.util.List;

public record MonthlyGlanceResponse(List<DailyTotal> dailyTotals, List<RelinkSignal> relinkRequired) {

    public record DailyTotal(String transactionDate, double total) {}
}
