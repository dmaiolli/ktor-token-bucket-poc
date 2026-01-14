package com.picpay.poc

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import kotlinx.coroutines.future.await
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * Rate Limiter usando Bucket4j com suporte para Kotlin Coroutines.
 *
 * Implementa Token Bucket algorithm que garante exatamente N requisições por segundo
 * sem permitir bursts que violem o limite.
 *
 * @param tokensPerSecond Número máximo de requisições permitidas por segundo
 * @param scheduler ScheduledExecutorService para agendamento não-bloqueante (opcional)
 *
 * @see <a href="https://bucket4j.com/">Bucket4j Documentation</a>
 */
class TokenBucketRateLimiterV2(
    private val tokensPerSecond: Int,
    private val scheduler: ScheduledExecutorService = defaultScheduler
) {
    companion object {
        private val defaultScheduler: ScheduledExecutorService by lazy {
            Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors(),
                Thread.ofVirtual().name("rate-limiter-", 0).factory()
            )
        }
    }

    private val bucket: Bucket = Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(tokensPerSecond.toLong())
                .refillGreedy(tokensPerSecond.toLong(), Duration.ofSeconds(1))
                .build()
        )
        .build()

    /**
     * Executa o bloco respeitando o rate limit.
     * Aguarda automaticamente se o limite for atingido usando asScheduler() não-bloqueante.
     *
     * @param block Bloco suspendível a ser executado
     * @return Result contendo o resultado do bloco ou erro
     */
    suspend fun <T> withRateLimit(block: suspend () -> T): Result<T> = runCatching {
        val availableTokensBefore = bucket.availableTokens

        if (availableTokensBefore == 0L) {
            val estimatedWaitTime = calculateEstimatedWaitTime()
            print("Rate limit atingido. Aguardando token disponível. Próxima execução estimada em: ${estimatedWaitTime}ms")
        }

        val startTime = Instant.now()
        // Pausa a thread de forma não-bloqueante até que um token esteja disponível
        bucket.asScheduler().consume(1, scheduler).await()
        val waitTime = Duration.between(startTime, Instant.now()).toMillis()

        if (waitTime > 10) {
            print("Token adquirido após aguardar ${waitTime}ms. Tokens disponíveis agora: ${bucket.availableTokens}")
        }

        block()
    }

    /**
     * Apenas aguarda e consome um token, sem executar nenhum bloco.
     * Útil quando você quer controlar o rate limit mas não quer que o token
     * seja segurado durante a execução da operação.
     */
    suspend fun acquireToken() {
        val availableTokensBefore = bucket.availableTokens

        val startTime = Instant.now()
        bucket.asScheduler().consume(1, scheduler).await()
        val waitTime = Duration.between(startTime, Instant.now()).toMillis()

        if (waitTime > 10) {
            print("Token adquirido após aguardar ${waitTime}ms. Tokens disponíveis agora: ${bucket.availableTokens}")
        }
    }

    /**
     * Tenta executar o bloco sem aguardar.
     * Retorna falha imediatamente se não houver tokens disponíveis.
     *
     * @param block Bloco suspendível a ser executado
     * @return Result contendo o resultado do bloco ou RateLimitExceededException
     */
    suspend fun <T> tryWithRateLimit(block: suspend () -> T): Result<T> = runCatching {
        if (bucket.tryConsume(1)) {
            block()
        } else {
            throw RateLimitExceededException("Rate limit exceeded: $tokensPerSecond req/s")
        }
    }

    /**
     * Retorna o número de tokens disponíveis no momento.
     */
    fun availableTokens(): Long {
        return bucket.availableTokens
    }

    /**
     * Calcula o tempo estimado de espera até o próximo token estar disponível (em milissegundos).
     */
    private fun calculateEstimatedWaitTime(): Long {
        return (1000L / tokensPerSecond)
    }
}

/**
 * Exceção lançada quando o rate limit é excedido.
 */
class RateLimitExceededException(
    message: String
) : Exception(message)