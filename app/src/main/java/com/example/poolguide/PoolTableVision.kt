package com.example.poolguide

import android.media.Image
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class NormalizedPoint(val x: Float, val y: Float)

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class PoolDetection(
    val table: NormalizedRect,
    val cueBall: NormalizedPoint?,
    val objectBalls: List<NormalizedPoint>,
    val pockets: List<NormalizedPoint>,
    val confidence: Float
)

/** Shares the newest frame result with the floating overlay in this app process. */
object PoolGuideDetectionBus {
    private val listeners = CopyOnWriteArraySet<(PoolDetection?) -> Unit>()

    @Volatile
    private var latestDetection: PoolDetection? = null

    fun addListener(listener: (PoolDetection?) -> Unit) {
        listeners.add(listener)
        listener(latestDetection)
    }

    fun removeListener(listener: (PoolDetection?) -> Unit) {
        listeners.remove(listener)
    }

    fun publish(detection: PoolDetection?) {
        latestDetection = detection
        listeners.forEach { listener ->
            try {
                listener(detection)
            } catch (_: Exception) {
                // A closing overlay must not interrupt frame analysis.
            }
        }
    }
}

/**
 * Lightweight first-pass detector for the portrait blue-felt table shown in
 * the supplied game screenshot. It finds the largest cyan felt region, then
 * scores circular non-felt patches inside it as ball candidates.
 *
 * Coordinates in PoolDetection are normalized to the captured screen.
 */
class PoolTableVision {

    private data class Frame(
        val width: Int,
        val height: Int,
        val scale: Int,
        val red: ByteArray,
        val green: ByteArray,
        val blue: ByteArray
    )

    private data class Component(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val count: Int
    )

    private data class BallCandidate(
        val x: Int,
        val y: Int,
        val ringScore: Int,
        val whiteScore: Int
    )

    fun analyze(image: Image): PoolDetection? {
        val frame = sampleFrame(image) ?: return null
        val feltMask = ByteArray(frame.width * frame.height)

        for (index in feltMask.indices) {
            val r = frame.red[index].toInt() and 0xFF
            val g = frame.green[index].toInt() and 0xFF
            val b = frame.blue[index].toInt() and 0xFF
            if (isBlueFelt(r, g, b)) feltMask[index] = 1
        }

        val table = largestComponent(feltMask, frame.width, frame.height) ?: return null
        val tableWidth = table.right - table.left
        val tableHeight = table.bottom - table.top
        if (table.count < frame.width * frame.height / 30 ||
            tableWidth < frame.width / 3 || tableHeight < frame.height / 3
        ) return null

        val candidates = findBallCandidates(frame, feltMask, table)
        val chosen = nonMaximumSuppression(
            candidates,
            (tableWidth * 0.022f).toInt().coerceAtLeast(4)
        )
        val cueCandidate = chosen
            .filter { it.whiteScore >= 12 }
            .maxByOrNull { it.whiteScore * 100 + it.ringScore }
        val objectBalls = chosen
            .asSequence()
            .filterNot { candidate ->
                cueCandidate != null && candidate.x == cueCandidate.x && candidate.y == cueCandidate.y
            }
            .take(16)
            .map { NormalizedPoint(it.x.toFloat() * frame.scale / image.width,
                                   it.y.toFloat() * frame.scale / image.height) }
            .toList()

        val left = table.left.toFloat() * frame.scale / image.width
        val top = table.top.toFloat() * frame.scale / image.height
        val right = table.right.toFloat() * frame.scale / image.width
        val bottom = table.bottom.toFloat() * frame.scale / image.height
        val width = right - left
        val height = bottom - top
        val insetX = width * 0.012f
        val insetY = height * 0.012f

        val cuePoint = cueCandidate?.let {
            NormalizedPoint(it.x.toFloat() * frame.scale / image.width,
                            it.y.toFloat() * frame.scale / image.height)
        }

        val pockets = listOf(
            NormalizedPoint(left + insetX, top + insetY),
            NormalizedPoint(right - insetX, top + insetY),
            NormalizedPoint(left + insetX, (top + bottom) / 2f),
            NormalizedPoint(right - insetX, (top + bottom) / 2f),
            NormalizedPoint(left + insetX, bottom - insetY),
            NormalizedPoint(right - insetX, bottom - insetY)
        )

        val confidence = (table.count.toFloat() / (tableWidth * tableHeight).coerceAtLeast(1))
            .coerceIn(0f, 1f)

        return PoolDetection(
            table = NormalizedRect(left, top, right, bottom),
            cueBall = cuePoint,
            objectBalls = objectBalls,
            pockets = pockets,
            confidence = confidence
        )
    }

    private fun sampleFrame(image: Image): Frame? {
        if (image.planes.isEmpty() || image.width <= 0 || image.height <= 0) return null
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val scale = max(1, ceil(image.width / 360f).toInt())
        val width = ceil(image.width.toFloat() / scale).toInt()
        val height = ceil(image.height.toFloat() / scale).toInt()
        val size = width * height
        val red = ByteArray(size)
        val green = ByteArray(size)
        val blue = ByteArray(size)

        for (y in 0 until height) {
            val sourceY = min(image.height - 1, y * scale)
            for (x in 0 until width) {
                val sourceX = min(image.width - 1, x * scale)
                val offset = sourceY * rowStride + sourceX * pixelStride
                if (offset < 0 || offset + 2 >= buffer.limit()) continue
                val target = y * width + x
                red[target] = buffer.get(offset)
                green[target] = buffer.get(offset + 1)
                blue[target] = buffer.get(offset + 2)
            }
        }
        return Frame(width, height, scale, red, green, blue)
    }

    private fun isBlueFelt(r: Int, g: Int, b: Int): Boolean =
        b > 115 && g > 85 && b - r > 55 && g - r > 35 && b >= g - 12

    private fun largestComponent(mask: ByteArray, width: Int, height: Int): Component? {
        val visited = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        var largest: Component? = null

        for (start in mask.indices) {
            if (mask[start].toInt() == 0 || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var minX = width
            var minY = height
            var maxX = 0
            var maxY = 0
            var count = 0

            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                count++
                minX = min(minX, x)
                minY = min(minY, y)
                maxX = max(maxX, x)
                maxY = max(maxY, y)

                if (x > 0) tail = enqueue(index - 1, mask, visited, queue, tail)
                if (x + 1 < width) tail = enqueue(index + 1, mask, visited, queue, tail)
                if (y > 0) tail = enqueue(index - width, mask, visited, queue, tail)
                if (y + 1 < height) tail = enqueue(index + width, mask, visited, queue, tail)
            }

            if (largest == null || count > largest.count) {
                largest = Component(minX, minY, maxX + 1, maxY + 1, count)
            }
        }
        return largest
    }

    private fun enqueue(
        index: Int,
        mask: ByteArray,
        visited: BooleanArray,
        queue: IntArray,
        tail: Int
    ): Int {
        if (mask[index].toInt() != 0 && !visited[index]) {
            visited[index] = true
            queue[tail] = index
            return tail + 1
        }
        return tail
    }

    private fun findBallCandidates(
        frame: Frame,
        feltMask: ByteArray,
        table: Component
    ): List<BallCandidate> {
        val radius = ((table.right - table.left) * 0.022f).toInt().coerceAtLeast(4)
        val candidates = ArrayList<BallCandidate>()
        val angles = 16
        val ringX = IntArray(angles)
        val ringY = IntArray(angles)
        for (i in 0 until angles) {
            val a = 2.0 * Math.PI * i / angles
            ringX[i] = (cos(a) * radius * 0.82).toInt()
            ringY[i] = (sin(a) * radius * 0.82).toInt()
        }

        var y = table.top + radius
        while (y < table.bottom - radius) {
            var x = table.left + radius
            while (x < table.right - radius) {
                val center = y * frame.width + x
                if (isBallPixel(frame, feltMask, center)) {
                    var ringScore = 0
                    var whiteScore = 0
                    for (i in 0 until angles) {
                        val px = x + ringX[i]
                        val py = y + ringY[i]
                        val index = py * frame.width + px
                        if (isBallPixel(frame, feltMask, index)) ringScore++
                        if (isNeutralWhite(frame, index)) whiteScore++
                    }
                    if (ringScore >= 12) {
                        candidates.add(BallCandidate(x, y, ringScore, whiteScore))
                    }
                }
                x += 2
            }
            y += 2
        }
        return candidates
    }

    private fun isBallPixel(frame: Frame, feltMask: ByteArray, index: Int): Boolean {
        if (index !in feltMask.indices || feltMask[index].toInt() != 0) return false
        val r = frame.red[index].toInt() and 0xFF
        val g = frame.green[index].toInt() and 0xFF
        val b = frame.blue[index].toInt() and 0xFF
        val high = max(r, max(g, b))
        val low = min(r, min(g, b))
        return high > 105 && (high - low > 38 || low > 155)
    }

    private fun isNeutralWhite(frame: Frame, index: Int): Boolean {
        val r = frame.red[index].toInt() and 0xFF
        val g = frame.green[index].toInt() and 0xFF
        val b = frame.blue[index].toInt() and 0xFF
        return min(r, min(g, b)) > 160 &&
            max(r, max(g, b)) - min(r, min(g, b)) < 55
    }

    private fun nonMaximumSuppression(
        input: List<BallCandidate>,
        radius: Int
    ): List<BallCandidate> {
        val kept = ArrayList<BallCandidate>()
        val minimumDistance = radius * 1.35f
        val ordered = input.sortedWith(
            compareByDescending<BallCandidate> { it.ringScore }
                .thenByDescending { it.whiteScore }
        )

        for (candidate in ordered) {
            val overlaps = kept.any {
                hypot((it.x - candidate.x).toFloat(), (it.y - candidate.y).toFloat()) < minimumDistance
            }
            if (!overlaps) kept.add(candidate)
            if (kept.size >= 32) break
        }
        return kept
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
