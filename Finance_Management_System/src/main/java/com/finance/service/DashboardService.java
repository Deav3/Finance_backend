package com.finance.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finance.dto.DashboardSummaryResponse;
import com.finance.dto.FinancialRecordResponse;
import com.finance.entity.FinancialRecord;
import com.finance.enums.RecordType;
import com.finance.repo.FinancialRecordRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) 
public class DashboardService {
	
	private final FinancialRecordRepository recordRepository;

    public DashboardSummaryResponse getSummary() {
        BigDecimal totalIncome = recordRepository.getTotalIncome();
        BigDecimal totalExpense = recordRepository.getTotalExpense();
        BigDecimal netBalance = totalIncome.subtract(totalExpense);

        // Category breakdown
        Map<String, BigDecimal> categoryBreakdown = new LinkedHashMap<>();
        for (Object[] row : recordRepository.getCategoryBreakdown()) {
            categoryBreakdown.put((String) row[0], (BigDecimal) row[1]);
        }

        // Monthly trends
        Map<String, DashboardSummaryResponse.MonthlyTrend> trendMap = new LinkedHashMap<>();
        for (Object[] row : recordRepository.getMonthlyTrends()) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            RecordType type = (RecordType) row[2];
            BigDecimal amount = (BigDecimal) row[3];

            String key = year + "-" + month;
            trendMap.computeIfAbsent(key, k ->
                    DashboardSummaryResponse.MonthlyTrend.builder()
                            .year(year).month(month)
                            .income(BigDecimal.ZERO).expense(BigDecimal.ZERO)
                            .build()
            );

            DashboardSummaryResponse.MonthlyTrend trend = trendMap.get(key);
            if (type == RecordType.INCOME) trend.setIncome(amount);
            else trend.setExpense(amount);
        }

        // Recent transactions
        List<FinancialRecordResponse> recent = recordRepository
                .findTop5ByDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return DashboardSummaryResponse.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netBalance(netBalance)
                .categoryBreakdown(categoryBreakdown)
                .monthlyTrends(new ArrayList<>(trendMap.values()))
                .recentTransactions(recent)
                .build();
    }

    private FinancialRecordResponse toResponse(FinancialRecord r) {
        return FinancialRecordResponse.builder()
                .id(r.getId())
                .amount(r.getAmount())
                .type(r.getType().name())
                .category(r.getCategory())
                .date(r.getDate())
                .description(r.getDescription())
                .createdBy(r.getCreatedBy().getUsername())
                .createdAt(r.getCreatedAt())
                .build();
    }

}
