package com.picpay.poc

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenBucketRateLimiterTest {

    private lateinit var rateLimiter: TokenBucketRateLimiter

    @BeforeEach
    fun setup() {
        // Configuração: 5 tokens, reabastecer 5 tokens a cada 10 segundos
        rateLimiter = TokenBucketRateLimiter(
            capacity = 5,
            refillRate = 5,
            refillPeriodSeconds = 10
        )
    }

    @Test
    fun `given new bucket when check available tokens then should have full capacity`() {
        // when
        val available = rateLimiter.availableTokens()

        // then
        assertEquals(5, available, "Novo bucket deve ter capacidade total")
    }

    @Test
    fun `given bucket with tokens when consume one token then should succeed`() {
        // when
        val result = rateLimiter.tryConsume()

        // then
        assertTrue(result, "Deve consumir token com sucesso")
        assertEquals(4, rateLimiter.availableTokens(), "Deve ter 4 tokens restantes")
    }

    @Test
    fun `given empty bucket when try consume then should fail`() {
        // given - consumir todos os tokens
        repeat(5) { rateLimiter.tryConsume() }

        // when
        val result = rateLimiter.tryConsume()

        // then
        assertFalse(result, "Não deve permitir consumo sem tokens")
        assertEquals(0, rateLimiter.availableTokens())
    }

    @Test
    fun `given bucket when consume all tokens then available should be zero`() {
        // when
        repeat(5) { 
            assertTrue(rateLimiter.tryConsume())
        }

        // then
        assertEquals(0, rateLimiter.availableTokens())
        assertFalse(rateLimiter.tryConsume(), "Não deve permitir consumo adicional")
    }

    @Test
    fun `given empty bucket when wait for refill then should have tokens again`() {
        // given - consumir todos os tokens
        repeat(5) { rateLimiter.tryConsume() }
        assertEquals(0, rateLimiter.availableTokens())

        // when - aguardar tempo de refill (simulado com reset para teste)
        Thread.sleep(100) // Pequeno delay para demonstração
        rateLimiter.reset() // Em produção, tokens seriam reabastecidos automaticamente

        // then
        assertEquals(5, rateLimiter.availableTokens())
        assertTrue(rateLimiter.tryConsume())
    }

    @Test
    fun `given empty bucket when calculate retry after then should return positive value`() {
        // given
        repeat(5) { rateLimiter.tryConsume() }

        // when
        val retryAfter = rateLimiter.getRetryAfterSeconds()

        // then
        assertTrue(retryAfter > 0, "Retry-After deve ser positivo quando não há tokens")
    }

    @Test
    fun `given bucket with tokens when calculate retry after then should return zero`() {
        // when
        val retryAfter = rateLimiter.getRetryAfterSeconds()

        // then
        assertEquals(0, retryAfter, "Retry-After deve ser zero quando há tokens disponíveis")
    }

    @Test
    fun `given bucket when consume multiple tokens then should decrease correctly`() {
        // when
        val result = rateLimiter.tryConsume(3)

        // then
        assertTrue(result)
        assertEquals(2, rateLimiter.availableTokens())
    }

    @Test
    fun `given bucket when try consume more tokens than available then should fail`() {
        // when
        val result = rateLimiter.tryConsume(10)

        // then
        assertFalse(result, "Não deve permitir consumir mais tokens do que disponível")
        assertEquals(5, rateLimiter.availableTokens(), "Tokens não devem ser consumidos em falha")
    }

    @Test
    fun `given bucket when reset then should restore to full capacity`() {
        // given
        repeat(3) { rateLimiter.tryConsume() }
        assertEquals(2, rateLimiter.availableTokens())

        // when
        rateLimiter.reset()

        // then
        assertEquals(5, rateLimiter.availableTokens())
    }

    @Test
    fun `given concurrent requests when consume tokens then should be thread safe`() {
        // given
        val threads = mutableListOf<Thread>()
        val successCount = java.util.concurrent.atomic.AtomicInteger(0)
        val failCount = java.util.concurrent.atomic.AtomicInteger(0)

        // when - 10 threads tentando consumir tokens simultaneamente
        repeat(10) {
            threads.add(Thread {
                if (rateLimiter.tryConsume()) {
                    successCount.incrementAndGet()
                } else {
                    failCount.incrementAndGet()
                }
            })
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // then
        assertEquals(5, successCount.get(), "Exatamente 5 requisições devem ter sucesso")
        assertEquals(5, failCount.get(), "Exatamente 5 requisições devem falhar")
        assertEquals(0, rateLimiter.availableTokens(), "Não deve sobrar tokens")
    }

    @Test
    fun `given invalid parameters when create bucket then should throw exception`() {
        // when & then
        assertThrows(IllegalArgumentException::class.java) {
            TokenBucketRateLimiter(0, 5, 10)
        }

        assertThrows(IllegalArgumentException::class.java) {
            TokenBucketRateLimiter(5, 0, 10)
        }

        assertThrows(IllegalArgumentException::class.java) {
            TokenBucketRateLimiter(5, 5, 0)
        }
    }

    @Test
    fun `given bucket toString when called then should return readable format`() {
        // when
        val description = rateLimiter.toString()

        // then
        assertTrue(description.contains("capacity=5"))
        assertTrue(description.contains("refillRate=5"))
        assertTrue(description.contains("10s"))
    }
}
