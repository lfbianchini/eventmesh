# Kubernetes and Helm

The Helm chart deploys only the EventMesh application. PostgreSQL, Kafka,
secrets, ingress/TLS, and telemetry backends remain platform responsibilities.
This keeps the application portable across local clusters and managed services
without pretending stateful infrastructure is one-size-fits-all.

## Production-style render

```bash
helm lint deploy/helm/eventmesh
helm template eventmesh deploy/helm/eventmesh \
  --set image.repository=registry.example/eventmesh \
  --set image.tag=COMMIT_SHA \
  --set config.databaseUrl=jdbc:postgresql://DATABASE/eventmesh \
  --set config.kafkaBootstrapServers=BROKER:9092 \
  --set secrets.existingSecret=eventmesh-secrets
```

Create `eventmesh-secrets` through the platform’s secret manager with:

- `database-password`;
- `encryption-key`, a Base64-encoded random 256-bit key.

Do not enable `secrets.create` in a shared environment; that option exists only
to make the disposable local smoke test self-contained.

## Local Kind proof

Requirements: Docker, `kind`, `kubectl`, Helm, and Java 21.

```bash
./scripts/kind-smoke.sh
```

The script:

1. packages the same JAR and runtime image used by Compose;
2. creates/reuses a Kind cluster;
3. loads pinned images into the node so the test does not depend on registry
   DNS after image acquisition;
4. starts disposable PostgreSQL and Kafka development dependencies;
5. installs the Helm chart;
6. waits on readiness and submits an authenticated event.

The development dependencies use ephemeral storage and are not production
manifests.

Cleanup:

```bash
kind delete cluster --name eventmesh
```

## Container constraints

The chart enforces:

- non-root UID/GID matching the OCI image;
- dropped Linux capabilities;
- no privilege escalation;
- read-only root filesystem with a writable empty `/tmp`;
- liveness/readiness probes;
- graceful termination;
- CPU/memory requests and limits;
- external ConfigMap and Secret injection.

Scaling above one replica preserves database lease correctness. It does not by
itself prove subscriber capacity, Kafka partition capacity, or database
headroom; those require observed load tests.
