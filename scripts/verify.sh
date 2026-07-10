#!/bin/sh

set -eu

if ! java -version 2>&1 | grep -q 'version "21'; then
  if command -v brew >/dev/null 2>&1; then
    java_home="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
    if [ -x "$java_home/bin/java" ]; then
      export JAVA_HOME="$java_home"
      export PATH="$JAVA_HOME/bin:$PATH"
    fi
  fi
fi

if ! java -version 2>&1 | grep -q 'version "21'; then
  echo "EventMesh verification requires Java 21." >&2
  exit 1
fi

if [ -z "${DOCKER_HOST:-}" ] && command -v docker >/dev/null 2>&1; then
  docker_host="$(docker context inspect --format '{{.Endpoints.docker.Host}}' 2>/dev/null || true)"
  if [ -n "$docker_host" ]; then
    export DOCKER_HOST="$docker_host"
  fi
fi

if [ -n "${DOCKER_HOST:-}" ]; then
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:-/var/run/docker.sock}"
fi

exec ./mvnw -B -ntp verify "$@"

