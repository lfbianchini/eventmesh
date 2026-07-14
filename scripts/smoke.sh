#!/bin/sh

set -eu

base_url="${EVENTMESH_BASE_URL:-http://localhost:8088}"
api_key="${EVENTMESH_DEV_API_KEY:-em_dev_local_key_change_me}"

curl --fail --silent --show-error \
  "$base_url/actuator/health/readiness" |
  grep -q '"status":"UP"'

curl --fail --silent --show-error \
  --request POST "$base_url/v1/events" \
  --header 'Content-Type: application/json' \
  --header "X-EventMesh-Key: $api_key" \
  --header 'Idempotency-Key: smoke-d2-order-123' \
  --data '{
    "type": "order.created",
    "source": "smoke.test",
    "subject": "order-123",
    "data": {"amount": 4200, "currency": "USD"}
  }' |
  grep -q '"type":"order.created"'

echo "EventMesh smoke test passed."
