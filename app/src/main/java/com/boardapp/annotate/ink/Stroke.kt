package com.boardapp.annotate.ink

import android.graphics.RectF

/** One sampled input point. Pressure is kept so width can vary later. */
class StrokePoint(val x: Float, val y: Float, val pressure: Float)

/**
 * An immutable-by-convention stroke: an id + the points it is made of.
 *
 * Modelling each stroke as a standalone object with an id is deliberate:
 *  - element/stroke erase is just "remove the stroke whose path you touched"
 *  - undo/redo is just add/remove of whole strokes
 *  - it is CRDT-friendly later (each object gets a stable id you can sync)
 */
class Stroke(
    val id: Long,
    val color: Int,
    val baseWidth: Float,
    val isStylus: Boolean
) {
    val points = ArrayList<StrokePoint>()
    private val bounds = RectF()
    private var hasBounds = false

    fun add(x: Float, y: Float, pressure: Float) {
        points.add(StrokePoint(x, y, pressure))
        if (!hasBounds) {
            bounds.set(x, y, x, y)
            hasBounds = true
        } else {
            if (x < bounds.left) bounds.left = x
            if (x > bounds.right) bounds.right = x
            if (y < bounds.top) bounds.top = y
            if (y > bounds.bottom) bounds.bottom = y
        }
    }

    /** True if (px,py) is within [radius] of any segment of this stroke. Used by the eraser. */
    fun hitTest(px: Float, py: Float, radius: Float): Boolean {
        // cheap bounding-box reject first
        if (px < bounds.left - radius || px > bounds.right + radius ||
            py < bounds.top - radius || py > bounds.bottom + radius
        ) return false

        val r2 = radius * radius
        if (points.size == 1) {
            val dx = px - points[0].x
            val dy = py - points[0].y
            return dx * dx + dy * dy <= r2
        }
        for (i in 0 until points.size - 1) {
            if (distanceToSegmentSquared(px, py, points[i], points[i + 1]) <= r2) return true
        }
        return false
    }

    private fun distanceToSegmentSquared(px: Float, py: Float, a: StrokePoint, b: StrokePoint): Float {
        val vx = b.x - a.x
        val vy = b.y - a.y
        val wx = px - a.x
        val wy = py - a.y
        val c1 = vx * wx + vy * wy
        if (c1 <= 0f) return wx * wx + wy * wy
        val c2 = vx * vx + vy * vy
        if (c2 <= c1) {
            val dx = px - b.x
            val dy = py - b.y
            return dx * dx + dy * dy
        }
        val t = c1 / c2
        val projX = a.x + t * vx
        val projY = a.y + t * vy
        val dx = px - projX
        val dy = py - projY
        return dx * dx + dy * dy
    }
}
