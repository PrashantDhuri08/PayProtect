package com.example.scamdetector

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
//import androidx.room.jarjarred.org.antlr.v4.gui.Interpreter
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TfLiteHelper(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var vocab: Map<String, Int> = mapOf()

    companion object {
        private const val TAG = "TfLiteHelper"
        private const val MODEL_FILE = "scam_detector_merged_data.tflite"
        private const val VOCAB_FILE = "tokenizer_vocab.json"
        private const val MAX_SENTENCE_LENGTH = 100
        private const val OOV_TOKEN = "<OOV>"
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
                val options = Interpreter.Options()
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
     * Prepares the text by cleaning, tokenizing, and padding it.
     */
    private fun prepareText(text: String): Array<FloatArray> {
        val cleanedText = cleanText(text)
        val sequence = tokenize(cleanedText)
        return padSequence(sequence)
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

    private fun cleanText(text: String): String {
        var processedText = text.lowercase()
        processedText = processedText.replace(Regex("https?://\\S+|www\\.\\S+"), "")
        processedText = processedText.replace(Regex("<.*?>"), "")
        processedText = processedText.replace(Regex("[^a-z\\s]"), "")
        processedText = processedText.replace(Regex("\\s+"), " ").trim()
        return processedText
    }

    private fun tokenize(text: String): List<Int> {
        val oovIndex = vocab[OOV_TOKEN] ?: 1 // Default to 1 if OOV not found
        return text.split(" ").map { word ->
            vocab[word] ?: oovIndex
        }
    }

    private fun padSequence(sequence: List<Int>): Array<FloatArray> {
        val paddedSequence = FloatArray(MAX_SENTENCE_LENGTH) { 0f }
        sequence.take(MAX_SENTENCE_LENGTH).forEachIndexed { index, token ->
            paddedSequence[index] = token.toFloat()
        }
        // The model expects a batch, so we wrap it in another array.
        return arrayOf(paddedSequence)
    }

    fun classify(text: String): Pair<String, Float> {
        if (interpreter == null || vocab.isEmpty()) {
            Log.e(TAG, "Cannot classify: Interpreter or vocabulary not initialized.")
            return Pair("Error", 0.0f)
        }

        try {
            val input = prepareText(text)
            val output = Array(1) { FloatArray(1) }
            interpreter?.run(input, output)

            val confidence = output[0][0]
            val prediction = if (confidence > 0.5) "Scam" else "Not Scam"
            return Pair(prediction, confidence)
        } catch (e: Exception) {
            Log.e(TAG, "Error during classification.", e)
            return Pair("Error", 0.0f)
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}

