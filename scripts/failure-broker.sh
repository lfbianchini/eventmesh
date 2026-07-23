#!/bin/sh

set -eu

base_url="${EVENTMESH_BASE_URL:-http://localhost:8088}"
api_key="${EVENTMESH_DEV_API_KEY:-em_dev_local_key_change_me}"
run_id="$(date +%s)-$$"
paused=false

cleanup() {
  if [ "$paused" = "true" ]; then
    docker compose -f compose.yml -f compose.local.yml unpause kafka >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

./scripts/dev-up.sh

docker compose -f compose.yml -f compose.local.yml pause kafka >/dev/null
paused=true

event_response="$(
  curl --fail --silent --show-error \
    --request POST "$base_url/v1/events" \
    --header 'Content-Type: application/json' \
    --header "X-EventMesh-Key: $api_key" \
    --header "Idempotency-Key: broker-failure-$run_id" \
    --data '{
      "type": "failure.broker",
      "source": "d4.failure-drill",
      "subject": "broker-outage",
      "data": {"expectedRecovery": true}
    }'
)"
event_id="$(printf '%s' "$event_response" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"
if [ -z "$event_id" ]; then
  echo "Could not read the failure-drill event ID." >&2
  exit 1
fi

pending="$(
  docker compose -f compose.yml -f compose.local.yml exec -T postgres \
    psql -U eventmesh -d eventmesh -Atc \
    "SELECT COUNT(*) FROM outbox_messages WHERE event_id = '$event_id' AND published_at IS NULL"
)"
if [ "$pending" != "1" ]; then
  echo "Expected one durable pending outbox row during the broker outage." >&2
  exit 1
fi

recovery_started_ms="$(python3 -c 'import time; print(time.time_ns() // 1_000_000)')"
docker compose -f compose.yml -f compose.local.yml unpause kafka >/dev/null
paused=false

attempt=0
while [ "$attempt" -lt 120 ]; do
  published="$(
    docker compose -f compose.yml -f compose.local.yml exec -T postgres \
      psql -U eventmesh -d eventmesh -Atc \
      "SELECT COUNT(*) FROM outbox_messages WHERE event_id = '$event_id' AND published_at IS NOT NULL"
  )"
  if [ "$published" = "1" ]; then
    recovered_ms="$(python3 -c 'import time; print(time.time_ns() // 1_000_000)')"
    echo "Broker recovery drill passed for event $event_id."
    echo "Durability proof: one unpublished row existed while Kafka was paused."
    echo "Publication recovery after unpause: $((recovered_ms - recovery_started_ms))ms."
    exit 0
  fi
  attempt=$((attempt + 1))
  sleep 0.25
done

echo "The pending outbox row was not published within 30 seconds of broker recovery." >&2
exit 1
