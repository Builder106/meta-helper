package com.metahelper.app

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

import android.util.Log

class ApiClient(private val baseUrl: String) {
    private val client = OkHttpClient()

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
                    val audioBytes = response.body?.bytes()
                    if (audioBytes != null) {
                        Log.d("ApiClient", "Success! Received ${audioBytes.size} audio bytes")
                        callback.onSuccess(audioBytes)
                    } else {
                        Log.e("ApiClient", "Error: Response body is null")
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

