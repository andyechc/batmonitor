package com.andyechc.batmonitor.protocol.ant

/**
 * CRC-16/Modbus used by the ANT `7E A1` protocol: polynomial 0x8005 reflected
 * (0xA001), init 0xFFFF, no final XOR.
 *
 * The value returned here is the Modbus register value. ANT frames transmit it
 * **low byte first**, so the wire bytes are `crc and 0xFF` then `crc shr 8`
 * (see [AntCodec] and [AntFrameParser]). Verified against the fixed real-time
 * request `7E A1 01 00 00 BE 18 55 AA 55`: the CRC over `A1 01 00 00 BE` is
 * 0x5518, transmitted as `18 55`.
 */
object AntCrc {
    fun crc16(bytes: List<Int>, offset: Int = 0, length: Int = bytes.size - offset): Int {
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            crc = crc xor (bytes[i] and 0xFF)
            repeat(8) {
                crc = if ((crc and 1) != 0) (crc shr 1) xor 0xA001 else crc shr 1
            }
        }
        return crc and 0xFFFF
    }
}
