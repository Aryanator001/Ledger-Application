package com.example

import org.junit.Assert.*
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun testApi() {
    val apiKey = com.example.BuildConfig.GEMINI_API_KEY
    val client = OkHttpClient()
    val request = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey").build()
    val response = client.newCall(request).execute()
    println(response.body?.string())
  }
}

