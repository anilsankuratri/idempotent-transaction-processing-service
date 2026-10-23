package com.assessment.transactions.service;

import com.assessment.transactions.model.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class TransactionProcessor {
    private final Map<String, TransactionResult> resultsByTransactionId = new ConcurrentHashMap<>();
    private final Map<String, TransactionResult> resultsByRequestId = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> balances = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSequenceByAccount = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> accountLocks = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> attemptsByTransactionId = new ConcurrentHashMap<>();
    private final Set<String> simulatedFailureUsed = ConcurrentHashMap.newKeySet();

    public TransactionResult process(TransactionRequest request) {
        ReentrantLock lock = accountLocks.computeIfAbsent(request.accountId(), ignored -> new ReentrantLock());
        lock.lock();
        try {
            TransactionResult existing = resultsByTransactionId.get(request.transactionId());
            if (existing != null && existing.status() == TransactionStatus.PROCESSED) {
                return new TransactionResult(request.transactionId(), request.requestId(), TransactionStatus.DUPLICATE,
                        "Transaction already processed; no balance change applied.", balances.getOrDefault(request.accountId(), BigDecimal.ZERO),
                        attemptsByTransactionId.getOrDefault(request.transactionId(), new AtomicInteger(1)).get());
            }
            if (resultsByRequestId.containsKey(request.requestId())) {
                TransactionResult previous = resultsByRequestId.get(request.requestId());
                return new TransactionResult(request.transactionId(), request.requestId(), TransactionStatus.DUPLICATE,
                        "Request ID was already seen; no balance change applied.", balances.getOrDefault(request.accountId(), BigDecimal.ZERO), previous.attempts());
            }

            long expectedSequence = lastSequenceByAccount.getOrDefault(request.accountId(), 0L) + 1L;
            if (request.sequenceNumber() > expectedSequence) {
                TransactionResult pending = new TransactionResult(request.transactionId(), request.requestId(), TransactionStatus.PENDING_ORDER,
                        "Waiting for sequence " + expectedSequence + " before this transaction.", balances.getOrDefault(request.accountId(), BigDecimal.ZERO), 0);
                resultsByRequestId.put(request.requestId(), pending);
                resultsByTransactionId.putIfAbsent(request.transactionId(), pending);
                return pending;
            }
            if (request.sequenceNumber() < expectedSequence) {
                TransactionResult stale = new TransactionResult(request.transactionId(), request.requestId(), TransactionStatus.FAILED,
                        "Sequence is older than the next expected sequence.", balances.getOrDefault(request.accountId(), BigDecimal.ZERO), 0);
                resultsByRequestId.put(request.requestId(), stale);
                return stale;
            }

            AtomicInteger attempts = attemptsByTransactionId.computeIfAbsent(request.transactionId(), ignored -> new AtomicInteger());
            attempts.incrementAndGet();
            TransactionResult processing = new TransactionResult(request.transactionId(), request.requestId(), TransactionStatus.PROCESSING,
                    "Processing transaction.", balances.getOrDefault(request.accountId(), BigDecimal.ZERO), attempts.get());
            resultsByRequestId.put(request.requestId(), processing);
            resultsByTransactionId.put(request.transactionId(), processing);

            // Demo hook: transaction IDs starting with RETRY simulate one transient failure.
            if (request.transactionId().startsWith("RETRY") && simulatedFailureUsed.add(request.transactionId())) {
                TransactionResult retry = new TransactionResult(request.transactionId(), request.requestId(), TransactionStatus.RETRY_PENDING,
                        "Simulated transient failure; safe to retry.", balances.getOrDefault(request.accountId(), BigDecimal.ZERO), attempts.get());
                resultsByRequestId.put(request.requestId(), retry);
                resultsByTransactionId.put(request.transactionId(), retry);
                return retry;
            }

            BigDecimal current = balances.getOrDefault(request.accountId(), BigDecimal.ZERO);
            BigDecimal updated = request.transactionType() == TransactionType.CREDIT
                    ? current.add(request.amount()) : current.subtract(request.amount());
            balances.put(request.accountId(), updated);
            lastSequenceByAccount.put(request.accountId(), request.sequenceNumber());
            TransactionResult processed = new TransactionResult(request.transactionId(), request.requestId(), TransactionStatus.PROCESSED,
                    "Transaction processed successfully.", updated, attempts.get());
            resultsByRequestId.put(request.requestId(), processed);
            resultsByTransactionId.put(request.transactionId(), processed);
            return processed;
        } finally {
            lock.unlock();
        }
    }

    public TransactionResult retry(TransactionRequest request) {
        TransactionResult current = resultsByTransactionId.get(request.transactionId());
        if (current == null || current.status() != TransactionStatus.RETRY_PENDING) {
            return current != null ? current : new TransactionResult(request.transactionId(), request.requestId(),
                    TransactionStatus.FAILED, "No retryable transaction found.", BigDecimal.ZERO, 0);
        }
        // Reuse the original request ID for an explicit retry, while allowing the retry path past request dedupe.
        resultsByRequestId.remove(request.requestId());
        return process(request);
    }

    public BigDecimal balance(String accountId) {
        return balances.getOrDefault(accountId, BigDecimal.ZERO);
    }

    public List<TransactionResult> allResults() {
        return resultsByTransactionId.values().stream()
                .sorted(Comparator.comparing(TransactionResult::transactionId))
                .collect(Collectors.toList());
    }

    public ProcessingSummary summary() {
        Map<TransactionStatus, Long> counts = resultsByTransactionId.values().stream()
                .collect(Collectors.groupingBy(TransactionResult::status, () -> new EnumMap<>(TransactionStatus.class), Collectors.counting()));
        return new ProcessingSummary(resultsByTransactionId.size(), counts);
    }
}
