package com.maayys.agent

interface RecognitionResult { val hit: Boolean }
interface CustomRecognition { fun recognize(image: ByteArray, param: String = ""): RecognitionResult }
data class BasicRecognitionResult(override val hit: Boolean, val score: Double = 0.0) : RecognitionResult
class RecognitionRegistry {
    private val recognizers = mutableMapOf<String, CustomRecognition>()
    fun register(name: String, recognition: CustomRecognition) { recognizers[name] = recognition }
    fun recognize(name: String, image: ByteArray, param: String = ""): RecognitionResult? = recognizers[name]?.recognize(image, param)
}