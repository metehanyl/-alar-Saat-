package com.metehanyl.calarsaat.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class PatternLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val NODES = 9
    private val COLS = 3

    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val nodeCenters = Array(NODES) { FloatArray(2) }
    private val selectedNodes = mutableListOf<Int>()
    private var touchX = 0f
    private var touchY = 0f
    private var isDrawing = false
    private var showError = false

    var onPatternComplete: ((List<Int>) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val cellW = w.toFloat() / COLS
        val cellH = h.toFloat() / COLS
        for (i in 0 until NODES) {
            nodeCenters[i][0] = cellW * (i % COLS) + cellW / 2
            nodeCenters[i][1] = cellH * (i / COLS) + cellH / 2
        }
    }

    override fun onDraw(canvas: Canvas) {
        val activeColor = if (showError) Color.parseColor("#FF9800") else Color.parseColor("#4FC3F7")
        linePaint.color = activeColor

        for (i in 1 until selectedNodes.size) {
            val a = nodeCenters[selectedNodes[i - 1]]
            val b = nodeCenters[selectedNodes[i]]
            canvas.drawLine(a[0], a[1], b[0], b[1], linePaint)
        }
        if (isDrawing && selectedNodes.isNotEmpty()) {
            val last = nodeCenters[selectedNodes.last()]
            canvas.drawLine(last[0], last[1], touchX, touchY, linePaint)
        }

        val cellW = if (width > 0) width.toFloat() / COLS else 100f
        val smallR = cellW * 0.12f
        val bigR = cellW * 0.22f

        for (i in 0 until NODES) {
            val x = nodeCenters[i][0]
            val y = nodeCenters[i][1]
            if (i in selectedNodes) {
                nodePaint.color = activeColor
                canvas.drawCircle(x, y, bigR, nodePaint)
                nodePaint.color = Color.parseColor("#1A237E")
                canvas.drawCircle(x, y, bigR * 0.5f, nodePaint)
            } else {
                nodePaint.color = Color.parseColor("#80FFFFFF")
                canvas.drawCircle(x, y, smallR, nodePaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        touchX = event.x
        touchY = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                selectedNodes.clear()
                isDrawing = true
                showError = false
                tryAdd()
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                tryAdd()
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isDrawing = false
                if (selectedNodes.size >= 4) {
                    onPatternComplete?.invoke(selectedNodes.toList())
                } else if (selectedNodes.isNotEmpty()) {
                    triggerError()
                }
                invalidate()
            }
        }
        return true
    }

    private fun tryAdd() {
        val cellW = if (width > 0) width.toFloat() / COLS else 100f
        val threshold = cellW * 0.4f
        for (i in 0 until NODES) {
            if (i in selectedNodes) continue
            val d = hypot((touchX - nodeCenters[i][0]).toDouble(), (touchY - nodeCenters[i][1]).toDouble()).toFloat()
            if (d <= threshold) {
                selectedNodes.add(i)
                break
            }
        }
    }

    private fun triggerError() {
        showError = true
        invalidate()
        postDelayed({
            showError = false
            selectedNodes.clear()
            invalidate()
        }, 800)
    }

    fun clear() {
        selectedNodes.clear()
        isDrawing = false
        showError = false
        invalidate()
    }
}
