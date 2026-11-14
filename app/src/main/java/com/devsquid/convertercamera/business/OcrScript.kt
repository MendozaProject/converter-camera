package com.devsquid.convertercamera.business

import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Script used for ML Kit text recognition. Every non-Latin recognizer also reads Latin text,
 * so picking one never loses the digits themselves.
 */
enum class OcrScript {
    LATIN, JAPANESE, CHINESE, KOREAN;

    fun createRecognizer(): TextRecognizer = when (this) {
        LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    companion object {
        /** Chooses the script whose price labels are likely to carry native currency markers. */
        fun forCurrency(isoCode: String): OcrScript = when (isoCode.uppercase()) {
            "JPY" -> JAPANESE
            "CNY", "TWD", "HKD", "MOP" -> CHINESE
            "KRW", "KPW" -> KOREAN
            else -> LATIN
        }
    }
}
