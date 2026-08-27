package com.example.data.api

import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object SupabaseConfig {
    const val PROJECT_URL = "https://xjxzkjyotbumxtddkepd.supabase.co"
    const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhqeHpranlvdGJ1bXh0ZGRrZXBkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODczOTI3MTUsImV4cCI6MjEwMjk2ODcxNX0.4pjCk64bUWROZCR3xD1r6CN1H95daqwTYzL4huk_1Ac"
    const val FUNCTIONS_URL = "$PROJECT_URL/functions/v1"
}

class SupabaseClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        })
        .build()

    // ==================== AUTH METHODS ====================

    suspend fun signInWithPassword(email: String, password: String): AuthResponse = withContext(Dispatchers.IO) {
        val url = "${SupabaseConfig.PROJECT_URL}/auth/v1/token?grant_type=password"
        val bodyJson = JSONObject().apply {
            put("email", email)
            put("password", password)
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val errorMsg = try {
                val json = JSONObject(responseBody)
                json.optString("error_description", json.optString("msg", json.optString("error", "Sign in failed (${response.code})")))
            } catch (e: Exception) {
                "Sign in failed (${response.code})"
            }
            throw IOException(errorMsg)
        }

        moshi.adapter(AuthResponse::class.java).fromJson(responseBody)
            ?: throw IOException("Failed to parse auth response")
    }

    suspend fun listFactors(accessToken: String): FactorListResponse = withContext(Dispatchers.IO) {
        val url = "${SupabaseConfig.PROJECT_URL}/auth/v1/factors"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IOException("Failed to list factors: ${response.code} $responseBody")
        }

        // Response is either {"all": [...], "totp": [...]} or a list
        try {
            moshi.adapter(FactorListResponse::class.java).fromJson(responseBody)
                ?: FactorListResponse()
        } catch (e: Exception) {
            val listType = Types.newParameterizedType(List::class.java, FactorDto::class.java)
            val factors = moshi.adapter<List<FactorDto>>(listType).fromJson(responseBody) ?: emptyList()
            FactorListResponse(all = factors, totp = factors.filter { it.factorType == "totp" })
        }
    }

    suspend fun enrollTotp(accessToken: String): FactorDto = withContext(Dispatchers.IO) {
        val url = "${SupabaseConfig.PROJECT_URL}/auth/v1/factors"
        val bodyJson = JSONObject().apply {
            put("factor_type", "totp")
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val err = parseErrorMessage(responseBody, "Failed to enroll MFA (${response.code})")
            throw IOException(err)
        }

        moshi.adapter(FactorDto::class.java).fromJson(responseBody)
            ?: throw IOException("Failed to parse enrolled factor")
    }

    suspend fun unenrollFactor(accessToken: String, factorId: String) = withContext(Dispatchers.IO) {
        val url = "${SupabaseConfig.PROJECT_URL}/auth/v1/factors/$factorId"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer $accessToken")
            .delete()
            .build()

        okHttpClient.newCall(request).execute().close()
    }

    suspend fun challengeFactor(accessToken: String, factorId: String): ChallengeResponse = withContext(Dispatchers.IO) {
        val url = "${SupabaseConfig.PROJECT_URL}/auth/v1/factors/$factorId/challenge"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer $accessToken")
            .post("{}".toRequestBody(jsonMediaType))
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val err = parseErrorMessage(responseBody, "Challenge failed (${response.code})")
            throw IOException(err)
        }

        moshi.adapter(ChallengeResponse::class.java).fromJson(responseBody)
            ?: throw IOException("Failed to parse challenge response")
    }

    suspend fun verifyChallenge(accessToken: String, factorId: String, challengeId: String, code: String): AuthResponse = withContext(Dispatchers.IO) {
        val url = "${SupabaseConfig.PROJECT_URL}/auth/v1/factors/$factorId/verify"
        val bodyJson = JSONObject().apply {
            put("challenge_id", challengeId)
            put("code", code)
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val err = parseErrorMessage(responseBody, "Verification failed (${response.code})")
            throw IOException(err)
        }

        moshi.adapter(AuthResponse::class.java).fromJson(responseBody)
            ?: throw IOException("Failed to parse verify response")
    }

    // NOTE: there used to be a getAal() here calling GET /auth/v1/aal —
    // removed. That endpoint does not exist in Supabase's real GoTrue API;
    // the Authenticator Assurance Level is a claim inside the JWT itself
    // (decoded locally, see LitigationRepository's token-expiry check),
    // not something fetched over the network. It was never called from
    // anywhere in this app — MFA enforcement in signIn()/
    // completeMfaChallenge() already does the real check correctly
    // (unconditionally via listFactors(), not via this).

    suspend fun refreshSession(refreshToken: String): AuthResponse = withContext(Dispatchers.IO) {
        val url = "${SupabaseConfig.PROJECT_URL}/auth/v1/token?grant_type=refresh_token"
        val bodyJson = JSONObject().apply {
            put("refresh_token", refreshToken)
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val errorMsg = parseErrorMessage(responseBody, "Session refresh failed (${response.code})")
            throw IOException(errorMsg)
        }

        moshi.adapter(AuthResponse::class.java).fromJson(responseBody)
            ?: throw IOException("Failed to parse refresh response")
    }

    // ==================== REST QUERIES ====================

    suspend fun <T> queryRestList(
        tablePath: String,
        accessToken: String,
        itemClass: Class<T>
    ): List<T> = withContext(Dispatchers.IO) {
        val url = "${SupabaseConfig.PROJECT_URL}/rest/v1/$tablePath"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val err = parseErrorMessage(responseBody, "Query failed for $tablePath (${response.code})")
            throw IOException(err)
        }

        val listType = Types.newParameterizedType(List::class.java, itemClass)
        moshi.adapter<List<T>>(listType).fromJson(responseBody) ?: emptyList()
    }

    suspend fun <T> queryRestSingle(
        tablePath: String,
        accessToken: String,
        itemClass: Class<T>
    ): T? = withContext(Dispatchers.IO) {
        val url = "${SupabaseConfig.PROJECT_URL}/rest/v1/$tablePath"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            return@withContext null
        }

        val listType = Types.newParameterizedType(List::class.java, itemClass)
        val list = moshi.adapter<List<T>>(listType).fromJson(responseBody)
        list?.firstOrNull()
    }

    // ==================== EDGE FUNCTION CALLS ====================

    suspend fun callEdgeFunction(
        functionName: String,
        bodyJson: String,
        accessToken: String
    ): String = withContext(Dispatchers.IO) {
        val url = "${SupabaseConfig.FUNCTIONS_URL}/$functionName"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val errorMsg = parseErrorMessage(responseBody, "$functionName failed (${response.code})")
            throw IOException(errorMsg)
        }

        responseBody
    }

    suspend fun uploadToSignedUrl(
        uploadUrl: String,
        token: String?,
        bytes: ByteArray,
        mimeType: String
    ): Boolean = withContext(Dispatchers.IO) {
        val mediaType = (mimeType.ifEmpty { "application/octet-stream" }).toMediaType()
        val requestBuilder = Request.Builder()
            .url(uploadUrl)
            .put(bytes.toRequestBody(mediaType))

        if (!token.isNullOrEmpty()) {
            requestBuilder.addHeader("x-upsert", "true")
        }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        response.isSuccessful
    }

    private fun parseErrorMessage(responseBody: String, defaultMsg: String): String {
        return try {
            val json = JSONObject(responseBody)
            if (json.has("error")) json.getString("error")
            else if (json.has("message")) json.getString("message")
            else if (json.has("msg")) json.getString("msg")
            else defaultMsg
        } catch (e: Exception) {
            defaultMsg
        }
    }
}
