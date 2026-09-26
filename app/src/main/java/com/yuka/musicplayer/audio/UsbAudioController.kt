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

                        // Fallback: On some OEM Android 13/14 ROMs (MIUI/HyperOS, OneUI),
                        // EXTRA_DEVICE can be null in the broadcast intent due to classloader isolation.
                        val targetDevice = device ?: usbManager.deviceList.values.find { isAudioDevice(it) }

                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            Log.i("UsbAudioController", "Permission granted for device: $targetDevice")
                            targetDevice?.let { openDevice(it) }
                        } else {
                            Log.e("UsbAudioController", "Permission denied for device $targetDevice")
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
                        Log.i("UsbAudioController", "USB Audio device detached: $device")
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
        val deviceList = usbManager.deviceList
        val audioDevice = deviceList.values.find { isAudioDevice(it) }

        if (audioDevice != null) {
            Log.i("UsbAudioController", "Found USB Audio Device: ${audioDevice.productName} (${audioDevice.deviceName})")
            
            // If connection exists and permission is still valid, verify or re-open cleanly
            if (usbDeviceConnection != null && usbManager.hasPermission(audioDevice)) {
                Log.i("UsbAudioController", "USB Device already opened. Re-notifying onDeviceReady.")
                onDeviceReady?.invoke(usbDeviceConnection!!.fileDescriptor)
                return
            }

            // Close stale or wedged connection before requesting / opening
            closeDevice()

            if (usbManager.hasPermission(audioDevice)) {
                Log.i("UsbAudioController", "Permission already granted for $audioDevice. Opening...")
                openDevice(audioDevice)
            } else {
                Log.i("UsbAudioController", "Requesting USB permission for $audioDevice...")
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
            Log.e("UsbAudioController", "No USB Audio Device found in device list (${deviceList.size} USB devices present).")
            closeDevice()
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
        try {
            usbDeviceConnection = usbManager.openDevice(device)
            if (usbDeviceConnection != null) {
                // Claim all audio interfaces using force=true to detach the kernel driver (snd-usb-audio).
                // In Android framework, claimInterface(..., force=true) issues USBDEVFS_DISCONNECT_CLAIM,
                // freeing the interface from AudioFlinger / ALSA contention before passing FD to libusb.
                for (i in 0 until device.interfaceCount) {
                    val iface = device.getInterface(i)
                    if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
                        try {
                            val claimed = usbDeviceConnection!!.claimInterface(iface, true)
                            Log.i("UsbAudioController", "Claimed Audio Interface ${iface.id} (force=true): $claimed")
                        } catch (e: Exception) {
                            Log.w("UsbAudioController", "Failed to force claim interface ${iface.id}: ${e.message}")
                        }
                    }
                }
                val fd = usbDeviceConnection!!.fileDescriptor
                Log.i("UsbAudioController", "Device opened successfully. FD: $fd")
                onDeviceReady?.invoke(fd)
            } else {
                Log.e("UsbAudioController", "Failed to open USB device via usbManager.openDevice(device).")
            }
        } catch (e: Exception) {
            Log.e("UsbAudioController", "Exception while opening USB device: ${e.message}", e)
            closeDevice()
        }
    }

    fun closeDevice() {
        try {
            usbDeviceConnection?.close()
        } catch (e: Exception) {
            Log.w("UsbAudioController", "Error closing usbDeviceConnection: ${e.message}")
        }
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
