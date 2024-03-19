package com.sibar.fruitdetectors

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.sibar.fruitdetectors.ml.ModelTM10 as Model
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

import android.speech.tts.TextToSpeech
import android.media.AudioManager
import android.widget.Toast
import androidx.core.os.postDelayed
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var textToSpeech: TextToSpeech

    lateinit var capReq: CaptureRequest.Builder
    lateinit var handlerThread: HandlerThread
    lateinit var handler: Handler
    lateinit var labels: List<String>
    var colors = listOf<Int>(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED)
    val paint = Paint()
    lateinit var imageProcessor: ImageProcessor
    lateinit var bitmap: Bitmap
    lateinit var cameraDevice: CameraDevice
    lateinit var cameraCaptureSession: CameraCaptureSession
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var model: Model
    lateinit var objectInfoTextView: LinearLayout
    lateinit var objectNameCont: TextView
    lateinit var ripenessCont: TextView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        get_permission()

        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = Model.newInstance(this)
        handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        textureView = findViewById(R.id.textureView)
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                open_camera()
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                startCapturing()
            }
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        objectInfoTextView = findViewById(R.id.objectInfoTextView)
        objectNameCont = findViewById(R.id.objectName)
        ripenessCont = findViewById(R.id.ripeness)
    }

    private fun voiceOut(option: Int){
        textToSpeech = TextToSpeech(this){status->
            if(status == TextToSpeech.SUCCESS){
                val result = textToSpeech.setLanguage(Locale.getDefault())

                if(result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED){
                    Toast.makeText(this, "Language not supported", Toast.LENGTH_LONG).show()
                }
            }
        }

//        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
//        audioManager.setStreamVolume(
//            AudioManager.STREAM_MUSIC,
//            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
//            0
//        )

        val objectName = objectNameCont.text.toString().trim()
        val quality = ripenessCont.text.toString().trim()

        // Add a short delay before speaking the value
        if (option == 1){
            Handler().postDelayed({
                textToSpeech.speak(objectName, TextToSpeech.QUEUE_FLUSH, null, null)
            }, 3000)
        }
        else if (option == 2){
            Handler().postDelayed({
                textToSpeech.speak(objectName, TextToSpeech.QUEUE_FLUSH, null, null)
            }, 3000)

            Handler().postDelayed({
                textToSpeech.speak(quality, TextToSpeech.QUEUE_FLUSH, null, null)
            }, 6000)
        }
        else {
            Handler().postDelayed({
                textToSpeech.speak(objectName, TextToSpeech.QUEUE_FLUSH, null, null)
            }, 2000)
        }
    }

    private var isCapturingPaused = false // For pausing after every detection and voicing out

    @SuppressLint("SetTextI18n")
    private fun startCapturing() {
        // Capture an image from the camera and store it in the 'bitmap' variable
        Handler().postDelayed({
            bitmap = textureView.bitmap!!
            var image = TensorImage.fromBitmap(bitmap) // Process the captured image
            image = imageProcessor.process(image)

            // Perform object detection
            val outputs2 = model.process(image)
            val probability = outputs2.probabilityAsCategoryList

            if (probability.isNotEmpty() && !isCapturingPaused) {
                var maxConfidence = 0.0f
                var detectedLabel = ""

                // Find the label with the highest confidence rate
                for (category in probability) {
                    if (category.score > maxConfidence) {
                        maxConfidence = (category.score * 100)
                        detectedLabel = category.label
                    }
                }

                if (detectedLabel == "Invalid" || detectedLabel == "") {
                    runOnUiThread {
                        objectNameCont.text = "Invalid!"
                        ripenessCont.text = ""
                        isCapturingPaused = true // Set to true for delay
                        Handler().postDelayed({ isCapturingPaused = false }, 4000)
                        voiceOut(3) // Voicing out the object information
                    }
                }
                else {
                    var ripeness: String? = null // Initialize ripeness variable

                    if (detectedLabel.contains("unripe", ignoreCase = true)) {
                        ripeness = "Unripe"
                    } else if (detectedLabel.contains("over ripe", ignoreCase = true)) {
                        ripeness = "Over ripe"
                    } else if (detectedLabel.contains("overripe", ignoreCase = true)) {
                        ripeness = "Overripe"
                    } else if (detectedLabel.contains("ripe", ignoreCase = true)) {
                        ripeness = "Ripe"
                    }

                    runOnUiThread {
                        if (ripeness != null) {
                            val fruitName = detectedLabel.replace(Regex("(?i)$ripeness"), "").trim()
                            objectNameCont.text = "Fruit: $fruitName"
                            ripenessCont.text = "Quality: $ripeness"
                        } else {
                            objectNameCont.text = detectedLabel
                            ripenessCont.text = ""
                        }
                    }

                    if (maxConfidence.toInt() >= 90) {
                        isCapturingPaused = true // Set to true for delay

                        if (ripeness != null) {
                            voiceOut(2) // Voicing out the object information
                            Handler().postDelayed({ isCapturingPaused = false }, 8500) // 8.5 seconds before setting to true to start capturing again
                        } else {
                            voiceOut(1) // Voicing out the object information
                            Handler().postDelayed({ isCapturingPaused = false }, 5500) // 5.5 seconds before setting to true to start capturing again
                        }
                    }
                }
            }
        }, 500)  // .05s delay
    }


    private fun get_permission() {
        var permissionLst = mutableListOf<String>()

        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) permissionLst.add(
            android.Manifest.permission.CAMERA
        )
        if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) permissionLst.add(
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) permissionLst.add(
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        if (permissionLst.size > 0) {
            requestPermissions(permissionLst.toTypedArray(), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        grantResults.forEach {
            if (it != PackageManager.PERMISSION_GRANTED) {
                get_permissions()
            }
        }
    }

    private fun get_permissions() {
        var permissionLst = mutableListOf<String>()

        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) permissionLst.add(
            android.Manifest.permission.CAMERA
        )
        if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) permissionLst.add(
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) permissionLst.add(
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        if (permissionLst.size > 0) {
            requestPermissions(permissionLst.toTypedArray(), 101)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
    }

    @SuppressLint("MissingPermission")
    fun open_camera() {
        cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0
                capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                var surface = Surface(textureView.surfaceTexture)
                capReq.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface), object :
                    CameraCaptureSession.StateCallback() {
                    override fun onConfigured(p0: CameraCaptureSession) {
                        cameraCaptureSession = p0
                        cameraCaptureSession.setRepeatingRequest(capReq.build(), null, null)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {

                    }
                }, handler)
            }

            override fun onDisconnected(camera: CameraDevice) {

            }

            override fun onError(camera: CameraDevice, error: Int) {

            }
        }, handler)
    }
}