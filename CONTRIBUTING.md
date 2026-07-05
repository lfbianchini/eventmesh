# Contributing

## Delivery discipline

Every change should belong to one delivery gate in
[docs/delivery-plan.md](docs/delivery-plan.md). A pull request or commit should
answer:

1. Which observable behavior changed?
2. Which automated test proves it?
3. Which failure case was considered?
4. Did an architectural tradeoff change?
5. Can a reviewer run it without private infrastructure?

Avoid adding a service, library, datastore, or deployment layer solely because
it is common in distributed systems. New infrastructure requires an ADR that
states the problem, alternatives, operational cost, and rollback path.

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

- Acceptance criteria for the delivery gate are satisfied.
- Tests cover the success path, invalid input, authorization, and relevant
  concurrency or failure behavior.
- Public API changes are reflected in the API documentation.
- New decisions are captured in an ADR.
- README commands work from a clean checkout.
- No benchmark number is stated without a committed, reproducible script and
  environment description.
