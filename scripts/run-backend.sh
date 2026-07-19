#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

set -a
source "$PROJECT_ROOT/.env"
set +a

cd "$PROJECT_ROOT/backend"
exec ./mvnw spring-boot:run
