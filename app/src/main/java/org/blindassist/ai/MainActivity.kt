package org.blindassist.ai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var preview: PreviewView
    private lateinit var overlay: DetectionOverlay
    private lateinit var status: TextView
    private lateinit var recent: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var speechSwitch: Switch
    private lateinit var analysisExecutor: ExecutorService
    private var detector: OnnxDetector? = null
    private var tts: TextToSpeech? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val running=AtomicBoolean(false)
    private val processing=AtomicBoolean(false)
    private val gate=AlertGate()
    private val alertHistory=ArrayDeque<String>()
    private var cooldown=3500L
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted && running.get()) bindCamera() else if(!granted) { status.text="Camera permission is required. Enable it in app settings to use live detection."; running.set(false); updateButton() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analysisExecutor=Executors.newSingleThreadExecutor(); tts=TextToSpeech(this,this)
        buildUi()
        status.text="Loading on-device AI model…"
        analysisExecutor.execute {
            try { detector=OnnxDetector(this).also { it.confidenceThreshold=getSharedPreferences("assist_settings",MODE_PRIVATE).getInt("confidence_percent",35)/100f }; runOnUiThread { status.text="Model ready. Press Start." } }
            catch(e: Exception) { runOnUiThread { status.text="Model unavailable: ${e.message ?: "check app assets"}. Add a validated yolo.onnx and labels.txt, then rebuild."; startButton.isEnabled=false } }
        }
    }

    private fun buildUi() {
        val prefs=getSharedPreferences("assist_settings",MODE_PRIVATE)
        cooldown=prefs.getLong("cooldown_ms",3500L)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(20,18,20,12); setBackgroundColor(0xFF101820.toInt()) }
        val title=TextView(this).apply { text="Blind Assist AI"; textSize=25f; setTextColor(0xFFFFFFFF.toInt()); contentDescription="Blind Assist AI" }
        status=TextView(this).apply { textSize=16f; setTextColor(0xFFFFD54F.toInt()); setPadding(0,10,0,10); setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE) }
        val cameraFrame=FrameLayout(this).apply { contentDescription="Live camera preview with detected object boxes" }
        preview=PreviewView(this).apply { scaleType=PreviewView.ScaleType.FIT_CENTER; contentDescription="Live camera preview" }
        overlay=DetectionOverlay(this).apply { importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        cameraFrame.addView(preview,FrameLayout.LayoutParams(-1,-1)); cameraFrame.addView(overlay,FrameLayout.LayoutParams(-1,-1))
        startButton=Button(this).apply { text="Start detection"; textSize=21f; minHeight=64; contentDescription="Start or pause live object detection"; setOnClickListener { toggle() } }
        stopButton=Button(this).apply { text="Stop detection"; textSize=18f; minHeight=56; contentDescription="Stop live camera detection"; setOnClickListener { stopDetection() } }
        speechSwitch=Switch(this).apply { text="Spoken alerts"; textSize=18f; setTextColor(0xFFFFFFFF.toInt()); isChecked=prefs.getBoolean("speech_enabled",true); contentDescription="Enable spoken object alerts"; setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("speech_enabled",checked).apply() } }
        val freqLabel=TextView(this).apply { text="Alert repeat interval"; textSize=16f; setTextColor(0xFFFFFFFF.toInt()) }
        val freq=SeekBar(this).apply { max=8; progress=prefs.getInt("frequency",5).coerceIn(0,8); contentDescription="Alert frequency, from less frequent to more frequent"; setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?,p:Int,user:Boolean) { cooldown=6000L-p*500L; prefs.edit().putInt("frequency",p).putLong("cooldown_ms",cooldown).apply() }
            override fun onStartTrackingTouch(s: SeekBar?) {} override fun onStopTrackingTouch(s: SeekBar?) {}
        }) }
        val rateLabel=TextView(this).apply { text="Speech rate"; textSize=16f; setTextColor(0xFFFFFFFF.toInt()) }
        val rate=SeekBar(this).apply { max=8; progress=prefs.getInt("speech_rate",3).coerceIn(0,8); contentDescription="Speech rate"; setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?,p:Int,user:Boolean) { prefs.edit().putInt("speech_rate",p).apply(); tts?.setSpeechRate(.7f+p*.1f) }
            override fun onStartTrackingTouch(s: SeekBar?) {} override fun onStopTrackingTouch(s: SeekBar?) {}
        }) }
        val confidenceLabel=TextView(this).apply { text="Minimum detection confidence: 35%"; textSize=16f; setTextColor(0xFFFFFFFF.toInt()) }
        val confidence=SeekBar(this).apply { max=70; progress=(prefs.getInt("confidence_percent",35)-10).coerceIn(0,70); contentDescription="Minimum object detection confidence"; setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?,p:Int,user:Boolean) { val value=(p+10)/100f; detector?.confidenceThreshold=value; prefs.edit().putInt("confidence_percent",p+10).apply(); confidenceLabel.text="Minimum detection confidence: ${((p+10))}%" }
            override fun onStartTrackingTouch(s: SeekBar?) {} override fun onStopTrackingTouch(s: SeekBar?) {}
        }) }
        recent=TextView(this).apply { text="Recent alerts: none"; textSize=17f; setTextColor(0xFFFFFFFF.toInt()); setPadding(0,10,0,5); contentDescription="Recent spoken alerts"; setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE) }
        val safety=TextView(this).apply { text="Experimental assistance only. Keep using your white cane, guide dog, and mobility training. The app does not measure distance or guarantee a clear path."; textSize=14f; setTextColor(0xFFCFD8DC.toInt()) }
        root.addView(title); root.addView(status); root.addView(cameraFrame,LinearLayout.LayoutParams(-1,0,1f)); root.addView(startButton); root.addView(stopButton); root.addView(speechSwitch); root.addView(freqLabel); root.addView(freq); root.addView(rateLabel); root.addView(rate); root.addView(confidenceLabel); root.addView(confidence); root.addView(recent); root.addView(safety)
        setContentView(root)
    }

    private fun toggle() {
        if(running.get()) { running.set(false); cameraProvider?.unbindAll(); overlay.setDetections(emptyList()); status.text="Paused. Press Resume to continue."; updateButton(); return }
        running.set(true); updateButton()
        if(detector==null) { status.text="AI model is still loading."; running.set(false); updateButton(); return }
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) bindCamera() else permission.launch(Manifest.permission.CAMERA)
    }
    private fun updateButton() { startButton.text=if(running.get()) "Pause detection" else "Start / resume detection" }
    private fun stopDetection() {
        running.set(false); cameraProvider?.unbindAll(); overlay.setDetections(emptyList()); recent.text="Recent alerts: none"; alertHistory.clear()
        status.text=if(detector==null) "Stopped. AI model is unavailable." else "Stopped. Press Start when ready."; updateButton()
    }

    private fun bindCamera() {
        val future=ProcessCameraProvider.getInstance(this)
        future.addListener({
            if(!running.get()) return@addListener
            try {
                val provider=future.get(); cameraProvider=provider
                val p=Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
                analysis.setAnalyzer(analysisExecutor) { image -> analyze(image) }
                provider.unbindAll(); provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,p,analysis)
                status.text="Camera active. Scanning locally on this device."
            } catch(e:Exception) { running.set(false); updateButton(); status.text="Camera could not start: ${e.message ?: "check camera permission"}" }
        },ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy) {
        try {
            if(!running.get() || !processing.compareAndSet(false,true)) return
            val plane=image.planes[0]; val buffer=plane.buffer; val bytes=ByteArray(buffer.remaining()); buffer.get(bytes)
            val w=image.width; val h=image.height; val stride=plane.rowStride; val pixelStride=plane.pixelStride
            val pixels=IntArray(w*h)
            for(y in 0 until h) for(x in 0 until w) { val i=y*stride+x*pixelStride; val r=bytes[i].toInt() and 255; val g=bytes[i+1].toInt() and 255; val b=bytes[i+2].toInt() and 255; val a=bytes[i+3].toInt() and 255; pixels[y*w+x]=(a shl 24) or (r shl 16) or (g shl 8) or b }
            var bmp=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888)
            val degrees=image.imageInfo.rotationDegrees
            if(degrees!=0) { val matrix=Matrix().apply { postRotate(degrees.toFloat()) }; bmp=Bitmap.createBitmap(bmp,0,0,bmp.width,bmp.height,matrix,true) }
            val frameWidth=bmp.width; val frameHeight=bmp.height
            val results=detector?.detect(bmp).orEmpty(); bmp.recycle()
            runOnUiThread {
                if(running.get()) {
                    overlay.setDetections(results,frameWidth,frameHeight)
                    val alert=DetectionLogic.alert(results)
                    status.text=if(results.isEmpty()) "Scanning. No confident objects detected." else "${results.size} object${if(results.size==1) "" else "s"} detected."
                    if(alert!=null) {
                        if(speechSwitch.isChecked && gate.shouldSpeak(alert,System.currentTimeMillis())) {
                            if(alertHistory.firstOrNull()!=alert) { alertHistory.addFirst(alert); while(alertHistory.size>3) alertHistory.removeLast() }
                            recent.text="Recent spoken alerts:\n${alertHistory.joinToString("\n")}" 
                            tts?.speak(alert,TextToSpeech.QUEUE_FLUSH,null,"object-alert")
                        }
                    }
                }
            }
        } catch(e:Exception) { runOnUiThread { status.text="Detection error: ${e.message ?: "frame unavailable"}" } }
        finally { processing.set(false); image.close() }
    }
    override fun onInit(code:Int) { if(code==TextToSpeech.SUCCESS) { tts?.language=Locale.US; val rate=getSharedPreferences("assist_settings",MODE_PRIVATE).getInt("speech_rate",3); tts?.setSpeechRate(.7f+rate*.1f) } }
    override fun onPause() { super.onPause(); if(running.get()) { running.set(false); cameraProvider?.unbindAll(); updateButton(); status.text="Paused while app is in the background." } }
    override fun onDestroy() { running.set(false); cameraProvider?.unbindAll(); analysisExecutor.shutdown(); detector?.close(); tts?.stop(); tts?.shutdown(); super.onDestroy() }
}
