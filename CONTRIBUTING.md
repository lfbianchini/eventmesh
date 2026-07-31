# Contributing

## Delivery discipline

Every change should be narrowly scoped and independently verifiable. A pull
request or commit should answer:

1. Which observable behavior changed?
2. Which automated test proves it?
3. Which failure case was considered?
4. Did an architectural tradeoff change?
5. Can a reviewer run it without private infrastructure?

Avoid adding a service, library, datastore, or deployment layer solely because
it is common in distributed systems. A change that introduces infrastructure
should state the problem, alternatives, operational cost, and rollback path in
its pull request.

## Local workflow

```bash
docker compose up -d postgres
./scripts/verify.sh
```

Run the application:

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

## Definition of done

- Acceptance criteria for the change are satisfied.
- Tests cover the success path, invalid input, authorization, and relevant
  concurrency or failure behavior.
- Public API changes are reflected in the API documentation.
- Material design tradeoffs are documented in the pull request.
- README commands work from a clean checkout.
- No benchmark number is stated without a committed, reproducible script and
  environment description.
