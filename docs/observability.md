# Observability

EventMesh exposes durable state, process activity, and distributed traces through
open telemetry contracts. The local stack is optional; the application remains
deployable without Grafana, Prometheus, or Tempo.

## Start the stack

```bash
./scripts/observability-up.sh
```

The script builds the application, starts the Compose stack, waits for every
service, and checks each public endpoint.

| Tool | Local URL | Purpose |
|---|---|---|
| Grafana | `http://localhost:3000/d/eventmesh-operations/eventmesh-operations` | Provisioned operations dashboard |
| Prometheus | `http://localhost:9090` | Metrics queries and scrape health |
| Tempo | Grafana Explore → Tempo | Trace search |
| Actuator | `http://localhost:8088/actuator/prometheus` | Raw application metrics |

The local stack pins Prometheus 3.13.0, Grafana 13.1.0, OpenTelemetry Collector
0.153.0, and Tempo 2.10.5. The application exports OTLP over HTTP to the
collector. No vendor-specific telemetry API appears in application code.

## Operational dashboard

The provisioned dashboard answers the five first-response questions directly:

| Question | Prometheus series |
|---|---|
| How many events were durably accepted? | `eventmesh_state_events` |
| How many messages await Kafka publication? | `eventmesh_state_outbox{status="pending"}` |
| How many deliveries succeeded? | `eventmesh_state_deliveries{status="succeeded"}` |
| How many deliveries are retrying? | `eventmesh_state_deliveries{status="retrying"}` |
| How many deliveries exhausted/permanently failed? | `eventmesh_state_deliveries{status="dead_letter"}` |

It also graphs process-lifetime outbox and webhook outcomes:

- `eventmesh_outbox_publish_total{outcome=...}`;
- `eventmesh_webhook_delivery_total{outcome=...}`;
- `eventmesh_outbox_publish_duration_seconds`.

## Why both gauges and counters?

Counters answer "what has this process done since it started?" They reset after
a restart and therefore cannot answer "what work exists right now?"

EventMesh snapshots authoritative PostgreSQL counts into gauges every five
seconds. A restarted instance reconstructs those values from durable state. The
tradeoff is bounded staleness and one small aggregate query per interval. A
large installation should replace whole-table counts with incremental
aggregation, database statistics, or a dedicated metrics projection.

Metric tags use a fixed status vocabulary. Tenant ID, event ID, and delivery ID
are intentionally excluded to prevent cardinality growth.

## Tracing

Tracing is disabled by default. The observability overlay enables it with 100%
sampling for a short local demonstration:

```text
EVENTMESH_TRACING_ENABLED=true
EVENTMESH_TRACE_SAMPLING_PROBABILITY=1.0
EVENTMESH_OTLP_TRACING_ENDPOINT=http://otel-collector:4318/v1/traces
```

Spring observations create traces for HTTP requests, Kafka operations, and
scheduled relay/worker executions. Production should use a lower sampling rate
and an external OTLP collector. The trace backend is not part of EventMesh's
durability path; telemetry loss must not reject events or deliveries.

## Stop

```bash
docker compose \
  -f compose.yml \
  -f compose.local.yml \
  -f compose.observability.yml \
  --profile observability down
```

Add `--volumes` only when intentionally deleting local Prometheus, Grafana,
Tempo, and PostgreSQL data.
