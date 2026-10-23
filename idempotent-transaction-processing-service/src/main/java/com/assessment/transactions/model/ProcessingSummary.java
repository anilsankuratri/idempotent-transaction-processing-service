package com.assessment.transactions.model;

import java.util.Map;

public record ProcessingSummary(long total, Map<TransactionStatus, Long> counts) {}
