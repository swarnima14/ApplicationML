package com.app.applicationml

import android.Manifest.permission.CAMERA
import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.app.applicationml.ml.Model
import com.google.firebase.storage.FirebaseStorage
import kotlinx.android.synthetic.main.activity_main.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import java.util.jar.Manifest


class MainActivity : AppCompatActivity() {

    lateinit var bitmap: Bitmap
    lateinit var date: String
     var uri: Uri? = null
    //lateinit var imgview: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val fileName = "classes.txt"
        val inpString = application.assets.open(fileName).bufferedReader().use { it.readText() }
        val townList = inpString.split("\n")

        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyMMddHHmmssZ", Locale.ENGLISH)
        date = sdf.format(calendar.time)

        btnSelect.setOnClickListener(View.OnClickListener {

            var intent: Intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"

            startActivityForResult(intent, 100)

        })


        btnCapture.setOnClickListener(View.OnClickListener {
            askForPermission()
        })


        btnPredict.setOnClickListener(View.OnClickListener {

            var resized: Bitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, true)


            val input = ByteBuffer.allocateDirect(200*200*1*4).order(ByteOrder.nativeOrder())
            for (y in 0 until 200) {
                for (x in 0 until 200) {
                    val px = resized.getPixel(x, y)

                    // Get channel values from the pixel value.
                    val r = Color.red(px)
                    val g = Color.green(px)
                    val b = Color.blue(px)

                    // Normalize channel values to [-1.0, 1.0]. This requirement depends on the model.
                    // For example, some models might require values to be normalized to the range
                    // [0.0, 1.0] instead.
                    val rf = (r - 127) / 255f
                    val gf = (g - 127) / 255f
                    val bf = (b - 127) / 255f
                    
                    input.putFloat(bf)
                }
            }


            val model = Model.newInstance(this)

            // Creates inputs for reference.
            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 200, 200, 1), DataType.FLOAT32)

            /*var tbuffer = TensorImage.fromBitmap(resized)
            var byteBuffer = tbuffer.buffer*/


            inputFeature0.loadBuffer(input)

            // Runs model inference and gets result.
            val outputs = model.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer

            var max = getMax(outputFeature0.floatArray)

            tvResult.text = townList[max]

            // Releases model resources if no longer used.
            model.close()

        })


        btnUpload.setOnClickListener(View.OnClickListener {

            uploadImage()

        })


    }

    private fun uploadImage() {

        if(uri!=null)
        {
            var pd = ProgressDialog(this)
            pd.setTitle("Uploading...")
            pd.show()

            var name = tvResult.text
            Toast.makeText(this, "name $name" ,Toast.LENGTH_LONG).show()
            var str = name.toString() + " "+ date


            var imageRef = FirebaseStorage.getInstance().reference.child("Images")
            uri?.let { imageRef.child(str).putFile(it)
                    .addOnSuccessListener {
                        pd.dismiss()
                        val downloadURL = it.storage.downloadUrl.toString()
                        Toast.makeText(this, "uploaded $downloadURL",Toast.LENGTH_LONG).show()
                        ivImg.setImageBitmap(null)
                        tvResult.text = ""

            } .addOnFailureListener{
                        Toast.makeText(this, "failed ${it.message.toString()}",Toast.LENGTH_LONG).show()
                        pd.dismiss()
                    }
            }

        }

    }

    fun askForPermission()
    {
     if((ActivityCompat.checkSelfPermission(this, CAMERA)) !=
             PackageManager.PERMISSION_GRANTED)
     {
         ActivityCompat.requestPermissions(this, arrayOf(CAMERA), 11)
     }
        else
     {
         openCamera()
     }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == 11 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
        {
            openCamera()
        }
        else
        {
            Toast.makeText(this, "Camera permission is necessary.", Toast.LENGTH_LONG).show()
        }

    }

    fun openCamera()
    {
        var camIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(camIntent, 99)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(requestCode == 100 && resultCode == Activity.RESULT_OK) {

            ivImg.setImageURI(data?.data)

           uri = data?.data

                bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            }
                 else if(requestCode == 99 && resultCode == Activity.RESULT_OK)
                {

                    uri = data?.data
                    bitmap = data?.getParcelableExtra<Bitmap>("data")!!
                    ivImg.setImageBitmap(bitmap)
                    Toast.makeText(this,"check cam",Toast.LENGTH_LONG).show()
                }

    }

    fun getMax(arr: FloatArray): Int{

        var ind = 0
        var min = 0.0f

        for(i in 0..3)
        {

            if (arr[i] > min)
            {
                ind = i
                min = arr[i]

            }

        }
        return ind
    }

    override fun onBackPressed() {
        super.onBackPressed()
        tvResult.text = ""
        ivImg.setImageBitmap(null)
    }
}


