package com.picpay.poc

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min
import kotlinx.coroutines.delay

/**
 * Implementação do algoritmo Token Bucket para Rate Limiting.
 * 
 * O Token Bucket funciona como um balde que contém tokens:
 * - Tem uma capacidade máxima (capacity)
 * - Tokens são adicionados a uma taxa constante (refillRate)
 * - Cada requisição consome 1 token
 * - Se não houver tokens disponíveis, a requisição é rejeitada
 * 
 * @param capacity Capacidade máxima do bucket (número máximo de tokens)
 * @param refillRate Taxa de reabastecimento (quantos tokens são adicionados por período)
 * @param refillPeriodSeconds Período de reabastecimento em segundos
 */
class TokenBucketRateLimiter(
    val capacity: Long,
    val refillRate: Long,
    val refillPeriodSeconds: Long
) {
    private val lock = ReentrantLock()
    private var tokens: Long = capacity
    private val lastRefillTimestamp = AtomicLong(System.currentTimeMillis())

    init {
        require(capacity > 0) { "Capacity must be positive" }
        require(refillRate > 0) { "Refill rate must be positive" }
        require(refillPeriodSeconds > 0) { "Refill period must be positive" }
    }

    /**
     * Tenta consumir um token do bucket.
     * 
     * @param tokensToConsume Número de tokens a consumir (padrão: 1)
     * @return true se o token foi consumido com sucesso, false caso contrário
     */
    fun tryConsume(tokensToConsume: Long = 1): Boolean {
        lock.withLock {
            refill()
            
            return if (tokens >= tokensToConsume) {
                tokens -= tokensToConsume
                true
            } else {
                false
            }
        }
    }

    /**
     * Consome um token do bucket, aguardando até que tokens estejam disponíveis.
     * Este método suspende a coroutine até que um token esteja disponível.
     * 
     * @param tokensToConsume Número de tokens a consumir (padrão: 1)
     */
    suspend fun consume(tokensToConsume: Long = 1) {
        while (true) {
            val consumed = lock.withLock {
                refill()
                
                if (tokens >= tokensToConsume) {
                    tokens -= tokensToConsume
                    true
                } else {
                    false
                }
            }
            
            if (consumed) {
                return
            }
            
            // Calcula quanto tempo esperar até o próximo token
            val waitTimeMillis = calculateWaitTimeMillis()
            delay(waitTimeMillis)
        }
    }

    /**
     * Calcula quanto tempo (em milissegundos) deve esperar até o próximo token.
     */
    private fun calculateWaitTimeMillis(): Long {
        lock.withLock {
            refill()
            
            if (tokens >= 1) {
                return 100 // Se tokens estão disponíveis, aguarda pouco tempo
            }
            
            // Calcula quanto tempo falta para adicionar pelo menos 1 token
            val refillPeriodMillis = refillPeriodSeconds * 1000
            val millisPerToken = refillPeriodMillis / refillRate
            
            return millisPerToken
        }
    }

    /**
     * Reabastece o bucket com tokens baseado no tempo decorrido.
     * Chamado automaticamente antes de cada tentativa de consumo.
     */
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

    /**
     * Retorna o número de tokens disponíveis atualmente.
     */
    fun availableTokens(): Long {
        lock.withLock {
            refill()
            return tokens
        }
    }

    /**
     * Calcula quanto tempo (em segundos) o cliente deve esperar antes de tentar novamente.
     */
    fun getRetryAfterSeconds(): Long {
        lock.withLock {
            refill()
            
            if (tokens >= 1) {
                return 0
            }
            
            // Calcula quanto tempo falta para adicionar pelo menos 1 token
            val refillPeriodMillis = refillPeriodSeconds * 1000
            val millisPerToken = refillPeriodMillis / refillRate
            
            return (millisPerToken / 1000) + 1
        }
    }

    /**
     * Reseta o bucket para o estado inicial (útil para testes).
     */
    fun reset() {
        lock.withLock {
            tokens = capacity
            lastRefillTimestamp.set(System.currentTimeMillis())
        }
    }

    override fun toString(): String {
        return "TokenBucket(capacity=$capacity, refillRate=$refillRate tokens/${refillPeriodSeconds}s, available=$tokens)"
    }
}

/**
 * Exceção lançada quando o rate limit é excedido.
 */
class RateLimitExceededException(
    message: String,
    val retryAfter: Long
) : Exception(message)
