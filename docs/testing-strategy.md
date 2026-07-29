# Testing strategy

Tests are evidence for delivery-gate claims, not an afterthought.

## Test pyramid

### Unit tests

Use for deterministic policies without infrastructure:

- canonical request hashing;
- API-key hashing;
- retry classification and backoff in D3;
- signature creation/verification in D3.

They should be fast enough to run continuously.

### Slice/API tests

Use Spring MVC tests for:

- validation;
- status codes;
- error representation;
- headers and serialization.

If a behavior depends on a database constraint, it belongs in an integration
test instead of a mocked repository test.

### Integration tests

Testcontainers starts the same PostgreSQL and Kafka image versions used by
Compose. Integration tests cover:

- migrations from an empty database;
- successful insertion and lookup;
- idempotent replay;
- conflicting replay;
- authentication;
- cross-tenant isolation;
- concurrent same-key requests.

D2 also covers:

- one outbox intent for an idempotent ingress replay;
- stable message identity across a failed and recovered publisher;
- the exact versioned envelope read from a real Kafka broker;
- a paused Kafka container retaining pending work and publishing after recovery.

D3 adds a deterministic fake HTTP subscriber.

D3 coverage includes:

- encrypted-secret create/read behavior;
- subscription event-type filtering;
- repeated accepted-message fan-out producing one initial delivery;
- exact HMAC verification by an independent HTTP handler;
- `500 → 429 → 204` retry and success history;
- permanent `400` dead-lettering;
- replay as a new delivery while the original remains unchanged;
- public-mode SSRF URL rejection.

### Container smoke tests

Build the production image, start it through Compose, wait for readiness, and
exercise the public API. This catches packaging and configuration defects that
in-process tests cannot.

The public `Dockerfile` performs a hermetic multi-stage build. The local
`Dockerfile.local` copies a Maven-Wrapper-built JAR into the identical runtime
base, allowing development behind a TLS-inspecting network without committing a
private certificate or weakening Maven TLS verification.

The local seed is a `V1000` migration loaded only by the `local` profile. That
profile enables Flyway out-of-order execution so an existing development volume
can still receive later product migrations such as `V2` and `V3`. Production
loads only `db/migration` and retains strict ordering.

### Failure tests

Beginning in D2, tests terminate or pause dependencies at controlled points and
assert eventual recovery. Each scenario records:

- initial state;
- injected failure;
- expected invariant;
- maximum observation window;
- final state.

D4 makes the drills executable:

- `failure-broker.sh` pauses Kafka, confirms an unpublished durable outbox row,
  unpauses Kafka, and measures recovery;
- `failure-worker-restart.sh` forces a retry, terminates the application,
  restores the subscriber, and measures completion after restart.

### Benchmarks

D4 k6 tests publish:

- commit SHA;
- hardware/container resource limits;
- dataset and concurrency;
- warm-up and measurement duration;
- p50/p95/p99 latency;
- accepted throughput and resulting durable backlog;
- error rate.

Benchmark results are not regression tests unless the environment is controlled.
The benchmark contract and reviewed local run are documented in
[benchmarks.md](benchmarks.md) and
[the D4 evidence report](evidence/d4-local-2026-07-29.md).

### Observability and deployment tests

D4 adds:

- an integration assertion against the real Prometheus Actuator endpoint;
- Compose configuration validation and live Prometheus target checks;
- provisioned Grafana dashboard discovery;
- Tempo trace search after an observed HTTP request;
- Helm lint and render validation;
- a Kind smoke test using the same image as Compose.

D5 adds:

- an integration assertion for the generated OpenAPI title, headline paths, and
  API-key security scheme;
- CI validation of all Compose overlays and the Helm chart;
- a self-checking public-API demonstration whose output is also the source of
  the repository walkthrough.

### Compose demonstration

`./scripts/demo.sh` adds the profile-scoped Python subscriber to the normal
PostgreSQL/Kafka/application stack. The subscriber deliberately fails twice,
verifies signature freshness and bytes independently, and exposes the received
attempts. The script asserts the attempt count, signature verdicts, stable
delivery identity, and final success before exiting zero. This is a
human-readable system proof, not a substitute for the deterministic integration
suite.

## CI gate

`./scripts/verify.sh` is the authoritative local code gate. It selects Java 21
from Homebrew when necessary and exposes the active Docker context to
Testcontainers. CI invokes the underlying `./mvnw verify` command in its
standard Docker environment. GitHub Actions additionally validates Compose and
Helm packaging, builds the OCI image, and uploads test and coverage artifacts.
A green test badge is useful only if the commands also work from a clean local
checkout.
