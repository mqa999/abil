package com.mywebviewapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.*
import android.webkit.WebChromeClient.FileChooserParams
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var fileUploadCallbackResult: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER_REQUEST_CODE = 1
    private val CAMERA_REQUEST_CODE = 2
    private var cameraPhotoUri: Uri? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        checkAndRequestPermissions()
        setupWebView()
        checkInternetAndLoadSite()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.CAMERA)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.CAMERA)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermissions = permissions.filterIndexed { index, _ ->
                grantResults[index] != PackageManager.PERMISSION_GRANTED
            }
            if (deniedPermissions.isNotEmpty()) {
                Toast.makeText(this, "برای آپلود فایل به دسترسی نیاز است", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    private fun checkInternetAndLoadSite() {
        if (isInternetAvailable()) {
            loadWebsite()
        } else {
            AlertDialog.Builder(this)
                .setTitle("بدون اتصال به اینترنت")
                .setMessage("لطفاً Wi-Fi یا داده همراه خود را روشن کنید و دوباره تلاش کنید.")
                .setPositiveButton("تلاش مجدد") { _, _ -> checkInternetAndLoadSite() }
                .setNegativeButton("خروج") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                defaultTextEncodingName = "utf-8"
                
                // برای آپلود فایل
                setSupportMultipleWindows(true)
            }
            
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    progressBar.visibility = android.view.View.VISIBLE
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar.visibility = android.view.View.GONE
                }
                
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url.toString()
                    if (url.startsWith("tel:") || url.startsWith("mailto:")) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    }
                    return false
                }
            }
            
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                    if (newProgress == 100) {
                        progressBar.visibility = android.view.View.GONE
                    } else {
                        progressBar.visibility = android.view.View.VISIBLE
                    }
                }
                
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    fileUploadCallbackResult = filePathCallback
                    val intent = fileChooserParams?.createIntent() ?: run {
                        val acceptTypes = fileChooserParams?.acceptTypes ?: arrayOf("*/*")
                        val intent = Intent(Intent.ACTION_GET_CONTENT)
                        intent.addCategory(Intent.CATEGORY_OPENABLE)
                        intent.type = if (acceptTypes.isNotEmpty()) acceptTypes[0] else "*/*"
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes)
                        intent
                    }
                    
                    val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    if (cameraIntent.resolveActivity(packageManager) != null) {
                        cameraPhotoUri = createImageFile()
                        if (cameraPhotoUri != null) {
                            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri)
                        }
                    }
                    
                    val chooserIntent = Intent.createChooser(intent, "انتخاب فایل")
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
                    
                    startActivityForResult(chooserIntent, FILE_CHOOSER_REQUEST_CODE)
                    return true
                }
            }
        }
    }
    
    private fun createImageFile(): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
        
        return FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            imageFile
        )
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            var results: Array<Uri>? = null
            
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    val dataUri = data.data
                    if (dataUri != null) {
                        results = arrayOf(dataUri)
                    } else if (data.clipData != null) {
                        val uris = mutableListOf<Uri>()
                        for (i in 0 until data.clipData!!.itemCount) {
                            uris.add(data.clipData!!.getItemAt(i).uri)
                        }
                        results = uris.toTypedArray()
                    }
                } else if (cameraPhotoUri != null) {
                    results = arrayOf(cameraPhotoUri!!)
                }
            }
            
            fileUploadCallbackResult?.onReceiveValue(results)
            fileUploadCallbackResult = null
            cameraPhotoUri = null
        }
    }
    
    private fun loadWebsite() {
        // آدرس سایت مورد نظر خود را اینجا قرار دهید
        val siteUrl = "https://www.google.com/webhp?hl=fa&sa=X&ved=0ahUKEwje-_LM4fSTAxUU9bsIHWdtBuwQPAgJ"
        webView.loadUrl(siteUrl)
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}