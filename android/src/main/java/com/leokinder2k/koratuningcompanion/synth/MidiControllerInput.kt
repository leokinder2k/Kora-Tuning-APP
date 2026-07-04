package com.leokinder2k.koratuningcompanion.synth

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class MidiControllerInput(
    context: Context,
    private val onEvent: (MidiControlEvent) -> Unit,
    private val onStatus: (String, String?, List<MidiDeviceSummary>, List<UsbDeviceSummary>) -> Unit
) {
    private val appContext = context.applicationContext
    private val usbPermissionAction = "${appContext.packageName}.USB_MIDI_PERMISSION"
    private val midiManager = appContext.getSystemService(MidiManager::class.java)
    private val usbManager = appContext.getSystemService(UsbManager::class.java)
    private val handlerThread = HandlerThread("KoraMidiInput").apply { start() }
    private val handler = Handler(handlerThread.looper)
    private val midiParser = MidiMessageParser()
    private val receiver = ControllerReceiver()

    private var deviceCallback: MidiManager.DeviceCallback? = null
    private var usbReceiver: BroadcastReceiver? = null
    private var midiDevice: MidiDevice? = null
    private var outputPort: MidiOutputPort? = null
    private var usbSession: UsbMidiSession? = null

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
        registerUsbReceiver()
        connectToFirstAvailable()
    }

    fun refreshAndConnect() {
        connectToFirstAvailable()
    }

    fun disconnect() {
        disconnectMidiPort()
        disconnectUsbSession()
        emitStatus("MIDI disconnected", null)
    }

    fun close() {
        disconnect()
        deviceCallback?.let { callback ->
            midiManager?.unregisterDeviceCallback(callback)
        }
        deviceCallback = null
        usbReceiver?.let { receiver ->
            runCatching { appContext.unregisterReceiver(receiver) }
        }
        usbReceiver = null
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

    private fun registerUsbReceiver() {
        if (usbReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    usbPermissionAction -> {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        val device = intent.usbDeviceExtra()
                        handler.post {
                            if (granted) {
                                connectToFirstAvailable()
                            } else {
                                emitStatus("USB permission denied for ${device?.usbDisplayName() ?: "controller"}", null)
                            }
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED,
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        handler.post { connectToFirstAvailable() }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(usbPermissionAction)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        usbReceiver = receiver
    }

    private fun connectToFirstAvailable() {
        val manager = midiManager
        val info = manager?.devices?.firstOrNull { device ->
            device.ports.any { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
        }
        if (info == null) {
            disconnectMidiPort()
            connectToUsbMidiFallback()
            return
        }
        disconnectUsbSession()
        if (midiDevice?.info?.id == info.id && outputPort != null) {
            emitStatus("Listening to ${info.displayName()}", info.displayName())
            return
        }
        disconnectMidiPort()
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

    private fun connectToUsbMidiFallback() {
        val manager = usbManager
        if (manager == null) {
            disconnectUsbSession()
            emitStatus("USB host MIDI is not available on this device", null)
            return
        }
        val candidate = usbMidiCandidate()
        if (candidate == null) {
            disconnectUsbSession()
            emitStatus(noMidiStatus(), null)
            return
        }
        val active = usbSession
        if (active?.deviceName == candidate.device.deviceName) {
            emitStatus(active.listeningStatus(), active.label)
            return
        }
        disconnectUsbSession()
        if (!manager.hasPermission(candidate.device)) {
            requestUsbPermission(candidate.device)
            emitStatus("${candidate.compatibility.readyStatus}. Approve Kora USB access if prompted", null)
            return
        }

        val connection = manager.openDevice(candidate.device)
        if (connection == null) {
            emitStatus("Could not open ${candidate.label}", null)
            return
        }
        if (!connection.claimInterface(candidate.midiInterface, true)) {
            connection.close()
            emitStatus("Could not claim ${candidate.label} MIDI interface", null)
            return
        }
        runCatching { connection.setInterface(candidate.midiInterface) }
        usbSession = UsbMidiSession(
            deviceName = candidate.device.deviceName,
            label = candidate.label,
            compatibility = candidate.compatibility,
            connection = connection,
            midiInterface = candidate.midiInterface,
            endpoint = candidate.inputEndpoint
        ).also { it.start() }
        emitStatus(usbSession?.listeningStatus() ?: "Listening to ${candidate.label}", candidate.label)
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val intent = Intent(usbPermissionAction).setPackage(appContext.packageName)
        val pendingIntent = PendingIntent.getBroadcast(appContext, device.deviceId, intent, flags)
        usbManager?.requestPermission(device, pendingIntent)
    }

    private fun usbMidiCandidate(): UsbMidiCandidate? {
        return usbManager?.deviceList
            ?.values
            ?.asSequence()
            ?.mapNotNull { device ->
                val midiEndpoint = device.findUsbMidiInputEndpoint()
                if (midiEndpoint == null) {
                    null
                } else {
                    UsbMidiCandidate(
                        device = device,
                        midiInterface = midiEndpoint.midiInterface,
                        inputEndpoint = midiEndpoint.inputEndpoint,
                        compatibility = midiEndpoint.compatibility,
                        label = device.usbDisplayName()
                    )
                }
            }
            ?.firstOrNull()
    }

    private fun disconnectMidiPort() {
        outputPort?.disconnect(receiver)
        outputPort?.close()
        midiDevice?.close()
        outputPort = null
        midiDevice = null
    }

    private fun disconnectUsbSession() {
        usbSession?.stop()
        usbSession = null
    }

    private fun emitStatus(status: String, connectedName: String?) {
        onStatus(status, connectedName, availableDevices(), usbDevices())
    }

    private fun noMidiStatus(): String {
        val usb = usbDevices()
        return if (usb.isEmpty()) {
            "Connect A-49 by USB OTG or Bluetooth MIDI"
        } else if (usb.any { it.isSupportedMidi }) {
            "USB MIDI ready. Approve Kora USB access if prompted"
        } else {
            "USB visible, but no supported MIDI endpoint. Check A-49 Generic USB mode and OTG/powered hub"
        }
    }

    private inner class ControllerReceiver : MidiReceiver() {
        override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
            midiParser.parse(data, offset, count)
        }
    }

    private inner class MidiMessageParser {
        private var runningStatus = 0

        fun parse(data: ByteArray, offset: Int, count: Int) {
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

    private inner class UsbMidiSession(
        val deviceName: String,
        val label: String,
        val compatibility: UsbMidiCompatibility,
        private val connection: UsbDeviceConnection,
        private val midiInterface: UsbInterface,
        private val endpoint: UsbEndpoint
    ) {
        private val running = AtomicBoolean(false)
        private var readerThread: Thread? = null

        fun start() {
            if (!running.compareAndSet(false, true)) return
            readerThread = Thread({
                val packetSize = endpoint.maxPacketSize.coerceAtLeast(DefaultUsbPacketSize)
                val buffer = ByteArray(packetSize)
                if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                    readInterruptEndpoint(buffer)
                } else {
                    readBulkEndpoint(buffer)
                }
            }, "KoraUsbMidiInput").apply {
                isDaemon = true
                start()
            }
        }

        fun listeningStatus(): String = "Listening to $label (${compatibility.statusSuffix})"

        fun stop() {
            running.set(false)
            runCatching { connection.releaseInterface(midiInterface) }
            runCatching { connection.close() }
            runCatching { readerThread?.join(UsbReaderJoinMs) }
            readerThread = null
        }

        private fun readBulkEndpoint(buffer: ByteArray) {
            while (running.get()) {
                val count = connection.bulkTransfer(endpoint, buffer, buffer.size, UsbReadTimeoutMs)
                if (count > 0) {
                    handleUsbMidiTransfer(buffer, count)
                }
            }
        }

        private fun readInterruptEndpoint(buffer: ByteArray) {
            val request = UsbRequest()
            if (!request.initialize(connection, endpoint)) return
            val byteBuffer = ByteBuffer.wrap(buffer)
            try {
                while (running.get()) {
                    byteBuffer.clear()
                    @Suppress("DEPRECATION")
                    if (!request.queue(byteBuffer, buffer.size)) break
                    val completed = runCatching { connection.requestWait() }.getOrNull()
                    if (completed != request) break
                    val count = byteBuffer.position()
                    if (count > 0) {
                        handleUsbMidiTransfer(buffer, count)
                    }
                }
            } finally {
                request.close()
            }
        }
    }

    private fun handleUsbMidiTransfer(buffer: ByteArray, count: Int) {
        var offset = 0
        while (offset + UsbMidiPacketSize <= count) {
            handleUsbMidiPacket(buffer, offset)
            offset += UsbMidiPacketSize
        }
    }

    private fun handleUsbMidiPacket(buffer: ByteArray, offset: Int) {
        val codeIndexNumber = buffer[offset].toInt() and UsbMidiCodeIndexMask
        val byteCount = when (codeIndexNumber) {
            UsbMidiCinMisc, UsbMidiCinCableEvent -> 0
            UsbMidiCinTwoByteSystemCommon,
            UsbMidiCinSysExEndsTwoBytes,
            UsbMidiCinProgramChange,
            UsbMidiCinChannelPressure -> 2
            UsbMidiCinThreeByteSystemCommon,
            UsbMidiCinSysExStartOrContinue,
            UsbMidiCinSysExEndsThreeBytes,
            UsbMidiCinNoteOff,
            UsbMidiCinNoteOn,
            UsbMidiCinPolyPressure,
            UsbMidiCinControlChange,
            UsbMidiCinPitchBend -> 3
            UsbMidiCinSysExEndsOneByte,
            UsbMidiCinSingleByte -> 1
            else -> 0
        }
        if (byteCount > 0) {
            midiParser.parse(buffer, offset + 1, byteCount)
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
        const val DefaultUsbPacketSize = 64
        const val UsbReadTimeoutMs = 20
        const val UsbReaderJoinMs = 200L
        const val UsbMidiPacketSize = 4
        const val UsbMidiCodeIndexMask = 0x0f
        const val UsbMidiCinMisc = 0x0
        const val UsbMidiCinCableEvent = 0x1
        const val UsbMidiCinTwoByteSystemCommon = 0x2
        const val UsbMidiCinThreeByteSystemCommon = 0x3
        const val UsbMidiCinSysExStartOrContinue = 0x4
        const val UsbMidiCinSysExEndsOneByte = 0x5
        const val UsbMidiCinSysExEndsTwoBytes = 0x6
        const val UsbMidiCinSysExEndsThreeBytes = 0x7
        const val UsbMidiCinNoteOff = 0x8
        const val UsbMidiCinNoteOn = 0x9
        const val UsbMidiCinPolyPressure = 0xa
        const val UsbMidiCinControlChange = 0xb
        const val UsbMidiCinProgramChange = 0xc
        const val UsbMidiCinChannelPressure = 0xd
        const val UsbMidiCinPitchBend = 0xe
        const val UsbMidiCinSingleByte = 0xf
    }
}

private data class UsbMidiEndpointMatch(
    val midiInterface: UsbInterface,
    val inputEndpoint: UsbEndpoint,
    val compatibility: UsbMidiCompatibility
)

private enum class UsbMidiCompatibility(
    val readyStatus: String,
    val statusSuffix: String,
    val summaryTag: String
) {
    ClassCompliant(
        readyStatus = "USB MIDI ready",
        statusSuffix = "USB direct",
        summaryTag = "MIDI"
    ),
    RolandAseries(
        readyStatus = "Roland A-Series MIDI ready",
        statusSuffix = "Roland direct",
        summaryTag = "Roland MIDI"
    )
}

private data class UsbMidiCandidate(
    val device: UsbDevice,
    val midiInterface: UsbInterface,
    val inputEndpoint: UsbEndpoint,
    val compatibility: UsbMidiCompatibility,
    val label: String
)

data class MidiDeviceSummary(
    val id: Int,
    val name: String
)

data class UsbDeviceSummary(
    val vendorId: Int,
    val productId: Int,
    val name: String,
    val deviceClass: Int,
    val isClassCompliantMidi: Boolean,
    val isSupportedMidi: Boolean,
    val midiTag: String?
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
    val midiEndpoint = findUsbMidiInputEndpoint()
    return UsbDeviceSummary(
        vendorId = vendorId,
        productId = productId,
        name = usbDisplayName(),
        deviceClass = deviceClass,
        isClassCompliantMidi = midiEndpoint?.compatibility == UsbMidiCompatibility.ClassCompliant,
        isSupportedMidi = midiEndpoint != null,
        midiTag = midiEndpoint?.compatibility?.summaryTag
    )
}

private fun UsbDevice.usbDisplayName(): String {
    return listOfNotNull(
        productName,
        manufacturerName,
        deviceName.substringAfterLast('/')
    ).firstOrNull { it.isNotBlank() } ?: "USB device"
}

private fun UsbDevice.findUsbMidiInputEndpoint(): UsbMidiEndpointMatch? {
    return findClassCompliantMidiInputEndpoint() ?: findRolandAseriesMidiInputEndpoint()
}

private fun UsbDevice.findClassCompliantMidiInputEndpoint(): UsbMidiEndpointMatch? {
    for (interfaceIndex in 0 until interfaceCount) {
        val usbInterface = getInterface(interfaceIndex)
        if (
            usbInterface.interfaceClass != UsbConstants.USB_CLASS_AUDIO ||
            usbInterface.interfaceSubclass != MidiControllerInputUsbConstants.MidiStreamingSubclass
        ) {
            continue
        }
        val endpoint = usbInterface.findMidiInputEndpoint()
        if (endpoint != null) {
            return UsbMidiEndpointMatch(
                midiInterface = usbInterface,
                inputEndpoint = endpoint,
                compatibility = UsbMidiCompatibility.ClassCompliant
            )
        }
    }
    return null
}

private fun UsbDevice.findRolandAseriesMidiInputEndpoint(): UsbMidiEndpointMatch? {
    if (!isRolandAseriesKeyboard()) return null
    for (interfaceIndex in 0 until interfaceCount) {
        val usbInterface = getInterface(interfaceIndex)
        val endpoint = usbInterface.findMidiInputEndpoint()
        if (endpoint != null) {
            return UsbMidiEndpointMatch(
                midiInterface = usbInterface,
                inputEndpoint = endpoint,
                compatibility = UsbMidiCompatibility.RolandAseries
            )
        }
    }
    return null
}

private fun UsbInterface.findMidiInputEndpoint(): UsbEndpoint? {
    val endpoints = (0 until endpointCount)
        .map { endpointIndex -> getEndpoint(endpointIndex) }
        .filter { endpoint ->
            endpoint.direction == UsbConstants.USB_DIR_IN &&
                (
                    endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK ||
                        endpoint.type == UsbConstants.USB_ENDPOINT_XFER_INT
                    )
        }
    return endpoints.firstOrNull { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
        ?: endpoints.firstOrNull()
}

private fun UsbDevice.isRolandAseriesKeyboard(): Boolean {
    return vendorId == MidiControllerInputUsbConstants.RolandVendorId &&
        productId == MidiControllerInputUsbConstants.RolandAseriesProductId
}

private fun Intent.usbDeviceExtra(): UsbDevice? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(UsbManager.EXTRA_DEVICE)
    }
}

private object MidiControllerInputUsbConstants {
    const val MidiStreamingSubclass = 3
    const val RolandVendorId = 0x0582
    const val RolandAseriesProductId = 0x0156
}
