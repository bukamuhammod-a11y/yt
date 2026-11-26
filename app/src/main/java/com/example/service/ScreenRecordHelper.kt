package com.example.service

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScreenRecordHelper {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null

    fun startRecordingWithProjection(context: Context, projection: MediaProjection) {
        if (_isRecording.value) return

        try {
            mediaProjection = projection

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = windowManager.currentWindowMetrics.bounds
                metrics.widthPixels = bounds.width()
                metrics.heightPixels = bounds.height()
                metrics.densityDpi = context.resources.configuration.densityDpi
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getMetrics(metrics)
            }

            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val videoFile = File(storageDir, "ScreenRecord_$timestamp.mp4")
            currentRecordingFile = videoFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setVideoEncodingBitRate(6 * 1000 * 1000)
            recorder.setVideoFrameRate(30)
            // Ensure even dimensions for H264
            val width = if (metrics.widthPixels % 2 == 0) metrics.widthPixels else metrics.widthPixels - 1
            val height = if (metrics.heightPixels % 2 == 0) metrics.heightPixels else metrics.heightPixels - 1
            recorder.setVideoSize(width, height)
            recorder.setOutputFile(videoFile.absolutePath)
            recorder.prepare()

            mediaRecorder = recorder

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "AssistiveScreenRecorder",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            )

            recorder.start()
            _isRecording.value = true
            Toast.makeText(context, "Screen recording started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording(context)
            Toast.makeText(context, "Recording failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun stopRecording(context: Context): File? {
        if (!_isRecording.value) return null

        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null

        _isRecording.value = false
        val savedFile = currentRecordingFile
        if (savedFile != null && savedFile.exists() && savedFile.length() > 0) {
            Toast.makeText(context, "Screen recording saved: ${savedFile.name}", Toast.LENGTH_LONG).show()
        }
        return savedFile
    }

    fun toggleRecording(context: Context, onNeedPermission: () -> Unit) {
        if (_isRecording.value) {
            stopRecording(context)
        } else {
            onNeedPermission()
        }
    }
}
