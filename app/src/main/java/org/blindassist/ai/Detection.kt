package org.blindassist.ai

import kotlin.math.max
import kotlin.math.min

data class Detection(val classId: Int, val label: String, val confidence: Float, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX get() = (left + right) / 2f
    val area get() = max(0f, right - left) * max(0f, bottom - top)
}

enum class RelativePosition { LEFT, CENTER, RIGHT }
object DetectionLogic {
    fun position(d: Detection): RelativePosition = when {
        d.centerX < .38f -> RelativePosition.LEFT
        d.centerX > .62f -> RelativePosition.RIGHT
        else -> RelativePosition.CENTER
    }
    fun alert(detections: List<Detection>): String? {
        val d = detections.maxByOrNull { it.confidence * (1f + it.area) } ?: return null
        val name = when (d.label.lowercase()) { "person" -> "Person"; "chair" -> "Chair"; else -> d.label.replaceFirstChar { it.uppercase() } }
        return when (position(d)) {
            RelativePosition.LEFT -> "$name on your left."
            RelativePosition.RIGHT -> "$name on your right."
            RelativePosition.CENTER -> "$name ahead."
        }
    }
    fun nms(input: List<Detection>, threshold: Float = .45f): List<Detection> {
        val out = mutableListOf<Detection>()
        input.groupBy { it.classId }.values.forEach { group ->
            val pending = group.sortedByDescending { it.confidence }.toMutableList()
            while (pending.isNotEmpty()) {
                val best = pending.removeAt(0); out += best
                pending.removeAll { iou(best, it) > threshold }
            }
        }
        return out
    }
    private fun iou(a: Detection, b: Detection): Float {
        val w = max(0f, min(a.right,b.right)-max(a.left,b.left)); val h = max(0f,min(a.bottom,b.bottom)-max(a.top,b.top))
        val inter=w*h; val union=a.area+b.area-inter
        return if (union <= 0f) 0f else inter/union
    }
}

class AlertGate(private val cooldownMs: Long = 3500) {
    private var lastText = ""; private var lastAt = 0L; private var hasSpoken = false
    @Synchronized fun shouldSpeak(text: String, now: Long): Boolean {
        if (text.isBlank()) return false
        if (hasSpoken && now - lastAt < cooldownMs) return false
        lastText=text; lastAt=now; hasSpoken=true; return true
    }
}
