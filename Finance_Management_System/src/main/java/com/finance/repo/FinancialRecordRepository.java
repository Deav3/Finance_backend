package com.finance.repo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.finance.entity.FinancialRecord;
import com.finance.enums.RecordType;

@Repository
public interface FinancialRecordRepository extends JpaRepository<FinancialRecord, Long> {
	
	@Query("""
	        SELECT f FROM FinancialRecord f
	        WHERE (:type IS NULL OR f.type = :type)
	        AND (:category IS NULL OR LOWER(f.category) LIKE LOWER(CONCAT('%', :category, '%')))
	        AND (:startDate IS NULL OR f.date >= :startDate)
	        AND (:endDate IS NULL OR f.date <= :endDate)
	        AND f.deleted = false
	    """)
	    Page<FinancialRecord> findWithFilters(
	            @Param("type") RecordType type,
	            @Param("category") String category,
	            @Param("startDate") LocalDate startDate,
	            @Param("endDate") LocalDate endDate,
	            Pageable pageable
	    );

	    // Dashboard totals
	    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.type = 'INCOME' AND f.deleted = false")
	    BigDecimal getTotalIncome();

	    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.type = 'EXPENSE' AND f.deleted = false")
	    BigDecimal getTotalExpense();

	    // Category breakdown
	    @Query("SELECT f.category, COALESCE(SUM(f.amount), 0) FROM FinancialRecord f WHERE f.deleted = false GROUP BY f.category")
	    List<Object[]> getCategoryBreakdown();

	    // Monthly trends
	    @Query("""
	        SELECT YEAR(f.date), MONTH(f.date), f.type, COALESCE(SUM(f.amount), 0)
	        FROM FinancialRecord f
	        WHERE f.deleted = false
	        GROUP BY YEAR(f.date), MONTH(f.date), f.type
	        ORDER BY YEAR(f.date), MONTH(f.date)
	    """)
	    List<Object[]> getMonthlyTrends();

	    // Recent records
	    List<FinancialRecord> findTop5ByDeletedFalseOrderByCreatedAtDesc();
}
