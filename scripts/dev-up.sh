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
  echo "EventMesh local development requires Java 21." >&2
  exit 1
fi

./mvnw -B -ntp package -DskipTests
docker compose -f compose.yml -f compose.local.yml up --build --detach --wait
./scripts/smoke.sh

