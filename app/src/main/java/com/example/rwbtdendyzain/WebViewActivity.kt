package com.example.rwbtdendyzain

import android.util.Base64
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.connection.DeviceConnection
import com.dantsu.escposprinter.textparser.PrinterTextParserImg

class WebViewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        val webView: WebView = findViewById(R.id.webView)
        webView.webViewClient = WebViewClient()

        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true

        // JS bisa panggil Android lewat "AndroidPrint"
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidPrint")

        webView.loadUrl("https://rwbt-fe.vercel.app/")
    }

    class WebAppInterface(private val mContext: Context) {

        @JavascriptInterface
        fun printText(text: String) {
            try {
                val printerConnection: DeviceConnection? =
                    BluetoothPrintersConnections.selectFirstPaired()

                if (printerConnection == null) {
                    Toast.makeText(mContext, "Tidak ada printer bluetooth yang terhubung", Toast.LENGTH_SHORT).show()
                    return
                }

                // DPI: 203 untuk printer thermal umum
                // Lebar kertas 58mm -> 48f, jumlah karakter per baris 32
                val printer = EscPosPrinter(printerConnection, 203, 48f, 32)
                printer.printFormattedText(text)

                Toast.makeText(mContext, "Teks berhasil dicetak", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(mContext, "Gagal print: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun printImage(base64: String) {
            try {
                val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

                val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                if (printerConnection == null) {
                    Toast.makeText(mContext, "Tidak ada printer yang terhubung", Toast.LENGTH_SHORT).show()
                    return
                }

                val printer = EscPosPrinter(printerConnection, 203, 48f, 32)
                val hexImage = PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap)

                printer.printFormattedTextAndCut("[C]<img>$hexImage</img>\n")

                Toast.makeText(mContext, "Gambar berhasil dicetak", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(mContext, "Gagal print gambar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
