#!/usr/bin/env bash

set -euo pipefail

: "${POSTGRES_PASSWORD:?请先设置 POSTGRES_PASSWORD 环境变量}"

kubectl create namespace transaction-system \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n transaction-system create secret generic transaction-secret \
  --from-literal=POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
  --from-literal=SPRING_DATASOURCE_PASSWORD="$POSTGRES_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "transaction-secret 已创建或更新"
