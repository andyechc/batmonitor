package com.andyechc.batmonitor.protocol.ant

/** A complete ANT frame, header to `AA 55` tail included. */
data class AntFrame(val cmd: Int, val bytes: List<Int>) {
    val payloadLength: Int get() = bytes[5] and 0xFF
    val payload: List<Int> get() = bytes.subList(6, 6 + payloadLength)
}

/**
 * Reassembles BLE notifications into complete `7E A1` frames, validating the
 * header, the payload length, both CRCs (payload and optional extra section)
 * and the `AA 55` tail. Mirrors `find_frames()` from the `ant-bms-ble` project.
 *
 * The BMS sends long responses in three layouts:
 *  - Case A: `cmd < 0x10`, fixed 10 bytes, no payload.
 *  - Case B: `header(6) + payload(r) + crc(2) + AA 55`.
 *  - Case C: `header(6) + payload(r) + crc(2) + extra(4+h) + crc(2) + AA 55`.
 */
class AntFrameParser {
    private val buffer = ArrayDeque<Int>()

    fun reset() {
        buffer.clear()
    }

    fun push(chunk: List<Int>): List<AntFrame> {
        buffer.addAll(chunk)
        val frames = mutableListOf<AntFrame>()

        while (buffer.size >= 10) {
            val start = headerIndex()
            if (start < 0) {
                keepPossibleHeaderByte()
                break
            }
            repeat(start) { buffer.removeFirst() }
            if (buffer.size < 10) break

            val cmd = buffer[2] and 0xFF
            if (cmd < 0x10) {
                // Case A: short frame, no payload.
                val tailOk = buffer[8] == 0xAA && buffer[9] == 0x55
                val crcOk = AntCrc.crc16(buffer, offset = 1, length = 5) == readLe16(6)
                if (tailOk && crcOk) {
                    frames.add(AntFrame(cmd, buffer.take(10)))
                    drop(10)
                } else {
                    drop(1)
                }
                continue
            }

            val payloadLength = buffer[5] and 0xFF
            if (payloadLength > 192 || buffer.size < 10 + payloadLength) break

            val payloadCrc = AntCrc.crc16(buffer, offset = 1, length = 5 + payloadLength)
            val payloadCrcOk = payloadCrc == readLe16(6 + payloadLength)

            if (buffer[8 + payloadLength] == 0xAA && buffer[9 + payloadLength] == 0x55) {
                // Case B: payload + CRC + tail.
                if (payloadCrcOk) {
                    frames.add(AntFrame(cmd, buffer.take(10 + payloadLength)))
                    drop(10 + payloadLength)
                } else {
                    drop(1)
                }
                continue
            }

            // Case C: an extra section sits between the payload CRC and the tail.
            if (buffer.size < 12 + payloadLength) break
            val extraLength = buffer[11 + payloadLength] and 0xFF
            val total = 16 + payloadLength + extraLength
            if (buffer.size < total) break
            val tailOk = buffer[14 + payloadLength + extraLength] == 0xAA &&
                buffer[15 + payloadLength + extraLength] == 0x55
            val extraCrcOk = AntCrc.crc16(
                buffer,
                offset = 8 + payloadLength,
                length = 4 + extraLength,
            ) == readLe16(total - 4)
            if (tailOk && payloadCrcOk && extraCrcOk) {
                frames.add(AntFrame(cmd, buffer.take(total)))
                drop(total)
            } else {
                drop(1)
            }
        }
        return frames
    }

    private fun headerIndex(): Int {
        var i = 0
        while (i + 1 < buffer.size) {
            if (buffer[i] == 0x7E && buffer[i + 1] == 0xA1) return i
            i++
        }
        return -1
    }

    /** Keeps a trailing 0x7E in case its 0xA1 arrives in the next notification. */
    private fun keepPossibleHeaderByte() {
        val last = buffer.lastOrNull() ?: return
        buffer.clear()
        if (last == 0x7E) buffer.add(last)
    }

    private fun readLe16(offset: Int): Int =
        (buffer[offset] and 0xFF) or ((buffer[offset + 1] and 0xFF) shl 8)

    private fun drop(count: Int) {
        repeat(count) { buffer.removeFirst() }
    }
}
