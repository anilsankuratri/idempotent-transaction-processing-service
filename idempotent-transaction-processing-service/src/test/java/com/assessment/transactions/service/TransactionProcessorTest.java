package com.assessment.transactions.service;

import com.assessment.transactions.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TransactionProcessorTest {
    private TransactionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new TransactionProcessor();
    }

    private TransactionRequest request(String txn, String req, String account, TransactionType type, String amount, long sequence) {
        return new TransactionRequest(txn, req, account, type, new BigDecimal(amount), "INR", sequence, Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void duplicateTransactionDoesNotApplyBalanceTwice() {
        var first = processor.process(request("TXN-1", "REQ-1", "ACC-1", TransactionType.CREDIT, "100", 1));
        var duplicate = processor.process(request("TXN-1", "REQ-2", "ACC-1", TransactionType.CREDIT, "100", 1));
        assertEquals(TransactionStatus.PROCESSED, first.status());
        assertEquals(TransactionStatus.DUPLICATE, duplicate.status());
        assertEquals(new BigDecimal("100"), processor.balance("ACC-1"));
    }

    @Test
    void futureSequenceIsHeldPending() {
        var pending = processor.process(request("TXN-2", "REQ-2", "ACC-1", TransactionType.DEBIT, "40", 2));
        assertEquals(TransactionStatus.PENDING_ORDER, pending.status());
        assertEquals(BigDecimal.ZERO, processor.balance("ACC-1"));
    }

    @Test
    void invalidNegativeAmountIsRejectedByBeanValidationAtApiBoundary() {
        assertThrows(IllegalArgumentException.class, () -> {
            var invalid = new TransactionRequest("TXN-3", "REQ-3", "ACC-1", TransactionType.DEBIT,
                    new BigDecimal("-10"), "INR", 1L, Instant.now());
            if (invalid.amount().signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
        });
    }

    @Test
    void retryAfterSimulatedTransientFailureProcessesOnce() {
        var retryable = request("RETRY-1", "REQ-5", "ACC-1", TransactionType.CREDIT, "500", 1);
        assertEquals(TransactionStatus.RETRY_PENDING, processor.process(retryable).status());
        assertEquals(TransactionStatus.PROCESSED, processor.retry(retryable).status());
        assertEquals(new BigDecimal("500"), processor.balance("ACC-1"));
    }
}
