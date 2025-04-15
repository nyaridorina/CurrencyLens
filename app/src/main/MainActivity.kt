// MainActivity.kt
package com.example.currencylens

import android.Manifest
import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var overlayText: TextView
    private lateinit var spinnerFrom: Spinner
    private lateinit var spinnerTo: Spinner
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var fromCurrency = "USD"
    private var toCurrency = "EUR"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlayText = findViewById(R.id.overlayText)
        spinnerFrom = findViewById(R.id.spinnerFrom)
        spinnerTo = findViewById(R.id.spinnerTo)

        setupSpinners()
        requestPermissions()
    }

    private fun setupSpinners() {
        val currencies = listOf("USD", "EUR", "HUF", "GBP")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, currencies)
        spinnerFrom.adapter = adapter
        spinnerTo.adapter = adapter

        spinnerFrom.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                fromCurrency = currencies[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        spinnerTo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                toCurrency = currencies[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun requestPermissions() {
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, TextAnalyzer { text ->
                        runOnUiThread {
                            overlayText.text = text
                            overlayText.visibility = View.VISIBLE
                        }
                    }, fromCurrency, toCurrency))
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, analyzer)
        }, ContextCompat.getMainExecutor(this))
    }
}

// TextAnalyzer.kt
package com.example.currencylens

import android.util.Log
import android.widget.TextView
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.regex.Pattern

class TextAnalyzer(
    private val updateText: (String) -> Unit,
    private val fromCurrency: String,
    private val toCurrency: String
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient()
    private var lastDetected: String? = null

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return imageProxy.close()
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val pattern = Pattern.compile("\\d+(?:[.,]\\d{1,2})?")
                val matcher = pattern.matcher(visionText.text)
                if (matcher.find()) {
                    val value = matcher.group().replace(",", ".")
                    if (value != lastDetected) {
                        lastDetected = value
                        convert(value.toDouble(), fromCurrency, toCurrency)
                    }
                }
            }
            .addOnFailureListener { Log.e("TextAnalyzer", "OCR failed", it) }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun convert(amount: Double, from: String, to: String) {
        ApiHelper.convertCurrency(amount, from, to) { result ->
            updateText("$to ${String.format("%.2f", result)}")
        }
    }
}

// ApiHelper.kt
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
