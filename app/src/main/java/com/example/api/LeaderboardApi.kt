package com.example.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface LeaderboardApi {
    @GET("leaderboard.json")
    suspend fun getLeaderboard(): Map<String, LeaderboardEntry>?

    @POST("leaderboard.json")
    suspend fun submitScore(@Body entry: LeaderboardEntry): Map<String, String>?
}

object LeaderboardClient {
    private const val BASE_URL = "https://cubes2048-arena-default-rtdb.firebaseio.com/"

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .build()
    }

    val api: LeaderboardApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(LeaderboardApi::class.java)
    }
}
