#!/bin/sh
# Peak memory of one model after one text, per load mode. Usage: memory.sh <model> <sheets> <plan>
# The CPU backend repacks the weights into its own layout. With mmap, the file pages and the repacked copy
# can both count as resident, so the modes show where the peak comes from.
cd "$(dirname "$0")/.."
for mode in default no-mmap no-repack; do
  case $mode in
    default) extra="" ;;
    no-mmap) extra="-lm none" ;;
    no-repack) extra="--no-repack" ;;
  esac
  runtime/llama.sh "$1" 6 -tb 10 $extra || exit 1
  pid=$(docker inspect -f '{{.State.Pid}}' bestia-llama)
  started=$(date +%s)
  python3 prose.py --sheets "$2" --plan "$3" --limit 1 --attempts 1 --label mem --out /dev/null >/dev/null
  echo "{\"model\": \"$1\", \"mode\": \"$mode\", \"peak_rss_kb\": $(grep VmHWM /proc/$pid/status | awk '{print $2}'), \"text_s\": $(( $(date +%s) - started ))}"
done
docker rm -f bestia-llama >/dev/null
