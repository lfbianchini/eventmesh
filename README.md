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

**Complete:** D0 repository foundation and D1 durable ingress  
**Next:** D2 transactional outbox and Kafka publication

The current implementation accepts tenant-authenticated events, persists them in
PostgreSQL, and provides atomic idempotency semantics. Kafka publication,
webhook delivery, retries, and Kubernetes are intentionally later deliverables.
See [the delivery plan](docs/delivery-plan.md).

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

The verification suite includes unit tests, PostgreSQL integration tests through
Testcontainers, API behavior tests, and a coverage gate. The script discovers
the active Docker context, including Rancher Desktop and Docker Desktop.

## Documentation map

Start at [docs/README.md](docs/README.md). For interview preparation, use
[docs/interview-guide.md](docs/interview-guide.md).

## Project status

EventMesh is under active development. Only completed delivery gates are
described as implemented; planned capabilities are labeled explicitly.
