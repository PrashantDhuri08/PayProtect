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
import java.util.concurrent.ConcurrentHashMap

class ScamDetectionService : AccessibilityService() {

    private lateinit var tfLiteHelper: TfLiteHelper

    companion object {
        private const val TAG = "ScamDetectionService"
        private const val NOTIFICATION_CHANNEL_ID = "scam_detector_channel"
        private const val NOTIFICATION_ID = 1

        // Tunable values:
        private const val CONFIDENCE_THRESHOLD = 0.90   // increase to reduce false positives
        private const val ALERT_DEBOUNCE_MS = 60_000L   // don't alert for same text within 60s
    }

    // Stores last alert time (ms) for message key to avoid duplicates
    private val recentAlerts = ConcurrentHashMap<String, Long>()

    // Simple keyword list — expand/adjust to your needs
    private val scamKeywords = listOf(
        "otp", "one time password", "verify", "verification code",
        "urgent", "click here", "account suspended", "winner", "congratulations",
        "claim now", "limited time", "transfer now", "send money", "bank", "password",
        "suspicious activity", "payment", "link"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        tfLiteHelper = TfLiteHelper(this)
        createNotificationChannel()
        Log.d(TAG, "Service connected and TfLiteHelper initialized.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            // Only process notification events
            if (event?.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
                val parcelable = event.parcelableData
                if (parcelable is Notification) {
                    val extras = parcelable.extras
                    val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
                    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                    val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
                    val fullTextParts = listOf(title, text, bigText).filter { it.isNotBlank() }
                    val fullText = fullTextParts.joinToString(" ").trim()

                    if (fullText.isNotBlank()) {
                        val pkgName = event.packageName?.toString() ?: "unknown"
                        Log.d(TAG, "Processing notification from $pkgName: $fullText")

                        // Classify with TF Lite model (assumes classify returns Pair(prediction, confidence))
                        val (prediction, confidence) = tfLiteHelper.classify(fullText)
                        Log.d(TAG, "Prediction: $prediction, Confidence: $confidence")

                        // Heuristics: keywords and OTP detection
                        val lower = fullText.lowercase()
                        val containsKeyword = scamKeywords.any { lower.contains(it) }
                        val otpRegex = Regex("\\b\\d{4,6}\\b")
                        val containsOtp = otpRegex.containsMatchIn(fullText)

                        Log.d(TAG, "containsKeyword=$containsKeyword containsOtp=$containsOtp")

                        // Build a message key to debounce alerts (package + first N chars)
                        val key = "${pkgName}:${fullText.take(200)}".hashCode().toString()
                        val now = System.currentTimeMillis()
                        val last = recentAlerts[key] ?: 0L

                        // Decision logic:
                        // - require model to predict "Scam" with high confidence
                        // - AND either a scam-like keyword OR an OTP pattern is present
                        if (prediction == "Scam" && confidence >= CONFIDENCE_THRESHOLD && (containsKeyword || containsOtp)) {
                            // Debounce duplicate alerts
                            if (now - last > ALERT_DEBOUNCE_MS) {
                                recentAlerts[key] = now
                                sendNotification("⚠️ Scam detected in a notification:\n\"$text\"")
                                Log.i(TAG, "Alert sent for potential scam (pkg=$pkgName).")
                            } else {
                                Log.d(TAG, "Duplicate alert suppressed (pkg=$pkgName).")
                            }
                        } else {
                            Log.d(TAG, "Notification not tagged as scam (prediction=$prediction, confidence=$confidence).")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    private fun sendNotification(detectedText: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent: PendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getActivity(this, 0, intent, 0)
        }

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // replace if needed
            .setContentTitle("Potential Scam Detected!")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detectedText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        Log.i(TAG, "Notification posted to user.")
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
        try {
            tfLiteHelper.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing TfLiteHelper", e)
        }
        recentAlerts.clear()
        super.onDestroy()
        Log.d(TAG, "Service destroyed and resources cleaned.")
    }
}
