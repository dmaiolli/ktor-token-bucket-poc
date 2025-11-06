#!/bin/bash

echo "=== Teste de Requisições Concorrentes ==="
echo "Fazendo 8 requisições em paralelo..."
echo ""

start=$(date +%s)

# Inicia todas as requisições em paralelo
for i in {1..8}; do
  (
    req_start=$(date +%s.%N)
    response=$(curl -s http://localhost:8080/api/public)
    req_end=$(date +%s.%N)
    duration=$(echo "$req_end - $req_start" | bc)
    timestamp=$(echo "$response" | grep -o '"timestamp":[0-9]*' | cut -d: -f2)
    tokens=$(echo "$response" | grep -o '"tokensRestantes":[0-9]*' | cut -d: -f2)
    echo "[$i] Duração: ${duration}s | Tokens restantes: $tokens"
  ) &
done

# Aguarda todas as requisições terminarem
wait

end=$(date +%s)
total=$((end - start))

echo ""
echo "Tempo total: ${total}s"
echo ""
