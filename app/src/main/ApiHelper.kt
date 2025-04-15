package com.example.currencylens

import okhttp3.*
import org.json.JSONObject
import java.io.IOException

object ApiHelper {
    private val client = OkHttpClient()
    private const val API_KEY = "YOUR_API_KEY"
    private const val BASE_URL = "https://v6.exchangerate-api.com/v6/$API_KEY/pair"

    fun convertCurrency(amount: Double, from: String, to: String, callback: (Double) -> Unit) {
        val url = "$BASE_URL/$from/$to/$amount"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let {
                    val result = JSONObject(it).optDouble("conversion_result", 0.0)
                    callback(result)
                }
            }
        })
    }
}
