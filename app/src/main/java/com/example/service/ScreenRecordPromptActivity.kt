package com.example.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast

class ScreenRecordPromptActivity : Activity() {

    companion object {
        private const val REQUEST_CODE_CAPTURE = 991

        fun launch(context: Context) {
            val intent = Intent(context, ScreenRecordPromptActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mediaProjectionManager != null) {
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            @Suppress("DEPRECATION")
            startActivityForResult(captureIntent, REQUEST_CODE_CAPTURE)
        } else {
            Toast.makeText(this, "Screen capture service not available", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                val projection = mediaProjectionManager?.getMediaProjection(resultCode, data)
                if (projection != null) {
                    ScreenRecordHelper.startRecordingWithProjection(applicationContext, projection)
                }
            } else {
                Toast.makeText(this, "Screen recording permission cancelled", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}
