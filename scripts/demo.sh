#!/bin/sh

set -eu

base_url="${EVENTMESH_BASE_URL:-http://localhost:8088}"
subscriber_url="${EVENTMESH_SUBSCRIBER_URL:-http://127.0.0.1:8090}"
api_key="${EVENTMESH_DEV_API_KEY:-em_dev_local_key_change_me}"
signing_secret="eventmesh-demo-signing-secret-change-me-32"
planned_failures="${EVENTMESH_DEMO_FAILURES:-2}"
skip_setup="${EVENTMESH_SKIP_SETUP:-false}"
run_id="$(date +%s)-$$"
event_type="recruiter.demo.$run_id"
setup_log="$(mktemp "${TMPDIR:-/tmp}/eventmesh-demo.XXXXXX")"
trap 'rm -f "$setup_log"' EXIT

if ! command -v jq >/dev/null 2>&1; then
  echo "The demo requires jq to validate and format its evidence." >&2
  exit 1
fi

printf '[1/4] Starting EventMesh and the failure-injecting subscriber ... '
if [ "$skip_setup" != "true" ]; then
  if ! ./scripts/dev-up.sh >"$setup_log" 2>&1; then
    printf 'failed\n' >&2
    cat "$setup_log" >&2
    exit 1
  fi
  if ! docker compose -f compose.yml -f compose.local.yml --profile demo \
    up --build --detach --wait subscriber >"$setup_log" 2>&1; then
    printf 'failed\n' >&2
    cat "$setup_log" >&2
    exit 1
  fi
fi
printf 'ready\n'

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
  --request POST "$subscriber_url/reset?failures=$planned_failures" >/dev/null

printf '[2/4] Registering one exact-match subscription ........... '
subscription_response="$(
  curl --fail --silent --show-error \
  --request POST "$base_url/v1/subscriptions" \
  --header 'Content-Type: application/json' \
  --header "X-EventMesh-Key: $api_key" \
  --data "{
    \"name\": \"recruiter-demo-$run_id\",
    \"endpointUrl\": \"http://subscriber:8090/webhooks\",
    \"eventTypes\": [\"$event_type\"],
    \"signingSecret\": \"$signing_secret\"
  }"
)"
subscription_id="$(printf '%s' "$subscription_response" | jq -er '.id')"
printf 'created\n'

printf '[3/4] Submitting an idempotent event ....................... '
started_at_ms="$(python3 -c 'import time; print(time.time_ns() // 1_000_000)')"
event_response="$(
  curl --fail --silent --show-error \
    --request POST "$base_url/v1/events" \
    --header 'Content-Type: application/json' \
    --header "X-EventMesh-Key: $api_key" \
    --header "Idempotency-Key: recruiter-demo-$run_id" \
    --data "{
      \"type\": \"$event_type\",
      \"source\": \"recruiter.demo\",
      \"subject\": \"order-demo\",
      \"data\": {\"amount\": 4200, \"currency\": \"USD\"}
    }"
)"

event_id="$(printf '%s' "$event_response" | jq -er '.id')"
printf 'accepted\n'
printf '[4/4] Injecting %s transient failures, then recovering ..... ' "$planned_failures"

attempt=0
while [ "$attempt" -lt 80 ]; do
  deliveries="$(
    curl --fail --silent --show-error \
      "$base_url/v1/deliveries" \
      --header "X-EventMesh-Key: $api_key"
  )"
  delivery_status="$(
    printf '%s' "$deliveries" |
      jq -r --arg event_id "$event_id" --arg subscription_id "$subscription_id" \
        '.[] | select(.eventId == $event_id and .subscriptionId == $subscription_id) | .status'
  )"
  if [ "$delivery_status" = "SUCCEEDED" ]; then
    finished_at_ms="$(python3 -c 'import time; print(time.time_ns() // 1_000_000)')"
    recovery_ms=$((finished_at_ms - started_at_ms))
    request_log="$(
      curl --fail --silent --show-error "$subscriber_url/requests" |
        jq -c --arg event_id "$event_id" \
          '[.requests[] | select(.event.id == $event_id)]'
    )"
    expected_attempts=$((planned_failures + 1))
    actual_attempts="$(printf '%s' "$request_log" | jq 'length')"
    unique_deliveries="$(printf '%s' "$request_log" | jq '[.[].deliveryId] | unique | length')"
    invalid_signatures="$(printf '%s' "$request_log" | jq '[.[] | select(.validSignature != true)] | length')"
    if [ "$actual_attempts" -ne "$expected_attempts" ] ||
      [ "$unique_deliveries" -ne 1 ] ||
      [ "$invalid_signatures" -ne 0 ]; then
      printf 'invalid proof\n' >&2
      printf '%s\n' "$request_log" | jq . >&2
      exit 1
    fi

    printf 'succeeded\n\n'
    printf 'PROOF: one durable delivery survived transient subscriber failures\n'
    printf '%-9s %-10s %-11s %s\n' ATTEMPT RESPONSE SIGNATURE DELIVERY_ID
    printf '%s' "$request_log" |
      jq -r '.[] | [.attempt, .responseStatus, (if .validSignature then "valid" else "INVALID" end), .deliveryId] | @tsv' |
      awk -F '\t' '{printf "%-9s %-10s %-11s %s\n", $1, $2, $3, $4}'
    printf '\nResult: SUCCEEDED in %sms; delivery ID stayed stable across all attempts.\n' \
      "$recovery_ms"
    exit 0
  fi
  attempt=$((attempt + 1))
  sleep 0.25
done

echo "Demo delivery did not succeed within 20 seconds." >&2
exit 1
