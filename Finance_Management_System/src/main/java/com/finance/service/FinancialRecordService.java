package com.finance.service;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.finance.dto.FinancialRecordRequest;
import com.finance.dto.FinancialRecordResponse;
import com.finance.entity.FinancialRecord;
import com.finance.entity.User;
import com.finance.enums.RecordType;
import com.finance.exception.BadRequestException;
import com.finance.exception.ResourceNotFoundException;
import com.finance.repo.FinancialRecordRepository;
import com.finance.repo.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FinancialRecordService {
	
	private final FinancialRecordRepository recordRepository;
    private final UserRepository userRepository;

    public FinancialRecordResponse create(FinancialRecordRequest request) {
        User currentUser = getCurrentUser();

        RecordType type;
        try {
            type = RecordType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid type. Must be INCOME or EXPENSE");
        }

        FinancialRecord record = FinancialRecord.builder()
                .amount(request.getAmount())
                .type(type)
                .category(request.getCategory())
                .date(request.getDate())
                .description(request.getDescription())
                .createdBy(currentUser)
                .deleted(false)
                .build();

        return toResponse(recordRepository.save(record));
    }

    public Page<FinancialRecordResponse> getAll(String type, String category,
                                                LocalDate startDate, LocalDate endDate,
                                                int page, int size, String sortBy, String sortDir) {
        RecordType recordType = null;
        if (type != null && !type.isBlank()) {
            try {
                recordType = RecordType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid type. Must be INCOME or EXPENSE");
            }
        }

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        return recordRepository
                .findWithFilters(recordType, category, startDate, endDate, pageable)
                .map(this::toResponse);
    }

    public FinancialRecordResponse getById(Long id) {
        return toResponse(findById(id));
    }

    public FinancialRecordResponse update(Long id, FinancialRecordRequest request) {
        FinancialRecord record = findById(id);

        RecordType type;
        try {
            type = RecordType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid type. Must be INCOME or EXPENSE");
        }

        record.setAmount(request.getAmount());
        record.setType(type);
        record.setCategory(request.getCategory());
        record.setDate(request.getDate());
        record.setDescription(request.getDescription());

        return toResponse(recordRepository.save(record));
    }


    public void delete(Long id) {
        FinancialRecord record = findById(id);
        record.setDeleted(true);
        recordRepository.save(record);
    }

    private FinancialRecord findById(Long id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Record not found with id: " + id));
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
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
