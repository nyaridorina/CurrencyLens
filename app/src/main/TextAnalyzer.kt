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
