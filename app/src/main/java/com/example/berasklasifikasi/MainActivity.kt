package com.example.berasklasifikasi

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.RatingBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import com.example.berasklasifikasi.databinding.ActivityMainBinding
import com.example.berasklasifikasi.ml.Tflitemodelbaru
import createTempFile
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import uriToFile
import java.io.File
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var getFile: File? = null
    private lateinit var currentPhotoPath: String
    private lateinit var savebtn : Button


    companion object {
        val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        const val REQUEST_CODE_PERMISSIONS = 10
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(
                    this,
                    "Tidak mendapatkan permission.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }

        binding.gallery.setOnClickListener {
            startGallery()
        }

        binding.camera.setOnClickListener {
            startTakePhoto()
        }
        var btncount = 0
        binding.prediksi.setOnClickListener {
            btncount++
            val image = binding.imageView.drawToBitmap(Bitmap.Config.ARGB_8888)
            val bitmap = image.let { Bitmap.createScaledBitmap(it, 480, 480, false) }
            classifyImage(bitmap)
            if(btncount == 5){
                btncount = 0
                val dialog = Dialog(this@MainActivity)
                dialog.setContentView(R.layout.popuprating)

                val ratingBar = dialog.findViewById<RatingBar>(R.id.dialogRatingBar)
                ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
                    Toast.makeText(this@MainActivity, "Thank you for your rating", Toast.LENGTH_SHORT)
                        .show()
                    dialog.dismiss()
                }
                dialog.show()

            }
        }




    }

    private fun classifyImage(result: Bitmap?) {

        val model = Tflitemodelbaru.newInstance(this)

        // Creates inputs for reference.
        val inputFeature0 =
            TensorBuffer.createFixedSize(intArrayOf(1, 480, 480, 3), DataType.FLOAT32)
        val byteBuffer = ByteBuffer
            .allocateDirect(
                1 *
                        480 *
                        480 *
                        4 *
                        3
            )
            .apply { order(ByteOrder.nativeOrder()) }
        val imageSize = 480
        val intValues = IntArray(imageSize * imageSize)
        result?.getPixels(intValues, 0, imageSize, 0, 0, imageSize, imageSize)
        var pixel = 0
        for (i in 0 until imageSize) {
            for (j in 0 until imageSize) {
                val pixelValue = intValues[pixel++]
                byteBuffer.putFloat((pixelValue shr 16 and 0xFF) / 255f)
                byteBuffer.putFloat((pixelValue shr 8 and 0xFF) / 255f)
                byteBuffer.putFloat((pixelValue and 0xFF) / 255f)
            }
        }
        inputFeature0.loadBuffer(byteBuffer)
        // Runs model inference and gets result.
        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer

        val pattern = "#.0#" // rounds to 2 decimal places if needed
        val locale = Locale.ENGLISH
        val formatter = DecimalFormat(pattern, DecimalFormatSymbols(locale))
        formatter.roundingMode = RoundingMode.HALF_EVEN // this is the default rounding mode anyway

        val output = outputFeature0.floatArray.joinToString(", ") { value ->
            formatter.format(value)
        }
        val num = output.split(",")
        val pera = num[0].toFloat()
        val pulen = num[1].toFloat()

        Log.d("TAG2", outputFeature0.floatArray.joinToString(", ", "[", "]"))

        if (pera > pulen) {
            binding.textView.text = "Beras Pera"
        } else binding.textView.text = "Beras Pulen"

        val hasil = binding.textView
        val animation = AlphaAnimation(0.0f, 1.0f)
        animation.duration = 100
        animation.startOffset = 20
        animation.repeatMode  = Animation.REVERSE
        animation.repeatCount = 3
        hasil.startAnimation(animation)
//        binding.result.text = "Probabilitas = $num"
//        val outint = output.toFloat()

//        Log.d("TAG", outint.toString())
//        Log.d("TAG2", outputFeature0.floatArray.joinToString(", ", "[", "]"))
        model.close()
    }

    private val launcherIntentGallery = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedImg: Uri = result.data?.data as Uri
            val myFile = uriToFile(selectedImg, this@MainActivity)
            getFile = myFile
            binding.imageView.setImageURI(selectedImg)
        }
    }

    private fun startGallery() {
        val intent = Intent()
        intent.action = Intent.ACTION_GET_CONTENT
        intent.type = "image/*"
        val chooser = Intent.createChooser(intent, "Choose a Picture")
        launcherIntentGallery.launch(chooser)
    }

    private fun startTakePhoto() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.resolveActivity(packageManager)

        createTempFile(application).also {
            val photoURI: Uri = FileProvider.getUriForFile(
                this@MainActivity,
                "com.example.berasklasifikasi",
                it
            )
            currentPhotoPath = it.absolutePath
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            launcherIntentCamera.launch(intent)
        }
    }

    private val launcherIntentCamera = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            val myFile = File(currentPhotoPath)
            getFile = myFile
            val result = BitmapFactory.decodeFile(getFile?.path)
            binding.imageView.setImageBitmap(result)
        }
    }

}