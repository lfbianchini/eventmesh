# Operations runbook

This runbook starts with durable state. Logs and traces explain a symptom, but
PostgreSQL records determine whether work is pending, retrying, or terminal.

## First checks

```bash
curl --fail http://localhost:8088/actuator/health/readiness
curl --fail http://localhost:8088/actuator/prometheus
docker compose ps
```

Then open the EventMesh Grafana dashboard and compare:

- pending outbox messages;
- pending and retrying deliveries;
- dead-letter deliveries;
- recent failure counters and trace spans.

## Symptom matrix

| Symptom | Likely boundary | Confirm | Safe first action |
|---|---|---|---|
| API is not ready | database, migration, or process startup | application logs and readiness details | restore PostgreSQL connectivity; do not bypass Flyway |
| Accepted count rises; outbox pending rises | Kafka unavailable or relay underprovisioned | broker health, outbox failure counter, relay traces | restore Kafka; pending rows recover automatically |
| Outbox is clear; delivery pending rises | consumer or delivery worker unavailable | Kafka consumer and scheduled-worker traces | restore/restart the worker |
| Retrying rises | subscriber returns transient status or times out | immutable attempt status/error | contact subscriber owner; retries are automatic |
| Dead-letter rises | permanent `4xx` or retry exhaustion | delivery details and attempt ledger | correct endpoint/subscriber, then use explicit replay |
| Signatures fail | secret mismatch, timestamp skew, or changed bytes | subscriber verification logs and exact request headers | rotate/recreate subscription; never log the secret |

## Broker outage

Expected behavior:

1. ingress still commits the event and outbox row;
2. publication attempts fail without deleting the row;
3. after Kafka returns, the same logical message is published;
4. a duplicate remains possible if Kafka acknowledged immediately before worker
   failure.

Reproduce:

```bash
./scripts/failure-broker.sh
```

The script always attempts to unpause Kafka through a shell trap.

## Worker restart

Expected behavior:

1. the delivery remains `PENDING` or `RETRYING` in PostgreSQL;
2. an in-flight claim becomes recoverable after its lease expires;
3. a due delivery succeeds after the process restarts;
4. immutable attempts remain attached to the same delivery ID.

Reproduce:

```bash
./scripts/failure-worker-restart.sh
```

## Dead-letter replay

Inspect before replay:

```bash
curl --fail \
  --header 'X-EventMesh-Key: em_dev_local_key_change_me' \
  http://localhost:8088/v1/deliveries/DELIVERY_ID
```

After fixing the subscriber:

```bash
curl --fail \
  --request POST \
  --header 'X-EventMesh-Key: em_dev_local_key_change_me' \
  http://localhost:8088/v1/deliveries/DELIVERY_ID/replay
```

Replay creates a new linked delivery. It never resets or deletes the original
attempt history.

## Backlog response

Do not delete pending rows to make a dashboard green.

1. restore the failed dependency;
2. verify failure rates stop increasing;
3. watch backlog slope, not only absolute size;
4. increase bounded batch/concurrency only after checking database, Kafka, and
   subscriber capacity;
5. preserve IDs and history during any manual repair.

The default single-process relay claims at most 50 rows per poll and the webhook
worker at most 25. Those conservative local values favor understandable
behavior over benchmark throughput.

## Secret and key incidents

- API keys are stored only as SHA-256 hashes and can be revoked in
  `tenant_api_keys`.
- Webhook signing secrets are recoverable AES-256-GCM ciphertext. Compromise of
  the master key requires rotating the master key and every affected
  subscription secret.
- Never copy the public local key or local AES key into a shared environment.
- Production webhook egress should also be restricted by network policy or an
  egress proxy.
