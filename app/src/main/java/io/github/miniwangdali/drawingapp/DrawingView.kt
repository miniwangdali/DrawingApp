package io.github.miniwangdali.drawingapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private var drawPath: CustomPath? = null
    private var canvasBitmap: Bitmap? = null
    private var drawPaint: Paint? = null
    private var canvasPaint: Paint? = null
    private var brushSize: Float = 0f
    private var color = Color.BLACK
    private var canvas: Canvas? = null
    private val paths = ArrayList<CustomPath>()
    private val undoPaths = ArrayList<CustomPath>()

    init {
        setupDrawing()
    }

    private fun setupDrawing() {
        drawPaint = Paint()
        drawPath = CustomPath(color, brushSize)
        drawPaint!!.color = color
        drawPaint!!.style = Paint.Style.STROKE
        drawPaint!!.strokeJoin = Paint.Join.ROUND
        drawPaint!!.strokeCap = Paint.Cap.ROUND
        canvasPaint = Paint(Paint.DITHER_FLAG)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvas = Canvas(canvasBitmap!!)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(canvasBitmap!!, 0f, 0f, canvasPaint)
        for (path in paths) {
            drawPaint!!.strokeWidth = path.brushThickness
            drawPaint!!.color = path.color
            canvas.drawPath(path, drawPaint!!)
        }
        if (!drawPath!!.isEmpty) {
            drawPaint!!.strokeWidth = drawPath!!.brushThickness
            drawPaint!!.color = drawPath!!.color
            canvas.drawPath(drawPath!!, drawPaint!!)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val touchX = event?.x
        val touchY = event?.y
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                drawPath?.color = color
                drawPath?.brushThickness = brushSize
                drawPath?.reset()
                if (touchX != null && touchY != null) {
                    drawPath?.moveTo(touchX, touchY)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchX != null && touchY != null) {
                    drawPath?.lineTo(touchX, touchY)
                }
            }
            MotionEvent.ACTION_UP -> {
                paths.add(drawPath!!)
                undoPaths.clear()
                drawPath = CustomPath(color, brushSize)
            }
            else -> return false
        }
        invalidate()
        return true
    }

    public fun setBrushSize(size: Float) {
        brushSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            size,
            resources.displayMetrics
        )
        drawPaint!!.strokeWidth = brushSize
    }

    public fun setColor(newColor: Int) {
        color = newColor
        drawPaint!!.color = color
    }

    public fun undo () {
        if (paths.size > 0) {
            undoPaths.add(paths.removeLast())
            invalidate()
        }
    }

    public fun redo () {
        if (undoPaths.size > 0) {
            paths.add(undoPaths.removeLast())
            invalidate()
        }
    }
    internal inner class CustomPath(var color: Int, var brushThickness: Float) : Path() {

    }
}