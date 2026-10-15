package org.blindassist.ai

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.*
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

class OnnxDetector(context: Context) : AutoCloseable {
    companion object { const val SIZE = 640; const val CONFIDENCE = .35f }
    @Volatile var confidenceThreshold: Float = CONFIDENCE
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val labels: List<String>
    private val inputName: String
    private val outName: String
    init {
        val model = context.assets.open("yolo.onnx").use { it.readBytes() }
        session = env.createSession(model, OrtSession.SessionOptions())
        val input = session.inputInfo.entries.firstOrNull() ?: error("Model has no input")
        inputName = input.key
        val tensorInfo = input.value.info as? TensorInfo ?: error("Model input is not a tensor")
        require(tensorInfo.type == OnnxJavaType.FLOAT) { "Expected float input, got ${tensorInfo.type}" }
        val shape = tensorInfo.shape
        require(shape.size == 4 && shape[1] == 3L && shape[2] == SIZE.toLong() && shape[3] == SIZE.toLong()) { "Expected NCHW RGB input [1,3,640,640], got ${shape.contentToString()}" }
        outName = session.outputNames.firstOrNull() ?: error("Model has no output")
        val outputInfo = session.outputInfo[outName]?.info as? TensorInfo ?: error("Model output is not a tensor")
        require(outputInfo.type == OnnxJavaType.FLOAT) { "Expected float output, got ${outputInfo.type}" }
        labels = context.assets.open("labels.txt").bufferedReader().useLines { it.toList() }
        require(labels.isNotEmpty()) { "Class label asset is empty" }
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val srcW=bitmap.width; val srcH=bitmap.height
        val scale=min(SIZE.toFloat()/srcW, SIZE.toFloat()/srcH)
        val rw=(srcW*scale).toInt(); val rh=(srcH*scale).toInt(); val px=(SIZE-rw)/2; val py=(SIZE-rh)/2
        val resized=Bitmap.createScaledBitmap(bitmap,rw,rh,true)
        val pixels=IntArray(rw*rh); resized.getPixels(pixels,0,rw,0,0,rw,rh)
        val pad=114f/255f
        val data=FloatArray(3*SIZE*SIZE) { pad }
        for (y in 0 until rh) for (x in 0 until rw) {
            val c=pixels[y*rw+x]; val i=(y+py)*SIZE+x+px
            data[i]=((c shr 16) and 255)/255f; data[SIZE*SIZE+i]=((c shr 8) and 255)/255f; data[2*SIZE*SIZE+i]=(c and 255)/255f
        }
        if (resized !== bitmap) resized.recycle()
        val tensor=OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1,3,SIZE.toLong(),SIZE.toLong()))
        tensor.use {
            val result=session.run(mapOf(inputName to tensor))
            result.use {
                val value=it.get(outName).orElseThrow().value
                val dims=(it.get(outName).orElseThrow().info as TensorInfo).shape
                return decode(value,dims,srcW,srcH,scale,px,py)
            }
        }
    }

    private fun decode(raw: Any, dims: LongArray, srcW: Int, srcH: Int, scale: Float, padX: Int, padY: Int): List<Detection> {
        require(dims.size == 3 && dims[0] == 1L) { "Unsupported output shape ${dims.contentToString()}" }
        val a=dims[1].toInt(); val b=dims[2].toInt()
        val expectedAttributes = labels.size + 4
        val transposed = (a == expectedAttributes && b != expectedAttributes) || (a < expectedAttributes && b > a)
        val attrs=if(transposed) a else b; val count=if(transposed) b else a
        require(attrs >= labels.size + 4) { "Output has $attrs values per box; expected at least ${labels.size+4}" }
        val flat=flatten(raw)
        fun v(row:Int, col:Int): Float = if(transposed) flat[col*count+row] else flat[row*attrs+col]
        val found=mutableListOf<Detection>()
        for (i in 0 until count) {
            var best=-1; var conf=0f
            // Ultralytics detect export without NMS: xywh followed by per-class scores.
            for (c in 0 until labels.size) { val score=v(i,c+4); if(score>conf){conf=score;best=c} }
            if (best<0 || conf<confidenceThreshold) continue
            val cx=v(i,0); val cy=v(i,1); val w=v(i,2); val h=v(i,3)
            val l=((cx-w/2-padX)/scale/srcW).coerceIn(0f,1f); val t=((cy-h/2-padY)/scale/srcH).coerceIn(0f,1f)
            val r=((cx+w/2-padX)/scale/srcW).coerceIn(0f,1f); val bot=((cy+h/2-padY)/scale/srcH).coerceIn(0f,1f)
            if(r>l && bot>t) found += Detection(best,labels[best],conf,l,t,r,bot)
        }
        return DetectionLogic.nms(found)
    }
    private fun flatten(value: Any): FloatArray {
        val total=rawShape(value).fold(1) { x,y -> x*y }
        val result=FloatArray(total)
        // Recurse into arbitrary JVM multidimensional ONNX output arrays.
        fun append(v: Any, dst: FloatArray, cursor: IntArray) {
            when(v) { is FloatArray -> { v.copyInto(dst,cursor[0]); cursor[0]+=v.size }; is Number -> dst[cursor[0]++]=v.toFloat(); else -> for(i in 0 until java.lang.reflect.Array.getLength(v)) append(java.lang.reflect.Array.get(v,i),dst,cursor) }
        }
        append(value,result,intArrayOf(0)); return result
    }
    private fun rawShape(value: Any): List<Int> {
        val dims=mutableListOf<Int>(); var v: Any?=value
        while(v != null && v.javaClass.isArray) { val n=java.lang.reflect.Array.getLength(v); dims += n; v=if(n>0) java.lang.reflect.Array.get(v,0) else null }
        return dims
    }
    override fun close() { session.close() }
}
