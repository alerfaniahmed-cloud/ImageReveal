package com.ahmed.imagereveal

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStream

class ImageRevealActivity : AppCompatActivity() {

    private lateinit var maskView: ImageMaskView
    private var isAddMode = false

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadBitmap(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_reveal)

        maskView = findViewById(R.id.maskView)
        val btnPick = findViewById<Button>(R.id.btnPickImage)
        val btnToggle = findViewById<Button>(R.id.btnToggleMode)
        val btnReset = findViewById<Button>(R.id.btnReset)
        val btnSave = findViewById<Button>(R.id.btnSave)

        btnPick.setOnClickListener { pickImageLauncher.launch("image/*") }

        btnToggle.setOnClickListener {
            isAddMode = !isAddMode
            maskView.mode = if (isAddMode) ImageMaskView.Mode.ADD else ImageMaskView.Mode.REVEAL
            btnToggle.text = if (isAddMode) "وضع الكشف" else "وضع التحديد"
            Toast.makeText(
                this,
                if (isAddMode) "ارسم مربع فوق أي منطقة تبي تخفيها" else "اضغط على أي منطقة سوداء لكشفها",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnReset.setOnClickListener { maskView.resetReveal() }

        btnSave.setOnClickListener { saveImage() }
    }

    private fun loadBitmap(uri: Uri) {
        val bmp: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            android.provider.MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }
        maskView.setImage(bmp)
    }

    private fun saveImage() {
        val bmp = maskView.exportCurrentState()
        if (bmp == null) {
            Toast.makeText(this, "اختر صورة أولاً", Toast.LENGTH_SHORT).show()
            return
        }

        val filename = "ImageReveal_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ImageReveal")
            }
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Toast.makeText(this, "فشل الحفظ", Toast.LENGTH_SHORT).show()
            return
        }

        val out: OutputStream? = contentResolver.openOutputStream(uri)
        out?.use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        Toast.makeText(this, "تم حفظ الصورة بالمعرض", Toast.LENGTH_SHORT).show()
    }
}
