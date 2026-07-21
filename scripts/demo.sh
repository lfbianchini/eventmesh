#!/bin/sh

set -eu

base_url="${EVENTMESH_BASE_URL:-http://localhost:8088}"
subscriber_url="${EVENTMESH_SUBSCRIBER_URL:-http://127.0.0.1:8090}"
api_key="${EVENTMESH_DEV_API_KEY:-em_dev_local_key_change_me}"
signing_secret="eventmesh-demo-signing-secret-change-me-32"
run_id="$(date +%s)-$$"

./scripts/dev-up.sh
docker compose -f compose.yml -f compose.local.yml --profile demo \
  up --build --detach --wait subscriber

subscriber_ready=false
for readiness_attempt in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
  if curl --fail --silent "$subscriber_url/health" >/dev/null 2>&1; then
    subscriber_ready=true
    break
  fi
  sleep 0.25
done
if [ "$subscriber_ready" != "true" ]; then
  echo "Demo subscriber did not become reachable from the host." >&2
  exit 1
fi

curl --fail --silent --show-error \
  --request POST "$subscriber_url/reset?failures=2" >/dev/null

curl --fail --silent --show-error \
  --request POST "$base_url/v1/subscriptions" \
  --header 'Content-Type: application/json' \
  --header "X-EventMesh-Key: $api_key" \
  --data "{
    \"name\": \"recruiter-demo-$run_id\",
    \"endpointUrl\": \"http://subscriber:8090/webhooks\",
    \"eventTypes\": [\"order.created\"],
    \"signingSecret\": \"$signing_secret\"
  }" >/dev/null

event_response="$(
  curl --fail --silent --show-error \
    --request POST "$base_url/v1/events" \
    --header 'Content-Type: application/json' \
    --header "X-EventMesh-Key: $api_key" \
    --header "Idempotency-Key: recruiter-demo-$run_id" \
    --data '{
      "type": "order.created",
      "source": "recruiter.demo",
      "subject": "order-demo",
      "data": {"amount": 4200, "currency": "USD"}
    }'
)"

event_id="$(printf '%s' "$event_response" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"
if [ -z "$event_id" ]; then
  echo "Could not read the demo event ID." >&2
  exit 1
fi

attempt=0
while [ "$attempt" -lt 80 ]; do
  deliveries="$(
    curl --fail --silent --show-error \
      "$base_url/v1/deliveries" \
      --header "X-EventMesh-Key: $api_key"
  )"
  if printf '%s' "$deliveries" |
    grep -q "\"eventId\":\"$event_id\".*\"status\":\"SUCCEEDED\""; then
    echo "Event $event_id reached SUCCEEDED after planned 503 retries."
    curl --fail --silent --show-error "$subscriber_url/requests"
    printf '\n'
    exit 0
  fi
  attempt=$((attempt + 1))
  sleep 0.25
done

echo "Demo delivery did not succeed within 20 seconds." >&2
exit 1
