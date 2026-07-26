# EventMesh

EventMesh is a multi-tenant event ingestion and webhook delivery platform built
to demonstrate reliable backend engineering under real failure modes.

The project is developed as a sequence of small, independently reviewable
deliverables. Each delivery gate must have:

- a narrow behavioral contract;
- automated tests;
- a one-command local path;
- an explicit design decision record;
- evidence before any performance or reliability claim is made.

## Delivery status

**Complete:** D0 foundation through D4 operational evidence

**Next:** D5 portfolio presentation

The current implementation accepts tenant-authenticated events, persists them in
PostgreSQL with atomic idempotency semantics, and publishes versioned messages
through a transactional outbox to Kafka. Matching subscriptions receive
HMAC-signed webhooks with inspectable retries, immutable attempt history,
dead-letter state, and operator replay. Observability packaging and Kubernetes
are implemented through Prometheus, Grafana, OpenTelemetry/Tempo, and Helm. See
[the delivery plan](docs/delivery-plan.md).

## Why this shape?

EventMesh is not a collection of infrastructure keywords. It is one concrete
product with an increasingly strong reliability model:

1. accept an event durably;
2. publish accepted work without losing it;
3. deliver signed webhooks despite transient failures;
4. expose enough evidence to debug and measure the system;
5. package the same application for a laptop, CI, or Kubernetes.

The architecture and rejected alternatives are documented in
[the architecture overview](docs/architecture.md) and
[architecture decision records](docs/adrs/README.md).

## Prerequisites

- Java 21
- Docker with Compose v2

Maven does not need to be installed; the repository includes Maven Wrapper.

On macOS with Homebrew:

```bash
brew install openjdk@21
export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
```

## Run locally

```bash
./scripts/dev-up.sh
```

The script builds the JAR with Maven Wrapper, packages it into the same Java 21
runtime image used by the public Dockerfile, waits for readiness, and executes a
public-API smoke test. This path also works on networks that inject a private
TLS certificate unavailable inside build containers.

The hermetic CI/release-image path is:

```bash
docker compose up --build
```

When the application is healthy:

```bash
curl --fail-with-body \
  --request POST http://localhost:8088/v1/events \
  --header 'Content-Type: application/json' \
  --header 'X-EventMesh-Key: em_dev_local_key_change_me' \
  --header 'Idempotency-Key: demo-order-123' \
  --data '{
    "type": "order.created",
    "source": "demo.checkout",
    "subject": "order-123",
    "data": {"amount": 4200, "currency": "USD"}
  }'
```

The development key is public by design and is valid only for the seeded local
tenant. It must never be reused outside local development.

## Verify

```bash
./scripts/verify.sh
```

The verification suite includes unit tests, PostgreSQL and Kafka integration
tests through Testcontainers, a real broker pause/recovery scenario, API
behavior tests, and a coverage gate. The script discovers the active Docker
context, including Rancher Desktop and Docker Desktop.

## Run the failure/recovery demo

```bash
./scripts/demo.sh
```

The script starts a separate subscriber container, configures it to return two
planned `503` responses, registers a subscription, submits an event, and waits
for delivery to succeed. Its final JSON shows three attempts with the same
delivery ID and a valid signature on every request.

## Observe and break it safely

```bash
./scripts/observability-up.sh
./scripts/failure-broker.sh
./scripts/failure-worker-restart.sh
```

The first command provisions the operations dashboard, Prometheus, an
OpenTelemetry Collector, and Tempo. The failure drills verify that database
state survives a paused broker and a terminated worker, and print a new recovery
measurement on every run.

## Benchmark and Kubernetes proof

```bash
./scripts/benchmark.sh
./scripts/kind-smoke.sh
```

The benchmark measures durable ingress only and writes a raw k6 summary under
`reports/`. The Kind smoke test loads the same local OCI image, installs the
application-only Helm chart, waits for readiness, and submits an authenticated
event. See [the reviewed local evidence](docs/evidence/d4-local-2026-07-29.md)
for measured results and limitations.

## Documentation map

Start at [docs/README.md](docs/README.md). For interview preparation, use
[docs/interview-guide.md](docs/interview-guide.md).

## Project status

EventMesh is under active development. Only completed delivery gates are
described as implemented; planned capabilities are labeled explicitly.
