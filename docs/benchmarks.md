# Benchmark methodology

The benchmark measures authenticated, durable ingress: HTTP parsing,
authentication, canonical hashing, one PostgreSQL event insert, and one
transactional outbox insert. It does **not** claim end-to-end webhook throughput.

## Reproduce

```bash
./scripts/benchmark.sh
```

Defaults:

- k6 2.0.0 in a pinned container;
- 10 constant virtual users;
- 20-second measurement;
- a unique idempotency key per request;
- `201 Created` required for every check;
- less than 1% failed requests;
- p95 below 500 ms.

Override the load without editing the script:

```bash
EVENTMESH_BENCHMARK_VUS=25 \
EVENTMESH_BENCHMARK_DURATION=60s \
./scripts/benchmark.sh
```

The raw k6 summary is written to `reports/k6/summary.json` and is intentionally
ignored by Git. Reviewed measurements belong in `docs/evidence/` with the exact
environment.

## Interpretation

The default relay drains at a deliberately conservative bounded rate. An ingress
load test can therefore grow the durable outbox backlog even while every API
request succeeds. That is evidence of queue decoupling, not proof that the
whole system sustains the ingress rate indefinitely.

For an end-to-end capacity claim, measure accepted and completed deliveries over
the same interval, hold subscriber latency constant, and report the maximum
stable rate at which both outbox and delivery backlogs remain bounded.

Local laptop results are portfolio evidence and a regression baseline, not a
production capacity forecast.
