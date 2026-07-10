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

Testcontainers starts the same major PostgreSQL version used by Compose. D1
integration tests cover:

- migrations from an empty database;
- successful insertion and lookup;
- idempotent replay;
- conflicting replay;
- authentication;
- cross-tenant isolation;
- concurrent same-key requests.

D2 will add a real Kafka-compatible container. D3 will add a deterministic fake
HTTP subscriber.

### Container smoke tests

Build the production image, start it through Compose, wait for readiness, and
exercise the public API. This catches packaging and configuration defects that
in-process tests cannot.

The public `Dockerfile` performs a hermetic multi-stage build. The local
`Dockerfile.local` copies a Maven-Wrapper-built JAR into the identical runtime
base, allowing development behind a TLS-inspecting network without committing a
private certificate or weakening Maven TLS verification.

### Failure tests

Beginning in D2, scripts will terminate dependencies or workers at controlled
points and assert eventual recovery. Each scenario records:

- initial state;
- injected failure;
- expected invariant;
- maximum observation window;
- final state.

### Benchmarks

D4 k6 tests will publish:

- commit SHA;
- hardware/container resource limits;
- dataset and concurrency;
- warm-up and measurement duration;
- p50/p95/p99 latency;
- accepted/delivered throughput;
- error rate.

Benchmark results are not regression tests unless the environment is controlled.

## CI gate

`./scripts/verify.sh` is the authoritative local code gate. It selects Java 21
from Homebrew when necessary and exposes the active Docker context to
Testcontainers. CI invokes the underlying `./mvnw verify` command in its
standard Docker environment. GitHub Actions additionally builds the OCI image.
A green test badge is useful only if the commands also work from a clean local
checkout.
