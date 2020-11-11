package com.mcdevelopers.brotherhoodnotes

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.InputStream
import java.net.URL
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {
    companion object {
        private lateinit var  sharedPreferences: SharedPreferences
       private  var refreshSaved :String? =null
        private var firstRun:Boolean =false
        lateinit var mWebView:WebView
        lateinit var webSettings:WebSettings
    }
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sharedPreferences = this.getSharedPreferences(
            "com.mcdevelopers.brotherhood",
            Context.MODE_PRIVATE
        )
        refreshSaved = sharedPreferences.getString("refreshKey", "")
        firstRun = sharedPreferences.getBoolean("firstRun", true)

        if(firstRun)
        sharedPreferences.edit().putBoolean("firstRun", false).apply()

        println("RefreshSaved:  $refreshSaved ")
         mWebView = findViewById<View>(R.id.webviewmain) as WebView
        mWebView.webChromeClient = WebChromeClient()
        mWebView.webViewClient = WebViewClient()
        val mSwipeRefreshLayout = findViewById<View>(R.id.swiperefresh) as SwipeRefreshLayout
        mWebView.requestFocus()
        mWebView.setBackgroundColor(Color.BLACK)
        mWebView.isSoundEffectsEnabled = true
         webSettings = mWebView.settings
        webSettings.javaScriptEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.domStorageEnabled = true
        webSettings.setGeolocationEnabled(true)

        webSettings.setAppCacheMaxSize(1024 * 1024 * 8);
        webSettings.setAppCachePath("/data/data/" + getPackageName() + "/cache");
        webSettings.setAppCacheEnabled(true);
        webSettings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK




        mSwipeRefreshLayout.setOnRefreshListener {
            webSettings.cacheMode = WebSettings.LOAD_DEFAULT
            mWebView.reload()
        }

        mWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                println("URL Clicked: $url")
                if (url == "https://brotherhoodnotes.000webhostapp.com/") {

                    val builder = AlertDialog.Builder(this@MainActivity)
                    //set title for alert dialog
                    builder.setTitle("Open Link in Browser")
                    //set message for alert dialog
                    builder.setMessage("https://brotherhoodnotes.000webhostapp.com/")
                    builder.setIcon(android.R.drawable.ic_dialog_info)

                    //performing positive action
                    builder.setPositiveButton("Open"){ _, _ ->
                        view.context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        )
                    }
                    //performing cancel action
                    builder.setNeutralButton("Cancel"){dialogInterface , _ ->
                       dialogInterface.dismiss()
                    }
                    //performing negative action
                    builder.setNegativeButton("Copy Link     "){_, _ ->
                        val textToCopy = "https://brotherhoodnotes.000webhostapp.com/"
                        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipData = ClipData.newPlainText("link", textToCopy)
                        clipboardManager.setPrimaryClip(clipData)
                        Toast.makeText(applicationContext, "Link copied to clipboard", Toast.LENGTH_LONG).show()
                        vibratePhone()
                    }
                    // Create the AlertDialog
                    val alertDialog: AlertDialog = builder.create()
                    // Set other dialog properties
                    alertDialog.setCancelable(true)
                    alertDialog.show()



                }else {

                    view.loadUrl(url)

                }
                return true
            }



            override fun onPageFinished(view: WebView, url: String) {

                mSwipeRefreshLayout.isRefreshing = false
            }

            override fun onReceivedError(
                view: WebView,
                errorCode: Int,
                description: String,
                failingUrl: String

            ) {
                Toast.makeText(this@MainActivity, "Error:$description", Toast.LENGTH_LONG).show()
            }

        }


        mWebView.setDownloadListener { url, _, _, _, _ ->
            val fileName = url.substring(url.lastIndexOf('/') + 1, url.length)
            val request = DownloadManager.Request(
                Uri.parse(url)
            )
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            vibratePhone()
            Toast.makeText(applicationContext, "Download Started", Toast.LENGTH_LONG).show()

        }

        mWebView.loadUrl("https://brotherhoodnotes.000webhostapp.com/")
        isStoragePermissionGranted()


        checkUpdate(mWebView, webSettings)
    }


    private fun isStoragePermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                Log.v("PGranted", "Permission is granted")
                true
            } else {
                Log.v("PDenied", "Permission is revoked")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1
                )
                false
            }
        } else { //permission is automatically granted on sdk<23 upon installation
            Log.v("Pgranted", "Permission is granted")
            true
        }
    }


    private fun vibratePhone() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    }


    private fun checkUpdate(mWebView: WebView, mWebSettings: WebSettings) {

        thread {
        try {
            Log.d("CheckUpdate", "checkUpdate:Invoked! ")
            val url = URL(" https://brotherhoodnotes.000webhostapp.com/refresh.txt")
            val inputStream: InputStream = url.openStream()
            val refreshKey :String = inputStream.bufferedReader().readLine()
            println("RefreshRetrieved:  $refreshKey ")

            if(!firstRun) {

                if (refreshKey != refreshSaved) {
                    Log.e("checkUpdate", "checkUpdate: Refresh Page Invoked!! ")
                    runOnUiThread {
                        mWebSettings.cacheMode = WebSettings.LOAD_DEFAULT
                        mWebView.reload()
                        vibratePhone()
                        Toast.makeText(this@MainActivity, "Auto Refreshing...", Toast.LENGTH_LONG).show()
                        sharedPreferences = this.getSharedPreferences(
                            "com.mcdevelopers.brotherhood",
                            Context.MODE_PRIVATE
                        )
                        val editor: SharedPreferences.Editor = sharedPreferences.edit()
                        editor.putString("refreshKey", refreshKey)
                        editor.apply()
                        println("RefreshKeyStored:  $refreshKey ")
                    }

                }
            }else{
                sharedPreferences = this.getSharedPreferences(
                    "com.mcdevelopers.brotherhood",
                    Context.MODE_PRIVATE
                )
                val editor: SharedPreferences.Editor = sharedPreferences.edit()
                editor.putString("refreshKey", refreshKey)
                editor.apply()
                println("RefreshKeyStored:  $refreshKey ")
            }


        } catch (e: Exception) {
            Log.e("errorInFetching", "checkUpdate: Error $e")
        }
        }

    }




}