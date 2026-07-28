package com.fenn.callshield.network

import com.fenn.callshield.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiClient @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val http: HttpClient = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) { requestTimeoutMillis = 8_000 }
        install(Logging) { level = LogLevel.NONE } // set to HEADERS in debug builds
        defaultRequest {
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
        }
        engine {
            connectTimeout = 5_000
            socketTimeout = 5_000
        }
    }

    private val baseUrl = "${BuildConfig.SUPABASE_URL}/functions/v1"

    private fun checkConfigured() {
        check(BuildConfig.SUPABASE_URL.isNotBlank()) {
            "SUPABASE_URL is not configured. Add it to local.properties."
        }
    }

    suspend fun postReport(
        numberHash: String,
        deviceTokenHash: String,
        category: String,
    ): Boolean {
        checkConfigured()
        val response = http.post("$baseUrl/report") {
            contentType(ContentType.Application.Json)
            setBody(ReportRequest(number_hash = numberHash, device_token_hash = deviceTokenHash, category = category))
        }
        if (response.status != HttpStatusCode.OK) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return true
    }

    suspend fun postCorrect(
        numberHash: String,
        deviceTokenHash: String,
    ): Boolean {
        checkConfigured()
        val response = http.post("$baseUrl/correct") {
            contentType(ContentType.Application.Json)
            setBody(CorrectRequest(number_hash = numberHash, device_token_hash = deviceTokenHash))
        }
        if (response.status != HttpStatusCode.OK) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return true
    }

    suspend fun getSeedDbManifest(deviceTokenHash: String): SeedDbManifestResponse {
        val response = http.get("$baseUrl/seed-db-manifest") {
            header("x-device-token", deviceTokenHash)
        }
        if (response.status != HttpStatusCode.OK) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return json.decodeFromString(response.bodyAsText())
    }
}

// ---- Request/Response models ----

@Serializable
data class ReportRequest(
    val number_hash: String,
    val device_token_hash: String,
    val category: String,
)

@Serializable
data class CorrectRequest(
    val number_hash: String,
    val device_token_hash: String,
)

@Serializable
data class SeedDbManifestResponse(
    val version: Int,
    val sha256: String,
    val download_url: String,
)

class ApiException(val statusCode: Int, message: String) : Exception("API error $statusCode: $message")
