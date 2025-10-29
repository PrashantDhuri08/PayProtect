package com.example.scamdetector

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TfLiteHelper(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var vocab: Map<String, Int> = mapOf()

    companion object {
        private const val TAG = "TfLiteHelper"
        private const val MODEL_FILE = "mobilebert_spam_detector.tflite"
        private const val VOCAB_FILE = "vocab.json" // Standard name for Hugging Face tokenizers
        private const val MAX_LEN = 128 // This MUST match the model's training length
        private const val CLS_TOKEN = "[CLS]"
        private const val SEP_TOKEN = "[SEP]"
        private const val UNK_TOKEN = "[UNK]"
    }

    init {
        // This block runs when the class is created. We'll check for files here.
        if (!assetExists(MODEL_FILE) || !assetExists(VOCAB_FILE)) {
            Log.e(TAG, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
            Log.e(TAG, "!!! CRITICAL ERROR: Model or Vocab file not found.")
            Log.e(TAG, "!!! Make sure '$MODEL_FILE' and '$VOCAB_FILE' are placed in the 'app/src/main/assets' folder.")
            Log.e(TAG, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        } else {
            try {
                val modelByteBuffer = loadModelFile(context.assets, MODEL_FILE)
                // Use GPU delegate for better performance
                val options = Interpreter.Options()
                // val compatList = CompatibilityList()
                // if(compatList.isDelegateSupportedOnThisDevice){
                //     options.addDelegate(GpuDelegate(compatList.bestOptionsForThisDevice))
                // } else {
                //     options.setNumThreads(4)
                // }
                interpreter = Interpreter(modelByteBuffer, options)
                loadVocabulary()
                Log.d(TAG, "TfLiteHelper initialized successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error during TFLite interpreter or vocabulary initialization.", e)
                interpreter = null // Ensure interpreter is null on failure
            }
        }
    }

    /**
     * Checks if a file exists in the assets folder.
     */
    private fun assetExists(fileName: String): Boolean {
        return try {
            context.assets.open(fileName).close()
            true
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Loads the TensorFlow Lite model from the assets folder.
     */
    @Throws(IOException::class)
    private fun loadModelFile(assetManager: AssetManager, modelPath: String): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Loads the vocabulary from the JSON file in the assets folder.
     */
    private fun loadVocabulary() {
        try {
            val jsonString = context.assets.open(VOCAB_FILE).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val mutableVocab = mutableMapOf<String, Int>()
            for (key in jsonObject.keys()) {
                mutableVocab[key] = jsonObject.getInt(key)
            }
            vocab = mutableVocab
            Log.d(TAG, "Vocabulary loaded with ${vocab.size} words.")
        } catch (e: IOException) {
            Log.e(TAG, "Error loading vocabulary from assets.", e)
            vocab = mapOf() // Ensure vocab is empty on failure
        }
    }

    /**
     * Tokenizes and prepares the input text for the BERT model.
     */
    private fun preprocessText(text: String): Pair<IntArray, IntArray> {
        val unkId = vocab[UNK_TOKEN] ?: 100
        val tokens = text.lowercase().split(" ").toMutableList()

        // Truncate if necessary to fit within [CLS] and [SEP]
        if (tokens.size > MAX_LEN - 2) {
            tokens.subList(MAX_LEN - 2, tokens.size).clear()
        }

        val tokenIds = mutableListOf<Int>()
        tokenIds.add(vocab[CLS_TOKEN] ?: 101) // Add [CLS] token
        tokens.mapTo(tokenIds) { token -> vocab[token] ?: unkId }
        tokenIds.add(vocab[SEP_TOKEN] ?: 102) // Add [SEP] token

        val inputIds = IntArray(MAX_LEN) { 0 }
        val attentionMask = IntArray(MAX_LEN) { 0 }

        tokenIds.forEachIndexed { index, id ->
            inputIds[index] = id
            attentionMask[index] = 1 // Mark real tokens
        }

        return Pair(inputIds, attentionMask)
    }

    fun classify(text: String): Pair<String, Float> {
        if (interpreter == null || vocab.isEmpty()) {
            Log.e(TAG, "Cannot classify: Interpreter or vocabulary not initialized.")
            return Pair("Error", 0.0f)
        }

        try {
            // 1. Preprocess text to get input_ids and attention_mask
            val (inputIds, attentionMask) = preprocessText(text)

            // 2. Prepare input buffers
            val inputIdsBuffer = ByteBuffer.allocateDirect(MAX_LEN * 4).apply {
                order(ByteOrder.nativeOrder())
                asIntBuffer().put(inputIds)
            }
            val attentionMaskBuffer = ByteBuffer.allocateDirect(MAX_LEN * 4).apply {
                order(ByteOrder.nativeOrder())
                asIntBuffer().put(attentionMask)
            }

            val inputs = arrayOf(inputIdsBuffer, attentionMaskBuffer)
            val outputs = mutableMapOf<Int, Any>()
            // The output is an array of logits for 2 classes ("not spam", "spam")
            val outputLogits = Array(1) { FloatArray(2) }
            outputs[0] = outputLogits

            // 3. Run inference
            interpreter?.runForMultipleInputsOutputs(inputs, outputs)

            // 4. Process the output
            val logits = outputLogits[0]
            val probabilities = softmax(logits)

            val predictedClassId = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
            val confidence = if(predictedClassId != -1) probabilities[predictedClassId] else 0.0f

            // As per the training script: 0 is "not spam", 1 is "spam"
            val prediction = if (predictedClassId == 1) "Scam" else "Not Scam"

            return Pair(prediction, confidence)
        } catch (e: Exception) {
            Log.e(TAG, "Error during classification.", e)
            return Pair("Error", 0.0f)
        }
    }

    /**
     * Converts raw logit scores into probabilities.
     */
    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0.0f
        val expLogits = logits.map { kotlin.math.exp(it - maxLogit) }
        val sumExpLogits = expLogits.sum()
        return expLogits.map { it / sumExpLogits }.toFloatArray()
    }


    fun close() {
        interpreter?.close()
        interpreter = null
    }
}

//
//package com.example.scamdetector
//
//import android.content.Context
//import android.content.res.AssetManager
//import android.util.Log
////import androidx.room.jarjarred.org.antlr.v4.gui.Interpreter
//import org.json.JSONObject
//import org.tensorflow.lite.Interpreter
//import java.io.FileInputStream
//import java.io.IOException
//import java.nio.MappedByteBuffer
//import java.nio.channels.FileChannel
//
//class TfLiteHelper(private val context: Context) {
//
//    private var interpreter: Interpreter? = null
//    private var vocab: Map<String, Int> = mapOf()
//
//    companion object {
//        private const val TAG = "TfLiteHelper"
//        private const val MODEL_FILE = "scam_detector_merged_data.tflite"
//        private const val VOCAB_FILE = "tokenizer_vocab.json"
//        private const val MAX_SENTENCE_LENGTH = 100
//        private const val OOV_TOKEN = "<OOV>"
//    }
//
//    init {
//        // This block runs when the class is created. We'll check for files here.
//        if (!assetExists(MODEL_FILE) || !assetExists(VOCAB_FILE)) {
//            Log.e(TAG, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
//            Log.e(TAG, "!!! CRITICAL ERROR: Model or Vocab file not found.")
//            Log.e(TAG, "!!! Make sure '$MODEL_FILE' and '$VOCAB_FILE' are placed in the 'app/src/main/assets' folder.")
//            Log.e(TAG, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
//        } else {
//            try {
//                val modelByteBuffer = loadModelFile(context.assets, MODEL_FILE)
//                val options = Interpreter.Options()
//                interpreter = Interpreter(modelByteBuffer, options)
//                loadVocabulary()
//                Log.d(TAG, "TfLiteHelper initialized successfully.")
//            } catch (e: Exception) {
//                Log.e(TAG, "Error during TFLite interpreter or vocabulary initialization.", e)
//                interpreter = null // Ensure interpreter is null on failure
//            }
//        }
//    }
//
//    /**
//     * Checks if a file exists in the assets folder.
//     */
//    private fun assetExists(fileName: String): Boolean {
//        return try {
//            context.assets.open(fileName).close()
//            true
//        } catch (e: IOException) {
//            false
//        }
//    }
//
//    /**
//     * Prepares the text by cleaning, tokenizing, and padding it.
//     */
//    private fun prepareText(text: String): Array<FloatArray> {
//        val cleanedText = cleanText(text)
//        val sequence = tokenize(cleanedText)
//        return padSequence(sequence)
//    }
//
//    /**
//     * Loads the TensorFlow Lite model from the assets folder.
//     */
//    @Throws(IOException::class)
//    private fun loadModelFile(assetManager: AssetManager, modelPath: String): MappedByteBuffer {
//        val fileDescriptor = assetManager.openFd(modelPath)
//        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
//        val fileChannel = inputStream.channel
//        val startOffset = fileDescriptor.startOffset
//        val declaredLength = fileDescriptor.declaredLength
//        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
//    }
//
//    /**
//     * Loads the vocabulary from the JSON file in the assets folder.
//     */
//    private fun loadVocabulary() {
//        try {
//            val jsonString = context.assets.open(VOCAB_FILE).bufferedReader().use { it.readText() }
//            val jsonObject = JSONObject(jsonString)
//            val mutableVocab = mutableMapOf<String, Int>()
//            for (key in jsonObject.keys()) {
//                mutableVocab[key] = jsonObject.getInt(key)
//            }
//            vocab = mutableVocab
//            Log.d(TAG, "Vocabulary loaded with ${vocab.size} words.")
//        } catch (e: IOException) {
//            Log.e(TAG, "Error loading vocabulary from assets.", e)
//            vocab = mapOf() // Ensure vocab is empty on failure
//        }
//    }
//
//    private fun cleanText(text: String): String {
//        var processedText = text.lowercase()
//        processedText = processedText.replace(Regex("https?://\\S+|www\\.\\S+"), "")
//        processedText = processedText.replace(Regex("<.*?>"), "")
//        processedText = processedText.replace(Regex("[^a-z\\s]"), "")
//        processedText = processedText.replace(Regex("\\s+"), " ").trim()
//        return processedText
//    }
//
//    private fun tokenize(text: String): List<Int> {
//        val oovIndex = vocab[OOV_TOKEN] ?: 1 // Default to 1 if OOV not found
//        return text.split(" ").map { word ->
//            vocab[word] ?: oovIndex
//        }
//    }
//
//    private fun padSequence(sequence: List<Int>): Array<FloatArray> {
//        val paddedSequence = FloatArray(MAX_SENTENCE_LENGTH) { 0f }
//        sequence.take(MAX_SENTENCE_LENGTH).forEachIndexed { index, token ->
//            paddedSequence[index] = token.toFloat()
//        }
//        // The model expects a batch, so we wrap it in another array.
//        return arrayOf(paddedSequence)
//    }
//
//    fun classify(text: String): Pair<String, Float> {
//        if (interpreter == null || vocab.isEmpty()) {
//            Log.e(TAG, "Cannot classify: Interpreter or vocabulary not initialized.")
//            return Pair("Error", 0.0f)
//        }
//
//        try {
//            val input = prepareText(text)
//            val output = Array(1) { FloatArray(1) }
//            interpreter?.run(input, output)
//
//            val confidence = output[0][0]
//            val prediction = if (confidence > 0.5) "Scam" else "Not Scam"
//            return Pair(prediction, confidence)
//        } catch (e: Exception) {
//            Log.e(TAG, "Error during classification.", e)
//            return Pair("Error", 0.0f)
//        }
//    }
//
//    fun close() {
//        interpreter?.close()
//        interpreter = null
//    }
//}
//
