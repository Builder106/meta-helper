package com.metahelper.app

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

import android.util.Log

class ApiClient(private val baseUrl: String) {
    // Render free-tier instances cold-start (~30-60s) and then Gemini + TTS add
    // more latency, well past OkHttp's ~10s defaults. Use generous timeouts and
    // let OkHttp retry a dropped connection while the instance spins up.
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    interface ApiResponseCallback {
        fun onSuccess(audioBytes: ByteArray)
        fun onError(message: String)
    }

    fun processImage(imageBytes: ByteArray, callback: ApiResponseCallback) {
        Log.d("ApiClient", "Preparing to send image to backend: $baseUrl")
        Log.d("ApiClient", "Image size: ${imageBytes.size} bytes")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "image.jpg",
                imageBytes.toRequestBody("image/jpeg".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("$baseUrl/process-image")
            .post(requestBody)
            .build()

        Log.d("ApiClient", "Request built. Executing call...")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiClient", "Network failure: ${e.message}")
                callback.onError(e.message ?: "Unknown error")
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("ApiClient", "Response received. Code: ${response.code}")
                if (response.isSuccessful) {
                    val audioBytes = response.body.bytes()
                    if (audioBytes.isNotEmpty()) {
                        Log.d("ApiClient", "Success! Received ${audioBytes.size} audio bytes")
                        callback.onSuccess(audioBytes)
                    } else {
                        Log.e("ApiClient", "Error: Response body is empty")
                        callback.onError("Empty response body")
                    }
                } else {
                    val errorMsg = "Server error: ${response.code} - ${response.message}"
                    Log.e("ApiClient", errorMsg)
                    callback.onError(errorMsg)
                }
            }
        })
    }
}
