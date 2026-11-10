package com.patrickreynolds.steady.sensor

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import kotlin.math.sin
import kotlin.random.Random

sealed interface HeartEvent {
    data object Connected : HeartEvent
    data object Disconnected : HeartEvent
    data class Packet(val packet: HrPacket) : HeartEvent
    data class Failed(val message: String) : HeartEvent
}

/** Something that streams heart-rate packets while collected. Collection stops = disconnect. */
fun interface HeartSource {
    fun events(): Flow<HeartEvent>
}

val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
private val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/**
 * Any strap with the standard Bluetooth heart-rate service, by address. The Polar H10 sends RR
 * intervals in every packet. Reconnects by itself if the link drops mid-reading.
 */
@SuppressLint("MissingPermission")
class BleHeartSource(private val context: Context, private val address: String) : HeartSource {
    override fun events(): Flow<HeartEvent> = callbackFlow {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            trySend(HeartEvent.Failed("Bluetooth is off. Turn it on and try again."))
            close(); return@callbackFlow
        }
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> { trySend(HeartEvent.Connected); gatt.discoverServices() }
                    BluetoothProfile.STATE_DISCONNECTED -> { trySend(HeartEvent.Disconnected); gatt.connect() }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val ch = gatt.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT)
                if (ch == null) {
                    trySend(HeartEvent.Failed("This device has no heart-rate service."))
                    return
                }
                gatt.setCharacteristicNotification(ch, true)
                val desc = ch.getDescriptor(CCCD) ?: return
                val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (Build.VERSION.SDK_INT >= 33) {
                    gatt.writeDescriptor(desc, enable)
                } else {
                    @Suppress("DEPRECATION")
                    desc.value = enable
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(desc)
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
                HrParser.parse(value)?.let { trySend(HeartEvent.Packet(it)) }
            }

            @Deprecated("Android 12 and older")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                ch.value?.let { v -> HrParser.parse(v)?.let { trySend(HeartEvent.Packet(it)) } }
            }
        }
        val gatt = adapter.getRemoteDevice(address).connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        awaitClose {
            gatt.disconnect()
            gatt.close()
        }
    }
}

/**
 * A pretend strap for trying the app without an H10 (and on the emulator). Lying: about 63 bpm with
 * breathing-linked variability. After [standAfterMs]: about 82 bpm and less variability.
 */
class DemoHeartSource(private val standAfterMs: Long) : HeartSource {
    override fun events(): Flow<HeartEvent> = flow {
        delay(800)
        emit(HeartEvent.Connected)
        val start = System.currentTimeMillis()
        var phase = 0.0
        while (true) {
            val t = System.currentTimeMillis() - start
            val standing = t > standAfterMs
            val base = if (standing) 730.0 else 950.0
            val swing = if (standing) 12.0 else 35.0
            val rr = mutableListOf<Double>()
            var sum = 0.0
            while (sum < 1000.0) {
                phase += base / 4000.0 * 2 * Math.PI       // ~15 breaths a minute
                val v = base + swing * sin(phase) + Random.nextDouble(-12.0, 12.0)
                rr += v; sum += v
            }
            emit(HeartEvent.Packet(HrPacket((60_000.0 / rr.average()).toInt(), rr, true)))
            delay(sum.toLong())
        }
    }
}

data class FoundSensor(val name: String, val address: String, val rssi: Int)

/** Scans for nearby heart-rate straps. Emits the growing list of what it has found. */
@SuppressLint("MissingPermission")
fun scanForSensors(context: Context): Flow<List<FoundSensor>> = callbackFlow {
    val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
    if (scanner == null) {
        close(IllegalStateException("Bluetooth is off. Turn it on and try again."))
        return@callbackFlow
    }
    val found = linkedMapOf<String, FoundSensor>()
    val cb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: return
            val hasHr = result.scanRecord?.serviceUuids?.contains(ParcelUuid(HR_SERVICE)) == true
            if (!hasHr && !name.startsWith("Polar", ignoreCase = true)) return
            found[result.device.address] = FoundSensor(name, result.device.address, result.rssi)
            trySend(found.values.sortedByDescending { it.rssi })
        }

        override fun onScanFailed(errorCode: Int) {
            close(IllegalStateException("Bluetooth scan failed (code $errorCode)."))
        }
    }
    scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
    awaitClose { runCatching { scanner.stopScan(cb) } }
}
