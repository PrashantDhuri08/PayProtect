package com.example.scamdetector

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat

class ScamDetectionService : AccessibilityService() {

    private lateinit var tfLiteHelper: TfLiteHelper

    companion object {
        private const val TAG = "ScamDetectionService"
        private const val NOTIFICATION_CHANNEL_ID = "scam_detector_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        tfLiteHelper = TfLiteHelper(this)
        createNotificationChannel()
        Log.d(TAG, "Scam Detection Service connected and TFLite helper initialized.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We only care about notification events now
        if (event?.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val parcelable = event.parcelableData
            if (parcelable is Notification) {
                // Extract title and text from the notification's details
                val extras = parcelable.extras
                val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                val fullText = "$title\n$text"

                if (fullText.isNotBlank()) {
                    Log.d(TAG, "Processing notification text: $fullText")
                    val (prediction, confidence) = tfLiteHelper.classify(fullText)

                    Log.d(TAG, "Prediction: $prediction, Confidence: $confidence")
                    if (prediction == "Scam" && confidence > 0.8) { // Confidence threshold
                        sendNotification("Scam detected in a notification from another app:\n\"$text\"")
                    }
                }
            }
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(detectedText))
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
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

    // The extractTextFromNode function is no longer needed, so it has been removed.
}