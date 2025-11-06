# 🚀 KTOR Token Bucket Rate Limiting POC

Prova de conceito demonstrando a implementação do algoritmo **Token Bucket** para Rate Limiting em aplicações KTOR.

## 📋 O que é Token Bucket?

Token Bucket é um algoritmo de controle de taxa (rate limiting) que funciona como um balde que armazena tokens:

1. **Capacidade**: O balde tem uma capacidade máxima de tokens (ex: 10 tokens)
2. **Refill**: Tokens são adicionados ao balde a uma taxa constante (ex: 5 tokens a cada 30 segundos)
3. **Consumo**: Cada requisição consome 1 token
4. **Rejeição**: Se não houver tokens disponíveis, a requisição é rejeitada com HTTP 429 (Too Many Requests)

### 🎯 Vantagens do Token Bucket

- ✅ **Permite bursts controlados**: Acumula tokens quando não está em uso
- ✅ **Flexível**: Diferente de rate limiters fixos, permite variação de tráfego
- ✅ **Simples de implementar**: Lógica clara e direta
- ✅ **Eficiente**: Baixo overhead computacional
- ✅ **Justo**: Tokens são reabastecidos de forma contínua

### 📊 Comparação com outros algoritmos

| Algoritmo | Permite Bursts | Precisão | Complexidade | Uso de Memória |
|-----------|---------------|----------|--------------|----------------|
| **Token Bucket** | ✅ Sim | Alta | Baixa | Baixo |
| Fixed Window | ❌ Não | Baixa | Muito Baixa | Muito Baixo |
| Sliding Window | ⚠️ Parcial | Muito Alta | Alta | Alto |
| Leaky Bucket | ❌ Não | Alta | Média | Baixo |

## 🏗️ Arquitetura da POC

```
┌─────────────────────────────────────────────────────────┐
│                     Cliente HTTP                         │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│                  KTOR Server (8080)                      │
├─────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────┐   │
│  │         Global Rate Limiter Interceptor          │   │
│  │     (10 requisições por minuto)                  │   │
│  └──────────────────┬───────────────────────────────┘   │
│                     │                                    │
│                     ▼                                    │
│  ┌──────────────────────────────────────────────────┐   │
│  │          API Rate Limiter Interceptor            │   │
│  │     (5 requisições por 30 segundos)              │   │
│  └──────────────────┬───────────────────────────────┘   │
│                     │                                    │
│                     ▼                                    │
│  ┌──────────────────────────────────────────────────┐   │
│  │              Application Routes                  │   │
│  │  • /api/public  • /api/github  • /api/pokemon    │   │
│  └──────────────────┬───────────────────────────────┘   │
└────────────────────┬┴───────────────────────────────────┘
                     │
                     ▼
        ┌────────────────────────┐
        │   External APIs        │
        │  • GitHub API          │
        │  • PokeAPI             │
        └────────────────────────┘
```

## 🛠️ Tecnologias Utilizadas

- **Kotlin** 1.9.22
- **KTOR** 2.3.7 (Server Framework)
- **Netty** (HTTP Server Engine)
- **Kotlinx Serialization** (JSON)
- **Logback** (Logging)
- **Coroutines** (Async/Await)

## 📁 Estrutura do Projeto

```
ktor-token-bucket-poc/
├── build.gradle.kts                    # Configuração Gradle
├── settings.gradle.kts
├── test-rate-limit.sh                  # Script de testes
└── src/main/kotlin/com/picpay/poc/
    ├── Application.kt                  # Aplicação KTOR principal
    ├── TokenBucketRateLimiter.kt       # Implementação do Token Bucket
    └── resources/
        └── logback.xml                 # Configuração de logs
```

## 🚀 Como Executar

### Pré-requisitos

- Java 17 ou superior
- Gradle (ou use o wrapper incluído)

### 1. Compilar e Executar

```bash
cd /tmp/ktor-token-bucket-poc
./gradlew run
```

O servidor iniciará na porta **8080**.

### 2. Testar Manualmente

```bash
# Health check (sem rate limit)
curl http://localhost:8080/health

# Status dos rate limiters
curl http://localhost:8080/status | jq

# Endpoint público
curl http://localhost:8080/api/public | jq

# Chamada para GitHub API
curl http://localhost:8080/api/github | jq '.source, .tokensRestantes'

# Chamada para PokeAPI
curl http://localhost:8080/api/pokemon | jq '.source, .tokensRestantes'
```

### 3. Executar Script de Testes Automatizados

```bash
./test-rate-limit.sh
```

Este script irá:
- ✅ Testar endpoints sem rate limit
- ✅ Consumir tokens até atingir o limite
- ✅ Demonstrar rejeição com HTTP 429
- ✅ Aguardar refill de tokens
- ✅ Verificar recuperação após refill

## 🔧 Configuração dos Rate Limiters

### Global Rate Limiter

```kotlin
val globalRateLimiter = TokenBucketRateLimiter(
    capacity = 10,           // Máximo de 10 tokens
    refillRate = 10,         // Adiciona 10 tokens...
    refillPeriodSeconds = 60 // ...a cada 60 segundos
)
```

**Resultado**: Máximo de 10 requisições por minuto

### API Rate Limiter

```kotlin
val apiRateLimiter = TokenBucketRateLimiter(
    capacity = 5,            // Máximo de 5 tokens
    refillRate = 5,          // Adiciona 5 tokens...
    refillPeriodSeconds = 30 // ...a cada 30 segundos
)
```

**Resultado**: Máximo de 5 requisições a cada 30 segundos

## 📡 Endpoints Disponíveis

| Endpoint | Método | Rate Limit | Descrição |
|----------|--------|------------|-----------|
| `/` | GET | ❌ Não | Página inicial com instruções |
| `/health` | GET | ❌ Não | Health check |
| `/status` | GET | ❌ Não | Status dos rate limiters |
| `/api/public` | GET | ✅ Sim | Endpoint de teste simples |
| `/api/github` | GET | ✅ Sim | Chama GitHub API |
| `/api/pokemon` | GET | ✅ Sim | Chama PokeAPI (Pokémon aleatório) |

## 🔍 Exemplo de Respostas

### ✅ Requisição Bem-Sucedida (200 OK)

```json
{
  "message": "Requisição bem-sucedida!",
  "timestamp": 1704123456789,
  "tokensRestantes": 3
}
```

### ❌ Rate Limit Excedido (429 Too Many Requests)

```json
{
  "error": "Rate limit exceeded",
  "message": "API rate limit exceeded",
  "retryAfter": "6s"
}
```

**Headers da resposta:**
```
HTTP/1.1 429 Too Many Requests
X-Rate-Limit-Retry-After: 6
```

## 🧪 Testando Cenários

### Cenário 1: Burst de Requisições

```bash
# Fazer 7 requisições rapidamente (limite é 5)
for i in {1..7}; do
  curl -s http://localhost:8080/api/public | jq '.tokensRestantes // .error'
done
```

**Resultado esperado:**
- Primeiras 5 requisições: ✅ 200 OK (tokens: 4, 3, 2, 1, 0)
- Próximas 2 requisições: ❌ 429 Too Many Requests

### Cenário 2: Aguardar Refill

```bash
# Consumir todos os tokens
for i in {1..5}; do curl -s http://localhost:8080/api/public > /dev/null; done

# Verificar status (0 tokens)
curl -s http://localhost:8080/status | jq '.apiRateLimiter.availableTokens'

# Aguardar 10 segundos
sleep 10

# Verificar status novamente (tokens parcialmente reabastecidos)
curl -s http://localhost:8080/status | jq '.apiRateLimiter.availableTokens'
```

### Cenário 3: Rate Limiters Independentes

```bash
# Consumir tokens do API rate limiter
curl http://localhost:8080/api/public

# O global rate limiter ainda tem tokens disponíveis
curl http://localhost:8080/status | jq '.globalRateLimiter.availableTokens'
```

## 📊 Como Funciona o Refill

O Token Bucket reabastecer tokens de forma **contínua** e **proporcional** ao tempo:

```
Tempo 0s:   [🪙🪙🪙🪙🪙] 5 tokens
Tempo 6s:   [🪙] 1 token  (após consumir 5)
Tempo 12s:  [🪙🪙] 2 tokens (1 token adicionado)
Tempo 18s:  [🪙🪙🪙] 3 tokens (mais 1 token)
Tempo 30s:  [🪙🪙🪙🪙🪙] 5 tokens (refill completo)
```

**Fórmula do refill:**
```
tokensToAdd = (timeSinceLastRefill × refillRate) / refillPeriod
```

## 🎯 Casos de Uso Reais

### 1. Proteção contra DDoS
```kotlin
val ddosProtection = TokenBucketRateLimiter(
    capacity = 100,
    refillRate = 100,
    refillPeriodSeconds = 60
)
```

### 2. Rate Limiting por Usuário
```kotlin
val userRateLimiters = mutableMapOf<String, TokenBucketRateLimiter>()

fun getUserRateLimiter(userId: String): TokenBucketRateLimiter {
    return userRateLimiters.getOrPut(userId) {
        TokenBucketRateLimiter(
            capacity = 20,
            refillRate = 20,
            refillPeriodSeconds = 60
        )
    }
}
```

### 3. Rate Limiting por IP
```kotlin
intercept(ApplicationCallPipeline.Call) {
    val clientIp = call.request.origin.remoteHost
    val rateLimiter = getIpRateLimiter(clientIp)
    
    if (!rateLimiter.tryConsume()) {
        throw RateLimitExceededException("Too many requests from this IP", 60)
    }
}
```

### 4. Different Tiers de API
```kotlin
enum class ApiTier {
    FREE,
    PREMIUM,
    ENTERPRISE
}

fun getRateLimiterForTier(tier: ApiTier) = when (tier) {
    ApiTier.FREE -> TokenBucketRateLimiter(10, 10, 60)       // 10 req/min
    ApiTier.PREMIUM -> TokenBucketRateLimiter(100, 100, 60)  // 100 req/min
    ApiTier.ENTERPRISE -> TokenBucketRateLimiter(1000, 1000, 60) // 1000 req/min
}
```

## 🔐 Melhorias Possíveis

### 1. Persistência (Redis)
```kotlin
// Salvar estado do rate limiter no Redis
class RedisTokenBucket(
    private val redisClient: RedisClient,
    private val key: String
) {
    fun tryConsume(): Boolean {
        // Implementar usando EVAL script do Redis
        val script = """
            local tokens = redis.call('get', KEYS[1])
            if tokens and tonumber(tokens) >= 1 then
                redis.call('decr', KEYS[1])
                return 1
            end
            return 0
        """
        return redisClient.eval(script, listOf(key)) == 1L
    }
}
```

### 2. Distributed Rate Limiting
```kotlin
// Usar cache distribuído (Hazelcast, Redis, etc)
class DistributedTokenBucket(
    private val cache: IMap<String, TokenBucketState>
) {
    fun tryConsume(key: String): Boolean {
        return cache.executeOnKey(key, TokenBucketProcessor())
    }
}
```

### 3. Métricas e Observabilidade
```kotlin
// Adicionar métricas Prometheus
val rateLimitHits = Counter.build()
    .name("rate_limit_hits_total")
    .help("Total de requisições bloqueadas por rate limit")
    .register()

val rateLimitTokens = Gauge.build()
    .name("rate_limit_tokens_available")
    .help("Tokens disponíveis no bucket")
    .register()
```

### 4. Rate Limiting Hierárquico
```kotlin
// Combinar múltiplos rate limiters
class HierarchicalRateLimiter(
    private val limiters: List<TokenBucketRateLimiter>
) {
    fun tryConsume(): Boolean {
        return limiters.all { it.tryConsume() }
    }
}
```

## 📚 Referências

- [Token Bucket Algorithm (Wikipedia)](https://en.wikipedia.org/wiki/Token_bucket)
- [KTOR Documentation](https://ktor.io/docs/)
- [Rate Limiting Strategies](https://cloud.google.com/architecture/rate-limiting-strategies-techniques)
- [Designing a Rate Limiter](https://stripe.com/blog/rate-limiters)

## 🤝 Como Contribuir

Esta é uma POC educacional. Sugestões de melhorias:

1. Implementar rate limiting por IP/usuário
2. Adicionar persistência com Redis
3. Criar dashboard de métricas
4. Implementar rate limiting distribuído
5. Adicionar testes de carga (JMeter, Gatling)

## 📝 Licença

Código livre para uso educacional e comercial.

---

**Criado com ❤️ usando KTOR e Kotlin**
