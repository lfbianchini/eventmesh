#!/bin/sh

set -eu

base_url="${EVENTMESH_BASE_URL:-http://localhost:8088}"
subscriber_url="${EVENTMESH_SUBSCRIBER_URL:-http://127.0.0.1:8090}"
api_key="${EVENTMESH_DEV_API_KEY:-em_dev_local_key_change_me}"
signing_secret="eventmesh-demo-signing-secret-change-me-32"
run_id="$(date +%s)-$$"
stopped=false

cleanup() {
  if [ "$stopped" = "true" ]; then
    docker compose -f compose.yml -f compose.local.yml start eventmesh >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

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
  --request POST "$subscriber_url/reset?failures=100" >/dev/null

curl --fail --silent --show-error \
  --request POST "$base_url/v1/subscriptions" \
  --header 'Content-Type: application/json' \
  --header "X-EventMesh-Key: $api_key" \
  --data "{
    \"name\": \"worker-restart-$run_id\",
    \"endpointUrl\": \"http://subscriber:8090/webhooks\",
    \"eventTypes\": [\"failure.worker\"],
    \"signingSecret\": \"$signing_secret\"
  }" >/dev/null

event_response="$(
  curl --fail --silent --show-error \
    --request POST "$base_url/v1/events" \
    --header 'Content-Type: application/json' \
    --header "X-EventMesh-Key: $api_key" \
    --header "Idempotency-Key: worker-restart-$run_id" \
    --data '{
      "type": "failure.worker",
      "source": "d4.failure-drill",
      "subject": "worker-restart",
      "data": {"expectedRecovery": true}
    }'
)"
event_id="$(printf '%s' "$event_response" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"
if [ -z "$event_id" ]; then
  echo "Could not read the worker-drill event ID." >&2
  exit 1
fi

attempt=0
while [ "$attempt" -lt 80 ]; do
  deliveries="$(
    curl --fail --silent --show-error "$base_url/v1/deliveries" \
      --header "X-EventMesh-Key: $api_key"
  )"
  if printf '%s' "$deliveries" |
    grep -q "\"eventId\":\"$event_id\".*\"status\":\"RETRYING\""; then
    break
  fi
  attempt=$((attempt + 1))
  sleep 0.25
done
if [ "$attempt" -ge 80 ]; then
  echo "Delivery never entered RETRYING before the worker restart." >&2
  exit 1
fi

docker compose -f compose.yml -f compose.local.yml stop eventmesh >/dev/null
stopped=true
curl --fail --silent --show-error \
  --request POST "$subscriber_url/reset?failures=0" >/dev/null

recovery_started_ms="$(python3 -c 'import time; print(time.time_ns() // 1_000_000)')"
docker compose -f compose.yml -f compose.local.yml start eventmesh >/dev/null
stopped=false

attempt=0
while [ "$attempt" -lt 120 ]; do
  if curl --fail --silent "$base_url/actuator/health/readiness" >/dev/null 2>&1; then
    deliveries="$(
      curl --fail --silent --show-error "$base_url/v1/deliveries" \
        --header "X-EventMesh-Key: $api_key"
    )"
    if printf '%s' "$deliveries" |
      grep -q "\"eventId\":\"$event_id\".*\"status\":\"SUCCEEDED\""; then
      recovered_ms="$(python3 -c 'import time; print(time.time_ns() // 1_000_000)')"
      echo "Worker restart drill passed for event $event_id."
      echo "The RETRYING row survived process termination and reached SUCCEEDED."
      echo "Recovery after worker start: $((recovered_ms - recovery_started_ms))ms."
      exit 0
    fi
  fi
  attempt=$((attempt + 1))
  sleep 0.25
done

echo "The durable delivery did not recover within 30 seconds of worker restart." >&2
exit 1
