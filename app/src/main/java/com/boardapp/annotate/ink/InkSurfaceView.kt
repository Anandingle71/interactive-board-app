package com.boardapp.annotate.ink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceView
import androidx.graphics.lowlatency.CanvasFrontBufferedRenderer
import androidx.input.motionprediction.MotionEventPredictor

/**
 * The whole point of Phase 0 lives in this file: lag-free ink.
 *
 * Two layers:
 *   - WET layer  -> the stroke being drawn right now. Rendered straight to the
 *                   FRONT BUFFER on every motion event (bypasses the compositor),
 *                   so the ink keeps up with the finger/stylus.
 *   - DRY layer  -> everything already committed. Kept as a Bitmap that we blit
 *                   in the multi-buffered callback. We only redraw it on
 *                   commit / erase / undo / clear, never per motion event.
 *
 * Do NOT "simplify" this into a custom View with onDraw()+invalidate(). That is
 * the laggy path and the reason naive whiteboard apps feel like they trail your hand.
 */
class InkSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs) {

    // ---- tool state (set from the toolbar) ----
    var toolColor: Int = Color.BLACK
    var toolWidth: Float = 6f
    var eraserMode: Boolean = false

    // ---- scene model ----
    private val strokes = ArrayList<Stroke>()
    private val history = ArrayList<Action>()   // undo stack
    private val redoStack = ArrayList<Action>() // redo stack
    private var nextId = 1L

    // ---- dry layer ----
    private var dryBitmap: Bitmap? = null
    private var dryCanvas: Canvas? = null

    // ---- active wet stroke + front-buffer running cursor ----
    private var active: Stroke? = null
    private var lastX = 0f
    private var lastY = 0f

    // ---- renderer + predictor ----
    private var renderer: CanvasFrontBufferedRenderer<WetSegment>? = null
    private var predictor: MotionEventPredictor? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val callback = object : CanvasFrontBufferedRenderer.Callback<WetSegment> {
        // Called for the in-progress stroke. The front buffer is NOT cleared between
        // calls, so we only draw the newest segment -> very cheap, very fast.
        override fun onDrawFrontBufferedLayer(
            canvas: Canvas,
            bufferWidth: Int,
            bufferHeight: Int,
            transform: FloatArray,
            param: WetSegment
        ) {
            // TODO(panel): some devices need `transform` applied for pre-rotation.
            // Fixed-landscape classroom panels are typically identity, so we skip it
            // in the spike. If ink appears rotated/offset on your panel, apply transform here.
            paint.color = param.color
            paint.strokeWidth = param.width
            if (param.first) {
                lastX = param.x
                lastY = param.y
                canvas.drawPoint(param.x, param.y, paint)
            } else {
                canvas.drawLine(lastX, lastY, param.x, param.y, paint)
                lastX = param.x
                lastY = param.y
            }
        }

        // Called on commit(): present the dry layer (white bg + all committed strokes).
        override fun onDrawMultiBufferedLayer(
            canvas: Canvas,
            bufferWidth: Int,
            bufferHeight: Int,
            transform: FloatArray,
            params: Collection<WetSegment>
        ) {
            val bmp = dryBitmap
            if (bmp != null) canvas.drawBitmap(bmp, 0f, 0f, null)
            else canvas.drawColor(Color.WHITE)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        renderer = CanvasFrontBufferedRenderer(this, callback)
        predictor = MotionEventPredictor.newInstance(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        for (s in strokes) drawStrokeSmoothed(c, s) // keep content across resize
        dryBitmap = bmp
        dryCanvas = c
        presentDry()
    }

    // ----------------------------------------------------------------- touch
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val r = renderer ?: return true
        predictor?.record(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (eraserMode) {
                    eraseAt(event.x, event.y)
                    return true
                }
                val s = Stroke(
                    id = nextId++,
                    color = toolColor,
                    baseWidth = toolWidth,
                    isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
                )
                s.add(event.x, event.y, event.pressure)
                active = s
                r.renderFrontBufferedLayer(WetSegment(event.x, event.y, toolColor, toolWidth, true))
            }

            MotionEvent.ACTION_MOVE -> {
                if (eraserMode) {
                    eraseAt(event.x, event.y)
                    return true
                }
                val s = active ?: return true
                // Consume batched historical samples for smoothness on fast strokes.
                for (i in 0 until event.historySize) {
                    val hx = event.getHistoricalX(i)
                    val hy = event.getHistoricalY(i)
                    s.add(hx, hy, event.getHistoricalPressure(i))
                    r.renderFrontBufferedLayer(WetSegment(hx, hy, s.color, s.baseWidth, false))
                }
                s.add(event.x, event.y, event.pressure)
                r.renderFrontBufferedLayer(WetSegment(event.x, event.y, s.color, s.baseWidth, false))

                // Predicted point: drawn to hide latency, NOT stored in the stroke.
                val predicted = predictor?.predict()
                if (predicted != null) {
                    r.renderFrontBufferedLayer(
                        WetSegment(predicted.x, predicted.y, s.color, s.baseWidth, false)
                    )
                    predicted.recycle()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (eraserMode) return true
                val s = active ?: return true
                active = null
                strokes.add(s)
                history.add(Action.Add(s))
                redoStack.clear()
                dryCanvas?.let { drawStrokeSmoothed(it, s) } // bake into dry layer
                r.commit() // clears the front buffer and shows the dry layer
            }
        }
        return true
    }

    // ----------------------------------------------------------------- ops
    private fun eraseAt(x: Float, y: Float) {
        val radius = toolWidth.coerceAtLeast(28f) // eraser feels bigger than the pen
        val removed = strokes.filter { it.hitTest(x, y, radius) }
        if (removed.isEmpty()) return
        strokes.removeAll(removed.toSet())
        history.add(Action.Remove(removed))
        redoStack.clear()
        rebuildDry()
        presentDry()
    }

    fun undo() {
        val a = history.removeLastOrNull() ?: return
        when (a) {
            is Action.Add -> strokes.remove(a.stroke)
            is Action.Remove -> strokes.addAll(a.strokes)
        }
        redoStack.add(a)
        rebuildDry()
        presentDry()
    }

    fun redo() {
        val a = redoStack.removeLastOrNull() ?: return
        when (a) {
            is Action.Add -> strokes.add(a.stroke)
            is Action.Remove -> strokes.removeAll(a.strokes.toSet())
        }
        history.add(a)
        rebuildDry()
        presentDry()
    }

    fun clearAll() {
        if (strokes.isEmpty()) return
        history.add(Action.Remove(ArrayList(strokes)))
        redoStack.clear()
        strokes.clear()
        rebuildDry()
        presentDry()
    }

    private fun rebuildDry() {
        val c = dryCanvas ?: return
        c.drawColor(Color.WHITE)
        for (s in strokes) drawStrokeSmoothed(c, s)
    }

    /**
     * Push the dry bitmap to the screen with an empty front buffer.
     * NOTE: if commit() with no preceding front-buffer render does not repaint on
     * your device, draw a 1px transparent point first, then commit (see AGENT_TASKS.md).
     */
    private fun presentDry() {
        renderer?.commit()
    }

    private fun drawStrokeSmoothed(canvas: Canvas, s: Stroke) {
        paint.color = s.color
        paint.strokeWidth = s.baseWidth
        val pts = s.points
        if (pts.size < 2) {
            if (pts.isNotEmpty()) canvas.drawPoint(pts[0].x, pts[0].y, paint)
            return
        }
        val path = Path()
        path.moveTo(pts[0].x, pts[0].y)
        // Quadratic smoothing through segment midpoints (Catmull-ish, cheap & smooth).
        for (i in 1 until pts.size - 1) {
            val midX = (pts[i].x + pts[i + 1].x) / 2f
            val midY = (pts[i].y + pts[i + 1].y) / 2f
            path.quadTo(pts[i].x, pts[i].y, midX, midY)
        }
        val last = pts.last()
        path.lineTo(last.x, last.y)
        canvas.drawPath(path, paint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        renderer?.release()
        renderer = null
    }

    data class WetSegment(
        val x: Float,
        val y: Float,
        val color: Int,
        val width: Float,
        val first: Boolean
    )

    sealed class Action {
        data class Add(val stroke: Stroke) : Action()
        data class Remove(val strokes: List<Stroke>) : Action()
    }
}
