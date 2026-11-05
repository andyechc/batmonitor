package com.andyechc.batmonitor.protocol

object BmsCommand {
    const val BASIC_INFO = 0x03
    const val CELL_VOLTAGES = 0x04
    const val CONTROL = 0x0A
    const val SWITCHES = 0xE1
    const val BLE_NAME = 0x07
}

data class BmsFrame(val status: Int, val cmd: Int, val data: List<Int>)

data class BmsProtection(val labelKey: String, val active: Boolean)

data class BmsBasicInfo(
    val totalVoltage: Double,
    /** Amperes. Positive while charging, negative while discharging. */
    val current: Double,
    /** Remaining capacity in Ah. */
    val remainingCapacity: Double,
    /** Nominal capacity in Ah. */
    val nominalCapacity: Double,
    val cycles: Int,
    val productionDate: String,
    val soc: Int,
    val firmwareVersion: String,
    val chargeOn: Boolean,
    val dischargeOn: Boolean,
    val cellCount: Int,
    val balancing: List<Boolean>,
    val protections: List<BmsProtection>,
    val temperatures: List<Double>,
)

object BmsChecksum {
    /** Request checksum: ~(cmd + len + sum(data)) + 1, low 16 bits. */
    fun request(cmd: Int, data: List<Int>): Int {
        var sum = cmd + data.size
        for (b in data) sum += b
        return (sum.inv() + 1) and 0xFFFF
    }

    /** Response checksum covers only length and data, not cmd or status. */
    fun response(data: List<Int>): Int {
        var sum = data.size
        for (b in data) sum += b
        return (sum.inv() + 1) and 0xFFFF
    }
}

object BmsCodec {
    fun readCommand(cmd: Int): List<Int> {
        val sum = BmsChecksum.request(cmd, emptyList())
        return listOf(0xDD, 0xA5, cmd, 0x00, sum shr 8, sum and 0xFF, 0x77)
    }

    fun writeCommand(cmd: Int, content: List<Int>): List<Int> {
        val sum = BmsChecksum.request(cmd, content)
        return listOf(0xDD, 0x5A, cmd, content.size) + content + listOf(sum shr 8, sum and 0xFF, 0x77)
    }

    /** Charge and discharge MOSFET switches. A bit set to 1 turns that path off. */
    fun switches(charge: Boolean, discharge: Boolean): List<Int> {
        val lock = (if (charge) 0 else 1) or ((if (discharge) 0 else 1) shl 1)
        return writeCommand(BmsCommand.SWITCHES, listOf(lock))
    }

    val balance: List<Int> = writeCommand(BmsCommand.CONTROL, listOf(0x07, 0x00))
    val clearProtection: List<Int> = writeCommand(BmsCommand.CONTROL, listOf(0x04, 0x00))

    /** Bluetooth name stored in the BMS. Uses its own frame format, FF AA 07. */
    fun bleName(name: String): List<Int> {
        val bytes = name.toByteArray(Charsets.UTF_8).take(255).map { it.toInt() and 0xFF }
        val sum = bytes.sum() + ((bytes.size + BmsCommand.BLE_NAME) and 0xFF)
        return listOf(0xFF, 0xAA, BmsCommand.BLE_NAME, bytes.size) + bytes + listOf(sum and 0xFF)
    }

    /** Name-write acknowledgement: FF AA 07 <status>. Status 0 means stored. */
    fun parseNameResponse(bytes: List<Int>): BmsFrame? {
        if (bytes.size < 4 || bytes[0] != 0xFF || bytes[1] != 0xAA || bytes[2] != BmsCommand.BLE_NAME) return null
        return BmsFrame(status = bytes[3], cmd = BmsCommand.BLE_NAME, data = bytes.drop(4))
    }

    fun decodeFrame(bytes: List<Int>): BmsFrame? {
        if (bytes.size < 7 || bytes.first() != 0xDD || bytes.last() != 0x77) return null
        val cmd = bytes[1]
        val status = bytes[2]
        val length = bytes[3]
        if (bytes.size != length + 7) return null
        val data = bytes.subList(4, 4 + length)
        val received = (bytes[4 + length] shl 8) or bytes[5 + length]
        if (BmsChecksum.response(data) != received) return null
        return BmsFrame(status = status, cmd = cmd, data = data)
    }

    fun parseBasicInfo(d: List<Int>): BmsBasicInfo? {
        if (d.size < 23) return null
        val ntcCount = d[22]
        if (d.size < 23 + ntcCount * 2) return null

        fun u16(i: Int): Int = (d[i] shl 8) or d[i + 1]
        fun s16(i: Int): Int = u16(i).toShort().toInt()

        val cellCount = d[21]
        val balanceBytes = listOf(13, 12, 15, 14)
        val balancing = (0 until minOf(cellCount, 32)).map { i ->
            ((d[balanceBytes[i / 8]] shr (i % 8)) and 1) == 1
        }

        val protections = (0 until 13).map { i ->
            val byte = if (i < 8) d[17] else d[16]
            BmsProtection(labelKey = "protection.$i", active = ((byte shr (i % 8)) and 1) == 1)
        }

        val temperatures = (0 until ntcCount).map { i -> (u16(23 + i * 2) - 2731).toDouble() / 10 }

        val dateCode = u16(10)
        val year = (dateCode shr 9) + 2000
        val month = (dateCode shr 5) and 0x0F
        val day = dateCode and 0x1F

        return BmsBasicInfo(
            totalVoltage = u16(0).toDouble() / 100,
            current = s16(2).toDouble() / 100,
            remainingCapacity = u16(4).toDouble() / 100,
            nominalCapacity = u16(6).toDouble() / 100,
            cycles = u16(8),
            productionDate = "%04d-%02d-%02d".format(year, month, day),
            soc = d[19],
            firmwareVersion = "${d[18] shr 4}.${d[18] and 0x0F}",
            chargeOn = (d[20] and 0x01) == 0x01,
            dischargeOn = (d[20] and 0x02) == 0x02,
            cellCount = cellCount,
            balancing = balancing,
            protections = protections,
            temperatures = temperatures,
        )
    }

    /** Cell voltages in volts, one entry per cell. */
    fun parseCellVoltages(d: List<Int>): List<Double> =
        (d.indices step 2).filter { it + 1 < d.size }.map { i -> ((d[i] shl 8) or d[i + 1]).toDouble() / 1000 }
}

/** Reassembles BLE notification chunks into complete BMS response frames. */
class BmsFrameParser {
    private val buffer = ArrayDeque<Int>()

    fun reset() {
        buffer.clear()
    }

    fun push(chunk: List<Int>): List<BmsFrame> {
        buffer.addAll(chunk)
        val frames = mutableListOf<BmsFrame>()
        while (buffer.size >= 7) {
            val start = buffer.indexOf(0xDD)
            if (start == -1) {
                buffer.clear()
                break
            }
            repeat(start) { buffer.removeFirst() }
            if (buffer.size < 4) break
            val total = buffer[3] + 7
            if (buffer.size < total) break
            val candidate = buffer.take(total)
            val frame = BmsCodec.decodeFrame(candidate)
            if (frame != null) {
                frames.add(frame)
                repeat(total) { buffer.removeFirst() }
            } else {
                buffer.removeFirst()
            }
        }
        return frames
    }
}
