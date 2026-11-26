package com.example.util

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScreenshotHelper {

    fun captureActivity(activity: Activity, onComplete: ((Boolean, String?) -> Unit)? = null) {
        val window = activity.window ?: run {
            onComplete?.invoke(false, null)
            return
        }
        val decorView = window.decorView.rootView ?: run {
            onComplete?.invoke(false, null)
            return
        }

        val width = decorView.width
        val height = decorView.height

        if (width <= 0 || height <= 0) {
            Toast.makeText(activity, "Cannot capture: Screen is not ready", Toast.LENGTH_SHORT).show()
            onComplete?.invoke(false, null)
            return
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val location = IntArray(2)
            decorView.getLocationInWindow(location)
            try {
                PixelCopy.request(
                    window,
                    Rect(location[0], location[1], location[0] + width, location[1] + height),
                    bitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            val savedPath = saveBitmap(activity, bitmap)
                            if (savedPath != null) {
                                Toast.makeText(activity, "Screenshot saved to Pictures!", Toast.LENGTH_SHORT).show()
                                onComplete?.invoke(true, savedPath)
                            } else {
                                Toast.makeText(activity, "Failed to save screenshot", Toast.LENGTH_SHORT).show()
                                onComplete?.invoke(false, null)
                            }
                        } else {
                            fallbackCanvasCapture(activity, decorView, onComplete)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } catch (e: Exception) {
                fallbackCanvasCapture(activity, decorView, onComplete)
            }
        } else {
            fallbackCanvasCapture(activity, decorView, onComplete)
        }
    }

    private fun fallbackCanvasCapture(
        context: Context,
        view: android.view.View,
        onComplete: ((Boolean, String?) -> Unit)?
    ) {
        try {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            val savedPath = saveBitmap(context, bitmap)
            if (savedPath != null) {
                Toast.makeText(context, "Screenshot saved to Pictures!", Toast.LENGTH_SHORT).show()
                onComplete?.invoke(true, savedPath)
            } else {
                Toast.makeText(context, "Failed to save screenshot", Toast.LENGTH_SHORT).show()
                onComplete?.invoke(false, null)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Screenshot failed: ${e.message}", Toast.LENGTH_SHORT).show()
            onComplete?.invoke(false, null)
        }
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap): String? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "Screenshot_$timestamp.png"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenshots")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    filename
                } else null
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                file.absolutePath
            }
        } catch (e: Exception) {
            // Internal storage fallback
            try {
                val file = File(context.cacheDir, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                file.absolutePath
            } catch (ex: Exception) {
                null
            }
        }
    }
}
