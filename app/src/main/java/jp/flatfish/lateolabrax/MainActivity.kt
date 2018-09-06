package jp.flatfish.lateolabrax

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.PixelCopy
import android.widget.TextView
import android.widget.Toast
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage

class MainActivity : AppCompatActivity() {
    private val TAG = MainActivity::class.java.simpleName
    private val MIN_OPENGL_VERSION = 3.0
    private var arFragment: ArFragment? = null
    private var viewRenderable: ViewRenderable? = null
    private var textView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!checkIsSupportedDeviceOrFinish(this)) {
            return
        }

        setContentView(R.layout.activity_main)
        textView = LayoutInflater.from(this).inflate(R.layout.text_view, null) as TextView
        arFragment = supportFragmentManager.findFragmentById(R.id.ux_fragment) as ArFragment?
        ViewRenderable.builder()
                .setView(this, textView)
                .build()
                .thenAccept({ renderable -> viewRenderable = renderable })

        arFragment?.setOnTapArPlaneListener { hitResult: HitResult, plane: Plane, motionEvent: MotionEvent ->
            // Create the Anchor.
            val anchor = hitResult.createAnchor()
            val anchorNode = AnchorNode(anchor)
            anchorNode.setParent(arFragment?.getArSceneView()?.scene)
            takePhoto(anchorNode)
        }
    }

    //README: Detect labels with MLKit after take photo
    private fun takePhoto(anchorNode: AnchorNode) {
        val view = arFragment?.getArSceneView()

        // Create a bitmap the size of the scene view.
        val bitmap = Bitmap.createBitmap(view!!.width, view.height,
                Bitmap.Config.ARGB_8888)

        // Create a handler thread to offload the processing of the image.
        val handlerThread = HandlerThread("PixelCopier")
        handlerThread.start()
        // Make the request to copy.
        PixelCopy.request(view, bitmap, { copyResult ->
            if (copyResult == PixelCopy.SUCCESS) {
                val image = FirebaseVisionImage.fromBitmap(bitmap)
                FirebaseVision.getInstance()
                        .visionLabelDetector
                        .detectInImage(image)
                        .addOnSuccessListener { labels ->
                            labels.forEach {
                                val transformableNode = TransformableNode(arFragment?.getTransformationSystem())
                                transformableNode.setParent(anchorNode)
                                transformableNode.renderable = viewRenderable
                                transformableNode.select()
                                textView?.text = labels[0].label
                            }
                        }
                        .addOnFailureListener { e ->
                            e.printStackTrace()
                        }
            } else {
                Toast.makeText(this,"Failed to copyPixels: $copyResult", Toast.LENGTH_LONG).show()
            }
            handlerThread.quitSafely()
        }, Handler(handlerThread.looper))
    }

    private fun checkIsSupportedDeviceOrFinish(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later")
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show()
            activity.finish()
            return false
        }
        val openGlVersionString = (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .deviceConfigurationInfo
                .glEsVersion
        if (java.lang.Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later")
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show()
            activity.finish()
            return false
        }
        return true
    }
}
