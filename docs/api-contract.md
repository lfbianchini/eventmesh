# D1 API contract

This contract describes implemented D1 behavior only.

## Authentication

All event endpoints require:

```http
X-EventMesh-Key: <tenant-api-key>
```

Missing, blank, expired, revoked, or unknown keys receive the same `401`
response. This avoids revealing key lifecycle or tenant existence.

## Create an event

```http
POST /v1/events
Content-Type: application/json
X-EventMesh-Key: em_dev_local_key_change_me
Idempotency-Key: checkout-order-123-v1
```

```json
{
  "type": "order.created",
  "source": "demo.checkout",
  "subject": "order-123",
  "data": {
    "amount": 4200,
    "currency": "USD"
  }
}
```

### Request fields

| Field | Required | Limit | Meaning |
|---|---:|---:|---|
| `type` | Yes | 128 | Lowercase event identifier such as `order.created` |
| `source` | Yes | 256 | Producer-defined application/component identity |
| `subject` | No | 512 | Producer-defined resource identity |
| `data` | Yes | — | Non-null JSON payload |

`Idempotency-Key` is required, tenant-scoped, non-blank, and at most 128
characters.

### First submission

Status: `201 Created`

```http
Location: /v1/events/9e7c2c9a-1a54-4b1f-b2ff-95ef06f238fd
```

```json
{
  "id": "9e7c2c9a-1a54-4b1f-b2ff-95ef06f238fd",
  "type": "order.created",
  "source": "demo.checkout",
  "subject": "order-123",
  "data": {
    "amount": 4200,
    "currency": "USD"
  },
  "receivedAt": "2026-07-30T02:00:00Z",
  "replayed": false
}
```

### Equivalent retry

Status: `200 OK`

The response contains the original event ID and `replayed: true`. JSON object
field order is ignored when comparing the request. Array order and envelope
fields remain significant.

### Conflicting retry

Status: `409 Conflict`

```json
{
  "type": "urn:eventmesh:problem:idempotency-conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "This idempotency key was already used for a different event request",
  "instance": "/v1/events",
  "code": "IDEMPOTENCY_CONFLICT",
  "path": "/v1/events",
  "timestamp": "2026-07-30T02:00:00Z"
}
```

## Get an event

```http
GET /v1/events/{eventId}
X-EventMesh-Key: <tenant-api-key>
```

- `200`: event belongs to the authenticated tenant.
- `404`: event does not exist or belongs to another tenant.
- `401`: presented key is not active.

Returning the same `404` for missing and cross-tenant resources avoids an
existence oracle.

## Error matrix

| Status | Code | Condition |
|---:|---|---|
| 400 | `INVALID_REQUEST` | Missing/oversized idempotency key |
| 400 | `VALIDATION_FAILED` | Invalid event fields |
| 400 | `MALFORMED_JSON` | Body is not valid JSON |
| 401 | `UNAUTHORIZED` | Missing or invalid tenant API key |
| 404 | `EVENT_NOT_FOUND` | Event not visible to tenant |
| 409 | `IDEMPOTENCY_CONFLICT` | Key reused with a different canonical request |

Errors use `application/problem+json` and include a stable machine-readable
`code`.

## Contract evolution

Breaking changes require a new path version. Adding optional response fields is
backward-compatible. Kafka message schema versioning begins separately in D2;
the HTTP and broker contracts must not be treated as the same representation.

