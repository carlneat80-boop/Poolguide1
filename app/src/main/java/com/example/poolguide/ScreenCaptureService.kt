package com.example.poolguide

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager

class ScreenCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var notificationManager: NotificationManager? = null
    private var notificationBuilder: Notification.Builder? = null
    private var visionThread: HandlerThread? = null
    private var visionHandler: Handler? = null
    private var frameCount = 0L
    private var lastQueuedFrame = 0L
    private var lastNotificationUpdate = 0L
    private var missedDetections = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val vision = PoolTableVision()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (projection != null) return START_NOT_STICKY

        val resultCode = intent?.getIntExtra("result_code", 0) ?: 0

        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("capture_data")

        if (data == null || resultCode == 0) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startCaptureNotification()
        visionThread = HandlerThread("PoolGuideVision").apply { start() }
        visionHandler = Handler(visionThread!!.looper)

        try {
            val projectionManager = getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager
            projection = projectionManager.getMediaProjection(resultCode, data)

            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.getRealMetrics(metrics)

            imageReader = ImageReader.newInstance(
                metrics.widthPixels,
                metrics.heightPixels,
                PixelFormat.RGBA_8888,
                2
            )

            imageReader?.setOnImageAvailableListener(
                { reader -> queueLatestImage(reader) },
                mainHandler
            )

            projection?.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        releaseCapture()
                        PoolGuideDetectionBus.publish(null)
                        stopSelf()
                    }
                },
                mainHandler
            )

            virtualDisplay = projection?.createVirtualDisplay(
                "PoolGuideCapture",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                mainHandler
            )
        } catch (_: Exception) {
            releaseCapture()
            PoolGuideDetectionBus.publish(null)
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private fun startCaptureNotification() {
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
                .setContentTitle("PoolGuide table detection")
                .setContentText("Starting screen analysis…")
                .setSmallIcon(android.R.drawable.ic_menu_view)
        } else {
            Notification.Builder(this)
                .setContentTitle("PoolGuide table detection")
                .setContentText("Starting screen analysis…")
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
    }

    private fun queueLatestImage(reader: ImageReader) {
        val image = try {
            reader.acquireLatestImage()
        } catch (_: IllegalStateException) {
            null
        } ?: return

        val now = SystemClock.elapsedRealtime()
        if (now - lastQueuedFrame < 400L) {
            image.close()
            return
        }
        lastQueuedFrame = now

        val handler = visionHandler
        if (handler == null) {
            image.close()
            return
        }

        handler.post {
            val detection = try {
                vision.analyze(image)
            } catch (_: Exception) {
                null
            } finally {
                image.close()
            }

            mainHandler.post {
                frameCount++
                if (detection != null) {
                    missedDetections = 0
                    PoolGuideDetectionBus.publish(detection)
                } else {
                    missedDetections++
                    if (missedDetections >= 5) PoolGuideDetectionBus.publish(null)
                }
                updateNotification(detection)
            }
        }
    }

    private fun updateNotification(detection: PoolDetection?) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotificationUpdate < 1000L) return
        lastNotificationUpdate = now

        val message = if (detection == null) {
            "Scanning for the blue pool table…"
        } else {
            val cue = if (detection.cueBall == null) "cue ball not found" else "cue ball found"
            "Table found; $cue; ${detection.objectBalls.size} ball candidates"
        }
        notificationBuilder?.setContentText(message)
        notificationManager?.notify(2, notificationBuilder?.build())
    }

    private fun releaseCapture() {
        val reader = imageReader
        imageReader = null
        reader?.setOnImageAvailableListener(null, null)
        reader?.close()

        val display = virtualDisplay
        virtualDisplay = null
        display?.release()

        val activeProjection = projection
        projection = null
        activeProjection?.stop()

        val thread = visionThread
        visionThread = null
        visionHandler = null
        thread?.quitSafely()

        notificationBuilder = null
        notificationManager = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        releaseCapture()
        PoolGuideDetectionBus.publish(null)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseCapture()
        PoolGuideDetectionBus.publish(null)
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/*
 * =========================================
 * POOLGUIDE
 * Created by: Carl Neat
 * Developer: Carl Neat
 * Copyright © 2026 Carl Neat
 * =========================================
 */
