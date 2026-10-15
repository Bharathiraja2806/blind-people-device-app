package org.blindassist.ai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class DetectionOverlay(context: Context) : View(context) {
    private val box=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFFFFD54F.toInt(); style=Paint.Style.STROKE; strokeWidth=5f }
    private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFFFFFFFF.toInt(); textSize=38f; style=Paint.Style.FILL; setShadowLayer(4f,1f,1f,0xFF000000.toInt()) }
    @Volatile private var items: List<Detection> = emptyList()
    @Volatile private var imageWidth=1
    @Volatile private var imageHeight=1
    fun setDetections(value: List<Detection>, frameWidth: Int = imageWidth, frameHeight: Int = imageHeight) { items=value; imageWidth=frameWidth.coerceAtLeast(1); imageHeight=frameHeight.coerceAtLeast(1); postInvalidateOnAnimation() }
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas)
        val scale=minOf(width.toFloat()/imageWidth,height.toFloat()/imageHeight); val ox=(width-imageWidth*scale)/2f; val oy=(height-imageHeight*scale)/2f
        items.forEach { d ->
        val l=ox+d.left*imageWidth*scale; val t=oy+d.top*imageHeight*scale; val r=ox+d.right*imageWidth*scale; val b=oy+d.bottom*imageHeight*scale
        canvas.drawRect(l,t,r,b,box); canvas.drawText("${d.label} ${(d.confidence*100).toInt()}%",l,(t-10f).coerceAtLeast(38f),text)
    } }
}
