package com.app.applicationml

import android.Manifest.permission.CAMERA
import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.app.applicationml.ml.Model
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.android.synthetic.main.activity_main.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import pyxis.uzuki.live.mediaresizer.MediaResizer
import pyxis.uzuki.live.mediaresizer.data.ImageResizeOption
import pyxis.uzuki.live.mediaresizer.data.ResizeOption
import pyxis.uzuki.live.mediaresizer.model.ImageMode
import pyxis.uzuki.live.mediaresizer.model.MediaType
import pyxis.uzuki.live.mediaresizer.model.ScanRequest
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    lateinit var bitmap: Bitmap
    lateinit var date: String
    lateinit var photoFile: File
    lateinit var fileProvider: Uri

    var output: ByteBuffer? = null

    var uri: Uri? = null
    val FILE_NAME = "pic.jpg"
    lateinit var name: String

    var pressGal: Boolean = false
    var pressCam: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


            // Make sure we're running on Honeycomb or higher to use ActionBar APIs
            /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                actionBar!!.setDisplayHomeAsUpEnabled(true)
            }*/


        val fileName = "classes.txt"
        val inpString = application.assets.open(fileName).bufferedReader().use { it.readText() }

        val sdf = SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z", Locale.ENGLISH)
        date = sdf.format(Date())

        btnSelect.setOnClickListener(View.OnClickListener {

            tvResult.text = ""
            var intent: Intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"

            startActivityForResult(intent, 100)

        })


        btnCapture.setOnClickListener(View.OnClickListener {
            tvResult.text = ""
            askForPermission()
        })

    }

    private fun uploadImage() {


            var pd = ProgressDialog(this)
            pd.setTitle("Uploading...")

            pd.show()

            var str = UUID.randomUUID().toString()

            var imageRef = FirebaseStorage.getInstance().reference.child("Images")

            imageRef.child(str).putFile(uri!!)
                    .addOnSuccessListener {
                        pd.dismiss()

                        val task = it.metadata!!.reference!!.downloadUrl
                        task.addOnSuccessListener {

                            var downloadURL = it.toString()

                            Toast.makeText(this, "Uploaded",Toast.LENGTH_SHORT).show()

                            val db = FirebaseDatabase.getInstance()
                            val ref = db.reference.child("Data")

                            var hashMap : HashMap<String, String> = HashMap<String, String> ()

                             hashMap.put("Name",name)
                             hashMap.put("Date",date)
                             hashMap.put("Image URL", downloadURL)

                             ref.push().setValue(hashMap).addOnSuccessListener {
                                 Toast.makeText(this, "Saved to database.",Toast.LENGTH_SHORT).show()
                                 ivImg.setImageBitmap(null)
                             }.addOnFailureListener{
                                 Toast.makeText(this, "Not saved to database.",Toast.LENGTH_SHORT).show()
                             }
                         }


                        ivImg.setImageBitmap(null)
                        tvResult.text = ""

                        pressCam = false
                        pressGal =false

                    }.addOnFailureListener{
                        Toast.makeText(this, "Upload failed due to error:  ${it.message.toString()}",Toast.LENGTH_SHORT).show()
                        pd.dismiss()
                    }


    }

    private fun predictName(){

        val fileName = "classes.txt"
        val inpString = application.assets.open(fileName).bufferedReader().use { it.readText() }
        val cropList = inpString.split("\n")

        var resized: Bitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, true)

        /*output = ByteBuffer.allocateDirect(200 * 200 * 1 * 4).order(ByteOrder.nativeOrder())
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

                output!!.putFloat(b.toFloat())
                output!!.putFloat(g.toFloat())
                output!!.putFloat(r.toFloat())
            }
        }*/


        val model = Model.newInstance(this)

        // Creates inputs for reference.
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 200, 200, 1), DataType.FLOAT32)

        output = convertBitmapToByteBuffer(resized)

        inputFeature0.loadBuffer(output!!)

       // ivImg.setImageBitmap(getOutputImage())

        // Runs model inference and gets result.
        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer


        var max = getMax(outputFeature0.floatArray)

        if(max == -1){
            tvResult.text = "Invalid picture"
            name = "Invalid"
        }

        else{
            name = cropList[max]
            tvResult.text = "Plant Name: ${cropList[max]}"
          //  uploadImage()
        }


        // Releases model resources if no longer used.
        model.close()


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
            Toast.makeText(this, "Camera permission is necessary.", Toast.LENGTH_SHORT).show()
        }

    }

    fun openCamera()
    {
        var camIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        photoFile = getPhotoFile(FILE_NAME)


        fileProvider = FileProvider.getUriForFile(this,"com.app.applicationml.fileprovider", photoFile)
        camIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileProvider)



        startActivityForResult(camIntent, 99)
    }

    private fun getPhotoFile(fileName: String): File {
            val storageDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            return File.createTempFile(fileName,".jpg", storageDirectory)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(requestCode == 100 && resultCode == Activity.RESULT_OK)
        {
            ivImg.setImageURI(data?.data)
            uri = data?.data
            bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)

            predictName()
            uploadImage()


        }
        else if(requestCode == 99 && resultCode == Activity.RESULT_OK)
        {



            bitmap = BitmapFactory.decodeFile(photoFile.path)

            /*val nh = (bitmap.height * (512.0 / bitmap.width)).toInt()
            val scaled = Bitmap.createScaledBitmap(bitmap, 512,nh,false)*/
           // bitmap.compress (Bitmap.CompressFormat.JPEG, 25, FileOutputStream(photoFile))

            ivImg.setImageBitmap(bitmap)

         //   MediaResizerGlobal.initializeApplication(this)

            val resizeOption = ImageResizeOption.Builder()
                .setImageProcessMode(ImageMode.ResizeAndCompress)
                .setImageResolution(1280, 720)
                .setBitmapFilter(false)
                .setCompressFormat(Bitmap.CompressFormat.JPEG)
                .setCompressQuality(75)
                .setScanRequest(ScanRequest.TRUE)
                .build()

            val option = ResizeOption.Builder()
                .setMediaType(MediaType.IMAGE)
                .setImageResizeOption(resizeOption)
                .setTargetPath(photoFile.absolutePath)
                .setOutputPath(photoFile.absolutePath)
                .build()

            MediaResizer.process(option)

            uri = Uri.fromFile(photoFile)

            predictName()
            uploadImage()

        }

    }

    fun getMax(arr: FloatArray): Int{

        var ind = -1
        var min = 0.0f

            for (i in 0..3) {

              //  Toast.makeText(this, "val: ${arr[i]}", Toast.LENGTH_SHORT).show()

                if (arr[i]>0.7 && arr[i]>min) {
                    ind = i
                    min = arr[i]

                }

            }

        return ind
    }

    private fun convertBitmapToByteBuffer(bmp: Bitmap): ByteBuffer {
        // Specify the size of the byteBuffer
        val byteBuffer = ByteBuffer.allocateDirect(200 * 200 * 1 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        // Calculate the number of pixels in the image
        val pixels = IntArray(200 * 200)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        var pixel = 0
        // Loop through all the pixels and save them into the buffer
        for (i in 0 until 200) {
            for (j in 0 until 200) {
                val pixelVal = pixels[pixel++]
                // Do note that the method to add pixels to byteBuffer is different for quantized models over normal tflite models
                 //byteBuffer.put((pixelVal shr 16 and 0xFF).toByte())
                 //byteBuffer.put((pixelVal shr 8 and 0xFF).toByte())
                 //byteBuffer.put((pixelVal and 0xFF).toByte())

 //               byteBuffer.putFloat((pixelVal shr 16 and 0xFF) / 255f)
//                byteBuffer.putFloat((pixelVal shr 8 and 0xFF) / 255f)
                byteBuffer.putFloat(((pixelVal and 0xFF).toFloat()))
            }
        }

        // Recycle the bitmap to save memory
        bmp.recycle()
        return byteBuffer
    }

    private fun getOutputImage(): Bitmap {
        output?.rewind() // Rewind the output buffer after running.

        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(200 * 200) // Set your expected output's height and width
        for (i in 0 until 200 * 200) {
            val a = 0xFF
            //val r: Float = output?.float!! * 255.0f
           // val g: Float = output?.float!! * 255.0f
            val b: Float = output?.float!!
            pixels[i] = a shl 24 or  b.toInt()
        }
//a shl 24 or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
        bitmap.setPixels(pixels, 0, 200, 0, 0, 200, 200)

        return bitmap
    }


    override fun onBackPressed() {
        super.onBackPressed()
        tvResult.text = ""
        ivImg.setImageBitmap(null)
    }
}


