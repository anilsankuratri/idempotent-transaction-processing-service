package com.assessment.transactions.controller;

import com.assessment.transactions.model.*;
import com.assessment.transactions.service.TransactionProcessor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionProcessor processor;

    public TransactionController(TransactionProcessor processor) {
        this.processor = processor;
    }

    @PostMapping
    public TransactionResult submit(@Valid @RequestBody TransactionRequest request) {
        return processor.process(request);
    }

    @PostMapping("/{transactionId}/retry")
    public TransactionResult retry(@PathVariable String transactionId, @Valid @RequestBody TransactionRequest request) {
        if (!transactionId.equals(request.transactionId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path transactionId must match request body.");
        }
        return processor.retry(request);
    }

    @GetMapping
    public List<TransactionResult> all() {
        return processor.allResults();
    }

    @GetMapping("/summary")
    public ProcessingSummary summary() {
        return processor.summary();
    }

    @GetMapping("/accounts/{accountId}/balance")
    public BigDecimal balance(@PathVariable String accountId) {
        return processor.balance(accountId);
    }
}
