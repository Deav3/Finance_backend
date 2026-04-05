package com.finance.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardSummaryResponse {
	
	private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netBalance;
    private Map<String, BigDecimal> categoryBreakdown;
    private List<MonthlyTrend> monthlyTrends;
    private List<FinancialRecordResponse> recentTransactions;

    @Data
    @Builder
    public static class MonthlyTrend {
        private int year;
        private int month;
        private BigDecimal income;
        private BigDecimal expense;
    }

}
