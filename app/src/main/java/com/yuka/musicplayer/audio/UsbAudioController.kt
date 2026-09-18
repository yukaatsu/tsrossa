package com.yuka.musicplayer.audio

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

class UsbAudioController(context: Context) {

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbDeviceConnection: android.hardware.usb.UsbDeviceConnection? = null

    // Callback when a valid USB Audio device is ready (permission granted and opened)
    var onDeviceReady: ((Int) -> Unit)? = null
    var onDeviceDetached: (() -> Unit)? = null
    var onDeviceAttached: (() -> Unit)? = null

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }

                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let { openDevice(it) }
                        } else {
                            Log.e("UsbAudioController", "Permission denied for device $device")
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (device != null && isAudioDevice(device)) {
                        onDeviceDetached?.invoke()
                        closeDevice()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.i("UsbAudioController", "USB Device attached event received.")
                    onDeviceAttached?.invoke()
                    scanAndRequestPermission()
                }
            }
        }
    }

    init {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)

        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(permissionReceiver, filter)
        }
    }

    fun isConnected(): Boolean = usbDeviceConnection != null

    fun scanAndRequestPermission() {
        if (usbDeviceConnection != null) {
            Log.i("UsbAudioController", "USB Device already opened and active. Skipping redundant scan.")
            return
        }
        val deviceList = usbManager.deviceList
        val audioDevice = deviceList.values.find { isAudioDevice(it) }

        if (audioDevice != null) {
            if (usbManager.hasPermission(audioDevice)) {
                openDevice(audioDevice)
            } else {
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val intent = Intent(ACTION_USB_PERMISSION)
                intent.setPackage(appContext.packageName)
                val permissionIntent = PendingIntent.getBroadcast(
                    appContext, 0, intent, flags
                )
                usbManager.requestPermission(audioDevice, permissionIntent)
            }
        } else {
            Log.e("UsbAudioController", "No USB Audio Device found!")
        }
    }

    private fun isAudioDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
                return true
            }
        }
        return false
    }

    private fun openDevice(device: UsbDevice) {
        usbDeviceConnection = usbManager.openDevice(device)
        if (usbDeviceConnection != null) {
            val fd = usbDeviceConnection!!.fileDescriptor
            Log.i("UsbAudioController", "Device opened successfully. FD: $fd")
            onDeviceReady?.invoke(fd)
        } else {
            Log.e("UsbAudioController", "Failed to open USB device.")
        }
    }

    fun closeDevice() {
        usbDeviceConnection?.close()
        usbDeviceConnection = null
    }

    fun release() {
        closeDevice()
        try {
            appContext.unregisterReceiver(permissionReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.yuka.musicplayer.USB_PERMISSION"
    }
}
