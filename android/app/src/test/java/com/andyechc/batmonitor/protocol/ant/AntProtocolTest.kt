package com.andyechc.batmonitor.protocol.ant

import com.andyechc.batmonitor.ble.BmsType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ANT `7E A1` protocol. The real-time frame is the capture from the
 * `ant-bms-ble` README (ANT-BLE22AAUB, 16 cells, LiFePO4).
 */
class AntProtocolTest {
    private fun hex(value: String): List<Int> {
        val clean = value.filterNot { it.isWhitespace() }
        return (0 until clean.length step 2).map {
            clean.substring(it, it + 2).toInt(16)
        }
    }

    private val capturedRealtime = hex(
        "7ea11100009e01040410000000000000000010008801000000000000000000000000" +
            "ca0cc40cc70ccc0cc50cc90cc30cc80ccb0cc40c9b0cc70cca0cc20cc80cc50c" +
            "1b001d0019001e002e001c006d14b4004800620001010000402c420680908104" +
            "50622500ad0300005a7e170100000000cc0c04009b0c0b003100c40c340034000" +
            "0000000f2fa18100000541000001055220060361e0000000000000000000000f" +
            "b00d3e6aa55",
    )

    @Test
    fun realtimeRequestMatchesTheFixedFrameFromTheApp() {
        assertEquals(
            listOf(0x7E, 0xA1, 0x01, 0x00, 0x00, 0xBE, 0x18, 0x55, 0xAA, 0x55),
            AntCodec.realtimeRequest(),
        )
    }

    @Test
    fun crc16ModbusMatchesTheVerifiedExample() {
        // CRC over A1 01 00 00 BE is 0x5518, transmitted low byte first (18 55).
        assertEquals(
            0x5518,
            AntCrc.crc16(listOf(0xA1, 0x01, 0x00, 0x00, 0xBE), offset = 0, length = 5),
        )
        // And the captured response carries a valid payload CRC.
        assertEquals(
            0xE6D3,
            AntCrc.crc16(capturedRealtime, offset = 1, length = 5 + 0x9E),
        )
        assertEquals(0xD3, capturedRealtime[6 + 0x9E])
        assertEquals(0xE6, capturedRealtime[7 + 0x9E])
    }

    @Test
    fun decodesCapturedRealtimeFrame() {
        val data = AntCodec.decodeRealtime(capturedRealtime)
        assertNotNull(data)
        data!!

        assertEquals(16, data.cells.size)
        assertEquals(3.274, data.cells[0], 0.0001)
        assertEquals(3.227, data.cells[10], 0.0001)
        assertEquals(listOf(27.0, 29.0, 25.0, 30.0), data.temperatures)
        assertEquals(46.0, data.mosTemperature, 0.0001)
        assertEquals(28.0, data.balanceTemperature, 0.0001)
        assertEquals(52.29, data.totalVoltage, 0.0001)
        // ANT reports the protocol sign: positive while discharging.
        assertEquals(18.0, data.current, 0.0001)
        assertEquals(72, data.soc)
        assertEquals(98, data.soh)
        assertEquals(75.6, data.remainingCapacity, 0.0001)
        assertEquals(105.0, data.nominalCapacity, 0.0001)
        assertEquals(4, data.systemState)
        assertTrue(data.chargeMosOn)
        assertTrue(data.dischargeMosOn)
        assertTrue(!data.balancing)
    }

    @Test
    fun rejectsForeignOrTruncatedFrames() {
        assertNull(AntCodec.decodeRealtime(emptyList()))
        assertNull(AntCodec.decodeRealtime(capturedRealtime.take(60)))
        val wrongCommand = capturedRealtime.toMutableList().also { it[2] = 0x12 }
        assertNull(AntCodec.decodeRealtime(wrongCommand))
    }

    @Test
    fun reassemblesNotificationsSplitInTheMiddle() {
        val parser = AntFrameParser()
        val head = capturedRealtime.subList(0, 57)
        val tail = capturedRealtime.subList(57, capturedRealtime.size)

        assertTrue(parser.push(head).isEmpty())
        val frames = parser.push(tail)

        assertEquals(1, frames.size)
        assertEquals(0x11, frames.first().cmd)
        assertEquals(capturedRealtime, frames.first().bytes)
        assertEquals(0x9E, frames.first().payloadLength)
    }

    @Test
    fun skipsGarbageAndKeepsAPartialHeader() {
        val parser = AntFrameParser()
        // Noise plus the first header byte of the next notification.
        assertTrue(parser.push(listOf(0x00, 0x11, 0x22, 0x7E)).isEmpty())
        val frames = parser.push(capturedRealtime.subList(1, capturedRealtime.size))
        assertEquals(1, frames.size)
        assertEquals(capturedRealtime, frames.first().bytes)
    }

    @Test
    fun dropsFramesWithABadCrc() {
        val parser = AntFrameParser()
        val corrupt = capturedRealtime.toMutableList().also { it[100] = it[100] xor 0xFF }
        assertTrue(parser.push(corrupt).isEmpty())
    }

    @Test
    fun reassemblesCaseCFramesWithAnExtraSection() {
        // Case C: header(6) + payload(4) + crc(2) + extra(4+4) + crc(2) + AA 55.
        val payloadLength = 4
        val extra = listOf(0xFF, 0x01, 0x00, 0x04, 0x0A, 0x00, 0x00, 0x00)
        val frame = mutableListOf(0x7E, 0xA1, 0x12, 0x00, 0x00, payloadLength, 1, 2, 3, 4)
        val payloadCrc = AntCrc.crc16(frame, offset = 1, length = 5 + payloadLength)
        frame += listOf(payloadCrc and 0xFF, (payloadCrc shr 8) and 0xFF)
        frame += extra
        val extraCrc = AntCrc.crc16(
            frame,
            offset = 8 + payloadLength,
            length = extra.size,
        )
        frame += listOf(extraCrc and 0xFF, (extraCrc shr 8) and 0xFF, 0xAA, 0x55)

        val frames = AntFrameParser().push(frame)

        assertEquals(1, frames.size)
        assertEquals(0x12, frames.first().cmd)
        assertEquals(frame, frames.first().bytes)
    }

    @Test
    fun decodesEveryRemainingFieldOfTheCapturedFrame() {
        val data = AntCodec.decodeRealtime(capturedRealtime)
        assertNotNull(data)
        data!!

        // Ground truth from decode_realtime() of the reference Python decoder on this frame.
        assertEquals(1, data.sysOperatingAut)
        assertEquals(0L, data.protectionMask)
        assertEquals(25690128L, data.warningMask)
        assertEquals(2450.0, data.lifetimeAh, 0.0001)
        // packPower is signed: positive while discharging.
        assertEquals(941, data.power)
        assertEquals(18316890L, data.uptimeSeconds)
        assertEquals(0L, data.balanceStateBits)

        assertEquals(1, data.dischargeMosState)
        assertEquals(1, data.chargeMosState)
        assertEquals(0, data.balanceState)

        assertEquals(3.276, data.cellVoltageMax, 0.0001)
        assertEquals(4, data.cellVoltageMaxIndex)
        assertEquals(3.227, data.cellVoltageMin, 0.0001)
        assertEquals(11, data.cellVoltageMinIndex)
        assertEquals(0.049, data.cellVoltageDiff, 0.0001)
        assertEquals(3.268, data.cellVoltageAverage, 0.0001)

        assertEquals(52, data.voltageDs)
        assertEquals(52, data.voltageDischargeMos)
        assertEquals(0, data.voltageChargeMos)
        assertEquals(0, data.voltageNhMos)
        assertEquals(64242, data.batteryType)
        assertEquals(4120L, data.lifetimeDischargedAh)
        assertEquals(4180L, data.lifetimeChargedAh)
        assertEquals(2250000L, data.dischargeTimerSeconds)
        assertEquals(1980000L, data.chargeTimerSeconds)

        val remaining = data.remainingTimes
        assertNotNull(remaining)
        remaining!!
        assertEquals(0L, remaining.newChargeTimeSeconds)
        assertEquals(0L, remaining.chargeTimeIntervalSeconds)
        assertEquals(0, remaining.chargeRemainingMinutes)
        assertEquals(251, remaining.dischargeRemainingMinutes)

        // The masks of the capture: no protection, four warnings.
        assertTrue(AntEnums.protectionIndices(data.protectionMask).isEmpty())
        assertEquals(listOf(4, 19, 23, 24), AntEnums.warningIndices(data.warningMask))
        assertEquals(1, AntEnums.batteryTypeIndex(data.batteryType))
    }

    @Test
    fun omitsRemainingTimesWhenThePayloadStopsBeforeThem() {
        // Same frame without the trailing time block (12 payload bytes): keep the header
        // and the fixed tail, then the CRC and the AA 55 tail.
        val cut = capturedRealtime.take(152) + capturedRealtime.drop(164)
        val truncated = cut.toMutableList().also { it[5] = 0x9E - 12 }

        val data = AntCodec.decodeRealtime(truncated)

        assertNotNull(data)
        assertNull(data!!.remainingTimes)
        // The fixed fields are still there.
        assertEquals(64242, data.batteryType)
        assertEquals(941, data.power)
    }

    @Test
    fun mapsActiveMaskBitsToIndexesAndIgnoresUndocumentedOnes() {
        // Protection: bits 0 (cell type) and 13 (charge overcurrent).
        assertEquals(
            listOf(0, 13),
            AntEnums.protectionIndices((1L shl 0) or (1L shl 13)),
        )
        // Warning: bits 4 (cell difference) and 12 (SOC low, level 1).
        assertEquals(
            listOf(4, 12),
            AntEnums.warningIndices((1L shl 4) or (1L shl 12)),
        )
        // The second mask word is shifted by 32 and bit 44 is the last documented one.
        assertEquals(listOf(32, 44), AntEnums.protectionIndices((1L shl 32) or (1L shl 44)))
        // Bits above 44 have no label, so they must not break the list.
        assertTrue(AntEnums.warningIndices(1L shl 45).isEmpty())
        assertTrue(AntEnums.warningIndices(0L).isEmpty())
    }

    @Test
    fun mapsBatteryTypeValuesToArrayIndexes() {
        assertEquals(0, AntEnums.batteryTypeIndex(64241))
        assertEquals(1, AntEnums.batteryTypeIndex(64242))
        assertEquals(5, AntEnums.batteryTypeIndex(64246))
        assertEquals(-1, AntEnums.batteryTypeIndex(64240))
        assertEquals(-1, AntEnums.batteryTypeIndex(64247))
        assertEquals(-1, AntEnums.batteryTypeIndex(0))
    }

    @Test
    fun detectsTheLegacyAntProtocolFromTheDeviceName() {
        // Vendor-app rule: a 10-character "ANT-BLE" name, or a dash at index 10.
        // "ANT-BLE123" is 10 chars total; "ANT-BLE123-456" has '-' at index 10.
        assertTrue(BmsType.isLegacyAnt("ANT-BLE123"))
        assertTrue(BmsType.isLegacyAnt("ANT-BLE123-456"))
        // New protocol.
        assertTrue(!BmsType.isLegacyAnt("ANT-BLE1234"))
        assertTrue(!BmsType.isLegacyAnt("ANT-BLE-2212A"))
        assertTrue(!BmsType.isLegacyAnt("ANT-BLE22AAUB-N88H"))
        assertTrue(!BmsType.isLegacyAnt("ANT@BLE22AAUB-N88H"))
        assertTrue(!BmsType.isLegacyAnt("JBD-SP04S003"))
        assertTrue(!BmsType.isLegacyAnt(null))
        assertTrue(!BmsType.isLegacyAnt("ANT-BLE"))
    }

    @Test
    fun controlFramesMatchTheCapturesFromRealHardware() {
        // Captures from the ESPHome implementation (syssi/esphome-ant-bms),
        // logged from a real ANT-BMS.
        assertEquals(
            listOf(0x7E, 0xA1, 0x51, 0x01, 0x00, 0x00, 0xD8, 0xE5, 0xAA, 0x55),
            AntCodec.controlRequest(AntCodec.ACTION_CLOSE_DISCHARGE),
        )
        assertEquals(
            listOf(0x7E, 0xA1, 0x51, 0x03, 0x00, 0x00, 0x79, 0x25, 0xAA, 0x55),
            AntCodec.controlRequest(AntCodec.ACTION_OPEN_DISCHARGE),
        )
        assertEquals(
            listOf(0x7E, 0xA1, 0x51, 0x04, 0x00, 0x00, 0xC8, 0xE4, 0xAA, 0x55),
            AntCodec.controlRequest(AntCodec.ACTION_CLOSE_CHARGE),
        )
        assertEquals(
            listOf(0x7E, 0xA1, 0x51, 0x06, 0x00, 0x00, 0x69, 0x24, 0xAA, 0x55),
            AntCodec.controlRequest(AntCodec.ACTION_OPEN_CHARGE),
        )
        assertEquals(
            listOf(0x7E, 0xA1, 0x51, 0x0D, 0x00, 0x00, 0x18, 0xE6, 0xAA, 0x55),
            AntCodec.controlRequest(AntCodec.ACTION_BALANCE_ON),
        )
        assertEquals(
            listOf(0x7E, 0xA1, 0x51, 0x0E, 0x00, 0x00, 0xE8, 0xE6, 0xAA, 0x55),
            AntCodec.controlRequest(AntCodec.ACTION_BALANCE_OFF),
        )
    }

    @Test
    fun mosAndBalanceActionsMapCorrectly() {
        assertEquals(AntCodec.ACTION_OPEN_CHARGE, AntCodec.mosAction(charge = true, on = true))
        assertEquals(AntCodec.ACTION_CLOSE_CHARGE, AntCodec.mosAction(charge = true, on = false))
        assertEquals(AntCodec.ACTION_OPEN_DISCHARGE, AntCodec.mosAction(charge = false, on = true))
        assertEquals(AntCodec.ACTION_CLOSE_DISCHARGE, AntCodec.mosAction(charge = false, on = false))
        assertEquals(AntCodec.ACTION_BALANCE_ON, AntCodec.balanceAction(on = true))
        assertEquals(AntCodec.ACTION_BALANCE_OFF, AntCodec.balanceAction(on = false))
        assertEquals(0x61, AntCodec.responseCommand(AntCodec.MOS_CONTROL))
    }

    @Test
    fun controlFramesParseWithTheFrameParser() {
        // The 0x61 ack has the same layout; the parser must accept it. CRC over
        // A1 61 01 00 00 is 0xE5D7 (transmitted D7 E5).
        val ack = hex("7e a1 61 01 00 00 d7 e5 aa 55".replace(" ", ""))
        val frames = AntFrameParser().push(ack)
        assertEquals(1, frames.size)
        assertEquals(0x61, frames[0].cmd)
    }
}
