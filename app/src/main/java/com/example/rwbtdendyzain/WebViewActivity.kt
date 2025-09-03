package com.example.rwbtdendyzain

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import android.content.Context
import androidx.core.content.ContextCompat

import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import android.graphics.Bitmap
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections

import java.io.File
import java.io.FileOutputStream

import android.print.PrintManager
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo

class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var selectedPrinter: BluetoothDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        webView = findViewById(R.id.webView)
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            ?: run {
                Toast.makeText(this, "Bluetooth tidak tersedia", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        webView.addJavascriptInterface(WebAppInterface(this), "AndroidPrint")
        webView.loadUrl("https://wolverine-dev.ottodigital.id/webview/v2/im3-ppob2?apps_id=PPOB-ISIMPEL&jwt_token=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwOTk2NDc3NDc2NTkzYWExMmFjNjc5M2JjNWI4ZDliMiIsImlwYWRkcmVzcyI6IjE4Mi4yNTMuNTkuMjMiLCJ1c2VyX3R5cGUiOiJpbTMiLCJkZXZpY2VfaWQiOiIyYjNhZGMxOWM2ZDlmNzMxIiwiY3VzdGlkIjoiMjQwYTc2MTgwMTE4MDhjYTY1NWYxMmJhZGVhZmMxNTEiLCJwcm9maWxlcyI6IiIsImxhbmd1YWdlIjoiRU4iLCJzdWJzdHlwZSI6ImRhZDIzMjBlZjJjNGRkYjFmYjA0NWJiYTkxOTYyMTZmIiwiZXhwIjoyNTUyOTgyOTQ4LCJpYXQiOjE3Mjk4NDAxOTYsInZlcnNpb24iOiI4Mi40LjAifQ.Pkd7S_ww4puhbc3aBbZzQEjVwU6E1KN5sCmYZs8KM4k&locale=id&client_id=PEDE")
    }

    inner class WebAppInterface(private val mContext: Context) {

        // 1. Scan printer Bluetooth
        @JavascriptInterface
        fun scanPrinters() {
            runOnUiThread {
                if (!hasBluetoothPermission()) {
                    requestBluetoothPermission()
                    return@runOnUiThread
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        Toast.makeText(applicationContext, "Izin Bluetooth Connect belum diberikan", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }

                    val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
                    val deviceList = pairedDevices?.map { device ->
                        mapOf("name" to device.name, "address" to device.address)
                    } ?: emptyList()

                    val json = deviceList.joinToString(
                        prefix = "[", postfix = "]"
                    ) { """{"name":"${it["name"]}","address":"${it["address"]}"}""" }

                    // kirim balik ke WebView agar tampil modal
                    webView.evaluateJavascript("window.onPrintersFound($json)", null)

                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(applicationContext, "Gagal akses perangkat Bluetooth", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun setPrinter(address: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(
                    mContext,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                runOnUiThread {
                    Toast.makeText(mContext, "Izin Bluetooth Connect belum diberikan", Toast.LENGTH_SHORT).show()
                }
                return
            }

            val device = bluetoothAdapter.bondedDevices.firstOrNull { it.address == address }
            if (device != null) {
                selectedPrinter = device
                runOnUiThread {
                    Toast.makeText(mContext, "Printer dipilih: ${device.name}", Toast.LENGTH_SHORT).show()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(mContext, "Printer tidak ditemukan", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun printImage(base64: String) {
            if (selectedPrinter == null) {
                runOnUiThread {
                    Toast.makeText(mContext, "Belum ada printer yang dipilih", Toast.LENGTH_SHORT).show()
                }
                return
            }
            printBase64(base64)
        }

        // Print image
        private fun printBase64(base64: String) {
            try {
                val printerDevice = selectedPrinter ?: return
                val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

                val connection = BluetoothConnection(printerDevice)
                val printer = EscPosPrinter(connection, 203, 48f, 32)
                val hexImage = PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap)
                printer.printFormattedTextAndCut("[C]<img>$hexImage</img>\n")
            } catch (e: Exception) {
                Toast.makeText(mContext, "Gagal print gambar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun printPdf(base64Pdf: String) {
            try {
                val base64Data = base64Pdf.substring(base64Pdf.indexOf(",") + 1)
                val pdfBytes = Base64.decode(base64Data, Base64.DEFAULT)

                // Simpan sementara
                val pdfFile = File(mContext.cacheDir, "receipt.pdf")
                FileOutputStream(pdfFile).use { it.write(pdfBytes) }

                // Render PDF → Bitmap
                val renderer = PdfRenderer(ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY))
                val page = renderer.openPage(0)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()
                renderer.close()

                // Kirim ke printer ESC/POS
                val connection = BluetoothPrintersConnections.selectFirstPaired()
                if (connection != null) {
                    val printer = EscPosPrinter(connection, 203, 58f, 32)
                    val hexImage = PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap)
                    printer.printFormattedTextAndCut("[L]<img>$hexImage</img>\n")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @JavascriptInterface
        fun printPdfDialog(base64Pdf: String) {
            try {
                val base64Data = base64Pdf.substring(base64Pdf.indexOf(",") + 1)
                val pdfBytes = Base64.decode(base64Data, Base64.DEFAULT)

                // Simpan sementara
                val pdfFile = File(mContext.cacheDir, "receipt.pdf")
                FileOutputStream(pdfFile).use { it.write(pdfBytes) }

                val printManager = mContext.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = PdfDocumentAdapter(mContext, pdfFile.absolutePath)

                // atur ukuran thermal 58mm
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize("58mm", "THERMAL", 216, 10000))
                    // 216 = 58mm dalam points (1 pt = 1/72 inch)
                    // 10000 = panjang fleksibel
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()

                printManager.print("Receipt", printAdapter, attributes)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return perms.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermission() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 101)
    }

}
