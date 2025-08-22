package com.example.rwbtdendyzain

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.OutputStream
import java.util.*

class MainActivity : AppCompatActivity() {

    private val REQUEST_BT_PERMISSIONS = 100
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var listDevices: ListView
    private lateinit var btnScan: Button
    private lateinit var btnPrint: Button
    private lateinit var arrayAdapter: ArrayAdapter<String>
    private val deviceList = mutableListOf<BluetoothDevice>()
    private var connectedSocket: BluetoothSocket? = null

    private val uuidSPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        val btnOpenWeb: Button = findViewById(R.id.btnOpenWeb)
        listDevices = findViewById(R.id.listDevices)
        btnScan = findViewById(R.id.btnScan)
        btnPrint = findViewById(R.id.btnPrint)

        btnOpenWeb.setOnClickListener {
            val intent = Intent(this, WebViewActivity::class.java)
            startActivity(intent)
        }


        arrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        listDevices.adapter = arrayAdapter

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            ?: run {
                Toast.makeText(this, "Bluetooth tidak tersedia", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        btnScan.setOnClickListener { scanDevices() }

        listDevices.setOnItemClickListener { _, _, position, _ ->
            if (deviceList.isEmpty()) {
                Toast.makeText(this, "Belum ada perangkat yang discan", Toast.LENGTH_SHORT).show()
                return@setOnItemClickListener
            }
            if (position < 0 || position >= deviceList.size) {
                Toast.makeText(this, "Posisi tidak valid", Toast.LENGTH_SHORT).show()
                return@setOnItemClickListener
            }

            val device = deviceList[position]
            connectToDevice(device)
        }


        btnPrint.setOnClickListener {
            connectedSocket?.let { socket ->
                try {
                    val outputStream: OutputStream = socket.outputStream
                    val message = "Hello from Kotlin!\n\n"
                    outputStream.write(message.toByteArray())
                    Toast.makeText(this, "Printed!", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    Toast.makeText(this, "Gagal print: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun scanDevices() {
        if (!hasBluetoothPermission()) {
            requestBluetoothPermission()
            return
        }

        arrayAdapter.clear()
        deviceList.clear() // ini deviceList milik class

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                101
            )
            return
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        pairedDevices?.forEach { device ->
            arrayAdapter.add("${device.name} - ${device.address}")
            deviceList.add(device) // masukkan ke list global
        }

        if (deviceList.isEmpty()) {
            Toast.makeText(this, "Tidak ada perangkat yang terhubung", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Pilih printer dari daftar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        // Cek permission dulu
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                102
            )
            return
        }

        Thread {
            try {
                val socket = device.createRfcommSocketToServiceRecord(uuidSPP)
                socket.connect()
                connectedSocket = socket
                PrinterConnection.connectedSocket = socket // simpan global

                runOnUiThread {
                    Toast.makeText(this, "Terhubung ke ${device.name}", Toast.LENGTH_SHORT).show()
                    btnPrint.isEnabled = true
                }
            } catch (e: IOException) {
                runOnUiThread {
                    Toast.makeText(this, "Gagal konek: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun hasBluetoothPermission(): Boolean {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return perms.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermission() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), REQUEST_BT_PERMISSIONS)
    }
}
