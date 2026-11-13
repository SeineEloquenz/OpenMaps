package nz.eloque.openmaps.core.platform

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Shared client for all public APIs. Transitous and Nominatim both require an identifying [userAgent]. */
fun createHttpClient(userAgent: String): HttpClient =
    HttpClient {
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                },
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
        }
        defaultRequest {
            headers.append(HttpHeaders.UserAgent, userAgent)
        }
    }
