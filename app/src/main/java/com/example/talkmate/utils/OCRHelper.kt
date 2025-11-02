package com.example.talkmate.utils

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class OCRHelper {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractTextFromBitmap(bitmap: Bitmap): Result<String> {
        return try {
            Log.d("OCRHelper", "Starting text extraction from bitmap")

            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(image).await()

            val extractedText = visionText.text

            Log.d("OCRHelper", "Extracted text: $extractedText")

            if (extractedText.isBlank()) {
                Result.failure(Exception("No text found in image"))
            } else {
                Result.success(extractedText)
            }
        } catch (e: Exception) {
            Log.e("OCRHelper", "Error extracting text: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun cleanup() {
        try {
            recognizer.close()
        } catch (e: Exception) {
            Log.e("OCRHelper", "Error closing recognizer: ${e.message}")
        }
    }
}