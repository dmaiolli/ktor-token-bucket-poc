#!/bin/bash

echo "🧪 Script de Teste - KTOR Token Bucket Rate Limiting POC"
echo "=========================================================="
echo ""

BASE_URL="http://localhost:8080"

# Cores para output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "1️⃣  Testando endpoint de health (sem rate limit)..."
for i in {1..3}; do
    echo -n "   Requisição $i: "
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL/health)
    if [ "$STATUS" == "200" ]; then
        echo -e "${GREEN}✓ OK ($STATUS)${NC}"
    else
        echo -e "${RED}✗ FALHA ($STATUS)${NC}"
    fi
done

echo ""
echo "2️⃣  Verificando status dos rate limiters..."
curl -s $BASE_URL/status | jq '.'

echo ""
echo "3️⃣  Testando endpoint /api/public (rate limit: 5 req/30s)..."
for i in {1..7}; do
    echo -n "   Requisição $i: "
    RESPONSE=$(curl -s -w "\n%{http_code}" $BASE_URL/api/public)
    STATUS=$(echo "$RESPONSE" | tail -n1)
    BODY=$(echo "$RESPONSE" | head -n-1)
    
    if [ "$STATUS" == "200" ]; then
        TOKENS=$(echo "$BODY" | jq -r '.tokensRestantes')
        echo -e "${GREEN}✓ OK - Tokens restantes: $TOKENS${NC}"
    elif [ "$STATUS" == "429" ]; then
        RETRY_AFTER=$(echo "$BODY" | jq -r '.retryAfter')
        echo -e "${RED}✗ RATE LIMITED - Retry after: $RETRY_AFTER${NC}"
    else
        echo -e "${RED}✗ ERRO ($STATUS)${NC}"
    fi
    sleep 0.5
done

echo ""
echo "4️⃣  Verificando status após consumir tokens..."
curl -s $BASE_URL/status | jq '.'

echo ""
echo "5️⃣  Testando endpoint /api/github (com chamada externa)..."
echo -n "   Requisição: "
RESPONSE=$(curl -s -w "\n%{http_code}" $BASE_URL/api/github)
STATUS=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

if [ "$STATUS" == "200" ]; then
    echo -e "${GREEN}✓ OK${NC}"
    echo "$BODY" | jq '.source, .tokensRestantes'
elif [ "$STATUS" == "429" ]; then
    echo -e "${RED}✗ RATE LIMITED${NC}"
    echo "$BODY" | jq '.'
else
    echo -e "${RED}✗ ERRO ($STATUS)${NC}"
fi

echo ""
echo "6️⃣  Aguardando 5 segundos para refill parcial..."
sleep 5

echo ""
echo "7️⃣  Verificando status após refill..."
curl -s $BASE_URL/status | jq '.'

echo ""
echo "8️⃣  Testando novamente após refill..."
echo -n "   Requisição: "
STATUS=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL/api/public)
if [ "$STATUS" == "200" ]; then
    echo -e "${GREEN}✓ OK - Tokens foram reabastecidos!${NC}"
elif [ "$STATUS" == "429" ]; then
    echo -e "${YELLOW}⚠ RATE LIMITED - Ainda aguardando refill completo${NC}"
else
    echo -e "${RED}✗ ERRO ($STATUS)${NC}"
fi

echo ""
echo "✅ Testes concluídos!"
echo ""
echo "💡 Dicas:"
echo "   - O rate limiter global permite 10 req/min"
echo "   - O rate limiter de API permite 5 req/30s"
echo "   - Tokens são reabastecidos continuamente ao longo do tempo"
echo "   - Quando exceder o limite, o header 'X-Rate-Limit-Retry-After' indica quando tentar novamente"
