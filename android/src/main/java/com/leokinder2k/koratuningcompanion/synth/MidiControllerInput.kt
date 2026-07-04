package com.leokinder2k.koratuningcompanion.synth

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

class MidiControllerInput(
    context: Context,
    private val onEvent: (MidiControlEvent) -> Unit,
    private val onStatus: (String, String?, List<MidiDeviceSummary>, List<UsbDeviceSummary>) -> Unit
) {
    private val appContext = context.applicationContext
    private val midiManager = appContext.getSystemService(MidiManager::class.java)
    private val usbManager = appContext.getSystemService(UsbManager::class.java)
    private val handlerThread = HandlerThread("KoraMidiInput").apply { start() }
    private val handler = Handler(handlerThread.looper)
    private val receiver = ControllerReceiver()

    private var deviceCallback: MidiManager.DeviceCallback? = null
    private var midiDevice: MidiDevice? = null
    private var outputPort: MidiOutputPort? = null

    fun availableDevices(): List<MidiDeviceSummary> {
        return midiManager?.devices
            ?.filter { info -> info.ports.any { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT } }
            ?.map { MidiDeviceSummary(it.id, it.displayName()) }
            ?: emptyList()
    }

    fun usbDevices(): List<UsbDeviceSummary> {
        return usbManager?.deviceList
            ?.values
            ?.map { it.summary() }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun startAutoConnect() {
        registerDeviceCallback()
        connectToFirstAvailable()
    }

    fun refreshAndConnect() {
        connectToFirstAvailable()
    }

    fun disconnect() {
        outputPort?.disconnect(receiver)
        outputPort?.close()
        midiDevice?.close()
        outputPort = null
        midiDevice = null
        emitStatus("MIDI disconnected", null)
    }

    fun close() {
        disconnect()
        deviceCallback?.let { callback ->
            midiManager?.unregisterDeviceCallback(callback)
        }
        deviceCallback = null
        handlerThread.quitSafely()
    }

    private fun registerDeviceCallback() {
        if (deviceCallback != null || midiManager == null) return
        val callback = object : MidiManager.DeviceCallback() {
            override fun onDeviceAdded(device: MidiDeviceInfo) {
                if (midiDevice == null) {
                    connectToFirstAvailable()
                }
            }

            override fun onDeviceRemoved(device: MidiDeviceInfo) {
                val connectedId = midiDevice?.info?.id
                if (connectedId == device.id) {
                    disconnect()
                    connectToFirstAvailable()
                }
            }
        }
        midiManager.registerDeviceCallback(callback, handler)
        deviceCallback = callback
    }

    private fun connectToFirstAvailable() {
        val manager = midiManager
        if (manager == null) {
            emitStatus("MIDI is not available on this device", null)
            return
        }
        val info = manager.devices.firstOrNull { device ->
            device.ports.any { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
        }
        if (info == null) {
            disconnect()
            emitStatus(noMidiStatus(), null)
            return
        }
        if (midiDevice?.info?.id == info.id && outputPort != null) {
            emitStatus("Listening to ${info.displayName()}", info.displayName())
            return
        }
        disconnect()
        emitStatus("Opening ${info.displayName()}", null)
        manager.openDevice(
            info,
            { openedDevice ->
                if (openedDevice == null) {
                    emitStatus("Could not open ${info.displayName()}", null)
                    return@openDevice
                }
                val portNumber = info.ports
                    .firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
                    ?.portNumber
                if (portNumber == null) {
                    openedDevice.close()
                    emitStatus("No MIDI output port on ${info.displayName()}", null)
                    return@openDevice
                }
                val port = openedDevice.openOutputPort(portNumber)
                if (port == null) {
                    openedDevice.close()
                    emitStatus("Could not listen to ${info.displayName()}", null)
                    return@openDevice
                }
                midiDevice = openedDevice
                outputPort = port
                port.connect(receiver)
                emitStatus("Listening to ${info.displayName()}", info.displayName())
            },
            handler
        )
    }

    private fun emitStatus(status: String, connectedName: String?) {
        onStatus(status, connectedName, availableDevices(), usbDevices())
    }

    private fun noMidiStatus(): String {
        val usb = usbDevices()
        return if (usb.isEmpty()) {
            "Connect A-49 by USB OTG or Bluetooth MIDI"
        } else {
            "USB seen, but no MIDI. Set A-49 FUNCTION > ADV > [-], then reconnect"
        }
    }

    private inner class ControllerReceiver : MidiReceiver() {
        private var runningStatus = 0

        override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
            var index = offset
            val end = offset + count
            while (index < end) {
                val first = data[index].toInt() and 0xff
                val status = if (first >= StatusByteStart) {
                    runningStatus = first
                    index += 1
                    first
                } else {
                    runningStatus
                }
                if (status == 0 || index >= end) break
                when (status and StatusCommandMask) {
                    NoteOffStatus -> {
                        if (index + 1 >= end) return
                        val note = data[index].toInt() and 0xff
                        index += 2
                        onEvent(MidiControlEvent.NoteOff(note))
                    }
                    NoteOnStatus -> {
                        if (index + 1 >= end) return
                        val note = data[index].toInt() and 0xff
                        val velocity = data[index + 1].toInt() and 0xff
                        index += 2
                        if (velocity == 0) {
                            onEvent(MidiControlEvent.NoteOff(note))
                        } else {
                            onEvent(MidiControlEvent.NoteOn(note, velocity / 127f))
                        }
                    }
                    ControlChangeStatus -> {
                        if (index + 1 >= end) return
                        val controller = data[index].toInt() and 0xff
                        val value = data[index + 1].toInt() and 0xff
                        index += 2
                        if (controller == SustainPedalController) {
                            onEvent(MidiControlEvent.Sustain(value >= SustainOnValue))
                        }
                    }
                    PitchBendStatus -> {
                        if (index + 1 >= end) return
                        index += 2
                    }
                    ProgramChangeStatus, ChannelPressureStatus -> {
                        index += 1
                    }
                    else -> {
                        index += 1
                    }
                }
            }
        }
    }

    private companion object {
        const val StatusByteStart = 0x80
        const val StatusCommandMask = 0xf0
        const val NoteOffStatus = 0x80
        const val NoteOnStatus = 0x90
        const val ControlChangeStatus = 0xb0
        const val ProgramChangeStatus = 0xc0
        const val ChannelPressureStatus = 0xd0
        const val PitchBendStatus = 0xe0
        const val SustainPedalController = 64
        const val SustainOnValue = 64
    }
}

data class MidiDeviceSummary(
    val id: Int,
    val name: String
)

data class UsbDeviceSummary(
    val vendorId: Int,
    val productId: Int,
    val name: String,
    val deviceClass: Int
)

sealed interface MidiControlEvent {
    data class NoteOn(val note: Int, val velocity: Float) : MidiControlEvent
    data class NoteOff(val note: Int) : MidiControlEvent
    data class Sustain(val enabled: Boolean) : MidiControlEvent
}

private fun MidiDeviceInfo.displayName(): String {
    val props: Bundle = properties
    return listOfNotNull(
        props.getString(MidiDeviceInfo.PROPERTY_NAME),
        props.getString(MidiDeviceInfo.PROPERTY_PRODUCT),
        props.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER)
    ).firstOrNull { it.isNotBlank() } ?: "MIDI device $id"
}

private fun UsbDevice.summary(): UsbDeviceSummary {
    val displayName = listOfNotNull(
        productName,
        manufacturerName,
        deviceName.substringAfterLast('/')
    ).firstOrNull { it.isNotBlank() } ?: "USB device"
    return UsbDeviceSummary(
        vendorId = vendorId,
        productId = productId,
        name = displayName,
        deviceClass = deviceClass
    )
}
