# Design Note

## Architecture
A Spring Boot REST controller validates incoming requests and delegates business processing to `TransactionProcessor`. The processor owns idempotency checks, ordering decisions, retry simulation, account balance updates, and summary reporting. The input adapter is separate from business logic so a queue/file adapter can replace the REST endpoint later.

## Idempotency and duplicate detection
`transactionId` represents the business transaction and is the primary deduplication key. A processed transaction replay returns `DUPLICATE` and does not mutate the balance. `requestId` independently detects replayed request identifiers. In production these keys need durable database uniqueness constraints to survive restarts and multiple service instances.

## Out-of-order strategy
The implementation uses per-account sequence numbers. The expected sequence is one greater than the last processed sequence. Future events are retained as `PENDING_ORDER`; stale sequence values are rejected. The current assessment version demonstrates the policy but does not automatically drain pending events. That is a known limitation and should be addressed before production.

## Retry and failure handling
Transaction IDs prefixed with `RETRY` simulate a single transient error. The first attempt returns `RETRY_PENDING`; a retry endpoint allows a later attempt. The transaction effect is applied only after the simulated failure has been cleared. A production implementation should use configurable retry limits, exponential backoff, persisted attempt counts, and `FAILED` after exhaustion.

## Observability
The API exposes per-transaction status, message, attempt count, resulting balance, and a summary count by status. Production should add structured logs, trace/correlation IDs, metrics, dashboards, and alerts while avoiding sensitive customer data.

## Consistency and concurrency
A per-account lock protects balance and sequence updates within one JVM. It is not a distributed lock and is lost on restart. Production should use database transactions, uniqueness constraints, and a row-level locking or optimistic concurrency strategy. Ledger entry, balance update, and status change must commit atomically.

## Test strategy
Tests cover duplicate replay, future sequence handling, invalid amount behavior, and retry safety. Before submission, run `mvn clean test` and add concurrency tests plus an API-level validation test.

## AI-assisted development and ownership
AI was used to accelerate scaffolding and documentation. The candidate must inspect the code, execute the tests, validate API behavior, and understand limitations rather than treating generated code as automatically correct.
