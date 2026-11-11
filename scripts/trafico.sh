#!/usr/bin/env bash
# Genera carga mixta sobre fx-catalog-service para poblar métricas y logs.
# Uso: ./scripts/trafico.sh [segundos=60]
DUR=${1:-60}; END=$((SECONDS+DUR))
while [ $SECONDS -lt $END ]; do
  curl -s -o /dev/null localhost:8002/
  curl -s -o /dev/null localhost:8002/catalog
  curl -s -o /dev/null localhost:8002/catalog
  curl -s -o /dev/null localhost:8002/firstcourse
  sleep 0.3
done
echo "Tráfico terminado (${DUR}s)"
