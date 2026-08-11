package com.metahelper.shared

import io.ktor.client.*
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Headers
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
data class ProcessImageResponse(
    val audioBase64: String,
    val success: Boolean,
    val error: String?
)

class ApiClient(private val baseUrl: String) {
    private val client = HttpClient(Darwin) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        // Render free-tier instances cold-start (~30-60s) and then Gemini + TTS add
        // more latency, well past default timeouts. Use generous timeouts.
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 120_000
        }
    }

    interface ApiResponseCallback {
        fun onSuccess(audioBytes: ByteArray)
        fun onError(message: String)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun processImage(imageBytes: ByteArray, callback: ApiResponseCallback) {
        println("ApiClient: Preparing to send image to backend: $baseUrl")
        println("ApiClient: Image size: ${imageBytes.size} bytes")

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val response: HttpResponse = client.post("$baseUrl/process-image") {
                    contentType(ContentType.MultiPart.FormData)
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append(
                                    "file",
                                    imageBytes,
                                    Headers.build {
                                        append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"image.jpg\"")
                                        append(HttpHeaders.ContentType, "image/jpeg")
                                    }
                                )
                            }
                        )
                    )
                }

                val responseBody = response.bodyAsText()
                println("ApiClient: Response received: $responseBody")

                val processResponse = Json { ignoreUnknownKeys = true }.decodeFromString<ProcessImageResponse>(responseBody)

                if (processResponse.success) {
                    val audioBytes = Base64.decode(processResponse.audioBase64)
                    println("ApiClient: Success! Received ${audioBytes.size} audio bytes")
                    callback.onSuccess(audioBytes)
                } else {
                    val errorMsg = processResponse.error ?: "Unknown server error"
                    println("ApiClient: Error: $errorMsg")
                    callback.onError(errorMsg)
                }
            } catch (e: Exception) {
                println("ApiClient: Network failure: ${e.message}")
                callback.onError(e.message ?: "Unknown error")
            }
        }
    }
}
