#!/usr/bin/env bash

set -euo pipefail

version="${1:?用法: ./k8s/scripts/set-release.sh <版本，例如 week11-v2>}"

kubectl -n transaction-system patch deployment transaction-service \
  --type=strategic \
  -p "{\"spec\":{\"template\":{\"spec\":{\"containers\":[{\"name\":\"app\",\"image\":\"transaction-service:${version}\",\"env\":[{\"name\":\"APP_VERSION\",\"value\":\"${version}\"}]}]}}}}"

kubectl -n transaction-system rollout status \
  deployment/transaction-service --timeout=180s
