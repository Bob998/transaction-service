#!/usr/bin/env bash

set -euo pipefail

base_url="${BASE_URL:-http://localhost:18080}"
user_id="${DEMO_USER_ID:-week12-demo-user}"
idempotency_key="week12-demo-$(date +%s)"

for command_name in curl jq; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "缺少命令: $command_name" >&2
    exit 1
  fi
done

echo "[1/6] 检查服务健康状态"
curl --fail --silent --show-error \
  "$base_url/actuator/health/readiness" | jq

echo "[2/6] 查看当前发布实例（若接口可用）"
curl --fail --silent --show-error \
  "$base_url/api/operations/release" | jq || true

echo "[3/6] 创建交易"
transaction_json=$(curl --fail --silent --show-error \
  -X POST "$base_url/api/transactions" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $user_id" \
  -d "{
    \"amount\": 36.00,
    \"description\": \"第12周系统演示\",
    \"idempotencyKey\": \"$idempotency_key\",
    \"currency\": \"CNY\",
    \"channelCode\": \"AUTO\"
  }")
echo "$transaction_json" | jq
transaction_id=$(echo "$transaction_json" | jq -er '.id')

echo "[4/6] 风控确认交易: $transaction_id"
curl --fail --silent --show-error \
  -X POST "$base_url/api/transactions/$transaction_id/confirm" | jq

echo "[5/6] 进入PROCESSING并执行支付"
curl --fail --silent --show-error \
  -X POST "$base_url/api/transactions/$transaction_id/processing" | jq
curl --fail --silent --show-error \
  -X POST "$base_url/api/transactions/$transaction_id/execute" | jq

echo "[6/6] 查询最终交易状态"
curl --fail --silent --show-error \
  "$base_url/api/transactions/$transaction_id" | jq

echo "演示完成，transactionId=$transaction_id"
