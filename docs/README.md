# EventMesh documentation

The documentation is organized so a reviewer can move from product intent to
implementation details without reverse-engineering the codebase.

## Read in this order

1. [Project charter](project-charter.md) — problem, users, scope, and success.
2. [Delivery plan](delivery-plan.md) — independently testable milestones.
3. [Architecture](architecture.md) — boundaries, flow, and evolution.
4. [D1 API contract](api-contract.md) — endpoints and idempotency behavior.
5. [D1 data model](data-model.md) — schema, constraints, and indexes.
6. [Reliability model](reliability-model.md) — what is and is not guaranteed.
7. [Platform portability](platform-portability.md) — cloud-neutral constraints.
8. [Security model](security-model.md) — threat boundaries and key handling.
9. [Testing strategy](testing-strategy.md) — evidence at each test layer.
10. [Interview guide](interview-guide.md) — concise explanations and tradeoffs.
11. [Architecture decision records](adrs/README.md) — decisions and alternatives.

## Documentation rules

- Current behavior and future plans must not be mixed.
- A guarantee must name its boundary and failure assumptions.
- A performance claim must link to a reproducible benchmark.
- An ADR is immutable after acceptance; a later ADR supersedes it.
- Diagrams describe logical responsibilities, not necessarily separate
  deployable services.
