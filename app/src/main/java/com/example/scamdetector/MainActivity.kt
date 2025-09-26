package com.example.scamdetector

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var permissionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.tvStatus)
        permissionButton = findViewById(R.id.btnEnableService)

        permissionButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun updateServiceStatus() {
        if (isAccessibilityServiceEnabled()) {
            statusText.text = "Service Status: ENABLED"
            statusText.setTextColor(resources.getColor(android.R.color.holo_green_dark, theme))
            permissionButton.text = "Accessibility Settings"
        } else {
            statusText.text = "Service Status: DISABLED"
            statusText.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
            permissionButton.text = "Enable Service"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (enabledServices == null || !am.isEnabled) {
            return false
        }
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals("${packageName}/${ScamDetectionService::class.java.name}", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted.
            } else {
                // Explain to the user that the feature is unavailable because the
                // feature requires a permission that the user has denied.
            }
        }
}
