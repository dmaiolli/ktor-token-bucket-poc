package com.picpay.poc

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configurePlugins()
    configureRouting()
}

fun Application.configurePlugins() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(
                """{"error":"Internal server error","message":"${
                    cause.message?.replace(
                        "\"",
                        "\\\""
                    ) ?: "Unknown error"
                }"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }
}

fun Application.configureRouting() {
    val httpClient = HttpClient(Java) {
        install (HttpRequestRetry) {
            noRetry()
        }
        install(HttpTimeout) {
            with((30 * 1000).toLong()) {
                requestTimeoutMillis = this
                connectTimeoutMillis = this
                socketTimeoutMillis = this
            }
        }
        engine {
            pipelining = true
        }
    }

    val globalRateLimiter = TokenBucketRateLimiter(
        capacity = 10,
        refillRate = 10,
        refillPeriodSeconds = 60
    )

    val apiRateLimiter = TokenBucketRateLimiter(
        capacity = 5,
        refillRate = 5,
        refillPeriodSeconds = 30
    )

    routing {
        get("/") {
            call.respondText(
                "KTOR Token Bucket Rate Limiting POC\n\nEndpoints disponíveis:\n" +
                        "- GET /api/public - Endpoint público com rate limit (AGUARDA tokens)\n" +
                        "- GET /api/github - Chama API do GitHub com rate limit (AGUARDA tokens)\n" +
                        "- GET /api/pokemon - Chama PokeAPI com rate limit (AGUARDA tokens)\n" +
                        "- GET /status - Status dos rate limiters\n" +
                        "- GET /health - Health check sem rate limit"
            )
        }

        get("/health") {
            call.respondText(
                """{"status":"UP","service":"ktor-poc"}""",
                ContentType.Application.Json
            )
        }

        get("/status") {
            call.respondText(
                """{"message":"Servidor rodando com Rate Limiting que AGUARDA tokens","globalRateLimit":"10 requisições/minuto","apiRateLimit":"5 requisições/30 segundos","globalAvailable":${globalRateLimiter.availableTokens()},"apiAvailable":${apiRateLimiter.availableTokens()}}""",
                ContentType.Application.Json
            )
        }

        route("/api") {
            intercept(ApplicationCallPipeline.Call) {
                globalRateLimiter.consume()
                apiRateLimiter.consume()
            }

            get("/public") {
                call.respondText(
                    """{"message":"Requisição bem-sucedida!","timestamp":${System.currentTimeMillis()},"tokensRestantes":${apiRateLimiter.availableTokens()}}""",
                    ContentType.Application.Json
                )
            }

            get("/github") {
                val response = httpClient.get("https://api.github.com/users/github")
                call.respondText(
                    """{"source":"GitHub API","data":${response.bodyAsText()},"tokensRestantes":${apiRateLimiter.availableTokens()}}""",
                    ContentType.Application.Json
                )
            }

            get("/pokemon") {
                val pokemonId = (1..151).random()
                val response = httpClient.get("https://pokeapi.co/api/v2/pokemon/$pokemonId")
                call.respondText(
                    """{"source":"PokeAPI","data":${response.bodyAsText()},"tokensRestantes":${apiRateLimiter.availableTokens()}}""",
                    ContentType.Application.Json
                )
            }
        }
    }
}
