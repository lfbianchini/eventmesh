#!/bin/sh

set -eu

report_path="${EVENTMESH_BENCHMARK_REPORT:-reports/k6/summary.json}"
base_url="${EVENTMESH_BENCHMARK_BASE_URL:-http://host.docker.internal:8088}"
api_key="${EVENTMESH_DEV_API_KEY:-em_dev_local_key_change_me}"
vus="${EVENTMESH_BENCHMARK_VUS:-10}"
duration="${EVENTMESH_BENCHMARK_DURATION:-20s}"
run_id="$(date +%s)-$$"
report_dir="$(dirname "$report_path")"

./scripts/dev-up.sh
mkdir -p "$report_dir"

docker run --rm \
  --volume "$(pwd)/benchmarks/k6:/scripts:ro" \
  --volume "$(pwd)/$report_dir:/reports" \
  --env "BASE_URL=$base_url" \
  --env "API_KEY=$api_key" \
  --env "VUS=$vus" \
  --env "DURATION=$duration" \
  --env "RUN_ID=$run_id" \
  grafana/k6:2.0.0 run \
  --summary-export "/reports/$(basename "$report_path")" \
  /scripts/ingress.js

echo "Benchmark environment:"
echo "  k6 image: grafana/k6:2.0.0"
echo "  virtual users: $vus"
echo "  duration: $duration"
echo "  Docker engine: $(docker version --format '{{.Server.Version}}')"
echo "  host: $(uname -s) $(uname -m)"
echo "Raw summary: $report_path"
