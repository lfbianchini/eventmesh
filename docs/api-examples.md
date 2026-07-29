# API examples

These commands target the seeded local tenant created by
`./scripts/dev-up.sh`.

```bash
export EVENTMESH_URL=http://localhost:8088
export EVENTMESH_KEY=em_dev_local_key_change_me
```

## Inspect the contract

- Swagger UI: `http://localhost:8088/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8088/v3/api-docs`

The documentation UI is enabled by default for review. Set
`EVENTMESH_API_DOCS_ENABLED=false` in a deployment that should not expose it.

## Create a subscription

The local profile permits this private HTTP destination for the demo.
Non-local configuration requires a public HTTPS endpoint.

```bash
subscription="$(
  curl --fail-with-body --silent \
    --request POST "$EVENTMESH_URL/v1/subscriptions" \
    --header 'Content-Type: application/json' \
    --header "X-EventMesh-Key: $EVENTMESH_KEY" \
    --data '{
      "name": "orders-local",
      "endpointUrl": "http://subscriber:8090/webhooks",
      "eventTypes": ["order.created"],
      "signingSecret": "replace-with-at-least-32-characters"
    }'
)"

printf '%s\n' "$subscription" | jq .
```

The signing secret appears only in the create response. Later reads omit it.

## Submit and safely retry an event

```bash
event="$(
  curl --fail-with-body --silent \
    --request POST "$EVENTMESH_URL/v1/events" \
    --header 'Content-Type: application/json' \
    --header "X-EventMesh-Key: $EVENTMESH_KEY" \
    --header 'Idempotency-Key: checkout-order-123-v1' \
    --data '{
      "type": "order.created",
      "source": "demo.checkout",
      "subject": "order-123",
      "data": {"amount": 4200, "currency": "USD"}
    }'
)"

event_id="$(printf '%s' "$event" | jq -r .id)"
curl --fail-with-body --silent \
  "$EVENTMESH_URL/v1/events/$event_id" \
  --header "X-EventMesh-Key: $EVENTMESH_KEY" | jq .
```

Repeat the same `POST` with the same body and idempotency key to receive the
original event with `replayed: true`. Change the body while retaining the key to
receive `409 Conflict`.

## Inspect deliveries and attempts

```bash
curl --fail-with-body --silent \
  "$EVENTMESH_URL/v1/deliveries" \
  --header "X-EventMesh-Key: $EVENTMESH_KEY" | jq .

delivery_id="<delivery-id-from-the-list>"
curl --fail-with-body --silent \
  "$EVENTMESH_URL/v1/deliveries/$delivery_id" \
  --header "X-EventMesh-Key: $EVENTMESH_KEY" | jq .
```

The detail response contains the current scheduling projection and all immutable
HTTP attempts.

## Replay a dead-letter delivery

After correcting the subscriber endpoint or behavior:

```bash
dead_letter_delivery_id="<dead-letter-delivery-id>"
curl --fail-with-body --silent \
  --request POST \
  "$EVENTMESH_URL/v1/deliveries/$dead_letter_delivery_id/replay" \
  --header "X-EventMesh-Key: $EVENTMESH_KEY" | jq .
```

Replay creates a new pending delivery linked through `replayOfDeliveryId`; it
does not change the original dead-letter record or its attempt history.

## Verify a webhook

Use the raw request body and compute:

```text
HMAC-SHA256(secret, timestamp + "." + deliveryId + "." + exactBody)
```

Compare the lowercase hex value in `X-EventMesh-Signature` in constant time,
reject stale `X-EventMesh-Timestamp` values, and deduplicate subscriber side
effects by `X-EventMesh-Delivery`. The executable reference verifier is
[`demo/subscriber/server.py`](../demo/subscriber/server.py).
