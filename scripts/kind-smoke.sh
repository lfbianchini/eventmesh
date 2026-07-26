#!/bin/sh

set -eu

cluster_name="${EVENTMESH_KIND_CLUSTER:-eventmesh}"
namespace="${EVENTMESH_KIND_NAMESPACE:-eventmesh}"
local_port="${EVENTMESH_KIND_PORT:-18088}"
port_forward_pid=""

cleanup() {
  if [ -n "$port_forward_pid" ]; then
    kill "$port_forward_pid" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

for command_name in kind kubectl helm; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "$command_name is required. Install it with: brew install $command_name" >&2
    exit 1
  fi
done

if ! java -version 2>&1 | grep -q 'version "21'; then
  java_home="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
  export JAVA_HOME="$java_home"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

./mvnw -B -ntp package -DskipTests
docker build --file Dockerfile.local --tag eventmesh:local .

if ! kind get clusters | grep -qx "$cluster_name"; then
  kind create cluster --name "$cluster_name" --wait 120s
fi
kind load docker-image eventmesh:local --name "$cluster_name"
for dependency_image in postgres:17-alpine apache/kafka-native:3.9.1; do
  if ! docker image inspect "$dependency_image" >/dev/null 2>&1; then
    docker pull "$dependency_image"
  fi
  kind load docker-image "$dependency_image" --name "$cluster_name"
done

kubectl create namespace "$namespace" --dry-run=client --output=yaml |
  kubectl apply --filename=-
kubectl --namespace "$namespace" apply --filename deploy/kubernetes/dev-dependencies.yaml
kubectl --namespace "$namespace" rollout status deployment/postgres --timeout=120s
kubectl --namespace "$namespace" rollout status deployment/kafka --timeout=180s

helm upgrade --install eventmesh deploy/helm/eventmesh \
  --namespace "$namespace" \
  --values deploy/kubernetes/dev-values.yaml \
  --wait \
  --timeout 3m

kubectl --namespace "$namespace" port-forward service/eventmesh-eventmesh \
  "$local_port:8080" >/tmp/eventmesh-kind-port-forward.log 2>&1 &
port_forward_pid=$!

attempt=0
while [ "$attempt" -lt 60 ]; do
  if curl --fail --silent \
    "http://127.0.0.1:$local_port/actuator/health/readiness" >/dev/null 2>&1; then
    break
  fi
  attempt=$((attempt + 1))
  sleep 0.25
done
if [ "$attempt" -ge 60 ]; then
  echo "EventMesh did not become reachable through the Kubernetes service." >&2
  exit 1
fi

curl --fail --silent --show-error \
  --request POST "http://127.0.0.1:$local_port/v1/events" \
  --header 'Content-Type: application/json' \
  --header 'X-EventMesh-Key: em_dev_local_key_change_me' \
  --header "Idempotency-Key: kind-smoke-$(date +%s)-$$" \
  --data '{
    "type": "deployment.smoke",
    "source": "kind.local",
    "subject": "same-image",
    "data": {"platform": "kubernetes"}
  }' |
  grep -q '"type":"deployment.smoke"'

echo "Kubernetes smoke test passed with image eventmesh:local."
echo "Cluster retained for inspection: kind delete cluster --name $cluster_name"
