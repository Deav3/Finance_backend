package com.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FinancialRecordResponse {
	
	 	private Long id;
	    private BigDecimal amount;
	    private String type;
	    private String category;
	    private LocalDate date;
	    private String description;
	    private String createdBy;
	    private LocalDateTime createdAt;

}
