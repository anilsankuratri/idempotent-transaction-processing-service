# Idempotent Background Transaction Processing Service

A Java 17 / Spring Boot REST service demonstrating idempotent transaction processing, duplicate detection, sequence-based ordering, simulated transient failures, retries, and a status summary. This is a lightweight assessment implementation using in-memory state; it is not production persistence.

## Requirements
- JDK 17+
- Maven 3.8+

## Run
```bash
mvn clean test
mvn spring-boot:run
```
Service base URL: `http://localhost:8080`

## API
### Submit a transaction
`POST /api/transactions`
```json
{
  "transactionId": "TXN-2001",
  "requestId": "REQ-1",
  "accountId": "ACC-201",
  "transactionType": "CREDIT",
  "amount": 100,
  "currency": "INR",
  "sequenceNumber": 1,
  "eventTime": "2026-10-22T10:00:00Z"
}
```

### Retry a transaction
`POST /api/transactions/RETRY-500/retry` with the same transaction JSON and transactionId `RETRY-500`. IDs beginning `RETRY` simulate one transient failure on the first attempt, then succeed on retry.

### Read processing report
- `GET /api/transactions` — results
- `GET /api/transactions/summary` — count by status
- `GET /api/transactions/accounts/ACC-201/balance` — current in-memory balance

## Design decisions
- **Idempotency:** transactionId is the business idempotency key. Once processed, a replay returns `DUPLICATE` without applying the balance effect again. requestId also detects repeated request IDs.
- **Concurrency:** a per-account `ReentrantLock` serializes changes for an account. Concurrent requests for the same account cannot update its balance at the same time.
- **Ordering:** sequence-based policy. The next expected sequence is `lastProcessedSequence + 1`. A future sequence is recorded as `PENDING_ORDER`; an old sequence is rejected as `FAILED`. This demo does not automatically drain pending events after a missing sequence arrives; a production version should persist and reprocess pending events.
- **Retry:** transactions whose ID starts with `RETRY` fail transiently once and return `RETRY_PENDING`. Call the retry endpoint to retry; the simulated failure is consumed once.
- **Consistency:** the in-memory update is performed under the account lock. A production implementation should use a database transaction and unique constraints.
- **Validation:** Bean Validation rejects missing fields, non-positive amounts, and invalid sequence numbers at the REST boundary.
- **Observability:** each result contains status, message, resulting balance, and attempts; summary endpoint counts statuses.

## Sample scenario from the assessment
1. Send `TXN-2002` sequence 2 first: expect `PENDING_ORDER`.
2. Send `TXN-2001` sequence 1: expect `PROCESSED`.
3. Replay `TXN-2001` with a new requestId: expect `DUPLICATE`.
4. Send an amount of `-10`: API validation returns HTTP 400.
5. Send a `RETRY-2005` transaction: first call gives `RETRY_PENDING`, then call retry endpoint to process.

## Production hardening / trade-offs
- Replace in-memory maps with a relational database and enforce unique constraints on transactionId and requestId.
- Use `@Transactional` to atomically write ledger entry, balance, and processing status.
- Use optimistic/pessimistic locking or account-row locking for cross-instance concurrency; JVM locks alone do not work across replicas.
- Persist pending sequences and automatically drain them when gaps close.
- Add bounded retries with exponential backoff, retry exhaustion to `FAILED`, and a dead-letter queue.
- Use an outbox pattern for exactly-once business side effects across a database and message broker.
- Add structured logs, correlation IDs, Micrometer metrics, health checks, and alerting.
- Add authentication/authorization, rate limits, secrets management, and currency/account business rules.
- Consider the domain trade-off: strict per-account sequence ordering can block later transactions when a sequence is missing.

## AI-assisted development note
AI assistance was used to scaffold the project and draft tests/documentation. Before submission, run the tests locally, review the behavior, add any missing tests, and be prepared to explain each design decision and limitation.
