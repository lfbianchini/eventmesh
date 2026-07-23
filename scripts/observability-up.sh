#!/bin/sh

set -eu

./mvnw -B -ntp package -DskipTests
docker compose \
  -f compose.yml \
  -f compose.local.yml \
  -f compose.observability.yml \
  --profile observability \
  up --build --detach --wait

wait_for_pattern() {
  url="$1"
  pattern="$2"
  attempt=0
  while [ "$attempt" -lt 60 ]; do
    if curl --fail --silent "$url" 2>/dev/null | grep -q "$pattern"; then
      return 0
    fi
    attempt=$((attempt + 1))
    sleep 0.5
  done
  echo "Timed out waiting for $url." >&2
  return 1
}

wait_for_pattern http://localhost:8088/actuator/prometheus eventmesh_state_events
wait_for_pattern http://localhost:9090/-/ready 'Prometheus Server is Ready'
wait_for_pattern http://localhost:3000/api/health '"database": "ok"'
wait_for_pattern http://localhost:3200/ready ready

echo "EventMesh observability stack is ready."
echo "Grafana:    http://localhost:3000/d/eventmesh-operations/eventmesh-operations"
echo "Prometheus: http://localhost:9090"
echo "Tempo:      Grafana Explore -> Tempo"
