#!/bin/zsh
# Uso: zsh load-tests/monitor.sh > /tmp/ucomap-stats.log &
# Registra CPU/memoria del backend y Mongo cada 10 s mientras corre la prueba.
while true; do
  print -- "--- $(date +%H:%M:%S)"
  docker stats --no-stream --format '{{.Name}} cpu={{.CPUPerc}} mem={{.MemUsage}}' \
    load-tests-backend-1 load-tests-mongo-1 2>/dev/null
  sleep 10
done
