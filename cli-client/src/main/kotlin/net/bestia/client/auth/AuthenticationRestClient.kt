package net.bestia.client.auth

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.bestia.login.eip712.Eip712AuthRequest
import net.bestia.login.eip712.Eip712AuthResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class AuthenticationRestClient(
    private val baseUrl: String = "http://localhost:8080"
) {
    private val client = OkHttpClient()
    private val objectMapper = jacksonObjectMapper()

    @Throws(IOException::class)
    fun authenticate(request: Eip712AuthRequest): Eip712AuthResponse {
        val jsonBody = objectMapper.writeValueAsString(request)
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url("$baseUrl/api/v1/auth/eip712sig")
            .post(requestBody)
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")

            return if (response.isSuccessful) {
                val successResponse: Map<String, Any> = objectMapper.readValue(responseBody)
                Eip712AuthResponse.Success(
                    wallet = successResponse["wallet"] as String,
                    tokenIndex = (successResponse["tokenIndex"] as Number).toLong(),
                    token = successResponse["token"] as String
                )
            } else {
                val errorResponse: Map<String, String> = objectMapper.readValue(responseBody)
                Eip712AuthResponse.Failure(
                    error = errorResponse["error"] ?: "Unknown error"
                )
            }
        }
    }
}
