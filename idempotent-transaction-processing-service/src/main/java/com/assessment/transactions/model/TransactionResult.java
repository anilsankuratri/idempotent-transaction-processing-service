package com.assessment.transactions.model;

import java.math.BigDecimal;

public record TransactionResult(
        String transactionId,
        String requestId,
        TransactionStatus status,
        String message,
        BigDecimal resultingBalance,
        int attempts
) {}
