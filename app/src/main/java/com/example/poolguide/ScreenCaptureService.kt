
package com.example.poolguide

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import android.content.pm.ServiceInfo

class ScreenCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var notificationManager: NotificationManager? = null
    private var notificationBuilder: Notification.Builder? = null
    private var frameCount = 0L
    private var lastNotificationUpdate = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val resultCode = intent?.getIntExtra("result_code", 0) ?: 0

        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("capture_data")

        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val channelId = "poolguide_capture"

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                channelId,
                "PoolGuide screen capture",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        notificationManager = getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        notificationBuilder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
                .setContentTitle("PoolGuide screen capture")
                .setContentText("Starting screen capture...")
                .setSmallIcon(android.R.drawable.ic_menu_view)
        } else {
            Notification.Builder(this)
                .setContentTitle("PoolGuide screen capture")
                .setContentText("Starting screen capture...")
                .setSmallIcon(android.R.drawable.ic_menu_view)
        }

        val notification = notificationBuilder!!.build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                2,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(2, notification)
        }

        try {
            val manager = getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

            projection = manager.getMediaProjection(resultCode, data)

            val metrics = DisplayMetrics()

            @Suppress("DEPRECATION")
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(
                width,
                height,
                PixelFormat.RGBA_8888,
                2
            )

            // Count captured frames and update the notification.
            imageReader?.setOnImageAvailableListener(
                { reader ->
                    val image = reader.acquireLatestImage()

                    if (image != null) {
                        image.close()
                        frameCount++

                        val now = SystemClock.elapsedRealtime()

                        if (now - lastNotificationUpdate >= 1000L) {
                            lastNotificationUpdate = now

                            notificationBuilder?.setContentText(
                                "Frames received: $frameCount"
                            )

                            notificationManager?.notify(
                                2,
                                notificationBuilder?.build()
                            )
                        }
                    }
                },
                mainHandler
            )

            projection?.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        releaseCapture()
                        stopSelf()
                    }
                },
                mainHandler
            )

            virtualDisplay = projection?.createVirtualDisplay(
                "PoolGuideCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                mainHandler
            )

        } catch (e: Exception) {
            releaseCapture()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun releaseCapture() {
        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null

        projection?.stop()
        projection = null
        notificationBuilder = null
        notificationManager = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        releaseCapture()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

/*
 * =========================================
 * POOLGUIDE
 * Created by: Carl Neat
 * Developer: Carl Neat
 * Copyright © 2026 Carl Neat
 * =========================================
 */
