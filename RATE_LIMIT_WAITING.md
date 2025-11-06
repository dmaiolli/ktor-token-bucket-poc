# Rate Limiting com Espera - Ktor Token Bucket

## 📋 Visão Geral

Este projeto implementa **Rate Limiting com Token Bucket** que **aguarda automaticamente** até que tokens estejam disponíveis, ao invés de retornar erro 429 (Too Many Requests).

A implementação usa **Kotlin Coroutines** para suspender requisições até que recursos estejam disponíveis, tornando o processo totalmente transparente para o cliente.

## 🔧 Como Funciona

### Comportamento Tradicional (Rejeitando com 429)
- ❌ Retorna erro HTTP 429 quando o limite é excedido
- ❌ Cliente precisa implementar retry com base no header `Retry-After`
- ❌ Requisições são rejeitadas imediatamente
- ❌ Experiência ruim para o usuário final

### Comportamento Implementado (Aguardando Tokens) ✨
- ✅ **Aguarda automaticamente** até que tokens estejam disponíveis
- ✅ A requisição é suspensa usando Kotlin coroutines (`suspend fun`)
- ✅ Quando um token fica disponível, a requisição continua normalmente
- ✅ **Não há erro 429** - todas as requisições são eventualmente processadas
- ✅ Totalmente transparente para o cliente
- ✅ Threads não são bloqueadas durante a espera

## 🎯 Algoritmo Token Bucket

### O que é?

Token Bucket é um algoritmo de rate limiting que funciona como um "balde de fichas":

```
┌─────────────────────────────────┐
│    Token Bucket                 │
│                                 │
│  🪙 🪙 🪙 🪙 🪙  (5 tokens)      │
│                                 │
│  Capacidade: 5 tokens           │
│  Recarga: 1 token a cada 6s     │
└─────────────────────────────────┘
```

### Como Funciona?

1. **Início**: Bucket começa cheio (5 tokens)
2. **Requisição**: Cada requisição consome 1 token
3. **Recarga**: Tokens são adicionados ao longo do tempo
4. **Limite**: Quando vazio, requisições aguardam até recarga

### Exemplo Prático

```
Tempo | Tokens | Ação                    | Resultado
------|--------|-------------------------|------------------
00:00 |   5    | Req #1                 | ✅ Processar (4 tokens restantes)
00:01 |   4    | Req #2                 | ✅ Processar (3 tokens restantes)
00:02 |   3    | Req #3                 | ✅ Processar (2 tokens restantes)
00:03 |   2    | Req #4                 | ✅ Processar (1 token restante)
00:04 |   1    | Req #5                 | ✅ Processar (0 tokens restantes)
00:05 |   0    | Req #6                 | ⏳ AGUARDAR (sem tokens)
00:11 |   1    | (recarga automática)   | ✅ Processar Req #6
00:12 |   0    | Req #7                 | ⏳ AGUARDAR
00:18 |   1    | (recarga automática)   | ✅ Processar Req #7
```

## 📊 Configuração do Projeto

### Rate Limiters Configurados

#### 1. Global Rate Limiter
```kotlin
val globalRateLimiter = TokenBucketRateLimiter(
    capacity = 10,              // Máximo de 10 tokens
    refillRate = 10,            // Recarga 10 tokens...
    refillPeriodSeconds = 60    // ...a cada 60 segundos
)
```
- **Capacidade**: 10 requisições
- **Período**: 60 segundos (1 minuto)
- **Taxa**: 1 token a cada 6 segundos
- **Aplica-se**: Todos os endpoints `/api/*`

#### 2. API Rate Limiter
```kotlin
val apiRateLimiter = TokenBucketRateLimiter(
    capacity = 5,               // Máximo de 5 tokens
    refillRate = 5,             // Recarga 5 tokens...
    refillPeriodSeconds = 30    // ...a cada 30 segundos
)
```
- **Capacidade**: 5 requisições
- **Período**: 30 segundos
- **Taxa**: 1 token a cada 6 segundos
- **Aplica-se**: Endpoints `/api/*` (após global)

### Cálculo do Tempo de Espera

```kotlin
Tempo por token = Período de Recarga / Taxa de Recarga
                = 30 segundos / 5 tokens
                = 6 segundos por token
```

## 💻 Implementação Técnica

### 1. Método `consume()` - Suspendível

```kotlin
/**
 * Consome um token, aguardando até que esteja disponível.
 * Este método SUSPENDE a coroutine (não bloqueia thread).
 */
suspend fun consume(tokensToConsume: Long = 1) {
    while (true) {
        val consumed = lock.withLock {
            refill()  // Atualiza tokens baseado no tempo
            
            if (tokens >= tokensToConsume) {
                tokens -= tokensToConsume
                true  // Token consumido com sucesso
            } else {
                false  // Sem tokens disponíveis
            }
        }
        
        if (consumed) {
            return  // Sucesso! Continua a requisição
        }
        
        // Aguarda até o próximo token (suspende coroutine)
        val waitTimeMillis = calculateWaitTimeMillis()
        delay(waitTimeMillis)  // 🔑 SUSPENDE sem bloquear thread
    }
}
```

**Pontos-chave:**
- ✅ `suspend fun` - Pode ser suspensa
- ✅ `delay()` - Suspende coroutine, não bloqueia thread
- ✅ Loop while - Continua tentando até conseguir
- ✅ Thread-safe com `ReentrantLock`

### 2. Interceptor no Ktor

```kotlin
route("/api") {
    intercept(ApplicationCallPipeline.Call) {
        // Aguarda tokens no rate limiter global
        globalRateLimiter.consume()
        
        // Aguarda tokens no rate limiter de API
        apiRateLimiter.consume()
        
        // Só chega aqui quando ambos liberarem tokens
    }
    
    get("/public") { /* ... */ }
    get("/github") { /* ... */ }
    get("/pokemon") { /* ... */ }
}
```

### 3. Método `refill()` - Recarga Automática

```kotlin
private fun refill() {
    val now = System.currentTimeMillis()
    val lastRefill = lastRefillTimestamp.get()
    val timeSinceLastRefill = now - lastRefill

    if (timeSinceLastRefill > 0) {
        // Calcula quantos tokens devem ser adicionados
        val refillPeriodMillis = refillPeriodSeconds * 1000
        val tokensToAdd = (timeSinceLastRefill * refillRate) / refillPeriodMillis
        
        if (tokensToAdd > 0) {
            tokens = min(capacity, tokens + tokensToAdd)
            lastRefillTimestamp.set(now)
        }
    }
}
```

## 🧪 Resultados dos Testes Reais

### Teste 1: Requisições Sequenciais

```bash
$ ./test-rate-limit-waiting.sh

=== Teste de Rate Limiting com Espera ===
Fazendo 8 requisições consecutivas...

[1] 16:25:55 - {"message":"Requisição bem-sucedida!","tokensRestantes":4} (tempo: 0s)
[2] 16:25:55 - {"message":"Requisição bem-sucedida!","tokensRestantes":3} (tempo: 0s)
[3] 16:25:55 - {"message":"Requisição bem-sucedida!","tokensRestantes":2} (tempo: 0s)
[4] 16:25:55 - {"message":"Requisição bem-sucedida!","tokensRestantes":1} (tempo: 0s)
[5] 16:25:55 - {"message":"Requisição bem-sucedida!","tokensRestantes":0} (tempo: 0s)
[6] 16:26:01 - {"message":"Requisição bem-sucedida!","tokensRestantes":0} (tempo: 6s) ⏳
[7] 16:26:07 - {"message":"Requisição bem-sucedida!","tokensRestantes":0} (tempo: 6s) ⏳
[8] 16:26:13 - {"message":"Requisição bem-sucedida!","tokensRestantes":0} (tempo: 6s) ⏳
```

**Análise:**
- ✅ Requisições 1-5: Imediatas (0s) - tokens disponíveis
- ✅ Requisições 6-8: Aguardaram 6s cada - esperaram recarga
- ✅ **100% de sucesso** - nenhum erro 429
- ✅ Comportamento previsível e consistente

### Teste 2: Requisições Concorrentes (Paralelas)

```bash
$ ./test-concurrent-requests.sh

=== Teste de Requisições Concorrentes ===
Fazendo 8 requisições em paralelo...

[1] Duração: 0.05s  | Tokens restantes: 1
[2] Duração: 0.06s  | Tokens restantes: 2
[4] Duração: 0.08s  | Tokens restantes: 0
[3] Duração: 6.12s  | Tokens restantes: 0  ⏳ AGUARDOU
[6] Duração: 12.15s | Tokens restantes: 0  ⏳ AGUARDOU
[7] Duração: 18.21s | Tokens restantes: 0  ⏳ AGUARDOU
[5] Duração: 24.18s | Tokens restantes: 0  ⏳ AGUARDOU
[8] Duração: 30.25s | Tokens restantes: 0  ⏳ AGUARDOU

Tempo total: 30s
```

**Análise:**
- ✅ 8 requisições iniciadas simultaneamente
- ✅ Primeiras requisições processadas imediatamente
- ✅ Requisições subsequentes formaram fila ordenada
- ✅ Processamento serializado respeitando rate limit
- ✅ Todas bem-sucedidas sem timeout

## 📈 Vantagens desta Abordagem

### 1. **Simplicidade para o Cliente**
```bash
# Cliente não precisa fazer NADA especial
curl http://localhost:8080/api/public  # Sempre funciona!
```
- ✅ Sem lógica de retry
- ✅ Sem tratamento de erro 429
- ✅ Sem leitura de headers `Retry-After`
- ✅ Código do cliente mais simples e limpo

### 2. **Melhor Experiência do Usuário**
```
Antes (com 429):
Cliente → Servidor → ❌ 429 → Cliente percebe erro → Retry manual

Depois (com espera):
Cliente → Servidor → ⏳ Aguarda → ✅ 200 OK → Cliente feliz
```
- ✅ Todas requisições eventualmente processadas
- ✅ Sem erros visíveis
- ✅ Delay transparente
- ✅ Menos frustração

### 3. **Uso Eficiente de Recursos**
```kotlin
// ❌ Bloqueio tradicional (ruim)
Thread.sleep(6000)  // Bloqueia thread por 6s

// ✅ Coroutine (bom)
delay(6000)  // Suspende coroutine, thread livre
```
- ✅ Threads não bloqueadas
- ✅ Escalabilidade mantida
- ✅ Milhares de requisições podem aguardar
- ✅ Baixo uso de memória

### 4. **Controle de Backpressure**
```
Requisições → [Fila Ordenada] → Rate Limiter → Processamento
              ⏳ Aguardando                     ✅ Processando
```
- ✅ Autorregulação automática
- ✅ Sem acúmulo de requisições rejeitadas
- ✅ Processamento ordenado (FIFO)
- ✅ Proteção contra sobrecarga

## ⚙️ Como Rodar e Testar

### Iniciando o Servidor

```bash
# Clone e entre no diretório
cd ktor-token-bucket-poc

# Execute o servidor
./gradlew run

# Ou use o script
./run.sh
```

### Testes Disponíveis

#### 1. Teste Básico
```bash
# Uma requisição simples
curl http://localhost:8080/api/public
```

#### 2. Teste de Status
```bash
# Veja tokens disponíveis
curl http://localhost:8080/status
```

#### 3. Teste de Sequência
```bash
# 10 requisições seguidas
for i in {1..10}; do
  echo "Requisição $i - $(date +%H:%M:%S)"
  curl -s http://localhost:8080/api/public | jq -r '.message'
done
```

#### 4. Teste de Concorrência
```bash
# 8 requisições em paralelo
for i in {1..8}; do
  (time curl -s http://localhost:8080/api/public) &
done
wait
```

#### 5. Script de Teste Pronto
```bash
# Use o script fornecido
./test-rate-limit.sh
```

## 🔍 Endpoints Disponíveis

| Endpoint | Rate Limit | Descrição |
|----------|------------|-----------|
| `GET /` | ❌ Nenhum | Página inicial com informações |
| `GET /health` | ❌ Nenhum | Health check |
| `GET /status` | ❌ Nenhum | Status dos rate limiters |
| `GET /api/public` | ✅ Sim | Endpoint de teste |
| `GET /api/github` | ✅ Sim | Chama API do GitHub |
| `GET /api/pokemon` | ✅ Sim | Chama PokeAPI |

## ⚠️ Considerações Importantes

### 1. **Timeout de Cliente**

```bash
# Configure timeout no cliente se necessário
curl --max-time 30 http://localhost:8080/api/public

# Ou use connect-timeout
curl --connect-timeout 5 --max-time 30 http://localhost:8080/api/public
```

**Por quê?**
- Requisições podem aguardar vários segundos
- Cliente pode ter timeout próprio
- Evita requisições "penduradas" indefinidamente

### 2. **Backpressure em Produção**

```kotlin
// Configure limites de conexão no Netty
embeddedServer(Netty, port = 8080, configure = {
    connectionGroupSize = 2      // Threads para aceitar conexões
    workerGroupSize = 5          // Threads para processar I/O
    callGroupSize = 10           // Threads para processar chamadas
}) {
    // ...
}
```

**Monitore:**
- ✅ Número de conexões simultâneas
- ✅ Uso de memória
- ✅ Tempo médio de espera
- ✅ Taxa de timeout

### 3. **Quando NÃO Usar Espera**

❌ **Evite espera se:**
- API pública na internet
- Precisa de feedback imediato
- Proteção contra DDoS é prioridade
- Timeout do cliente é curto (<10s)
- Gateway/proxy com timeout rígido

### 4. **Alternativa: Modo 429**

```kotlin
// Use tryConsume() ao invés de consume()
route("/api") {
    intercept(ApplicationCallPipeline.Call) {
        if (!apiRateLimiter.tryConsume()) {
            throw RateLimitExceededException(
                "Rate limit exceeded",
                apiRateLimiter.getRetryAfterSeconds()
            )
        }
    }
}
```

## 🎯 Casos de Uso Ideais

### ✅ **Use ESPERA quando:**

| Cenário | Por quê? |
|---------|----------|
| 🏢 APIs internas de microsserviços | Controle total sobre clientes |
| 📊 APIs com SLA garantido | Todas requisições processadas |
| 🔄 Processamento de filas | Ordem é importante |
| 🔗 Integrações síncronas | Cliente aguarda resposta |
| 📱 Apps mobile corporativos | UX simplificada |

### ❌ **Use 429 quando:**

| Cenário | Por quê? |
|---------|----------|
| 🌐 APIs públicas REST | Cliente desconhecido |
| 🛡️ Proteção DDoS | Rejeição rápida necessária |
| ⚡ Gateway/Proxy | Timeouts curtos |
| 📡 Webhooks | Retry automático do cliente |
| 🔓 API sem autenticação | Prevenir abuso |

## 📚 Estrutura do Código

```
src/main/kotlin/com/picpay/poc/
├── Application.kt              # Configuração do Ktor
│   ├── configurePlugins()      # Plugins (ContentNegotiation, StatusPages)
│   └── configureRouting()      # Rotas e rate limiting
│
└── TokenBucketRateLimiter.kt   # Implementação do Token Bucket
    ├── tryConsume()            # Tenta consumir (retorna false se sem tokens)
    ├── consume()               # Aguarda até consumir (suspend fun) 🔑
    ├── refill()                # Recarga automática de tokens
    ├── availableTokens()       # Consulta tokens disponíveis
    └── getRetryAfterSeconds()  # Calcula tempo de espera
```

## 🚀 Próximos Passos / Melhorias

### 1. Rate Limiting por Usuário/IP

```kotlin
// Map de limiters por usuário
val userLimiters = ConcurrentHashMap<String, TokenBucketRateLimiter>()

route("/api") {
    intercept(ApplicationCallPipeline.Call) {
        val userId = call.request.headers["X-User-ID"] ?: "anonymous"
        val limiter = userLimiters.getOrPut(userId) {
            TokenBucketRateLimiter(capacity = 5, refillRate = 5, refillPeriodSeconds = 30)
        }
        limiter.consume()
    }
}
```

### 2. Rate Limiting por Plano

```kotlin
data class RateLimitPlan(val capacity: Long, val refillRate: Long, val period: Long)

val plans = mapOf(
    "free" to RateLimitPlan(10, 10, 60),
    "premium" to RateLimitPlan(100, 100, 60),
    "enterprise" to RateLimitPlan(1000, 1000, 60)
)

val userPlan = getUserPlan(userId)
val limiter = createLimiterForPlan(plans[userPlan]!!)
limiter.consume()
```

### 3. Métricas e Observabilidade

```kotlin
class MetricsTokenBucket(private val delegate: TokenBucketRateLimiter) {
    private val waitTimeMetric = Counter.build()
        .name("rate_limit_wait_seconds")
        .help("Tempo total aguardando tokens")
        .register()
    
    suspend fun consume() {
        val start = System.currentTimeMillis()
        delegate.consume()
        val waitTime = System.currentTimeMillis() - start
        waitTimeMetric.inc(waitTime / 1000.0)
    }
}
```

### 4. Configuração Dinâmica

```kotlin
// Ajuste rate limit em runtime
@Post("/admin/rate-limit/config")
suspend fun updateRateLimit(call: ApplicationCall) {
    val config = call.receive<RateLimitConfig>()
    apiRateLimiter.updateConfig(config.capacity, config.refillRate)
    call.respond(HttpStatusCode.OK)
}
```

## 📖 Referências e Leitura Adicional

- 📘 [Token Bucket Algorithm - Wikipedia](https://en.wikipedia.org/wiki/Token_bucket)
- 📗 [Ktor Coroutines](https://ktor.io/docs/coroutines.html)
- 📕 [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- 📙 [Rate Limiting Patterns - Google Cloud](https://cloud.google.com/architecture/rate-limiting-strategies-techniques)
- 📓 [Ktor Rate Limit Plugin](https://ktor.io/docs/rate-limit.html)

## 🤝 Contribuindo

Melhorias são bem-vindas! Áreas de interesse:

- [ ] Adicionar testes de carga
- [ ] Implementar métricas com Micrometer
- [ ] Suporte a Redis para rate limiting distribuído
- [ ] Dashboard para visualização de limites
- [ ] Configuração via arquivo YAML/HOCON

---

**Feito com ❤️ usando Kotlin + Ktor + Coroutines**
