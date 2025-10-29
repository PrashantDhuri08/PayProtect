
//package com.example.scamdetector
//
//import android.accessibilityservice.AccessibilityService
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.content.Context
//import android.os.Build
//import android.util.Log
//import android.view.accessibility.AccessibilityEvent
//import android.view.accessibility.AccessibilityNodeInfo
//import androidx.core.app.NotificationCompat
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.SupervisorJob
//import kotlinx.coroutines.cancel
//import kotlinx.coroutines.launch
//
//class ScamDetectionService : AccessibilityService() {
//
//    private lateinit var tfLiteHelper: TfLiteHelper
//    private val serviceJob = SupervisorJob()
//    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
//
//    // Keep track of the last processed content to avoid re-analyzing the same screen.
//    private var lastProcessedText: String? = null
//    // Keep track of the last text that triggered a notification to avoid spam.
//    private var lastNotifiedBlock: String? = null
//
//
//    companion object {
//        private const val TAG = "ScamDetectionService"
//        private const val NOTIFICATION_CHANNEL_ID = "scam_detector_channel"
//        private const val MIN_WORD_COUNT_FOR_ANALYSIS = 5 // Don't analyze text with fewer words
//        private const val CONFIDENCE_THRESHOLD = 0.80f // Only notify if confidence is over 80%
//    }
//
//    override fun onServiceConnected() {
//        super.onServiceConnected()
//        tfLiteHelper = TfLiteHelper(this)
//        createNotificationChannel()
//        Log.i(TAG, "Accessibility service connected and TFLite helper initialized.")
//    }
//
//    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
//        // CRITICAL FIX: Ignore events coming from our own app to prevent feedback loops.
//        if (event?.packageName == this.packageName) {
//            return
//        }
//
//        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
//            val rootNode = rootInActiveWindow ?: return
//            val textBlocks = mutableListOf<String>()
//            collectTextNodes(rootNode, textBlocks)
//            rootNode.recycle()
//
//            val fullScreenText = textBlocks.joinToString(separator = "\n")
//            if (fullScreenText.isNotBlank() && fullScreenText != lastProcessedText) {
//                lastProcessedText = fullScreenText
//                // When the screen content changes, reset the last notified block.
//                lastNotifiedBlock = null
//                processScreenText(textBlocks)
//            }
//        }
//    }
//
//    private fun processScreenText(textBlocks: List<String>) {
//        serviceScope.launch {
//            for (block in textBlocks) {
//                // Heuristic: Only process blocks that have a reasonable number of words.
//                if (block.split("\\s+".toRegex()).size >= MIN_WORD_COUNT_FOR_ANALYSIS) {
//                    Log.d(TAG, "Analyzing block: \"$block\"")
//                    val (prediction, confidence) = tfLiteHelper.classify(block)
//                    Log.d(TAG, "Prediction for block: $prediction, Confidence: $confidence")
//
//                    // If a scam is detected with high confidence...
//                    if (prediction == "Scam" && confidence >= CONFIDENCE_THRESHOLD) {
//                        // ...and we haven't already notified for this exact text...
//                        if (block != lastNotifiedBlock) {
//                            lastNotifiedBlock = block // Remember this block
//                            val notificationMessage = "Suspicious text found: \"${block.take(40)}...\""
//                            sendNotification("Potential Scam Detected", notificationMessage)
//                            break // Stop processing further blocks on this screen
//                        }
//                    }
//                }
//            }
//        }
//    }
//
//    private fun collectTextNodes(node: AccessibilityNodeInfo, textList: MutableList<String>) {
//        if (node.text != null && node.text.isNotBlank()) {
//            textList.add(node.text.toString())
//        }
//        for (i in 0 until node.childCount) {
//            val child = node.getChild(i)
//            if (child != null) {
//                collectTextNodes(child, textList)
//                child.recycle()
//            }
//        }
//    }
//
//
//    private fun sendNotification(title: String, message: String) {
//        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
//            .setContentTitle(title)
//            .setContentText(message)
//            .setStyle(NotificationCompat.BigTextStyle().bigText(message)) // Allow longer text
//            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure you have this drawable
//            .setPriority(NotificationCompat.PRIORITY_HIGH)
//            .setAutoCancel(true)
//            .build()
//        notificationManager.notify(1, notification)
//        Log.i(TAG, "Notification sent for potential scam.")
//    }
//
//    private fun createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val name = getString(R.string.channel_name)
//            val descriptionText = getString(R.string.channel_description)
//            val importance = NotificationManager.IMPORTANCE_HIGH
//            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
//                description = descriptionText
//            }
//            val notificationManager: NotificationManager =
//                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//            notificationManager.createNotificationChannel(channel)
//        }
//    }
//
//    override fun onInterrupt() {
//        Log.w(TAG, "Accessibility service interrupted.")
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        tfLiteHelper.close()
//        serviceJob.cancel()
//        Log.i(TAG, "Accessibility service destroyed.")
//    }
//}
//
//
//
