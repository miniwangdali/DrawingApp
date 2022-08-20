package io.github.miniwangdali.drawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.TypedValue
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.children
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.ArithmeticException
import java.lang.Exception

class MainActivity : AppCompatActivity() {
    private var drawingView: DrawingView? = null
    private var brushSizeButton: ImageButton? = null
    private var backgroundImageButton: ImageButton? = null
    private var saveImageButton: ImageButton? = null
    private var undoImageButton: ImageButton? = null
    private var redoImageButton: ImageButton? = null
    private var colorPalletLayout: LinearLayout? = null
    private var currentColor: Int? = null
    private var colors = arrayListOf<Int>()

    private var customProgressDialog: Dialog? = null

    private val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val imageBackground = findViewById<ImageView>(R.id.backgroundImageView)
                imageBackground.setImageURI(result.data?.data)
            }
        }

    private var permissionResultLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value
                if (isGranted) {
                    val pickIntent =
                        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Permission denied for $permissionName",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    private fun showRationaleDialog(title: String, message: String) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title).setMessage(message)
            .setPositiveButton("Cancel") { dialog, _ -> dialog.dismiss() }
        builder.create().show()
    }

    private fun isReadStorageAllowed(): Boolean {
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        return result == PackageManager.PERMISSION_GRANTED
    }
    private fun isWriteStorageAllowed(): Boolean {
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return result == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        currentColor = getColor(R.color.black)

        drawingView = findViewById(R.id.drawingView)
        drawingView?.setBrushSize(20f)

        backgroundImageButton = findViewById(R.id.backgroundImageButton)
        backgroundImageButton?.setOnClickListener {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                showRationaleDialog("Requesting camera access", "Camera cannot be used if denied")
            } else {
                permissionResultLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }

        saveImageButton = findViewById(R.id.saveImageButton)
        saveImageButton?.setOnClickListener {
            if (isReadStorageAllowed() && isWriteStorageAllowed()) {
                showProgressDialog()
                lifecycleScope.launch {
                    val image = getBitmapFromView(findViewById(R.id.canvasFrameLayout))
                    saveImageFile(image)
                }
            }
        }

        undoImageButton = findViewById(R.id.undoImageButton)
        undoImageButton?.setOnClickListener {
            drawingView?.undo()
        }

        redoImageButton = findViewById(R.id.redoImageButton)
        redoImageButton?.setOnClickListener {
            drawingView?.redo()
        }

        brushSizeButton = findViewById(R.id.brushSizeImageButton)
        brushSizeButton?.setOnClickListener { showBrushSizeDialog() }

        colorPalletLayout = findViewById(R.id.colorPalletLinearLayout)
        colors = arrayListOf<Int>(
            getColor(R.color.skin),
            getColor(R.color.black),
            getColor(R.color.red),
            getColor(R.color.green),
            getColor(R.color.blue),
            getColor(R.color.yellow),
            getColor(R.color.lollipop),
            getColor(R.color.random),
            getColor(R.color.white)
        )
        for (color in colors) {
            val colorButton = ImageButton(this)
            colorPalletLayout?.addView(colorButton)
            val params = colorButton.layoutParams as LinearLayout.LayoutParams
            colorButton.setPadding(0)
            params.leftMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                2f,
                resources.displayMetrics
            ).toInt()
            params.rightMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                2f,
                resources.displayMetrics
            ).toInt()
            params.width = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                24f,
                resources.displayMetrics
            ).toInt()
            params.height = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                24f,
                resources.displayMetrics
            ).toInt()
            colorButton.layoutParams = params
            colorButton.background = color.toDrawable()
            colorButton.setOnClickListener { v ->
                onColorClicked(v, color)
            }
        }
        updateColorButtons()
    }

    private fun showBrushSizeDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size")
        val smallBrushButton = brushDialog.findViewById<ImageButton>(R.id.smallBrushImageButton)
        val mediumBrushButton = brushDialog.findViewById<ImageButton>(R.id.mediumBrushImageButton)
        val largeBrushButton = brushDialog.findViewById<ImageButton>(R.id.largeBrushImageButton)

        smallBrushButton.setOnClickListener {
            drawingView?.setBrushSize(10f)
            brushDialog.dismiss()
        }
        mediumBrushButton.setOnClickListener {
            drawingView?.setBrushSize(20f)
            brushDialog.dismiss()
        }
        largeBrushButton.setOnClickListener {
            drawingView?.setBrushSize(30f)
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    private fun updateColorButtons() {
        val selectedColorIndex = colors.indexOf(currentColor)
        colorPalletLayout?.children?.forEachIndexed { index, child ->
            val colorButton = child as ImageButton
            colorButton.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    if (index == selectedColorIndex) {
                        R.drawable.pallet_pressed
                    } else {
                        R.drawable.pallet_normal
                    }
                )
            )
        }
    }

    private fun onColorClicked(view: View, color: Int) {
        if (color != currentColor) {
            currentColor = color
            drawingView?.setColor(currentColor!!)
            updateColorButtons()
        }
    }

    private fun getBitmapFromView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val backgroundDrawable = view.background
        if (backgroundDrawable != null) {
            backgroundDrawable.draw(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return bitmap
    }

    private suspend fun saveImageFile(bitmap: Bitmap?): String {
        var result = ""
        withContext(Dispatchers.IO) {
            if (bitmap != null) {
                try {
                    val bytes = ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
                    val f =
                        File(
                            externalCacheDir?.absoluteFile.toString()
                                    + File.separator
                                    + "DrawingApp_"
                                    + System.currentTimeMillis() / 1000
                                    + ".png"
                        )
                    val outputStream = FileOutputStream(f)
                    outputStream.write(bytes.toByteArray())
                    outputStream.close()

                    result = f.absolutePath;
                    runOnUiThread {
                        hideProgressDialog()
                        if (result.isNotEmpty()) {
                            Toast.makeText(
                                this@MainActivity,
                                "File saved successfully at: $result",
                                Toast.LENGTH_LONG
                            ).show()
                            shareImage(result)
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "File failed to save",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }

        return result
    }

    private fun showProgressDialog() {
        customProgressDialog = Dialog(this@MainActivity)
        customProgressDialog?.setContentView(R.layout.custom_progress_dialog)
        customProgressDialog?.show()
    }

    private fun hideProgressDialog() {
        customProgressDialog?.dismiss()
        customProgressDialog = null
    }

    private fun shareImage(result: String) {
        MediaScannerConnection.scanFile(this, arrayOf(result), null) {
            path, uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "Share image"))
        }
    }
}