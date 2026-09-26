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

                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        log("ACTION_USB_PERMISSION intent received. Extra device: ${device?.productName ?: "null"}, granted: $granted")

                        // Fallback: On some OEM Android 13/14 ROMs (MIUI/HyperOS, OneUI),
                        // EXTRA_DEVICE can be null in the broadcast intent due to classloader isolation.
                        val targetDevice = device ?: usbManager.deviceList.values.find { isAudioDevice(it) }
                        log("Target audio device resolved: ${targetDevice?.productName} (${targetDevice?.deviceName})")

                        if (granted) {
                            if (targetDevice != null) {
                                log("Permission granted! Opening device ${targetDevice.productName}...")
                                openDevice(targetDevice)
                            } else {
                                log("ERROR: Permission granted but targetDevice could not be resolved from deviceList!")
                            }
                        } else {
                            log("ERROR: Permission denied by user or OS for device $targetDevice")
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
                    log("ACTION_USB_DEVICE_DETACHED received for: ${device?.productName ?: "unknown"}")
                    if (device != null && isAudioDevice(device)) {
                        log("USB Audio device detached: $device")
                        onDeviceDetached?.invoke()
                        closeDevice()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    log("ACTION_USB_DEVICE_ATTACHED received. Triggering scanAndRequestPermission...")
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
        log("UsbAudioController initialized and receivers registered.")
    }

    fun isConnected(): Boolean = usbDeviceConnection != null

    fun scanAndRequestPermission() {
        val deviceList = usbManager.deviceList
        log("scanAndRequestPermission: Found ${deviceList.size} USB devices.")
        for ((name, dev) in deviceList) {
            val isAudio = isAudioDevice(dev)
            val vidHex = Integer.toHexString(dev.vendorId).uppercase()
            val pidHex = Integer.toHexString(dev.productId).uppercase()
            log("  Device: '$name' (${dev.productName}) VID=0x$vidHex PID=0x$pidHex isAudio=$isAudio")
        }

        val audioDevice = deviceList.values.find { isAudioDevice(it) }

        if (audioDevice != null) {
            val hasPerm = usbManager.hasPermission(audioDevice)
            log("Audio Device Selected: ${audioDevice.productName} (${audioDevice.deviceName}), hasPermission=$hasPerm")
            
            // If connection exists and permission is still valid, verify or re-open cleanly
            if (usbDeviceConnection != null && hasPerm) {
                log("USB Device already opened (FD=${usbDeviceConnection!!.fileDescriptor}). Re-notifying onDeviceReady.")
                onDeviceReady?.invoke(usbDeviceConnection!!.fileDescriptor)
                return
            }

            // Close stale or wedged connection before requesting / opening
            closeDevice()

            if (hasPerm) {
                log("Permission is already granted. Proceeding to openDevice...")
                openDevice(audioDevice)
            } else {
                log("Requesting USB permission popup for ${audioDevice.productName}...")
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
            log("scanAndRequestPermission: No USB Audio Class device found among ${deviceList.size} devices.")
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
        log("openDevice called for: ${device.productName} (interfaces: ${device.interfaceCount})")
        try {
            usbDeviceConnection = usbManager.openDevice(device)
            if (usbDeviceConnection != null) {
                val fd = usbDeviceConnection!!.fileDescriptor
                log("usbManager.openDevice SUCCESS: FD=$fd")
                for (i in 0 until device.interfaceCount) {
                    val iface = device.getInterface(i)
                    val cls = iface.interfaceClass
                    val sub = iface.interfaceSubclass
                    if (cls == UsbConstants.USB_CLASS_AUDIO) {
                        try {
                            val claimed = usbDeviceConnection!!.claimInterface(iface, true)
                            log("  Iface ${iface.id} (cls=$cls sub=$sub) Java claimInterface(force=true): $claimed")
                        } catch (e: Exception) {
                            log("  Iface ${iface.id} (cls=$cls sub=$sub) Java claimInterface threw: ${e.message}")
                        }
                    } else {
                        log("  Iface ${iface.id} (cls=$cls sub=$sub) non-audio interface skipped")
                    }
                }
                log("Invoking onDeviceReady with FD $fd...")
                onDeviceReady?.invoke(fd)
            } else {
                log("FATAL: usbManager.openDevice returned NULL for ${device.productName}!")
            }
        } catch (e: Exception) {
            log("FATAL: Exception while opening USB device: ${e.message}")
            closeDevice()
        }
    }


    fun closeDevice() {
        log("closeDevice() called. Current connection: $usbDeviceConnection")
        try {
            usbDeviceConnection?.close()
        } catch (e: Exception) {
            log("Error closing usbDeviceConnection: ${e.message}")
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
        private val traceLogs = java.util.Collections.synchronizedList(mutableListOf<String>())

        fun log(msg: String) {
            val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
            val entry = "[$ts] $msg"
            Log.i("UsbAudioController", entry)
            synchronized(traceLogs) {
                if (traceLogs.size > 50) traceLogs.removeAt(0)
                traceLogs.add(entry)
            }
        }

        fun getTrace(): String {
            synchronized(traceLogs) {
                return if (traceLogs.isEmpty()) "No USB events recorded yet." else traceLogs.joinToString("\n")
            }
        }
    }
}

