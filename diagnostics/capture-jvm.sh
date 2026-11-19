#!/usr/bin/env bash

set -euo pipefail

output_dir="diagnostics/output/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$output_dir"

docker exec transaction-app jcmd 1 VM.flags \
  > "$output_dir/vm-flags.txt"
docker exec transaction-app jcmd 1 GC.heap_info \
  > "$output_dir/heap-info.txt"
docker exec transaction-app jcmd 1 VM.native_memory summary \
  > "$output_dir/native-memory.txt"
docker exec transaction-app jcmd 1 Thread.print -l \
  > "$output_dir/thread-dump.txt"
docker exec transaction-app jmap -histo:live 1 \
  > "$output_dir/class-histogram.txt"
docker exec transaction-app sh -c 'tail -n 500 /tmp/gc.log' \
  > "$output_dir/gc-log-tail.txt"

echo "JVM diagnostics written to $output_dir"
