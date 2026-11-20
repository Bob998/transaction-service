#!/usr/bin/env bash

set -euo pipefail

version="${1:-week11-v1}"

docker build \
  --build-arg APP_VERSION="$version" \
  --tag "transaction-service:$version" \
  .

echo "已构建 transaction-service:$version"
