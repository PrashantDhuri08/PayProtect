package com.example.scamdetector

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat

class ScamDetectionService : AccessibilityService() {

    private lateinit var tfLiteHelper: TfLiteHelper
    private var lastProcessedText: String = ""
    private var lastProcessedTime: Long = 0

    companion object {
        private const val TAG = "ScamDetectionService"
        private const val NOTIFICATION_CHANNEL_ID = "scam_detector_channel"
        private const val NOTIFICATION_ID = 1
        // Debounce delay to avoid re-processing the same text too quickly
        private const val DEBOUNCE_DELAY_MS = 2000
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        tfLiteHelper = TfLiteHelper(this)
        createNotificationChannel()
        Log.d(TAG, "Scam Detection Service connected and TFLite helper initialized.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {

            val rootNode = rootInActiveWindow ?: return
            val textBuilder = StringBuilder()
            extractTextFromNode(rootNode, textBuilder)
            val fullText = textBuilder.toString()
            rootNode.recycle()

            val currentTime = System.currentTimeMillis()
            // Debounce check: process only if text is new and enough time has passed
            if (fullText.isNotBlank() && fullText != lastProcessedText && (currentTime - lastProcessedTime > DEBOUNCE_DELAY_MS)) {
                lastProcessedText = fullText
                lastProcessedTime = currentTime

                Log.d(TAG, "Processing text: $fullText")
                val (prediction, confidence) = tfLiteHelper.classify(fullText)

                Log.d(TAG, "Prediction: $prediction, Confidence: $confidence")
                if (prediction == "Scam" && confidence > 0.8) { // Confidence threshold
                    sendNotification(fullText)
                }
            }
        }
    }

    /**
     * Recursively traverses the node tree to extract all text content.
     */
    private fun extractTextFromNode(node: AccessibilityNodeInfo?, builder: StringBuilder) {
        if (node == null) return
        if (node.text != null && node.text.isNotBlank()) {
            builder.append(node.text).append("\n")
        }
        for (i in 0 until node.childCount) {
            extractTextFromNode(node.getChild(i), builder)
        }
    }

    private fun sendNotification(detectedText: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure you have this drawable
            .setContentTitle("Potential Scam Detected!")
            .setContentText("Suspicious text found on your screen.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Detected Content:\n\"$detectedText\""))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        Log.i(TAG, "Notification sent for potential scam.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }


    override fun onInterrupt() {
        Log.w(TAG, "Scam Detection Service interrupted.")
    }

    override fun onDestroy() {
        tfLiteHelper.close()
        super.onDestroy()
        Log.d(TAG, "Scam Detection Service destroyed and TFLite helper closed.")
    }
}

